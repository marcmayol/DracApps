"""Adaptador de GitHub: leer Releases y descargar sus APKs.

Aquí sí se consulta la API de GitHub, porque esto corre en la máquina del admin o en
una Action. El cliente Android nunca ejecuta nada de este módulo: él solo conoce la
URL del catálogo ya generado.

Se usa `gh` en vez de HTTP a pelo para heredar su autenticación: así los repos
privados del admin funcionan igual que los públicos.
"""

from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from fnmatch import fnmatch
from pathlib import Path
from typing import Protocol

from .errores import ErrorDeHerramienta, ErrorDeRelease


@dataclass(frozen=True)
class Asset:
    """Un fichero adjunto a una Release."""

    nombre: str
    url: str
    tamano: int
    digest: str | None = None


@dataclass(frozen=True)
class Release:
    """La última Release publicada de un repo."""

    repo: str
    etiqueta: str
    nombre: str
    cuerpo: str
    assets: tuple[Asset, ...]

    def apks(self) -> tuple[Asset, ...]:
        return tuple(a for a in self.assets if a.nombre.lower().endswith(".apk"))


@dataclass(frozen=True)
class InfoRepo:
    """Lo poco que hace falta del repo en sí."""

    nombre: str
    descripcion: str
    privado: bool


class ProveedorDeReleases(Protocol):
    """Puerto: lo que el generador necesita de GitHub.

    Los tests lo doblan para funcionar sin red.
    """

    def info_repo(self, repo: str) -> InfoRepo: ...

    def ultima_release(self, repo: str) -> Release: ...

    def descargar_apk(self, repo: str, release: Release, asset: Asset, destino: Path) -> Path: ...


def elegir_apk(release: Release, preferido: str | None = None) -> Asset:
    """Decide qué asset es el APK de la Release. Nunca adivina."""
    apks = release.apks()

    if not apks:
        adjuntos = ", ".join(a.nombre for a in release.assets) or "ninguno"
        raise ErrorDeRelease(
            f"La Release {release.etiqueta} de '{release.repo}' no adjunta ningún APK "
            f"(adjuntos: {adjuntos}).",
            "Sube el APK firmado a esa Release, o desactiva la app en apps.yaml con "
            "'activo: false'.",
        )

    if preferido:
        # El nombre del asset suele llevar la versión dentro, así que se admiten
        # comodines ("building-my-future-v*.apk"): sin ellos habría que editar
        # apps.yaml en cada versión nueva y el catálogo se rompería solo.
        elegidos = [a for a in apks if a.nombre == preferido or fnmatch(a.nombre, preferido)]
        if len(elegidos) == 1:
            return elegidos[0]
        disponibles = ", ".join(a.nombre for a in apks)
        if len(elegidos) > 1:
            coincidencias = ", ".join(a.nombre for a in elegidos)
            raise ErrorDeRelease(
                f"El patrón '{preferido}' encaja con {len(elegidos)} APKs de la Release "
                f"{release.etiqueta} de '{release.repo}': {coincidencias}.",
                "Afina el campo 'apk' de esa app en apps.yaml para que solo quede uno.",
            )
        raise ErrorDeRelease(
            f"La Release {release.etiqueta} de '{release.repo}' no tiene ningún APK "
            f"llamado '{preferido}' (hay: {disponibles}).",
            "Corrige el campo 'apk' de esa app en apps.yaml.",
        )

    if len(apks) > 1:
        disponibles = ", ".join(a.nombre for a in apks)
        raise ErrorDeRelease(
            f"La Release {release.etiqueta} de '{release.repo}' adjunta {len(apks)} "
            f"APKs y no sé cuál publicar: {disponibles}.",
            "Añade 'apk: <nombre.apk>' a esa app en apps.yaml para elegir.",
        )

    return apks[0]


