"""Lectura y validación de apps.yaml, el fichero que cura el admin.

Sin dependencias de red, de GitHub ni del SDK de Android: aquí solo se decide si lo
que el admin ha escrito tiene sentido. Todo lo que no cumple aborta con un mensaje
que dice qué línea del fichero está mal y cómo se arregla.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

from .errores import ErrorDeAltas

# owner/repo, tal como se escribe en GitHub.
_PATRON_REPO = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?/[A-Za-z0-9._-]+$")

_CAMPOS_APP = {
    "repo",
    "activo",
    "nombre",
    "descripcion",
    "notas",
    "icono",
    "apk",
    # Extensión declarada, todavía sin efecto (ver apps.yaml).
    "canal",
    "min_sdk",
    # Aceptado por compatibilidad con el formato del plan; no se usa, porque la
    # fuente es siempre la última Release, no una rama.
    "rama",
}

_CAMPOS_CATALOGO = {"titulo", "url_publica"}


@dataclass(frozen=True)
class AltaDeApp:
    """Una app tal como el admin la ha dado de alta."""

    repo: str
    activo: bool = True
    nombre: str | None = None
    descripcion: str | None = None
    notas: str | None = None
    icono: str | None = None
    apk: str | None = None
    canal: str | None = None
    min_sdk: int | None = None

    @property
    def duenyo(self) -> str:
        return self.repo.split("/", 1)[0]

    @property
    def nombre_repo(self) -> str:
        return self.repo.split("/", 1)[1]


@dataclass(frozen=True)
class Altas:
    """El contenido completo de apps.yaml, ya validado."""

    titulo: str
    url_publica: str
    apps: tuple[AltaDeApp, ...] = field(default_factory=tuple)

    @property
    def activas(self) -> tuple[AltaDeApp, ...]:
        return tuple(app for app in self.apps if app.activo)


def leer_altas(ruta: Path) -> Altas:
    """Lee apps.yaml y lo valida. Aborta con ErrorDeAltas si algo no cuadra."""
    if not ruta.exists():
        raise ErrorDeAltas(
            f"No encuentro el fichero de altas: {ruta}",
            "Créalo con la lista de repos de la tienda, o pásame la ruta con --altas.",
        )

    try:
        crudo = yaml.safe_load(ruta.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        raise ErrorDeAltas(
            f"{ruta.name} no es YAML válido: {exc}",
            "Revisa la indentación y los dos puntos de la línea que indica el error.",
        ) from exc

    return validar_altas(crudo, origen=ruta.name)


def validar_altas(crudo: object, origen: str = "apps.yaml") -> Altas:
    """Valida la estructura ya deserializada. Separado de la E/S para poder testearlo."""
    if crudo is None:
        raise ErrorDeAltas(
            f"{origen} está vacío.",
            "Necesita al menos las claves 'catalogo' y 'apps'.",
        )
    if not isinstance(crudo, dict):
        raise ErrorDeAltas(
            f"{origen} debería ser un mapa con las claves 'catalogo' y 'apps', "
            f"pero es {_nombre_tipo(crudo)}.",
            "Revisa que no falte la indentación de primer nivel.",
        )

    sobrantes = set(crudo) - {"catalogo", "apps"}
    if sobrantes:
        raise ErrorDeAltas(
            f"{origen} tiene claves que no reconozco en el primer nivel: "
            f"{_lista(sobrantes)}.",
            "Solo se admiten 'catalogo' y 'apps'.",
        )

    titulo, url_publica = _validar_catalogo(crudo.get("catalogo"), origen)
    apps = _validar_apps(crudo.get("apps"), origen)

    return Altas(titulo=titulo, url_publica=url_publica, apps=apps)


def _validar_catalogo(crudo: object, origen: str) -> tuple[str, str]:
    if crudo is None:
        raise ErrorDeAltas(
            f"A {origen} le falta la sección 'catalogo'.",
            "Debe declarar 'titulo' y 'url_publica'.",
        )
    if not isinstance(crudo, dict):
        raise ErrorDeAltas(
            f"'catalogo' debería ser un mapa, pero es {_nombre_tipo(crudo)}.",
            "Escríbelo como 'titulo: ...' y 'url_publica: ...' indentados debajo.",
        )

    sobrantes = set(crudo) - _CAMPOS_CATALOGO
    if sobrantes:
        raise ErrorDeAltas(
            f"'catalogo' tiene campos que no reconozco: {_lista(sobrantes)}.",
            f"Los admitidos son: {_lista(_CAMPOS_CATALOGO)}.",
        )

    titulo = crudo.get("titulo")
    if not isinstance(titulo, str) or not titulo.strip():
        raise ErrorDeAltas(
            "'catalogo.titulo' tiene que ser un texto con contenido.",
            "Por ejemplo: 'titulo: DracApps'.",
        )

    url = crudo.get("url_publica")
    if not isinstance(url, str) or not url.startswith("https://"):
        raise ErrorDeAltas(
            "'catalogo.url_publica' tiene que ser una URL https.",
            "Es la URL donde GitHub Pages sirve el catálogo, por ejemplo "
            "'https://marcmayol.github.io/DracApps/catalogo.json'.",
        )

    return titulo.strip(), url.strip()


def _validar_apps(crudo: object, origen: str) -> tuple[AltaDeApp, ...]:
    if crudo is None:
        raise ErrorDeAltas(
            f"A {origen} le falta la lista 'apps'.",
            "Aunque esté vacía, tiene que existir: 'apps: []'.",
        )
    if not isinstance(crudo, list):
        raise ErrorDeAltas(
            f"'apps' debería ser una lista, pero es {_nombre_tipo(crudo)}.",
            "Cada app va como un elemento que empieza por guion: '- repo: owner/nombre'.",
        )

    apps: list[AltaDeApp] = []
    vistos: dict[str, int] = {}

    for posicion, entrada in enumerate(crudo, start=1):
        app = _validar_app(entrada, posicion)
        clave = app.repo.lower()
        if clave in vistos:
            raise ErrorDeAltas(
                f"El repo '{app.repo}' está dado de alta dos veces "
                f"(apps #{vistos[clave]} y #{posicion}).",
                "Borra una de las dos entradas.",
            )
        vistos[clave] = posicion
        apps.append(app)

    return tuple(apps)


def _validar_app(crudo: object, posicion: int) -> AltaDeApp:
    donde = f"la app #{posicion} de 'apps'"

    if not isinstance(crudo, dict):
        raise ErrorDeAltas(
            f"{donde} debería ser un mapa, pero es {_nombre_tipo(crudo)}.",
            "Cada app se escribe así: '- repo: owner/nombre'.",
        )

    sobrantes = set(crudo) - _CAMPOS_APP
    if sobrantes:
        raise ErrorDeAltas(
            f"{donde} tiene campos que no reconozco: {_lista(sobrantes)}.",
            f"Los admitidos son: {_lista(_CAMPOS_APP)}.",
        )

    repo = crudo.get("repo")
    if not isinstance(repo, str) or not repo.strip():
        raise ErrorDeAltas(
            f"A {donde} le falta 'repo'.",
            "Es obligatorio y se escribe como 'owner/nombre', por ejemplo "
            "'marcmayol/building-my-future'.",
        )
    repo = repo.strip()
    if not _PATRON_REPO.match(repo):
        raise ErrorDeAltas(
            f"El repo '{repo}' de {donde} no tiene la forma 'owner/nombre'.",
            "Escríbelo tal cual aparece en la URL de GitHub, sin https ni barra final.",
        )

    activo = crudo.get("activo", True)
    if not isinstance(activo, bool):
        raise ErrorDeAltas(
            f"'activo' de {donde} tiene que ser true o false, no {_nombre_tipo(activo)}.",
            "Sin comillas: 'activo: false'.",
        )

    min_sdk = crudo.get("min_sdk")
    if min_sdk is not None and not isinstance(min_sdk, int):
        raise ErrorDeAltas(
            f"'min_sdk' de {donde} tiene que ser un número entero.",
            "Por ejemplo: 'min_sdk: 26'.",
        )

    textos = {}
    for campo in ("nombre", "descripcion", "notas", "icono", "apk", "canal"):
        valor = crudo.get(campo)
        if valor is None:
            textos[campo] = None
            continue
        if not isinstance(valor, str):
            raise ErrorDeAltas(
                f"'{campo}' de {donde} tiene que ser texto, no {_nombre_tipo(valor)}.",
                "Ponlo entre comillas si contiene dos puntos o almohadillas.",
            )
        limpio = valor.strip()
        textos[campo] = limpio or None

    if textos["icono"] is not None and not textos["icono"].startswith("https://"):
        raise ErrorDeAltas(
            f"'icono' de {donde} tiene que ser una URL https.",
            "Si no lo pones, el icono se saca del propio APK, que es lo recomendable.",
        )

    return AltaDeApp(
        repo=repo,
        activo=activo,
        min_sdk=min_sdk,
        **textos,
    )


def _nombre_tipo(valor: object) -> str:
    return {
        type(None): "nada",
        bool: "un booleano",
        int: "un número",
        float: "un número",
        str: "un texto",
        list: "una lista",
        dict: "un mapa",
    }.get(type(valor), f"un {type(valor).__name__}")


def _lista(valores) -> str:
    return ", ".join(sorted(str(v) for v in valores))
