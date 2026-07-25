"""Orquestación: de apps.yaml al catálogo verificado.

Por cada app activa, en este orden y sin saltarse ninguno:

1. Se lee su última Release y se elige el APK.
2. Se descarga.
3. Se verifica que está firmado (si no, se aborta).
4. Se le pregunta al propio APK quién es: applicationId, versionCode, versionName.
5. Se calcula su SHA-256.
6. Se resuelve su icono.

Al final se comprueba que ningún versionCode retrocede respecto a lo ya publicado.
Cualquier fallo aborta la generación entera: más vale no publicar que publicar un
catálogo en el que una app apunta a un APK que no es.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from .altas import AltaDeApp, Altas
from .apk import IdentidadApk, LectorDeApk, sha256_de
from .catalogo import (
    Catalogo,
    EntradaCatalogo,
    comprobar_monotonia,
    construir_catalogo,
    preservar_sello,
)
from .releases import Asset, ProveedorDeReleases, Release, elegir_apk

EXTENSIONES_ICONO = (".png", ".webp")


@dataclass
class Resultado:
    """Lo generado, más lo que el admin debería saber aunque no sea un error."""

    catalogo: Catalogo
    avisos: list[str] = field(default_factory=list)
    detalles: list[str] = field(default_factory=list)


def generar(
    altas: Altas,
    proveedor: ProveedorDeReleases,
    lector: LectorDeApk,
    directorio_cache: Path,
    directorio_iconos: Path,
    catalogo_anterior: Catalogo | None = None,
    momento: datetime | None = None,
) -> Resultado:
    """Construye el catálogo a partir de las altas activas."""
    entradas: list[EntradaCatalogo] = []
    avisos: list[str] = []
    detalles: list[str] = []

    inactivas = [app.repo for app in altas.apps if not app.activo]
    if inactivas:
        avisos.append(
            f"Fuera del catálogo por estar desactivadas en apps.yaml: {', '.join(inactivas)}."
        )

    base_iconos = _base_de(altas.url_publica)

    for alta in altas.activas:
        entrada, aviso, detalle = _generar_entrada(
            alta=alta,
            proveedor=proveedor,
            lector=lector,
            directorio_cache=directorio_cache,
            directorio_iconos=directorio_iconos,
            base_iconos=base_iconos,
        )
        entradas.append(entrada)
        detalles.append(detalle)
        if aviso:
            avisos.append(aviso)

    _comprobar_ids_repetidos(entradas)
    comprobar_monotonia(catalogo_anterior, entradas)

    nuevo = construir_catalogo(altas.titulo, entradas, momento=momento)

    return Resultado(
        catalogo=preservar_sello(catalogo_anterior, nuevo),
        avisos=avisos,
        detalles=detalles,
    )


def _generar_entrada(
    alta: AltaDeApp,
    proveedor: ProveedorDeReleases,
    lector: LectorDeApk,
    directorio_cache: Path,
    directorio_iconos: Path,
    base_iconos: str,
) -> tuple[EntradaCatalogo, str | None, str]:
    release: Release = proveedor.ultima_release(alta.repo)
    asset: Asset = elegir_apk(release, alta.apk)

    destino = directorio_cache / alta.nombre_repo / f"{release.etiqueta}-{asset.nombre}"
    apk = proveedor.descargar_apk(alta.repo, release, asset, destino)

    # El orden importa: primero se verifica que está firmado, y solo entonces se le
    # hace caso a lo que el APK dice de sí mismo.
    firma = lector.verificar_firma(apk)
    identidad: IdentidadApk = lector.leer_identidad(apk)

    sha256 = sha256_de(apk)
    tamano = apk.stat().st_size

    icono_url, aviso = _resolver_icono(
        alta=alta,
        application_id=identidad.application_id,
        directorio_iconos=directorio_iconos,
        base_iconos=base_iconos,
    )

    info_repo = proveedor.info_repo(alta.repo)

    entrada = EntradaCatalogo(
        id=identidad.application_id,
        nombre=alta.nombre or identidad.etiqueta or info_repo.nombre,
        descripcion=alta.descripcion or info_repo.descripcion,
        iconoUrl=icono_url,
        versionCode=identidad.version_code,
        versionName=identidad.version_name,
        apkUrl=asset.url,
        sha256=sha256,
        firmaSha256=firma.sha256,
        tamanoBytes=tamano,
        notas=alta.notas if alta.notas is not None else release.cuerpo,
        canal=alta.canal,
        minSdk=alta.min_sdk,
    )

    detalle = (
        f"{alta.repo}: {entrada.nombre} {entrada.versionName} "
        f"(versionCode {entrada.versionCode}), {_legible(tamano)}, "
        f"firmado por {firma.sujeto or 'certificado sin DN'}"
    )

    if info_repo.privado:
        aviso = (
            f"'{alta.repo}' es un repo privado: la URL del APK exige autenticación y "
            f"la tienda no podrá descargarlo. Hazlo público para publicarlo."
        )

    return entrada, aviso, detalle


def _resolver_icono(
    alta: AltaDeApp,
    application_id: str,
    directorio_iconos: Path,
    base_iconos: str,
) -> tuple[str, str | None]:
    """Decide la URL del icono.

    Los iconos no se pueden sacar del APK: las apps modernas los llevan como vector
    adaptativo, no como imagen. Así que o el admin deja un PNG en docs/iconos con el
    applicationId por nombre, o lo apunta a mano en apps.yaml. Si no hay ninguno, la
    tienda pinta su marcador de marca y aquí solo se avisa: quedarse sin icono no es
    razón para no publicar.
    """
    if alta.icono:
        return alta.icono, None

    for extension in EXTENSIONES_ICONO:
        fichero = directorio_iconos / f"{application_id}{extension}"
        if fichero.exists():
            return f"{base_iconos}iconos/{fichero.name}", None

    esperado = f"docs/iconos/{application_id}.png"
    return "", (
        f"'{alta.repo}' se publica sin icono; la tienda usará su marcador de marca. "
        f"Deja un PNG cuadrado (512 px) en {esperado} y vuelve a generar."
    )


def _comprobar_ids_repetidos(entradas: list[EntradaCatalogo]) -> None:
    from .errores import ErrorDeCatalogo

    vistos: dict[str, str] = {}
    for entrada in entradas:
        if entrada.id in vistos:
            raise ErrorDeCatalogo(
                f"Dos apps distintas declaran el mismo applicationId '{entrada.id}'.",
                "En el móvil solo puede haber una app por applicationId. Revisa qué "
                "repos publicas: seguramente uno tiene el APK equivocado.",
            )
        vistos[entrada.id] = entrada.nombre


def _base_de(url_publica: str) -> str:
    """De la URL del catálogo saca la carpeta que lo contiene."""
    return url_publica.rsplit("/", 1)[0] + "/"


def _legible(bytes_: int) -> str:
    mb = bytes_ / (1024 * 1024)
    return f"{mb:.1f} MB" if mb >= 1 else f"{bytes_ / 1024:.0f} KB"
