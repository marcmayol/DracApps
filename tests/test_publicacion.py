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
