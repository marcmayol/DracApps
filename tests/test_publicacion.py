"""Publicar es que la URL pública sirva el catálogo nuevo, no que el push salga bien.

Estos tests cubren lo que de verdad puede fallar: que Pages tarde, que el CDN siga
sirviendo lo viejo, o que la URL no responda un momento.
"""

from __future__ import annotations

import pytest

from dracapps.errores import ErrorDePublicacion
from dracapps.publicacion import verificar_publicado

CATALOGO = '{"version": 1, "apps": []}\n'
VIEJO = b'{"version": 1, "apps": ["lo de antes"]}\n'


def verificar(respuestas, esperas=(1, 1, 1, 1)):
    """Verifica contra una URL que devuelve, en orden, lo que diga `respuestas`."""
    pendientes = list(respuestas)
    dormidas = []

    def descargar(url):
        siguiente = pendientes.pop(0)
        if isinstance(siguiente, Exception):
            raise siguiente
        return siguiente

    intentos = verificar_publicado(
        "https://ejemplo.github.io/D/catalogo.json",
        CATALOGO,
        esperas=esperas,
        descargar=descargar,
        dormir=dormidas.append,
        avisar=lambda mensaje: None,
    )
    return intentos, dormidas


def test_si_la_url_ya_sirve_lo_nuevo_no_espera():
    intentos, dormidas = verificar([CATALOGO.encode()])

    assert intentos == 1
    assert dormidas == []


def test_espera_mientras_el_cdn_sirve_lo_viejo():
    intentos, dormidas = verificar([VIEJO, VIEJO, CATALOGO.encode()])

    assert intentos == 3
    assert len(dormidas) == 2


def test_aguanta_que_la_url_no_responda_un_momento():
    """Pages devuelve 404 mientras construye; eso no es un fallo definitivo."""
    intentos, _ = verificar([OSError("HTTP 404"), CATALOGO.encode()])

    assert intentos == 2


def test_aborta_si_nunca_llega_a_servirse():
    with pytest.raises(ErrorDePublicacion) as error:
        verificar([VIEJO] * 4, esperas=(1, 1, 1, 1))

    mensaje = str(error.value)
    assert "no lo sirve todavía" in mensaje
    assert "el commit ya está subido" in mensaje, "el remedio tiene que tranquilizar"


def test_no_da_por_bueno_un_catalogo_parecido():
    """Compara por hash: un byte distinto es un fichero distinto."""
    casi = CATALOGO.replace('"apps": []', '"apps": [ ]').encode()

    with pytest.raises(ErrorDePublicacion):
        verificar([casi] * 4, esperas=(1, 1, 1, 1))


def test_las_esperas_van_creciendo():
    from dracapps.publicacion import ESPERAS

    assert list(ESPERAS) == sorted(ESPERAS), "esperar cada vez más da tiempo a Pages"
    assert sum(ESPERAS) >= 180, "tiene que aguantar varios minutos de CDN"


# --- Publicar desde dos sitios a la vez ------------------------------------------
#
# Nació de un caso real: se publicó Kuse desde un sitio y LunAlign desde otro. El
# segundo push fue rechazado por no ser avance rápido y el catálogo público se quedó
# dos versiones atrás. El script avisaba, pero el remedio era manual.
#
# Ahora se reordena sobre lo remoto antes de empujar. El catálogo es un fichero
# generado y el recién generado es el bueno: acaba de leer TODAS las releases, así
# que ya incluye lo que publicó el otro.

from dracapps.publicacion import commitear_y_subir


class GitFalso:
    """Un git de mentira que apunta lo que le piden y falla cuando se le dice."""

    def __init__(self, fallan=()):
        self.ordenes = []
        self.fallan = list(fallan)

    def __call__(self, argumentos):
        self.ordenes.append(" ".join(argumentos))
        for patron in list(self.fallan):
            if patron in " ".join(argumentos):
                self.fallan.remove(patron)
                raise ErrorDePublicacion(f"falla '{patron}'", "remedio")
        if argumentos[:2] == ["status", "--porcelain"]:
            return " M docs/catalogo.json\n"
        if argumentos[0] == "rev-parse" and "--abbrev-ref" in argumentos:
            return "main\n"
        if argumentos[0] == "rev-parse":
            return "abc1234\n"
        return ""


def test_se_reordena_sobre_lo_remoto_antes_de_empujar():
    git = GitFalso()

    hubo, commit = commitear_y_subir(None, [], "mensaje", ejecutar_git=git)

    assert hubo is True
    assert commit == "abc1234"
    orden = [o for o in git.ordenes if o.startswith(("pull", "push"))]
    assert orden[0].startswith("pull --rebase"), f"orden real: {git.ordenes}"
    assert orden[1].startswith("push"), "el push tiene que ir DESPUÉS del pull"


def test_el_rebase_prefiere_el_catalogo_recien_generado():
    # Acaba de leer todas las releases: ya incluye lo que publicó el otro.
    git = GitFalso()
    commitear_y_subir(None, [], "mensaje", ejecutar_git=git)

    pull = next(o for o in git.ordenes if o.startswith("pull"))
    assert "-X theirs" in pull


def test_si_el_rebase_falla_se_deshace_y_se_avisa():
    git = GitFalso(fallan=["pull --rebase"])

    with pytest.raises(ErrorDePublicacion) as fallo:
        commitear_y_subir(None, [], "mensaje", ejecutar_git=git)

    assert "rebase --abort" in " ".join(git.ordenes), "hay que dejar el repo como estaba"
    assert "a mano" in str(fallo.value).lower() or "conflicto" in str(fallo.value).lower()


def test_sin_cambios_no_toca_el_remoto():
    class GitLimpio(GitFalso):
        def __call__(self, argumentos):
            self.ordenes.append(" ".join(argumentos))
            if argumentos[:2] == ["status", "--porcelain"]:
                return ""
            return ""

    git = GitLimpio()
    hubo, commit = commitear_y_subir(None, [], "mensaje", ejecutar_git=git)

    assert hubo is False and commit is None
    assert not any(o.startswith(("pull", "push")) for o in git.ordenes)
