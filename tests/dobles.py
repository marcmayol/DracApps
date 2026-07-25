"""Dobles de GitHub y del SDK de Android para los tests.

Los tests del generador no tocan la red ni necesitan build-tools instaladas: todo lo
que sale del proceso está detrás de un puerto (`ProveedorDeReleases`, `LectorDeApk`)
y aquí se sustituye por algo que responde lo que el test necesite, incluidos los
casos que no se pueden provocar a voluntad contra GitHub, como un APK sin firmar.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from dracapps.apk import FirmaApk, IdentidadApk
from dracapps.errores import ErrorDeApk, ErrorDeRelease
from dracapps.releases import Asset, InfoRepo, Release


@dataclass
class ApkFalso:
    """Un APK inventado: lo que diría de sí mismo y con qué está firmado."""

    application_id: str
    version_code: int
    version_name: str = "1.0"
    etiqueta: str = "App"
    min_sdk: int | None = 26
    firmado: bool = True
    firma_sha256: str = "a" * 64
    sujeto: str = "CN=Marc Mayol, C=ES"
    contenido: bytes = b"apk-de-mentira"


@dataclass
class ProveedorFalso:
    """Doble de GitHub. Se le dice qué Release tiene cada repo y ya está."""

    releases: dict[str, Release] = field(default_factory=dict)
    repos: dict[str, InfoRepo] = field(default_factory=dict)
    apks: dict[str, ApkFalso] = field(default_factory=dict)
    descargas: list[str] = field(default_factory=list)

    def info_repo(self, repo: str) -> InfoRepo:
        if repo not in self.repos:
            return InfoRepo(nombre=repo.split("/")[-1], descripcion="", privado=False)
        return self.repos[repo]

    def ultima_release(self, repo: str) -> Release:
        if repo not in self.releases:
            raise ErrorDeRelease(
                f"El repo '{repo}' no tiene ninguna Release publicada.",
                "Publica una Release con el APK firmado.",
            )
        return self.releases[repo]

    def descargar_apk(self, repo: str, release: Release, asset: Asset, destino: Path) -> Path:
        self.descargas.append(f"{repo}:{asset.nombre}")
        destino.parent.mkdir(parents=True, exist_ok=True)
        apk = self.apks[repo]
        destino.write_bytes(apk.contenido)
        return destino


@dataclass
class LectorFalso:
    """Doble de apksigner y aapt2. Responde según el APK falso de cada ruta."""

    proveedor: ProveedorFalso
    por_repo: dict[str, ApkFalso] = field(default_factory=dict)
    firmas_verificadas: list[str] = field(default_factory=list)

    def _apk_de(self, ruta: Path) -> ApkFalso:
        for repo, apk in self.proveedor.apks.items():
            if apk.contenido == ruta.read_bytes():
                return apk
        raise AssertionError(f"El test no ha declarado ningún APK para {ruta}")

    def verificar_firma(self, apk: Path) -> FirmaApk:
        falso = self._apk_de(apk)
        self.firmas_verificadas.append(falso.application_id)
        if not falso.firmado:
            raise ErrorDeApk(
                f"El APK '{apk.name}' no pasa la verificación de firma.",
                "Publica el APK firmado de release.",
            )
        return FirmaApk(sha256=falso.firma_sha256, sujeto=falso.sujeto)

    def leer_identidad(self, apk: Path) -> IdentidadApk:
        falso = self._apk_de(apk)
        return IdentidadApk(
            application_id=falso.application_id,
            version_code=falso.version_code,
            version_name=falso.version_name,
            etiqueta=falso.etiqueta,
            min_sdk=falso.min_sdk,
        )


def release_con_apk(
    repo: str,
    etiqueta: str = "v1.0",
    nombre_apk: str = "app-release.apk",
    cuerpo: str = "Notas de la versión.",
    extras: tuple[str, ...] = (),
) -> Release:
    """Una Release con un APK y, si se quiere, otros adjuntos."""
    assets = [
        Asset(
            nombre=nombre_apk,
            url=f"https://github.com/{repo}/releases/download/{etiqueta}/{nombre_apk}",
            tamano=len(b"apk-de-mentira"),
        )
    ]
    assets.extend(
        Asset(nombre=nombre, url=f"https://github.com/{repo}/x/{nombre}", tamano=10)
        for nombre in extras
    )
    return Release(
        repo=repo,
        etiqueta=etiqueta,
        nombre=f"Release {etiqueta}",
        cuerpo=cuerpo,
        assets=tuple(assets),
    )


def montar(
    apps: dict[str, ApkFalso],
    etiquetas: dict[str, str] | None = None,
    nombres_apk: dict[str, str] | None = None,
) -> tuple[ProveedorFalso, LectorFalso]:
    """Atajo: monta proveedor y lector coherentes para un puñado de repos."""
    etiquetas = etiquetas or {}
    nombres_apk = nombres_apk or {}

    proveedor = ProveedorFalso()
    for repo, apk in apps.items():
        # Cada APK falso necesita bytes distintos para que el lector sepa cuál es.
        if apk.contenido == b"apk-de-mentira":
            apk = ApkFalso(**{**apk.__dict__, "contenido": f"apk:{repo}".encode()})
        proveedor.apks[repo] = apk
        proveedor.releases[repo] = release_con_apk(
            repo,
            etiqueta=etiquetas.get(repo, "v1.0"),
            nombre_apk=nombres_apk.get(repo, "app-release.apk"),
        )
        proveedor.repos[repo] = InfoRepo(
            nombre=repo.split("/")[-1],
            descripcion=f"Descripción de {repo.split('/')[-1]}",
            privado=False,
        )

    return proveedor, LectorFalso(proveedor=proveedor)
