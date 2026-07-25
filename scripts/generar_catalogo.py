#!/usr/bin/env python3
"""Genera el catálogo de DracApps a partir de apps.yaml.

    python scripts/generar_catalogo.py --dry-run    comprueba sin tocar nada
    python scripts/generar_catalogo.py              genera docs/catalogo.json
    python scripts/generar_catalogo.py --publicar   genera, sube y verifica la URL

Nada se da por bueno: cada APK se descarga de su Release, se comprueba que está
firmado y se le pregunta al propio fichero qué versión es. Si algo no cuadra, no se
publica nada.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from dracapps.altas import leer_altas  # noqa: E402
from dracapps.apk import LectorApkDelSdk  # noqa: E402
from dracapps.catalogo import diferencias, leer_catalogo_publicado  # noqa: E402
from dracapps.errores import ErrorDeCatalogo  # noqa: E402
from dracapps.generacion import generar  # noqa: E402
from dracapps.releases import ProveedorGh  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent


def construir_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="generar_catalogo.py",
        description="Genera el catálogo de DracApps desde apps.yaml.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--altas",
        type=Path,
        default=RAIZ / "apps.yaml",
        help="Fichero de altas del admin (por defecto: apps.yaml).",
    )
    parser.add_argument(
        "--salida",
        type=Path,
        default=RAIZ / "docs" / "catalogo.json",
        help="Dónde se escribe el catálogo (por defecto: docs/catalogo.json).",
    )
    parser.add_argument(
        "--iconos",
        type=Path,
        default=RAIZ / "docs" / "iconos",
        help="Carpeta con los iconos de las apps (por defecto: docs/iconos).",
    )
    parser.add_argument(
        "--cache",
        type=Path,
        default=RAIZ / ".cache",
        help="Dónde se guardan los APKs descargados (por defecto: .cache).",
    )
    parser.add_argument(
        "--sdk",
        type=Path,
        default=None,
        help="Ruta del SDK de Android si no está en ANDROID_HOME.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Comprueba todo y enseña qué cambiaría, pero no escribe nada.",
    )
    parser.add_argument(
        "--publicar",
        action="store_true",
        help="Además de escribir, sube el catálogo y comprueba que la URL pública lo sirve.",
    )
    parser.add_argument(
        "--sin-verificar",
        action="store_true",
        help="Con --publicar, sube sin comprobar la URL pública. Para la Action, que "
        "publica desde el propio GitHub.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    # La consola de Windows va en cp1252: sin esto, un carácter raro en unas notas de
    # versión tumbaría la generación entera al imprimirlas.
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(errors="replace")
        except (AttributeError, ValueError):
            pass

    parser = construir_parser()
    args = parser.parse_args(argv)

    try:
        return _generar(args)
    except ErrorDeCatalogo as error:
        # Sin el flush, al redirigir la salida el error aparece antes que el avance
        # que lo explica, porque stdout se acumula y stderr no.
        sys.stdout.flush()
        print(f"\nABORTADO: {error}", file=sys.stderr)
        sys.stderr.flush()
        return 1


def _generar(args: argparse.Namespace) -> int:
    altas = leer_altas(args.altas)
    activas = altas.activas

    print(f"Catálogo: {altas.titulo}")
    print(f"Altas:    {args.altas}")
    print(f"Apps:     {len(activas)} activas de {len(altas.apps)}")
    print()

    if not activas:
        raise ErrorDeCatalogo(
            "No hay ninguna app activa en apps.yaml.",
            "Un catálogo vacío dejaría la tienda sin nada que mostrar. Activa al menos "
            "una app antes de generar.",
        )

    from dracapps.apk import localizar_build_tools

    lector = LectorApkDelSdk(localizar_build_tools(args.sdk) if args.sdk else None)
    print(f"Herramientas: build-tools {lector.build_tools.name}")
    print()

    anterior = leer_catalogo_publicado(args.salida)
    resultado = generar(
        altas=altas,
        proveedor=ProveedorGh(),
        lector=lector,
        directorio_cache=args.cache,
        directorio_iconos=args.iconos,
        catalogo_anterior=anterior,
    )

    for detalle in resultado.detalles:
        print(f"  OK  {detalle}")

    if resultado.avisos:
        print()
        for aviso in resultado.avisos:
            print(f"  AVISO  {aviso}")

    cambios = diferencias(anterior, resultado.catalogo)
    print()
    if cambios:
        print("Cambios respecto a lo publicado:")
        for cambio in cambios:
            print(f"  {cambio}")
    else:
        print("Sin cambios respecto a lo publicado.")

    json_nuevo = resultado.catalogo.a_json()

    if args.dry_run:
        print()
        print(f"Ensayo: no se ha escrito nada. Serían {len(json_nuevo)} bytes en {args.salida}.")
        if args.publicar:
            print("(--dry-run manda sobre --publicar: no se ha subido nada.)")
        return 0

    args.salida.parent.mkdir(parents=True, exist_ok=True)
    args.salida.write_text(json_nuevo, encoding="utf-8", newline="\n")
    print()
    print(f"Escrito {args.salida} ({len(json_nuevo)} bytes).")

    if not args.publicar:
        return 0

    return _publicar(args, altas.url_publica, json_nuevo, cambios)


def _publicar(
    args: argparse.Namespace,
    url_publica: str,
    json_nuevo: str,
    cambios: list[str],
) -> int:
    from dracapps.publicacion import commitear_y_subir, verificar_publicado

    print()
    rutas = [args.salida.parent]
    mensaje = _mensaje_de_commit(cambios)

    subido, commit = commitear_y_subir(RAIZ, rutas, mensaje)
    if subido:
        print(f"Subido el commit {commit}.")
    else:
        print("Nada que subir: el catálogo publicado ya estaba al día.")

    if args.sin_verificar:
        print("Sin verificar la URL pública, como se ha pedido.")
        return 0

    print(f"Comprobando que {url_publica} sirve el catálogo nuevo...")
    intentos = verificar_publicado(url_publica, json_nuevo)
    print(f"Publicado y verificado en la URL pública (intento {intentos}).")
    return 0


def _mensaje_de_commit(cambios: list[str]) -> str:
    if not cambios:
        return "Regenera el catálogo"
    if len(cambios) == 1:
        return f"Actualiza el catálogo: {cambios[0].split(maxsplit=1)[1]}"
    cuerpo = "\n".join(f"- {c}" for c in cambios)
    return f"Actualiza el catálogo ({len(cambios)} cambios)\n\n{cuerpo}"


if __name__ == "__main__":
    raise SystemExit(main())
