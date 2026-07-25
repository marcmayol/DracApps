"""Adaptador del SDK de Android: qué dice el APK de sí mismo.

Regla del proyecto: la identidad de una app sale del propio APK, nunca del nombre del
fichero ni de la etiqueta de la Release. Un fichero llamado 'mi-app-v2.0.apk' puede
contener perfectamente el versionCode 1, y publicar eso rompería las actualizaciones
de todos los móviles.

Dos herramientas de build-tools:
- `apksigner` responde si el APK está firmado y con qué certificado.
- `aapt2` responde el applicationId, el versionCode, el versionName y la etiqueta.
"""

from __future__ import annotations

import hashlib
import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from .errores import ErrorDeApk, ErrorDeHerramienta

_PATRON_PAQUETE = re.compile(
    r"package:\s+name='(?P<id>[^']+)'"
    r"\s+versionCode='(?P<codigo>[^']*)'"
    r"\s+versionName='(?P<nombre>[^']*)'"
)
_PATRON_ETIQUETA = re.compile(r"^application-label:'(?P<etiqueta>.*)'$", re.MULTILINE)
_PATRON_MIN_SDK = re.compile(r"^minSdkVersion:'(?P<min_sdk>\d+)'$", re.MULTILINE)
# Deliberadamente laxos. apksigner cambia el prefijo entre versiones de build-tools
# ("Signer #1", "Signer (v3.1) #1"...), y atarse al formato exacto de una versión
# hace que el mismo APK pase en el portátil y falle en la Action.
_PATRON_CERT = re.compile(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", re.IGNORECASE)
_PATRON_DN = re.compile(r"certificate DN:\s*(?P<dn>.+)")


@dataclass(frozen=True)
class IdentidadApk:
    """Lo que el APK dice de sí mismo."""

    application_id: str
    version_code: int
    version_name: str
    etiqueta: str
    min_sdk: int | None


@dataclass(frozen=True)
class FirmaApk:
    """El certificado con el que se firmó el APK."""

    sha256: str
    sujeto: str


class LectorDeApk(Protocol):
    """Puerto: lo que el generador necesita saber de un APK.

    Los tests lo doblan para funcionar sin SDK de Android instalado.
    """

    def verificar_firma(self, apk: Path) -> FirmaApk: ...

    def leer_identidad(self, apk: Path) -> IdentidadApk: ...


def sha256_de(fichero: Path) -> str:
    """SHA-256 del fichero, leído por trozos para no cargar 13 MB en memoria."""
    digest = hashlib.sha256()
    with fichero.open("rb") as f:
        for trozo in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(trozo)
    return digest.hexdigest()


def localizar_build_tools(sdk: Path | None = None, version: str | None = None) -> Path:
    """Encuentra la carpeta de build-tools que se va a usar.

    Por defecto, la más reciente instalada. Si se fija una versión (parámetro o
    variable DRACAPPS_BUILD_TOOLS), esa y solo esa: el runner de la Action trae
    versiones más nuevas que el portátil del admin, y apksigner no siempre contesta
    igual entre versiones, así que conviene poder clavarla.
    """
    raiz = sdk or _sdk_del_entorno()
    version = version or os.environ.get("DRACAPPS_BUILD_TOOLS")
    if raiz is None:
        raise ErrorDeHerramienta(
            "No sé dónde está el SDK de Android.",
            "Define ANDROID_HOME (o ANDROID_SDK_ROOT) apuntando al SDK, o pásame la "
            "ruta con --sdk.",
        )
    if not raiz.exists():
        raise ErrorDeHerramienta(
            f"La ruta del SDK de Android no existe: {raiz}",
            "Revisa ANDROID_HOME.",
        )

    carpeta = raiz / "build-tools"
    versiones = sorted(
        (d for d in carpeta.iterdir() if d.is_dir()) if carpeta.exists() else [],
        key=lambda d: _clave_version(d.name),
    )
    if not versiones:
        raise ErrorDeHerramienta(
            f"No hay build-tools instaladas en {carpeta}.",
            "Instálalas desde el SDK Manager de Android Studio.",
        )

    if version:
        for candidata in versiones:
            if candidata.name == version:
                return candidata
        instaladas = ", ".join(d.name for d in versiones)
        raise ErrorDeHerramienta(
            f"Se ha pedido build-tools {version}, pero no está instalada "
            f"(hay: {instaladas}).",
            "Instálala o cambia DRACAPPS_BUILD_TOOLS.",
        )

    return versiones[-1]


def _sdk_del_entorno() -> Path | None:
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        valor = os.environ.get(variable)
        if valor:
            return Path(valor)
    return None


def _clave_version(nombre: str) -> tuple:
    partes = []
    for trozo in nombre.split("."):
        partes.append(int(trozo) if trozo.isdigit() else 0)
    return tuple(partes)


class LectorApkDelSdk:
    """Implementación real, sobre apksigner y aapt2 de build-tools."""

    def __init__(self, build_tools: Path | None = None) -> None:
        self._build_tools = build_tools or localizar_build_tools()
        self._apksigner = self._herramienta("apksigner", (".bat", ".exe", ""))
        self._aapt2 = self._herramienta("aapt2", (".exe", ""))

    @property
    def build_tools(self) -> Path:
        return self._build_tools

    def verificar_firma(self, apk: Path) -> FirmaApk:
        """Aborta si el APK no está firmado. Devuelve la huella del certificado."""
        proceso = self._ejecutar([str(self._apksigner), "verify", "--print-certs", str(apk)])

        if proceso.returncode != 0:
            detalle = (proceso.stderr or proceso.stdout or "").strip().splitlines()
            primera = detalle[0] if detalle else "apksigner no dio detalles"
            raise ErrorDeApk(
                f"El APK '{apk.name}' no pasa la verificación de firma.\n  ({primera})",
                "Un APK sin firmar no se puede instalar. Publica en la Release el APK "
                "firmado de release, no el de debug ni el sin firmar.",
            )

        salida = proceso.stdout
        firmas = _PATRON_CERT.findall(salida)
        if not firmas:
            # Si esto salta, apksigner ha cambiado el formato de su salida: se enseña
            # tal cual para poder arreglarlo sin tener que reproducir la versión.
            muestra = "\n    ".join((salida or "(vacía)").strip().splitlines()[:6])
            raise ErrorDeApk(
                f"El APK '{apk.name}' se verifica, pero no encuentro la huella del "
                f"certificado en lo que ha contestado apksigner "
                f"({self._apksigner.parent.name}):\n    {muestra}",
                "Compruébalo a mano con 'apksigner verify --print-certs' y avisa: hay "
                "que enseñarle al generador el formato de esa versión.",
            )

        dn = _PATRON_DN.search(salida)
        return FirmaApk(
            sha256=firmas[0].lower(),
            sujeto=dn.group("dn").strip() if dn else "",
        )

    def leer_identidad(self, apk: Path) -> IdentidadApk:
        """Saca del APK su applicationId, versionCode, versionName y etiqueta."""
        proceso = self._ejecutar([str(self._aapt2), "dump", "badging", str(apk)])

        if proceso.returncode != 0:
            detalle = (proceso.stderr or proceso.stdout or "").strip().splitlines()
            primera = detalle[0] if detalle else "aapt2 no dio detalles"
            raise ErrorDeApk(
                f"No he podido leer el manifiesto de '{apk.name}'.\n  ({primera})",
                "¿Seguro que ese adjunto es un APK y no un AAB o un ZIP?",
            )

        salida = proceso.stdout
        paquete = _PATRON_PAQUETE.search(salida)
        if not paquete:
            raise ErrorDeApk(
                f"'{apk.name}' no declara package, versionCode y versionName.",
                "Compruébalo con 'aapt2 dump badging' sobre ese fichero.",
            )

        codigo = paquete.group("codigo")
        if not codigo.isdigit():
            raise ErrorDeApk(
                f"El versionCode de '{apk.name}' no es un entero: '{codigo}'.",
                "El cliente compara versiones por entero; un versionCode no numérico "
                "haría imposible saber si hay actualización.",
            )

        etiqueta = _PATRON_ETIQUETA.search(salida)
        min_sdk = _PATRON_MIN_SDK.search(salida)

        return IdentidadApk(
            application_id=paquete.group("id"),
            version_code=int(codigo),
            version_name=paquete.group("nombre"),
            etiqueta=etiqueta.group("etiqueta").strip() if etiqueta else "",
            min_sdk=int(min_sdk.group("min_sdk")) if min_sdk else None,
        )

    def _herramienta(self, nombre: str, extensiones: tuple[str, ...]) -> Path:
        for extension in extensiones:
            candidato = self._build_tools / f"{nombre}{extension}"
            if candidato.exists():
                return candidato
        raise ErrorDeHerramienta(
            f"No encuentro '{nombre}' en {self._build_tools}.",
            "Reinstala las build-tools desde el SDK Manager de Android Studio.",
        )

    @staticmethod
    def _ejecutar(comando: list[str]) -> subprocess.CompletedProcess:
        try:
            return subprocess.run(
                comando,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
        except OSError as exc:
            raise ErrorDeHerramienta(
                f"No he podido ejecutar '{Path(comando[0]).name}': {exc}"
            ) from exc
