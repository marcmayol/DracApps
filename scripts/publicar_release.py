"""Publica una release de la tienda DracApps y actualiza su manifiesto.

Hermano del de Building My Future, con una vuelta de tuerca: la tienda es la única
app que además publica el catálogo de las demás, así que al final del ritual encadena
`generar_catalogo.py --publicar`. Así se acaba el viejo pie: publicar una versión ya no
deja el catálogo hablando de la anterior.

Ritual: build del APK de release FIRMADO, lectura del versionCode/versionName (fuente
única: app/build.gradle.kts), cálculo del sha256, verificación de coherencia (el
versionCode del APK construido, leído con aapt2, coincide con el declarado y supera al
publicado; la firma sigue siendo la misma), creación de la Release con gh (verificando
antes gh auth status), publicación de docs/updates.json en GitHub Pages y comprobación
de que la URL pública ya lo sirve (reintentando por la caché del CDN).

Secretos: la firma sale de keystore.properties (fuera del repo, gitignored) o de las
variables DRACAPPS_STORE_FILE / DRACAPPS_STORE_PASSWORD / DRACAPPS_KEY_ALIAS /
DRACAPPS_KEY_PASSWORD. Si faltan, aborta con mensaje claro. Ningún secreto se commitea.

Uso:
    python scripts/publicar_release.py              # construye y publica
    python scripts/publicar_release.py --dry-run    # prepara sin publicar
    python scripts/publicar_release.py --notas "…"  # notas de la versión
    python scripts/publicar_release.py --sin-catalogo   # no regenera el catálogo
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
BUILD_GRADLE = RAIZ / "app" / "build.gradle.kts"
MANIFIESTO = RAIZ / "docs" / "updates.json"
FIRMA_ESPERADA = RAIZ / "scripts" / "firma_esperada.txt"
APK_RELEASE = RAIZ / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"

_REPO = "marcmayol/DracApps"
_PAGES_URL = "https://marcmayol.com/DracApps/updates.json"
_CHECK_HORAS = 24
_ENV_FIRMA = (
    "DRACAPPS_STORE_FILE",
    "DRACAPPS_STORE_PASSWORD",
    "DRACAPPS_KEY_ALIAS",
    "DRACAPPS_KEY_PASSWORD",
)


# --- utilidades ---------------------------------------------------------------

def _ejecutar(cmd: list[str], **kw) -> None:
    print("»", " ".join(cmd))
    if subprocess.call(cmd, cwd=str(RAIZ), **kw) != 0:
        raise SystemExit(f"Falló: {' '.join(cmd)}")

def _salida(cmd: list[str]) -> str:
    return subprocess.run(cmd, cwd=str(RAIZ), capture_output=True, text=True).stdout

def sha256(ruta: Path) -> str:
    h = hashlib.sha256()
    with ruta.open("rb") as f:
        for bloque in iter(lambda: f.read(65536), b""):
            h.update(bloque)
    return h.hexdigest()

def _gradlew() -> str:
    return "gradlew.bat" if os.name == "nt" else "./gradlew"


# --- versión (fuente única: app/build.gradle.kts) -----------------------------

def leer_version() -> tuple[int, str]:
    texto = BUILD_GRADLE.read_text(encoding="utf-8")
    vc = re.search(r"versionCode\s*=\s*(\d+)", texto)
    vn = re.search(r'versionName\s*=\s*"([^"]+)"', texto)
    if not vc or not vn:
        raise SystemExit("No se pudo leer versionCode/versionName de app/build.gradle.kts.")
    return int(vc.group(1)), vn.group(1)


# --- firma --------------------------------------------------------------------

def asegurar_firma() -> None:
    """Comprueba que hay credenciales de firma; si vienen por env, las materializa en un
    keystore.properties temporal (borrado al terminar). Nunca sobrescribe uno existente
    ni deja secretos en el repo."""
    props = RAIZ / "keystore.properties"
    if props.exists():
        print("Firma: usando keystore.properties existente.")
        return
    if all(os.environ.get(k) for k in _ENV_FIRMA):
        print("Firma: usando variables de entorno (keystore.properties temporal).")
        props.write_text(
            f"storeFile={os.environ['DRACAPPS_STORE_FILE']}\n"
            f"storePassword={os.environ['DRACAPPS_STORE_PASSWORD']}\n"
            f"keyAlias={os.environ['DRACAPPS_KEY_ALIAS']}\n"
            f"keyPassword={os.environ['DRACAPPS_KEY_PASSWORD']}\n",
            encoding="utf-8",
        )
        import atexit
        atexit.register(lambda: props.exists() and props.unlink())
        return
    raise SystemExit(
        "Faltan credenciales de firma. Crea keystore.properties en la raíz (fuera de "
        "git) con storeFile/storePassword/keyAlias/keyPassword, o define las variables "
        f"de entorno: {', '.join(_ENV_FIRMA)}."
    )


# --- herramientas del SDK -----------------------------------------------------

def _sdk_dir() -> Path:
    local = RAIZ / "local.properties"
    if local.exists():
        m = re.search(r"sdk\.dir=(.+)", local.read_text(encoding="utf-8"))
        if m:
            return Path(m.group(1).strip().replace("\\\\", "\\").replace("\\:", ":"))
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            return Path(os.environ[env])
    raise SystemExit("No encuentro el Android SDK (local.properties o ANDROID_HOME).")

def _build_tool(nombre: str) -> Path:
    exe = f"{nombre}.exe" if os.name == "nt" else nombre
    candidatos = sorted((_sdk_dir() / "build-tools").glob(f"*/{exe}"), reverse=True)
    if not candidatos:
        alt = sorted((_sdk_dir() / "build-tools").glob(f"*/{nombre}.bat"), reverse=True)
        if alt:
            return alt[0]
        raise SystemExit(f"No encuentro {nombre} en build-tools del SDK.")
    return candidatos[0]

def version_code_del_apk(apk: Path) -> int:
    salida = _salida([str(_build_tool("aapt2")), "dump", "badging", str(apk)])
    m = re.search(r"versionCode='(\d+)'", salida)
    if not m:
        raise SystemExit("No pude leer el versionCode del APK con aapt2.")
    return int(m.group(1))

def huella_firma(apk: Path) -> str | None:
    try:
        salida = _salida([str(_build_tool("apksigner")), "verify", "--print-certs", str(apk)])
    except SystemExit:
        return None
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", salida)
    return m.group(1).lower() if m else None


# --- manifiesto ---------------------------------------------------------------

def nombre_asset(vn: str) -> str:
    return f"dracapps-v{vn}.apk"

def url_release(vn: str) -> str:
    return f"https://github.com/{_REPO}/releases/download/v{vn}/{nombre_asset(vn)}"

def generar_manifiesto(vc: int, vn: str, sha: str, notas: str) -> dict:
    return {
        "versionCode": vc,
        "versionName": vn,
        "url": url_release(vn),
        "sha256": sha,
        "notas": notas or f"DracApps {vn}.",
        "check_horas": _CHECK_HORAS,
    }

def version_code_publicado() -> int | None:
    """versionCode del último manifiesto COMMITEADO (None si es el primero).

    Se lee de git y no del working tree: un --dry-run previo ya lo ha reescrito con la
    versión que estamos preparando, y compararse contra sí misma abortaría siempre."""
    ruta = MANIFIESTO.relative_to(RAIZ).as_posix()
    salida = _salida(["git", "show", f"HEAD:{ruta}"])
    if not salida.strip():
        return None
    try:
        return int(json.loads(salida)["versionCode"])
    except Exception:  # noqa: BLE001
        return None

def verificar_coherencia(vc_declarado: int, apk: Path, manifiesto: dict) -> None:
    """Cinturón: versionCode construido == declarado == manifiesto, sha256 real, la
    versión sube respecto a la publicada y la firma no ha cambiado. Con la tienda esto
    importa el doble: si se rompe, no puede repararse a sí misma."""
    vc_apk = version_code_del_apk(apk)
    if vc_apk != vc_declarado:
        raise SystemExit(
            f"El APK construido tiene versionCode {vc_apk}, pero build.gradle.kts "
            f"declara {vc_declarado}. Aborto."
        )
    if manifiesto["versionCode"] != vc_declarado:
        raise SystemExit("El versionCode del manifiesto no coincide con el declarado.")
    if manifiesto["sha256"] != sha256(apk):
        raise SystemExit("El sha256 del manifiesto no coincide con el APK construido.")

    publicado = version_code_publicado()
    if publicado is not None and vc_declarado <= publicado:
        raise SystemExit(
            f"El versionCode {vc_declarado} no supera al ya publicado ({publicado}): "
            "nadie detectaría la actualización. Sube el versionCode. Aborto."
        )

    huella = huella_firma(apk)
    if huella is None:
        print("Aviso: no pude leer la firma del APK (apksigner no disponible).")
        return
    if FIRMA_ESPERADA.is_file():
        esperada = FIRMA_ESPERADA.read_text(encoding="utf-8").strip().lower()
        if esperada and esperada != huella:
            raise SystemExit(
                "La firma del APK ha cambiado respecto a la de las versiones ya "
                f"distribuidas ({esperada[:16]}… → {huella[:16]}…). Con otra firma, "
                "ninguna tienda instalada podría actualizarse. Aborto."
            )
        print(f"Firma verificada: {huella[:16]}…")
    else:
        FIRMA_ESPERADA.write_text(huella + "\n", encoding="utf-8")
        print(f"Firma registrada por primera vez en {FIRMA_ESPERADA.name}: {huella[:16]}…")


# --- construcción -------------------------------------------------------------

def construir() -> Path:
    asegurar_firma()
    _ejecutar([_gradlew(), ":app:assembleRelease"])
    if not APK_RELEASE.is_file():
        raise SystemExit(f"No se generó el APK de release: {APK_RELEASE}")
    return APK_RELEASE

def preparar(notas: str) -> tuple[dict, Path]:
    vc, vn = leer_version()
    apk = construir()
    manifiesto = generar_manifiesto(vc, vn, sha256(apk), notas)
    verificar_coherencia(vc, apk, manifiesto)
    MANIFIESTO.parent.mkdir(parents=True, exist_ok=True)
    MANIFIESTO.write_text(
        json.dumps(manifiesto, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return manifiesto, apk


# --- publicación --------------------------------------------------------------

def _asset_con_nombre(apk: Path, vn: str) -> Path:
    destino = apk.with_name(nombre_asset(vn))
    if destino != apk:
        destino.write_bytes(apk.read_bytes())
    return destino

def verificar_gh() -> None:
    if subprocess.call(["gh", "auth", "status"]) != 0:
        raise SystemExit("gh no está autenticado. Ejecuta: gh auth login")

def publicar(apk: Path, manifiesto: dict, notas: str) -> None:
    vn = manifiesto["versionName"]
    asset = _asset_con_nombre(apk, vn)
    _ejecutar([
        "gh", "release", "create", f"v{vn}", str(asset),
        "--repo", _REPO,
        "--title", f"DracApps {vn}",
        "--notes", notas or f"DracApps {vn}.",
    ])
    _ejecutar(["git", "add", str(MANIFIESTO), str(FIRMA_ESPERADA)])
    _ejecutar(["git", "commit", "-m", f"Publica el manifiesto de la v{vn}"])
    _ejecutar(["git", "push", "origin", "main"])

def verificar_url_publica(vc_esperado: int, intentos: int = 30, espera_s: int = 10) -> None:
    """La URL de Pages puede tardar por la caché del CDN: reintenta unos minutos."""
    for i in range(1, intentos + 1):
        try:
            with urllib.request.urlopen(_PAGES_URL, timeout=15) as r:
                data = json.loads(r.read().decode("utf-8"))
            if data.get("versionCode") == vc_esperado:
                print(f"URL pública OK: sirve versionCode {vc_esperado}.")
                return
            print(f"[{i}/{intentos}] Pages sirve {data.get('versionCode')}, esperaba {vc_esperado}…")
        except Exception as e:  # noqa: BLE001
            print(f"[{i}/{intentos}] Aún no disponible ({e.__class__.__name__})…")
        time.sleep(espera_s)
    raise SystemExit(
        "La URL pública no sirvió el versionCode nuevo a tiempo. La Release SÍ se creó; "
        "revisa GitHub Pages (rama main, carpeta /docs) y la caché del CDN."
    )

def regenerar_catalogo(dry_run: bool) -> None:
    """El catálogo es lo que ven las demás apps: si se queda atrás, la tienda anuncia
    versiones viejas. Va aquí para que publicar sea un solo gesto."""
    cmd = [sys.executable, str(RAIZ / "scripts" / "generar_catalogo.py")]
    cmd.append("--dry-run" if dry_run else "--publicar")
    _ejecutar(cmd)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Publica una release de la tienda DracApps.")
    parser.add_argument("--dry-run", action="store_true", help="prepara sin publicar")
    parser.add_argument("--notas", default="", help="notas de la versión")
    parser.add_argument(
        "--sin-catalogo",
        action="store_true",
        help="no regenera el catálogo de las demás apps al terminar",
    )
    args = parser.parse_args(argv)

    if not args.dry_run:
        verificar_gh()

    manifiesto, apk = preparar(args.notas)
    print(f"Manifiesto v{manifiesto['versionName']} "
          f"(versionCode {manifiesto['versionCode']}, sha256 {manifiesto['sha256'][:12]}…)")
    print(f"APK: {apk}")

    if args.dry_run:
        print("--dry-run: preparado sin publicar (Release y manifiesto no subidos).")
        if not args.sin_catalogo:
            regenerar_catalogo(dry_run=True)
        return 0

    publicar(apk, manifiesto, args.notas)
    verificar_url_publica(manifiesto["versionCode"])
    if not args.sin_catalogo:
        regenerar_catalogo(dry_run=False)
    print(f"Release v{manifiesto['versionName']} publicada y manifiesto en Pages.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
