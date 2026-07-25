"""El catálogo publicado: modelo, serialización y reglas de versión.

Este módulo es lo único que decide qué forma tiene catalogo.json, el contrato entre
el admin y el cliente Android. No consulta nada: recibe hechos ya verificados y los
convierte en el fichero que sirve GitHub Pages.

Dos garantías que el cliente da por supuestas:

- El JSON es **determinista**: los mismos hechos producen los mismos bytes, lo genere
  el Windows del admin o la Action en Linux. Así los diffs enseñan cambios reales.
- El `versionCode` de una app **nunca retrocede**. Si retrocediera, un móvil ya
  actualizado vería una versión vieja como si fuera nueva.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from .errores import ErrorDeVersion

VERSION_FORMATO = 1

# Orden fijo de las claves de cada app. Es parte del determinismo y hace los diffs
# legibles: primero identidad, luego versión, luego descarga.
_ORDEN_CLAVES = (
    "id",
    "nombre",
    "descripcion",
    "iconoUrl",
    "versionCode",
    "versionName",
    "apkUrl",
    "sha256",
    "firmaSha256",
    "tamanoBytes",
    "notas",
    "canal",
    "minSdk",
)


@dataclass(frozen=True)
class EntradaCatalogo:
    """Una app tal como la ve el cliente.

    `firmaSha256` es la huella del certificado que firmó el APK. El cliente la usa
    para distinguir una app suya de una instalada por fuera con otra firma.

    `canal` y `minSdk` son campos de extensión: se publican declarados pero el
    cliente todavía no actúa sobre ellos.
    """

    id: str
    nombre: str
    descripcion: str
    iconoUrl: str
    versionCode: int
    versionName: str
    apkUrl: str
    sha256: str
    firmaSha256: str
    tamanoBytes: int
    notas: str
    canal: str | None = None
    minSdk: int | None = None

    def a_dict(self) -> dict:
        crudo = {
            "id": self.id,
            "nombre": self.nombre,
            "descripcion": self.descripcion,
            "iconoUrl": self.iconoUrl,
            "versionCode": self.versionCode,
            "versionName": self.versionName,
            "apkUrl": self.apkUrl,
            "sha256": self.sha256,
            "firmaSha256": self.firmaSha256,
            "tamanoBytes": self.tamanoBytes,
            "notas": self.notas,
            "canal": self.canal,
            "minSdk": self.minSdk,
        }
        return {clave: crudo[clave] for clave in _ORDEN_CLAVES}


@dataclass(frozen=True)
class Catalogo:
    """El catálogo completo, listo para publicar."""

    titulo: str
    generado: str
    apps: tuple[EntradaCatalogo, ...]
    version: int = VERSION_FORMATO

    def a_dict(self) -> dict:
        return {
            "version": self.version,
            "titulo": self.titulo,
            "generado": self.generado,
            "apps": [app.a_dict() for app in self.apps],
        }

    def a_json(self) -> str:
        """Serializa de forma determinista, con salto de línea final."""
        return json.dumps(self.a_dict(), indent=2, ensure_ascii=False) + "\n"

    def por_id(self) -> dict[str, EntradaCatalogo]:
        return {app.id: app for app in self.apps}


def construir_catalogo(
    titulo: str,
    entradas: list[EntradaCatalogo],
    momento: datetime | None = None,
) -> Catalogo:
    """Ordena las entradas por id y sella la fecha de generación en UTC."""
    ahora = momento or datetime.now(timezone.utc)
    return Catalogo(
        titulo=titulo,
        generado=ahora.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        apps=tuple(sorted(entradas, key=lambda app: app.id)),
    )


def leer_catalogo_publicado(ruta: Path) -> Catalogo | None:
    """Lee el catálogo anterior si existe. Devuelve None la primera vez.

    Si el fichero está corrupto no se aborta: se trata como si no hubiera catálogo
    previo, porque lo que se está haciendo es precisamente regenerarlo. Lo único que
    se pierde es la comprobación de monotonía.
    """
    if not ruta.exists():
        return None
    try:
        crudo = json.loads(ruta.read_text(encoding="utf-8"))
        apps = tuple(
            EntradaCatalogo(
                id=app["id"],
                nombre=app.get("nombre", ""),
                descripcion=app.get("descripcion", ""),
                iconoUrl=app.get("iconoUrl", ""),
                versionCode=int(app["versionCode"]),
                versionName=app.get("versionName", ""),
                apkUrl=app.get("apkUrl", ""),
                sha256=app.get("sha256", ""),
                firmaSha256=app.get("firmaSha256", ""),
                tamanoBytes=int(app.get("tamanoBytes", 0)),
                notas=app.get("notas", ""),
                canal=app.get("canal"),
                minSdk=app.get("minSdk"),
            )
            for app in crudo.get("apps", [])
        )
        return Catalogo(
            titulo=crudo.get("titulo", ""),
            generado=crudo.get("generado", ""),
            apps=apps,
            version=int(crudo.get("version", VERSION_FORMATO)),
        )
    except (ValueError, KeyError, TypeError):
        return None


def comprobar_monotonia(anterior: Catalogo | None, nuevas: list[EntradaCatalogo]) -> None:
    """Aborta si el versionCode de alguna app retrocede respecto a lo publicado.

    Un versionCode igual está permitido: es el caso normal de regenerar el catálogo
    sin que haya salido versión nueva. Lo que nunca se admite es bajar.
    """
    if anterior is None:
        return

    publicadas = anterior.por_id()
    for nueva in nuevas:
        vieja = publicadas.get(nueva.id)
        if vieja is None:
            continue
        if nueva.versionCode < vieja.versionCode:
            raise ErrorDeVersion(
                f"'{nueva.id}' retrocede de versionCode {vieja.versionCode} "
                f"a {nueva.versionCode}.",
                "El catálogo publicado ya anuncia una versión más nueva. Publica una "
                "Release con versionCode mayor, o corrige el versionCode del APK.",
            )


def preservar_sello(anterior: Catalogo | None, nuevo: Catalogo) -> Catalogo:
    """Conserva la fecha anterior si el contenido no ha cambiado.

    Sin esto, regenerar sin que haya salido ninguna versión nueva produciría un
    fichero distinto solo por la marca de tiempo: diffs falsos en cada ejecución y
    commits vacíos cada vez que la Action se dispara. La fecha solo cambia cuando
    cambia algo de verdad.
    """
    if anterior is None:
        return nuevo
    mismo_contenido = (
        anterior.titulo == nuevo.titulo
        and anterior.version == nuevo.version
        and [app.a_dict() for app in anterior.apps] == [app.a_dict() for app in nuevo.apps]
    )
    if mismo_contenido and anterior.generado:
        return Catalogo(
            titulo=nuevo.titulo,
            generado=anterior.generado,
            apps=nuevo.apps,
            version=nuevo.version,
        )
    return nuevo


def diferencias(anterior: Catalogo | None, nuevo: Catalogo) -> list[str]:
    """Describe en español qué cambia respecto al catálogo publicado."""
    publicadas = anterior.por_id() if anterior else {}
    nuevas = nuevo.por_id()
    lineas: list[str] = []

    for id_app in sorted(nuevas):
        nueva = nuevas[id_app]
        vieja = publicadas.get(id_app)
        if vieja is None:
            lineas.append(f"nueva      {id_app} {nueva.versionName} ({nueva.versionCode})")
        elif vieja.versionCode != nueva.versionCode:
            lineas.append(
                f"actualiza  {id_app} {vieja.versionName} ({vieja.versionCode})"
                f" -> {nueva.versionName} ({nueva.versionCode})"
            )
        elif vieja.sha256 != nueva.sha256:
            lineas.append(
                f"rehecha    {id_app} {nueva.versionName} ({nueva.versionCode}), "
                "mismo versionCode pero distinto APK"
            )

    for id_app in sorted(set(publicadas) - set(nuevas)):
        lineas.append(f"retirada   {id_app}")

    return lineas
