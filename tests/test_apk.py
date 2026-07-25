"""Lectura de APKs: firma, identidad y elección de build-tools.

Estos tests nacen de un fallo real: el mismo APK pasaba en el portátil del admin
(build-tools 36) y fallaba en la Action (build-tools 37), porque el prefijo de la
salida de apksigner cambia entre versiones y el patrón estaba atado a uno concreto.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from dracapps.apk import (
    _PATRON_CERT,
    _PATRON_DN,
    _PATRON_ETIQUETA,
    _PATRON_MIN_SDK,
    _PATRON_PAQUETE,
    localizar_build_tools,
)
from dracapps.errores import ErrorDeHerramienta

HUELLA = "0e4410d009fa18e09f0b92197693305d4725d01aeaa437b1cc690b8f633e523c"


@pytest.mark.parametrize(
    "salida",
    [
        f"Signer #1 certificate SHA-256 digest: {HUELLA}",
        f"Signer (v3.1) #1 certificate SHA-256 digest: {HUELLA}",
        f"Signer #1 certificate SHA-256 digest: {HUELLA.upper()}",
        f"Signer (minSdkVersion=24) #1 certificate SHA-256 digest: {HUELLA}",
        f"Signer #1 certificate DN: CN=Marc\nSigner #1 certificate SHA-256 digest: {HUELLA}",
    ],
    ids=["clasico", "v3.1", "mayusculas", "con-minsdk", "con-dn"],
)
def test_encuentra_la_huella_diga_apksigner_lo_que_diga(salida):
    encontradas = _PATRON_CERT.findall(salida)

    assert encontradas, "el patrón no puede atarse al formato de una versión"
    assert encontradas[0].lower() == HUELLA


def test_no_se_inventa_una_huella_si_no_la_hay():
    assert _PATRON_CERT.findall("Verified using v2 scheme: true") == []


def test_lee_el_sujeto_del_certificado():
    dn = _PATRON_DN.search("Signer #1 certificate DN: CN=Marc Mayol Orell, C=ES")

    assert dn is not None
    assert dn.group("dn").strip() == "CN=Marc Mayol Orell, C=ES"


def test_lee_la_identidad_que_declara_aapt2():
    salida = (
        "package: name='com.marc.gymplan100' versionCode='4' versionName='1.3' "
        "platformBuildVersionName='16'\n"
        "minSdkVersion:'26'\n"
        "application-label:'Building My Future'\n"
        "application-label-ca:'Building My Future'\n"
    )

    paquete = _PATRON_PAQUETE.search(salida)
    assert paquete.group("id") == "com.marc.gymplan100"
    assert paquete.group("codigo") == "4"
    assert paquete.group("nombre") == "1.3"
    assert _PATRON_MIN_SDK.search(salida).group("min_sdk") == "26"
    assert _PATRON_ETIQUETA.search(salida).group("etiqueta") == "Building My Future"


def test_la_etiqueta_es_la_general_no_la_de_un_idioma():
    """application-label-ca va antes en algunos APKs; manda la sin sufijo."""
    salida = "application-label-ca:'Etiqueta catalana'\napplication-label:'La buena'\n"

    assert _PATRON_ETIQUETA.search(salida).group("etiqueta") == "La buena"


def _sdk_falso(tmp_path: Path, versiones: tuple[str, ...]) -> Path:
    for version in versiones:
        (tmp_path / "build-tools" / version).mkdir(parents=True)
    return tmp_path


def test_sin_version_fijada_coge_la_mas_nueva(tmp_path):
    sdk = _sdk_falso(tmp_path, ("34.0.0", "36.0.0", "35.0.0"))

    assert localizar_build_tools(sdk).name == "36.0.0"


def test_ordena_por_numero_no_por_texto(tmp_path):
    """'9.0.0' es mayor que '10.0.0' si se comparan como texto."""
    sdk = _sdk_falso(tmp_path, ("9.0.0", "10.0.0"))

    assert localizar_build_tools(sdk).name == "10.0.0"


def test_la_version_fijada_manda_sobre_la_mas_nueva(tmp_path):
    sdk = _sdk_falso(tmp_path, ("36.0.0", "37.0.0"))

    assert localizar_build_tools(sdk, version="36.0.0").name == "36.0.0"


def test_la_variable_de_entorno_tambien_fija_la_version(tmp_path, monkeypatch):
    sdk = _sdk_falso(tmp_path, ("36.0.0", "37.0.0"))
    monkeypatch.setenv("DRACAPPS_BUILD_TOOLS", "36.0.0")

    assert localizar_build_tools(sdk).name == "36.0.0"


def test_avisa_si_la_version_fijada_no_esta(tmp_path):
    sdk = _sdk_falso(tmp_path, ("36.0.0",))

    with pytest.raises(ErrorDeHerramienta) as error:
        localizar_build_tools(sdk, version="37.0.0")

    assert "no está instalada" in str(error.value)
    assert "36.0.0" in str(error.value), "el error tiene que decir qué hay"


def test_avisa_si_no_hay_build_tools(tmp_path):
    with pytest.raises(ErrorDeHerramienta) as error:
        localizar_build_tools(tmp_path)

    assert "No hay build-tools" in str(error.value)


def test_avisa_si_el_sdk_no_existe(tmp_path):
    with pytest.raises(ErrorDeHerramienta) as error:
        localizar_build_tools(tmp_path / "sdk-que-no-esta")

    assert "no existe" in str(error.value)
