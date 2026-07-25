"""Qué acepta y qué rechaza apps.yaml.

Un fichero de altas mal escrito tiene que abortar aquí, con un mensaje que diga qué
pasa. Lo que no puede pasar nunca es que un descuido del admin acabe publicado.
"""

from __future__ import annotations

import pytest

from dracapps.altas import leer_altas, validar_altas
from dracapps.errores import ErrorDeAltas

CATALOGO = {"titulo": "DracApps", "url_publica": "https://ejemplo.github.io/D/catalogo.json"}


def altas(apps):
    return {"catalogo": CATALOGO, "apps": apps}


def test_lee_una_app_con_lo_minimo():
    resultado = validar_altas(altas([{"repo": "marcmayol/app"}]))

    assert resultado.titulo == "DracApps"
    assert len(resultado.apps) == 1
    app = resultado.apps[0]
    assert app.repo == "marcmayol/app"
    assert app.activo is True
    assert app.duenyo == "marcmayol"
    assert app.nombre_repo == "app"


def test_las_desactivadas_no_cuentan_como_activas():
    resultado = validar_altas(
        altas([{"repo": "m/a"}, {"repo": "m/b", "activo": False}, {"repo": "m/c"}])
    )

    assert len(resultado.apps) == 3
    assert [app.repo for app in resultado.activas] == ["m/a", "m/c"]


def test_acepta_los_campos_opcionales_y_los_de_extension():
    resultado = validar_altas(
        altas(
            [
                {
                    "repo": "m/a",
                    "nombre": "Otro nombre",
                    "descripcion": "Otra descripción",
                    "notas": "Notas propias",
                    "icono": "https://ejemplo/icono.png",
                    "apk": "elegido.apk",
                    "canal": "estable",
                    "min_sdk": 26,
                }
            ]
        )
    )

    app = resultado.apps[0]
    assert app.nombre == "Otro nombre"
    assert app.apk == "elegido.apk"
    assert app.canal == "estable"
    assert app.min_sdk == 26


def test_los_textos_vacios_valen_como_no_puestos():
    resultado = validar_altas(altas([{"repo": "m/a", "nombre": "   ", "notas": ""}]))

    assert resultado.apps[0].nombre is None
    assert resultado.apps[0].notas is None


@pytest.mark.parametrize(
    "entrada, esperado",
    [
        (None, "vacío"),
        ("una cadena", "mapa"),
        ({"apps": []}, "sección 'catalogo'"),
        ({"catalogo": CATALOGO}, "lista 'apps'"),
        ({"catalogo": CATALOGO, "apps": {}}, "lista"),
        ({"catalogo": CATALOGO, "apps": [], "otra": 1}, "no reconozco"),
    ],
)
def test_rechaza_estructuras_que_no_son_el_formato(entrada, esperado):
    with pytest.raises(ErrorDeAltas) as error:
        validar_altas(entrada)

    assert esperado in str(error.value)


@pytest.mark.parametrize(
    "app, esperado",
    [
        ({}, "le falta 'repo'"),
        ({"repo": "sin-barra"}, "forma 'owner/nombre'"),
        ({"repo": "https://github.com/m/a"}, "forma 'owner/nombre'"),
        ({"repo": "m/a", "activo": "si"}, "true o false"),
        ({"repo": "m/a", "min_sdk": "26"}, "número entero"),
        ({"repo": "m/a", "nombre": 3}, "tiene que ser texto"),
        ({"repo": "m/a", "icono": "http://inseguro/i.png"}, "URL https"),
        ({"repo": "m/a", "inventado": 1}, "no reconozco"),
    ],
)
def test_rechaza_apps_mal_declaradas(app, esperado):
    with pytest.raises(ErrorDeAltas) as error:
        validar_altas(altas([app]))

    assert esperado in str(error.value)


def test_rechaza_el_mismo_repo_dos_veces_aunque_cambie_la_caja():
    with pytest.raises(ErrorDeAltas) as error:
        validar_altas(altas([{"repo": "marcmayol/app"}, {"repo": "MarcMayol/App"}]))

    assert "dos veces" in str(error.value)


def test_rechaza_url_publica_que_no_es_https():
    with pytest.raises(ErrorDeAltas) as error:
        validar_altas({"catalogo": {"titulo": "X", "url_publica": "http://x/c.json"}, "apps": []})

    assert "URL https" in str(error.value)


def test_el_error_dice_que_hacer():
    with pytest.raises(ErrorDeAltas) as error:
        validar_altas(altas([{"repo": "mal"}]))

    assert "->" in str(error.value)


def test_avisa_si_el_fichero_no_existe(tmp_path):
    with pytest.raises(ErrorDeAltas) as error:
        leer_altas(tmp_path / "no-existe.yaml")

    assert "No encuentro" in str(error.value)


def test_avisa_si_el_yaml_esta_roto(tmp_path):
    fichero = tmp_path / "apps.yaml"
    fichero.write_text("catalogo:\n  titulo: [sin cerrar\n", encoding="utf-8")

    with pytest.raises(ErrorDeAltas) as error:
        leer_altas(fichero)

    assert "no es YAML válido" in str(error.value)


def test_el_apps_yaml_de_verdad_es_valido():
    """El apps.yaml del repo tiene que pasar su propia validación."""
    from pathlib import Path

    raiz = Path(__file__).resolve().parent.parent
    resultado = leer_altas(raiz / "apps.yaml")

    assert resultado.titulo == "DracApps"
    assert resultado.url_publica.startswith("https://")
    assert resultado.activas, "el catálogo se quedaría vacío"
