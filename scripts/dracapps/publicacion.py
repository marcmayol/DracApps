"""Publicación a GitHub Pages y comprobación de que ha llegado.

Publicar no es hacer push. Hacer push es pedirlo; publicar es que la URL pública
sirva el catálogo nuevo. Entre una cosa y otra hay una build de Pages y una caché de
CDN, y cualquiera de las dos puede tardar o quedarse con lo viejo.

Por eso aquí se hace push y **después se comprueba contra la URL pública**, con
reintentos, hasta ver el catálogo nuevo servido de verdad. Si no llega, se aborta con
error: si el admin cree que ha publicado y no ha publicado, los móviles se quedan sin
la actualización y nadie se entera.
"""

from __future__ import annotations

import hashlib
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from .errores import ErrorDePublicacion

# Esperas entre intentos, en segundos. Van creciendo: Pages suele tardar entre
# treinta segundos y un par de minutos en servir lo nuevo.
ESPERAS = (5, 5, 10, 10, 15, 20, 30, 30, 45, 60)


@dataclass(frozen=True)
class ResultadoPublicacion:
    """Qué pasó al publicar."""

    hubo_cambios: bool
    commit: str | None
    intentos: int


def hay_cambios(repo: Path, rutas: list[Path]) -> bool:
    """¿Hay algo por commitear en esas rutas?"""
    salida = _git(repo, ["status", "--porcelain", "--", *[str(r) for r in rutas]])
    return bool(salida.strip())


def commitear_y_subir(
    repo: Path,
    rutas: list[Path],
    mensaje: str,
) -> tuple[bool, str | None]:
    """Commitea las rutas indicadas y las sube. Devuelve si hubo algo que subir."""
    if not hay_cambios(repo, rutas):
        return False, None

    _git(repo, ["add", "--", *[str(r) for r in rutas]])
    _git(repo, ["commit", "-m", mensaje])
    commit = _git(repo, ["rev-parse", "--short", "HEAD"]).strip()

    rama = _git(repo, ["rev-parse", "--abbrev-ref", "HEAD"]).strip()
    _git(
        repo,
        ["push", "origin", rama],
        fallo=(
            f"No he podido subir la rama '{rama}' a origin.",
            "Comprueba que el repo tiene remoto ('git remote -v') y que tienes acceso.",
        ),
    )
    return True, commit


def verificar_publicado(
    url: str,
    contenido_esperado: str,
    esperas: tuple[int, ...] = ESPERAS,
    dormir: Callable[[float], None] = time.sleep,
    descargar: Callable[[str], bytes] | None = None,
    avisar: Callable[[str], None] = print,
) -> int:
    """Espera hasta que la URL pública sirva exactamente este catálogo.

    Compara por hash, no por 'parece igual': o es el mismo fichero o no lo es.
    Devuelve cuántos intentos hicieron falta; aborta si no llega a servirse.
    """
    obtener = descargar or _descargar
    esperado = hashlib.sha256(contenido_esperado.encode("utf-8")).hexdigest()
    ultimo_motivo = "no se llegó a intentar"

    for intento in range(1, len(esperas) + 1):
        try:
            cuerpo = obtener(url)
            servido = hashlib.sha256(cuerpo).hexdigest()
            if servido == esperado:
                return intento
            ultimo_motivo = "la URL sirve todavía el catálogo anterior"
        except OSError as exc:
            ultimo_motivo = f"la URL no responde ({exc})"

        espera = esperas[intento - 1]
        avisar(f"  intento {intento}: {ultimo_motivo}; reintento en {espera}s")
        dormir(espera)

    raise ErrorDePublicacion(
        f"El catálogo se subió, pero {url} no lo sirve todavía "
        f"({ultimo_motivo}) tras {len(esperas)} intentos.",
        "Suele ser la caché del CDN o que la build de Pages aún no ha terminado. "
        "Mira la pestaña Actions del repo y vuelve a comprobarlo en unos minutos; el "
        "commit ya está subido, no hace falta regenerar nada.",
    )


def _descargar(url: str) -> bytes:
    # El parámetro y las cabeceras son contra la caché: sin ellos se puede estar
    # comprobando una copia vieja del CDN y dar por publicado lo que no lo está.
    separador = "&" if "?" in url else "?"
    peticion = urllib.request.Request(
        f"{url}{separador}_={int(time.time())}",
        headers={
            "Cache-Control": "no-cache, no-store, max-age=0",
            "Pragma": "no-cache",
            "User-Agent": "DracApps-generador",
        },
    )
    try:
        with urllib.request.urlopen(peticion, timeout=30) as respuesta:
            return respuesta.read()
    except urllib.error.HTTPError as exc:
        raise OSError(f"HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise OSError(str(exc.reason)) from exc


def _git(
    repo: Path,
    argumentos: list[str],
    fallo: tuple[str, str] | None = None,
) -> str:
    proceso = subprocess.run(
        ["git", *argumentos],
        cwd=repo,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proceso.returncode != 0:
        detalle = (proceso.stderr or proceso.stdout or "").strip().splitlines()
        primera = detalle[0] if detalle else f"git salió con código {proceso.returncode}"
        if fallo:
            mensaje, remedio = fallo
            raise ErrorDePublicacion(f"{mensaje}\n  ({primera})", remedio)
        raise ErrorDePublicacion(
            f"Falló 'git {' '.join(argumentos[:2])}': {primera}",
            "Revisa el estado del repositorio con 'git status'.",
        )
    return proceso.stdout
