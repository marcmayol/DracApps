#!/usr/bin/env python3
"""Trae los iconos de las apps del catálogo desde sus propios repos.

    python scripts/iconos_desde_repos.py

Los iconos no se pueden sacar del APK: las apps modernas los llevan como vector
adaptativo y R8 les cambia el nombre a los recursos. Pero en el **código fuente** están
enteros y sin ofuscar, así que se cogen de ahí.

Hay dos caminos según lo que tenga cada repo:

1. Si ya publica un PNG cuadrado grande (el típico `play/icono_512.png` que genera
   Android Studio para la ficha de la tienda), se usa tal cual. Es lo ideal.
2. Si solo tiene los VectorDrawable del icono adaptativo, se copian al módulo
   `herramientas/iconos`, que los rasteriza con el propio motor de Android — el mismo
   que los dibujará en el móvil, así que el resultado es exactamente lo que verá la
   familia. Ese paso se lanza después con:

       gradlew :herramientas:iconos:testDebugUnitTest

Nada de esto corre en el cliente: es trabajo de admin, como generar el catálogo.
"""

from __future__ import annotations

import base64
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from dracapps.altas import leer_altas  # noqa: E402
from dracapps.errores import ErrorDeCatalogo  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent
DESTINO_PNG = RAIZ / "docs" / "iconos"
DESTINO_VECTORES = RAIZ / "herramientas" / "iconos" / "src" / "main" / "res" / "drawable"
LISTA_PENDIENTES = RAIZ / "herramientas" / "iconos" / "src" / "main" / "assets" / "iconos.json"

# Dónde suelen estar las cosas en un proyecto de Android Studio.
RUTAS_PNG = (
    "play/icono_512.png",
    "play/icon512.png",
    "app/src/main/ic_launcher-playstore.png",
    "docs/icono_512.png",
)
CAPAS = {
    "background": "app/src/main/res/drawable/ic_launcher_background.xml",
    "foreground": "app/src/main/res/drawable/ic_launcher_foreground.xml",
    "monochrome": "app/src/main/res/drawable/ic_launcher_monochrome.xml",
}


@dataclass
class Icono:
    application_id: str
    repo: str
    slug: str


def main() -> int:
    altas = leer_altas(RAIZ / "apps.yaml")
    catalogo = RAIZ / "docs" / "catalogo.json"

    if not catalogo.exists():
        print("Primero genera el catálogo: no sé qué applicationId tiene cada repo.")
        return 1

    publicado = json.loads(catalogo.read_text(encoding="utf-8"))
    por_repo = _repos_por_application_id(publicado)

    DESTINO_PNG.mkdir(parents=True, exist_ok=True)
    DESTINO_VECTORES.mkdir(parents=True, exist_ok=True)
    LISTA_PENDIENTES.parent.mkdir(parents=True, exist_ok=True)

    pendientes: list[dict] = []
    resueltos = 0

    for alta in altas.activas:
        application_id = por_repo.get(alta.repo)
        if application_id is None:
            print(f"  SALTO   {alta.repo}: todavía no está en el catálogo generado")
            continue

        destino = DESTINO_PNG / f"{application_id}.png"
        if destino.exists():
            print(f"  YA HAY  {application_id}")
            resueltos += 1
            continue

        png = _buscar_png(alta.repo)
        if png is not None:
            destino.write_bytes(png)
            print(f"  PNG     {application_id} <- {alta.repo} ({len(png)} bytes)")
            resueltos += 1
            continue

        capas = _buscar_vectores(alta.repo)
        if capas:
            slug = _slug(application_id)
            for capa, contenido in capas.items():
                fichero = DESTINO_VECTORES / f"{slug}_{capa}.xml"
                fichero.write_bytes(contenido)
            pendientes.append(
                {"applicationId": application_id, "slug": slug, "capas": sorted(capas)}
            )
            print(f"  VECTOR  {application_id} <- {alta.repo} ({', '.join(sorted(capas))})")
            continue

        print(f"  NADA    {application_id}: ese repo no publica ni PNG ni vectores de icono")

    LISTA_PENDIENTES.write_text(
        json.dumps(pendientes, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )

    print()
    print(f"Listos: {resueltos}. Por rasterizar: {len(pendientes)}.")
    if pendientes:
        print("Ahora ejecuta:  gradlew :herramientas:iconos:testDebugUnitTest")
    return 0


def _repos_por_application_id(catalogo: dict) -> dict[str, str]:
    """El catálogo trae la URL del APK, y de ahí sale de qué repo vino cada app."""
    mapa: dict[str, str] = {}
    for app in catalogo.get("apps", []):
        encontrado = re.search(r"github\.com/([^/]+/[^/]+)/releases", app.get("apkUrl", ""))
        if encontrado:
            mapa[encontrado.group(1)] = app["id"]
    return mapa


def _buscar_png(repo: str) -> bytes | None:
    for ruta in RUTAS_PNG:
        contenido = _leer_del_repo(repo, ruta)
        if contenido:
            return contenido
    return None


def _buscar_vectores(repo: str) -> dict[str, bytes]:
    encontradas = {}
    for capa, ruta in CAPAS.items():
        contenido = _leer_del_repo(repo, ruta)
        if contenido:
            encontradas[capa] = contenido
    # Sin primer plano no hay icono que valga: el fondo solo es un color.
    return encontradas if "foreground" in encontradas else {}


def _leer_del_repo(repo: str, ruta: str) -> bytes | None:
    proceso = subprocess.run(
        ["gh", "api", f"repos/{repo}/contents/{ruta}", "--jq", ".content"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proceso.returncode != 0 or not proceso.stdout.strip():
        return None
    try:
        return base64.b64decode(proceso.stdout.strip())
    except Exception:
        return None


def _slug(application_id: str) -> str:
    """Los nombres de recurso de Android solo admiten minúsculas, números y guion bajo."""
    return re.sub(r"[^a-z0-9_]", "_", application_id.lower())


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ErrorDeCatalogo as error:
        print(f"\nABORTADO: {error}", file=sys.stderr)
        raise SystemExit(1)