class ProveedorGh:
    """Implementación real, sobre la CLI `gh`."""

    def __init__(self, ejecutable: str = "gh") -> None:
        self._gh = ejecutable

    def info_repo(self, repo: str) -> InfoRepo:
        crudo = self._gh_json(
            ["repo", "view", repo, "--json", "name,description,isPrivate"],
            fallo=(
                f"No puedo leer el repo '{repo}'.",
                "Comprueba que existe y que tu 'gh' tiene acceso ('gh auth status').",
            ),
        )
        return InfoRepo(
            nombre=crudo.get("name") or repo.split("/")[-1],
            descripcion=(crudo.get("description") or "").strip(),
            privado=bool(crudo.get("isPrivate")),
        )

    def ultima_release(self, repo: str) -> Release:
        crudo = self._gh_json(
            ["release", "view", "--repo", repo, "--json", "tagName,name,body,assets"],
            fallo=(
                f"El repo '{repo}' no tiene ninguna Release publicada.",
                "Publica una Release con el APK firmado, o desactiva la app en "
                "apps.yaml con 'activo: false'.",
            ),
        )
        assets = tuple(
            Asset(
                nombre=a["name"],
                url=a["url"],
                tamano=int(a.get("size", 0)),
                digest=a.get("digest"),
            )
            for a in crudo.get("assets", [])
        )
        return Release(
            repo=repo,
            etiqueta=crudo.get("tagName", ""),
            nombre=(crudo.get("name") or "").strip(),
            cuerpo=(crudo.get("body") or "").strip(),
            assets=assets,
        )

    def descargar_apk(self, repo: str, release: Release, asset: Asset, destino: Path) -> Path:
        """Descarga el APK a `destino`. Reutiliza el fichero si ya tiene el tamaño esperado.

        La caché es solo una optimización: quien decide si el APK vale es la
        verificación de firma y de hash que viene después, no esto.
        """
        destino.parent.mkdir(parents=True, exist_ok=True)
        if destino.exists() and asset.tamano and destino.stat().st_size == asset.tamano:
            return destino

        if destino.exists():
            destino.unlink()

        self._ejecutar(
            [
                "release", "download", release.etiqueta,
                "--repo", repo,
                "--pattern", asset.nombre,
                "--dir", str(destino.parent),
                "--clobber",
            ],
            fallo=(
                f"No he podido descargar '{asset.nombre}' de {release.etiqueta} "
                f"en '{repo}'.",
                "Comprueba tu conexión y que el adjunto sigue publicado.",
            ),
        )

        descargado = destino.parent / asset.nombre
        if not descargado.exists():
            raise ErrorDeRelease(
                f"'gh' dijo que descargó '{asset.nombre}' pero el fichero no está.",
                f"Mira qué hay en {destino.parent}.",
            )
        if descargado != destino:
            descargado.replace(destino)
        return destino

    def _gh_json(self, argumentos: list[str], fallo: tuple[str, str]) -> dict:
        salida = self._ejecutar(argumentos, fallo)
        try:
            return json.loads(salida)
        except json.JSONDecodeError as exc:
            raise ErrorDeRelease(
                f"'gh {' '.join(argumentos[:3])}' devolvió algo que no es JSON.",
                "Prueba a ejecutar ese mismo comando a mano para ver qué contesta.",
            ) from exc

    def _ejecutar(self, argumentos: list[str], fallo: tuple[str, str]) -> str:
        if shutil.which(self._gh) is None:
            raise ErrorDeHerramienta(
                "No encuentro la herramienta 'gh' en el PATH.",
                "Instálala desde https://cli.github.com/ y autentícate con 'gh auth login'.",
            )
        try:
            proceso = subprocess.run(
                [self._gh, *argumentos],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
        except OSError as exc:
            raise ErrorDeHerramienta(f"No he podido ejecutar 'gh': {exc}") from exc

        if proceso.returncode != 0:
            detalle = (proceso.stderr or proceso.stdout or "").strip().splitlines()
            primera = detalle[0] if detalle else f"gh salió con código {proceso.returncode}"
            mensaje, remedio = fallo
            raise ErrorDeRelease(f"{mensaje}\n  ({primera})", remedio)

        return proceso.stdout
