"""El script de línea de comandos, con GitHub y el SDK doblados.

Lo que importa aquí: que --dry-run no escriba nada nunca, que los fallos salgan como
mensaje y código de salida 1 en vez de como traza de Python, y que --publicar no se
active por error.
"""

from __future__ import annotations

import json

import pytest
from dobles import ApkFalso, montar

import generar_catalogo
from dracapps import generacion

ALTAS = """
catalogo:
  titulo: DracApps
  url_publica: https://marcmayol.github.io/DracApps/catalogo.json
apps:
  - repo: marcmayol/una-app
"""


@pytest.fixture
def entorno(tmp_path, monkeypatch):
    """Un proyecto de mentira con su apps.yaml y GitHub y el SDK doblados."""
    altas = tmp_path / "apps.yaml"
    altas.write_text(ALTAS, encoding="utf-8")
    salida = tmp_path / "docs" / "catalogo.json"

    proveedor, lector = montar(
        {"marcmayol/una-app": ApkFalso("com.una.app", version_code=3, version_name="1.2")}
    )

    monkeypatch.setattr(generar_catalogo, "ProveedorGh", lambda *a, **k: proveedor)
    monkeypatch.setattr(generar_catalogo, "LectorApkDelSdk", lambda *a, **k: _LectorConNombre(lector))

    return {
        "altas": altas,
        "salida": salida,
        "tmp": tmp_path,
        "proveedor": proveedor,
        "argumentos": [
            "--altas", str(altas),
            "--salida", str(salida),
            "--iconos", str(tmp_path / "docs" / "iconos"),
            "--cache", str(tmp_path / ".cache"),
        ],
    }


class _LectorConNombre:
    """El CLI enseña qué build-tools usa; el doble no tiene carpeta, así que se finge."""

    def __init__(self, lector):
        self._lector = lector
        self.build_tools = type("Falsa", (), {"name": "dobles"})()

    def verificar_firma(self, apk):
        return self._lector.verificar_firma(apk)

    def leer_identidad(self, apk):
        return self._lector.leer_identidad(apk)


def test_genera_y_escribe_el_catalogo(entorno, capsys):
    codigo = generar_catalogo.main(entorno["argumentos"])

    assert codigo == 0
    assert entorno["salida"].exists()
    crudo = json.loads(entorno["salida"].read_text(encoding="utf-8"))
    assert crudo["apps"][0]["id"] == "com.una.app"
    assert "Escrito" in capsys.readouterr().out


def test_dry_run_no_escribe_nada(entorno, capsys):
    codigo = generar_catalogo.main([*entorno["argumentos"], "--dry-run"])

    assert codigo == 0
    assert not entorno["salida"].exists()
    assert "no se ha escrito nada" in capsys.readouterr().out


def test_dry_run_manda_sobre_publicar(entorno, capsys, monkeypatch):
    def no_llamar(*args, **kwargs):
        raise AssertionError("--dry-run no puede publicar nunca")

    monkeypatch.setattr("dracapps.publicacion.commitear_y_subir", no_llamar)

    codigo = generar_catalogo.main([*entorno["argumentos"], "--dry-run", "--publicar"])

    assert codigo == 0
    assert "no se ha subido nada" in capsys.readouterr().out


def test_sin_publicar_no_toca_git(entorno, monkeypatch):
    def no_llamar(*args, **kwargs):
        raise AssertionError("sin --publicar no se toca el repositorio")

    monkeypatch.setattr("dracapps.publicacion.commitear_y_subir", no_llamar)

    assert generar_catalogo.main(entorno["argumentos"]) == 0


def test_un_apk_sin_firmar_sale_como_mensaje_y_codigo_1(entorno, capsys, monkeypatch):
    proveedor, lector = montar(
        {"marcmayol/una-app": ApkFalso("com.una.app", version_code=1, firmado=False)}
    )
    monkeypatch.setattr(generar_catalogo, "ProveedorGh", lambda *a, **k: proveedor)
    monkeypatch.setattr(
        generar_catalogo, "LectorApkDelSdk", lambda *a, **k: _LectorConNombre(lector)
    )

    codigo = generar_catalogo.main(entorno["argumentos"])
    salida = capsys.readouterr()

    assert codigo == 1
    assert "ABORTADO" in salida.err
    assert "firma" in salida.err
    assert not entorno["salida"].exists(), "un fallo no puede dejar catálogo a medias"


def test_un_repo_sin_release_sale_como_mensaje_y_codigo_1(entorno, capsys, monkeypatch):
    entorno["proveedor"].releases.clear()

    codigo = generar_catalogo.main(entorno["argumentos"])

    assert codigo == 1
    assert "Release" in capsys.readouterr().err


def test_sin_apps_activas_aborta(tmp_path, capsys):
    altas = tmp_path / "apps.yaml"
    altas.write_text(
        ALTAS.replace("  - repo: marcmayol/una-app", "  - repo: marcmayol/una-app\n    activo: false"),
        encoding="utf-8",
    )

    codigo = generar_catalogo.main(["--altas", str(altas), "--salida", str(tmp_path / "c.json")])

    assert codigo == 1
    assert "ninguna app activa" in capsys.readouterr().err


def test_un_apps_yaml_roto_sale_como_mensaje_y_codigo_1(tmp_path, capsys):
    altas = tmp_path / "apps.yaml"
    altas.write_text("catalogo:\n  titulo: [roto\n", encoding="utf-8")

    codigo = generar_catalogo.main(["--altas", str(altas), "--salida", str(tmp_path / "c.json")])

    assert codigo == 1
    assert "ABORTADO" in capsys.readouterr().err


def test_el_mensaje_de_commit_resume_los_cambios():
    uno = generar_catalogo._mensaje_de_commit(["nueva      com.a 1.0 (1)"])
    varios = generar_catalogo._mensaje_de_commit(
        ["nueva      com.a 1.0 (1)", "actualiza  com.b 1.0 (1) -> 1.1 (2)"]
    )

    assert uno == "Actualiza el catálogo: com.a 1.0 (1)"
    assert varios.startswith("Actualiza el catálogo (2 cambios)")
    assert "- actualiza  com.b" in varios


def test_sin_cambios_el_mensaje_es_neutro():
    assert generar_catalogo._mensaje_de_commit([]) == "Regenera el catálogo"


def test_generacion_no_importa_nada_de_android_ni_de_red():
    """El dominio tiene que poder correr sin SDK ni conexión."""
    fuente = generacion.__file__
    with open(fuente, encoding="utf-8") as f:
        texto = f.read()

    for prohibido in ("subprocess", "urllib", "requests", "import os"):
        assert prohibido not in texto, f"generacion.py no debería usar {prohibido}"
