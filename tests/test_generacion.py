"""El generador, de apps.yaml al catálogo, sin red ni SDK.

Lo que se comprueba aquí es lo que protege a los móviles de la familia: que un APK
sin firmar no llega nunca al catálogo, que la versión sale del propio APK y no del
nombre del fichero, y que una versión publicada no puede retroceder.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone

import pytest
from dobles import ApkFalso, montar, release_con_apk

from dracapps.altas import validar_altas
from dracapps.catalogo import leer_catalogo_publicado
from dracapps.errores import ErrorDeApk, ErrorDeCatalogo, ErrorDeRelease, ErrorDeVersion
from dracapps.generacion import generar

MOMENTO = datetime(2026, 7, 25, 10, 0, 0, tzinfo=timezone.utc)
CATALOGO = {"titulo": "DracApps", "url_publica": "https://marcmayol.github.io/DracApps/catalogo.json"}


def altas_de(*repos, **campos):
    apps = [{"repo": repo, **campos.get(repo, {})} for repo in repos]
    return validar_altas({"catalogo": CATALOGO, "apps": apps})


def generar_con(altas, proveedor, lector, tmp_path, anterior=None):
    return generar(
        altas=altas,
        proveedor=proveedor,
        lector=lector,
        directorio_cache=tmp_path / "cache",
        directorio_iconos=tmp_path / "iconos",
        catalogo_anterior=anterior,
        momento=MOMENTO,
    )


def test_publica_la_identidad_que_dice_el_apk_no_la_del_fichero(tmp_path):
    """El nombre del fichero dice 9.9; el APK dice versionCode 4. Manda el APK."""
    proveedor, lector = montar(
        {"m/gym": ApkFalso("com.marc.gymplan100", version_code=4, version_name="1.3",
                           etiqueta="Building My Future")},
        etiquetas={"m/gym": "v9.9"},
        nombres_apk={"m/gym": "app-v9.9-final.apk"},
    )

    resultado = generar_con(altas_de("m/gym"), proveedor, lector, tmp_path)

    app = resultado.catalogo.apps[0]
    assert app.id == "com.marc.gymplan100"
    assert app.versionCode == 4
    assert app.versionName == "1.3"
    assert app.nombre == "Building My Future"


def test_un_apk_sin_firmar_aborta_y_no_publica_nada(tmp_path):
    proveedor, lector = montar(
        {
            "m/buena": ApkFalso("com.buena", version_code=1),
            "m/mala": ApkFalso("com.mala", version_code=1, firmado=False),
        }
    )

    with pytest.raises(ErrorDeApk) as error:
        generar_con(altas_de("m/buena", "m/mala"), proveedor, lector, tmp_path)

    assert "verificación de firma" in str(error.value)


def test_la_firma_se_comprueba_antes_de_hacerle_caso_al_apk(tmp_path):
    """Un APK sin firmar no llega ni a que le pregunten la versión."""
    proveedor, lector = montar({"m/mala": ApkFalso("com.mala", version_code=1, firmado=False)})

    with pytest.raises(ErrorDeApk):
        generar_con(altas_de("m/mala"), proveedor, lector, tmp_path)

    assert lector.firmas_verificadas == ["com.mala"]


def test_publica_la_huella_del_certificado(tmp_path):
    proveedor, lector = montar(
        {"m/a": ApkFalso("com.a", version_code=1, firma_sha256="b" * 64)}
    )

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    assert resultado.catalogo.apps[0].firmaSha256 == "b" * 64


def test_un_repo_sin_release_aborta_con_su_mensaje(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    altas = altas_de("m/a", "m/sin-release")
    proveedor.apks["m/sin-release"] = ApkFalso("com.x", version_code=1)

    with pytest.raises(ErrorDeRelease) as error:
        generar_con(altas, proveedor, lector, tmp_path)

    assert "no tiene ninguna Release" in str(error.value)


def test_una_release_sin_apk_aborta(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    proveedor.releases["m/a"] = release_con_apk("m/a", nombre_apk="notas.txt")

    with pytest.raises(ErrorDeRelease) as error:
        generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    assert "ningún APK" in str(error.value)


def test_con_varios_apks_pide_elegir_en_vez_de_adivinar(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    proveedor.releases["m/a"] = release_con_apk("m/a", extras=("otra.apk",))

    with pytest.raises(ErrorDeRelease) as error:
        generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    assert "no sé cuál publicar" in str(error.value)
    assert "apk:" in str(error.value)


def test_el_campo_apk_desempata(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    proveedor.releases["m/a"] = release_con_apk("m/a", extras=("otra.apk",))

    altas = altas_de("m/a", **{"m/a": {"apk": "app-release.apk"}})
    resultado = generar_con(altas, proveedor, lector, tmp_path)

    assert resultado.catalogo.apps[0].apkUrl.endswith("app-release.apk")


def test_el_campo_apk_admite_comodines(tmp_path):
    # El nombre del asset lleva la versión dentro ("...-v1.5.apk"): sin patrón habría
    # que editar apps.yaml en cada versión nueva y el catálogo se rompería solo.
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    proveedor.releases["m/a"] = release_con_apk(
        "m/a", nombre_apk="gym-v1.5.apk", extras=("gym-reloj-v1.5.apk",)
    )

    altas = altas_de("m/a", **{"m/a": {"apk": "gym-v*.apk"}})
    resultado = generar_con(altas, proveedor, lector, tmp_path)

    assert resultado.catalogo.apps[0].apkUrl.endswith("gym-v1.5.apk")


def test_un_patron_ambiguo_aborta_en_vez_de_elegir_al_azar(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    proveedor.releases["m/a"] = release_con_apk(
        "m/a", nombre_apk="gym-v1.5.apk", extras=("gym-v1.5-beta.apk",)
    )

    with pytest.raises(ErrorDeRelease) as error:
        generar_con(altas_de("m/a", **{"m/a": {"apk": "gym-v*.apk"}}), proveedor, lector, tmp_path)

    assert "encaja con 2" in str(error.value)


def test_el_version_code_no_puede_retroceder(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=3)})
    publicado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo

    proveedor2, lector2 = montar({"m/a": ApkFalso("com.a", version_code=2)})

    with pytest.raises(ErrorDeVersion) as error:
        generar_con(altas_de("m/a"), proveedor2, lector2, tmp_path, anterior=publicado)

    assert "retrocede" in str(error.value)


def test_el_mismo_version_code_se_admite(tmp_path):
    """Regenerar sin que haya salido versión nueva es lo normal, no un error."""
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=3)})
    publicado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path, anterior=publicado)

    assert resultado.catalogo.apps[0].versionCode == 3


def test_dos_apps_con_el_mismo_application_id_abortan(tmp_path):
    proveedor, lector = montar(
        {
            "m/a": ApkFalso("com.repetido", version_code=1),
            "m/b": ApkFalso("com.repetido", version_code=2),
        }
    )

    with pytest.raises(ErrorDeCatalogo) as error:
        generar_con(altas_de("m/a", "m/b"), proveedor, lector, tmp_path)

    assert "mismo applicationId" in str(error.value)


def test_las_apps_desactivadas_no_se_descargan_siquiera(tmp_path):
    proveedor, lector = montar(
        {"m/a": ApkFalso("com.a", version_code=1), "m/b": ApkFalso("com.b", version_code=1)}
    )
    altas = altas_de("m/a", "m/b", **{"m/b": {"activo": False}})

    resultado = generar_con(altas, proveedor, lector, tmp_path)

    assert [app.id for app in resultado.catalogo.apps] == ["com.a"]
    assert proveedor.descargas == ["m/a:app-release.apk"]
    assert any("desactivadas" in aviso for aviso in resultado.avisos)


def test_sin_icono_avisa_pero_publica(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    assert resultado.catalogo.apps[0].iconoUrl == ""
    assert any("sin icono" in aviso for aviso in resultado.avisos)


def test_usa_el_icono_de_docs_si_esta(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    iconos = tmp_path / "iconos"
    iconos.mkdir()
    (iconos / "com.a.png").write_bytes(b"png")

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    assert resultado.catalogo.apps[0].iconoUrl == (
        "https://marcmayol.github.io/DracApps/iconos/com.a.png"
    )
    assert not any("sin icono" in aviso for aviso in resultado.avisos)


def test_el_icono_de_apps_yaml_manda_sobre_el_de_docs(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    iconos = tmp_path / "iconos"
    iconos.mkdir()
    (iconos / "com.a.png").write_bytes(b"png")

    altas = altas_de("m/a", **{"m/a": {"icono": "https://otro/sitio.png"}})
    resultado = generar_con(altas, proveedor, lector, tmp_path)

    assert resultado.catalogo.apps[0].iconoUrl == "https://otro/sitio.png"


def test_avisa_si_el_repo_es_privado(tmp_path):
    from dracapps.releases import InfoRepo

    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    proveedor.repos["m/a"] = InfoRepo(nombre="a", descripcion="", privado=True)

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    assert any("privado" in aviso for aviso in resultado.avisos)


def test_las_notas_salen_de_la_release_y_apps_yaml_las_sustituye(tmp_path):
    proveedor, lector = montar(
        {"m/a": ApkFalso("com.a", version_code=1), "m/b": ApkFalso("com.b", version_code=1)}
    )
    altas = altas_de("m/a", "m/b", **{"m/b": {"notas": "Notas escritas a mano"}})

    resultado = generar_con(altas, proveedor, lector, tmp_path)
    por_id = resultado.catalogo.por_id()

    assert por_id["com.a"].notas == "Notas de la versión."
    assert por_id["com.b"].notas == "Notas escritas a mano"


def test_el_catalogo_sale_ordenado_y_es_json_valido(tmp_path):
    proveedor, lector = montar(
        {
            "m/z": ApkFalso("com.zeta", version_code=1),
            "m/a": ApkFalso("com.alfa", version_code=1),
        }
    )

    resultado = generar_con(altas_de("m/z", "m/a"), proveedor, lector, tmp_path)
    crudo = json.loads(resultado.catalogo.a_json())

    assert [app["id"] for app in crudo["apps"]] == ["com.alfa", "com.zeta"]
    assert crudo["generado"] == "2026-07-25T10:00:00Z"
    assert crudo["version"] == 1


def test_publica_todos_los_campos_del_contrato(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=7, version_name="2.1")})

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path)
    app = json.loads(resultado.catalogo.a_json())["apps"][0]

    assert set(app) == {
        "id", "nombre", "descripcion", "iconoUrl", "versionCode", "versionName",
        "apkUrl", "sha256", "firmaSha256", "tamanoBytes", "notas", "canal", "minSdk",
    }
    assert app["canal"] is None and app["minSdk"] is None, "campos de extensión declarados"
    assert len(app["sha256"]) == 64
    assert app["tamanoBytes"] > 0


def test_el_sha256_es_el_del_apk_descargado(tmp_path):
    import hashlib

    apk = ApkFalso("com.a", version_code=1, contenido=b"contenido concreto del apk")
    proveedor, lector = montar({"m/a": apk})

    resultado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path)

    esperado = hashlib.sha256(b"contenido concreto del apk").hexdigest()
    assert resultado.catalogo.apps[0].sha256 == esperado


def test_generar_dos_veces_da_exactamente_los_mismos_bytes(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})

    primero = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo
    segundo = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo

    assert primero.a_json() == segundo.a_json()


def test_regenerar_sin_novedades_conserva_la_fecha(tmp_path):
    """Si nada cambia, el fichero no cambia: ni diffs falsos ni commits vacíos."""
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    publicado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo

    despues = generar(
        altas=altas_de("m/a"),
        proveedor=proveedor,
        lector=lector,
        directorio_cache=tmp_path / "cache",
        directorio_iconos=tmp_path / "iconos",
        catalogo_anterior=publicado,
        momento=datetime(2027, 1, 1, tzinfo=timezone.utc),
    ).catalogo

    assert despues.a_json() == publicado.a_json()


def test_una_version_nueva_si_actualiza_la_fecha(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=1)})
    publicado = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo

    proveedor2, lector2 = montar({"m/a": ApkFalso("com.a", version_code=2)})
    despues = generar(
        altas=altas_de("m/a"),
        proveedor=proveedor2,
        lector=lector2,
        directorio_cache=tmp_path / "cache",
        directorio_iconos=tmp_path / "iconos",
        catalogo_anterior=publicado,
        momento=datetime(2027, 1, 1, tzinfo=timezone.utc),
    ).catalogo

    assert despues.generado == "2027-01-01T00:00:00Z"


def test_el_catalogo_escrito_se_puede_volver_a_leer(tmp_path):
    proveedor, lector = montar({"m/a": ApkFalso("com.a", version_code=5)})
    catalogo = generar_con(altas_de("m/a"), proveedor, lector, tmp_path).catalogo

    fichero = tmp_path / "catalogo.json"
    fichero.write_text(catalogo.a_json(), encoding="utf-8", newline="\n")
    releido = leer_catalogo_publicado(fichero)

    assert releido is not None
    assert releido.a_json() == catalogo.a_json()


def test_un_catalogo_corrupto_se_trata_como_si_no_hubiera(tmp_path):
    """Se está regenerando precisamente eso: no es motivo para abortar."""
    fichero = tmp_path / "catalogo.json"
    fichero.write_text("{ esto no es json", encoding="utf-8")

    assert leer_catalogo_publicado(fichero) is None
