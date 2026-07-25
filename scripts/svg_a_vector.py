#!/usr/bin/env python3
"""Convierte los SVG del paquete de diseño en VectorDrawable de Android.

    python scripts/svg_a_vector.py

Los iconos de DracApps se dibujan una sola vez, en Claude Design, y de ahí salen
tanto las maquetas como los recursos de la app. Este conversor evita el paso manual
por Android Studio: si el diseño cambia, se vuelve a ejecutar y los recursos quedan
otra vez fieles al original.

No pretende convertir cualquier SVG del mundo, solo los de este paquete: rectángulos
redondeados, círculos, un trazado de silueta con comandos M/L/Z, grupos con
translate y scale, y un gradiente radial. Si aparece algo que no entiende, aborta en
vez de dibujar cualquier cosa.
"""

from __future__ import annotations

import math
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ORIGEN = RAIZ / "diseno" / "assets"
DESTINO = RAIZ / "app" / "src" / "main" / "res" / "drawable"

SVG = "{http://www.w3.org/2000/svg}"


class ErrorDeConversion(Exception):
    """El SVG trae algo que este conversor no sabe traducir con fidelidad."""


@dataclass
class Contexto:
    """Lo que un elemento hereda de sus padres."""

    color: str | None = None
    relleno: str | None = None
    opacidad: float = 1.0


def convertir(svg: Path, ancho_dp: float | None = None) -> str:
    raiz = ET.parse(svg).getroot()
    caja = [float(v) for v in raiz.get("viewBox", "0 0 24 24").split()]
    ancho_vp, alto_vp = caja[2], caja[3]
    dp = ancho_dp or ancho_vp

    gradientes = _recoger_gradientes(raiz)
    cuerpo: list[str] = []
    for hijo in raiz:
        cuerpo.extend(_convertir_elemento(hijo, Contexto(), gradientes, sangria=1))

    if not cuerpo:
        raise ErrorDeConversion(f"{svg.name} no ha producido ningún trazado")

    cabecera = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generado desde diseno/assets/{nombre} por scripts/svg_a_vector.py.\n"
        "     No se edita a mano: se cambia el diseño y se vuelve a generar. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="{dp}dp"\n'
        '    android:height="{dp_alto}dp"\n'
        '    android:viewportWidth="{vp}"\n'
        '    android:viewportHeight="{vp_alto}">\n'
    ).format(
        nombre=svg.name,
        dp=_num(dp),
        dp_alto=_num(dp * alto_vp / ancho_vp),
        vp=_num(ancho_vp),
        vp_alto=_num(alto_vp),
    )
    return cabecera + "\n".join(cuerpo) + "\n</vector>\n"


def _recoger_gradientes(raiz: ET.Element) -> dict[str, ET.Element]:
    gradientes = {}
    for elemento in raiz.iter():
        if elemento.tag == f"{SVG}radialGradient" and elemento.get("id"):
            gradientes[elemento.get("id")] = elemento
    return gradientes


def _convertir_elemento(
    elemento: ET.Element,
    heredado: Contexto,
    gradientes: dict[str, ET.Element],
    sangria: int,
) -> list[str]:
    etiqueta = elemento.tag.replace(SVG, "")
    tab = "    " * sangria

    if etiqueta in ("defs", "title", "desc", "clipPath"):
        return []

    contexto = Contexto(
        color=elemento.get("color", heredado.color),
        # El fill de un grupo lo heredan sus hijos: sin esto, el círculo dorado del
        # fondo del icono salía negro.
        relleno=elemento.get("fill", heredado.relleno),
        opacidad=heredado.opacidad * float(elemento.get("opacity", 1)),
    )

    if etiqueta == "g":
        hijos: list[str] = []
        for hijo in elemento:
            hijos.extend(_convertir_elemento(hijo, contexto, gradientes, sangria + 1))
        if not hijos:
            return []
        transformacion = _transformacion(elemento.get("transform"))
        if not transformacion:
            return [linea.replace("    " * (sangria + 1), tab, 1) for linea in hijos]
        return [f"{tab}<group{transformacion}>", *hijos, f"{tab}</group>"]

    if etiqueta == "rect":
        datos = _rect_a_path(elemento)
    elif etiqueta == "circle":
        datos = _circulo_a_path(elemento)
    elif etiqueta == "path":
        datos = elemento.get("d", "")
    else:
        raise ErrorDeConversion(f"No sé convertir <{etiqueta}>")

    relleno = _resolver_relleno(elemento, contexto)
    atributos = [f'{tab}    android:pathData="{datos}"']

    con_gradiente = isinstance(relleno, str) and relleno.startswith("url(")
    if not con_gradiente:
        atributos.append(f'{tab}    android:fillColor="{relleno}"')
    if elemento.get("fill-rule") == "evenodd":
        atributos.append(f'{tab}    android:fillType="evenOdd"')
    if contexto.opacidad < 1:
        atributos.append(f'{tab}    android:fillAlpha="{_num(contexto.opacidad)}"')

    if not con_gradiente:
        atributos[-1] += " />"
        return [f"{tab}<path", *atributos]

    identificador = relleno[5:-1] if relleno.startswith("url(#") else relleno
    atributos[-1] += ">"
    return [
        f"{tab}<path",
        *atributos,
        *_gradiente(gradientes, identificador, tab),
        f"{tab}</path>",
    ]


def _gradiente(gradientes: dict[str, ET.Element], identificador: str, tab: str) -> list[str]:
    gradiente = gradientes.get(identificador)
    if gradiente is None:
        raise ErrorDeConversion(f"El gradiente '{identificador}' no está definido")

    # Los porcentajes del SVG son relativos a la caja; VectorDrawable los quiere en
    # coordenadas del viewport, así que se resuelven aquí sobre 192 (el tamaño de
    # todos los iconos adaptativos de este paquete).
    lado = 192.0
    centro_x = _porcentaje(gradiente.get("cx", "50%"), lado)
    centro_y = _porcentaje(gradiente.get("cy", "50%"), lado)
    radio = _porcentaje(gradiente.get("r", "50%"), lado)

    paradas = []
    for stop in gradiente:
        paradas.append(
            f'{tab}            <item android:offset="{_num(float(stop.get("offset", 0)))}"'
            f' android:color="{_color(stop.get("stop-color", "#000000"))}" />'
        )

    return [
        f'{tab}    <aapt:attr name="android:fillColor">',
        f"{tab}        <gradient",
        f'{tab}            android:type="radial"',
        f'{tab}            android:centerX="{_num(centro_x)}"',
        f'{tab}            android:centerY="{_num(centro_y)}"',
        f'{tab}            android:gradientRadius="{_num(radio)}">',
        *paradas,
        f"{tab}        </gradient>",
        f"{tab}    </aapt:attr>",
    ]


def _resolver_relleno(elemento: ET.Element, contexto: Contexto) -> str:
    relleno = elemento.get("fill") or contexto.relleno
    if relleno is None:
        relleno = contexto.color or "#000000"
    if relleno == "currentColor":
        # Sin color heredado, currentColor es negro por defecto en SVG. En Android
        # eso es justo lo que hace falta para una silueta que se tiñe desde el tema.
        relleno = contexto.color or "#000000"
    if relleno.startswith("url("):
        return relleno
    return _color(relleno)


def _color(valor: str) -> str:
    valor = valor.strip()
    if valor.startswith("#") and len(valor) == 4:  # #abc -> #aabbcc
        return "#" + "".join(c * 2 for c in valor[1:]).upper()
    if valor.startswith("#"):
        return valor.upper()
    nombres = {"black": "#000000", "white": "#FFFFFF", "none": "#00000000"}
    if valor in nombres:
        return nombres[valor]
    raise ErrorDeConversion(f"No sé interpretar el color '{valor}'")


def _rect_a_path(elemento: ET.Element) -> str:
    x = float(elemento.get("x", 0))
    y = float(elemento.get("y", 0))
    ancho = float(elemento.get("width", 0))
    alto = float(elemento.get("height", 0))
    radio = float(elemento.get("rx", elemento.get("ry", 0)))
    radio = min(radio, ancho / 2, alto / 2)

    if radio == 0:
        return f"M{_num(x)},{_num(y)}h{_num(ancho)}v{_num(alto)}h{_num(-ancho)}z"

    # Rectángulo redondeado con arcos, que es lo que entiende VectorDrawable.
    return (
        f"M{_num(x + radio)},{_num(y)}"
        f"h{_num(ancho - 2 * radio)}"
        f"a{_num(radio)},{_num(radio)} 0 0 1 {_num(radio)},{_num(radio)}"
        f"v{_num(alto - 2 * radio)}"
        f"a{_num(radio)},{_num(radio)} 0 0 1 {_num(-radio)},{_num(radio)}"
        f"h{_num(-(ancho - 2 * radio))}"
        f"a{_num(radio)},{_num(radio)} 0 0 1 {_num(-radio)},{_num(-radio)}"
        f"v{_num(-(alto - 2 * radio))}"
        f"a{_num(radio)},{_num(radio)} 0 0 1 {_num(radio)},{_num(-radio)}z"
    )


def _circulo_a_path(elemento: ET.Element) -> str:
    cx = float(elemento.get("cx", 0))
    cy = float(elemento.get("cy", 0))
    r = float(elemento.get("r", 0))
    return (
        f"M{_num(cx - r)},{_num(cy)}"
        f"a{_num(r)},{_num(r)} 0 1 0 {_num(2 * r)},0"
        f"a{_num(r)},{_num(r)} 0 1 0 {_num(-2 * r)},0z"
    )


def _transformacion(transform: str | None) -> str:
    if not transform:
        return ""
    partes = []
    for nombre, argumentos in re.findall(r"(\w+)\(([^)]*)\)", transform):
        valores = [float(v) for v in re.split(r"[,\s]+", argumentos.strip()) if v]
        if nombre == "translate":
            partes.append(f' android:translateX="{_num(valores[0])}"')
            partes.append(f' android:translateY="{_num(valores[1] if len(valores) > 1 else 0)}"')
        elif nombre == "scale":
            escala_x = valores[0]
            escala_y = valores[1] if len(valores) > 1 else escala_x
            partes.append(f' android:scaleX="{_num(escala_x)}"')
            partes.append(f' android:scaleY="{_num(escala_y)}"')
        elif nombre == "rotate":
            partes.append(f' android:rotation="{_num(valores[0])}"')
        else:
            raise ErrorDeConversion(f"No sé aplicar la transformación '{nombre}'")
    return "".join(partes)


def _porcentaje(valor: str, sobre: float) -> float:
    if valor.endswith("%"):
        return float(valor[:-1]) / 100 * sobre
    return float(valor)


def _num(valor: float) -> str:
    if math.isclose(valor, round(valor), abs_tol=1e-9):
        return str(int(round(valor)))
    return f"{valor:.4f}".rstrip("0").rstrip(".")


# Qué se convierte y con qué tamaño en dp.
ICONOS = {
    "ic-launcher-background.svg": ("ic_launcher_background.xml", 108),
    "ic-launcher-foreground.svg": ("ic_launcher_foreground.xml", 108),
    "ic-launcher-monochrome.svg": ("ic_launcher_monochrome.xml", 108),
    "ic-notification-24.svg": ("ic_notificacion.xml", 24),
    "logo-mark.svg": ("logo_dracapps.xml", 192),
    "dragon.svg": ("dragon.xml", 192),
}


def main() -> int:
    DESTINO.mkdir(parents=True, exist_ok=True)
    fallos = 0

    for origen, (destino, dp) in ICONOS.items():
        svg = ORIGEN / origen
        if not svg.exists():
            print(f"  FALTA  {origen}")
            fallos += 1
            continue
        try:
            xml = convertir(svg, ancho_dp=dp)
        except (ErrorDeConversion, ET.ParseError) as error:
            print(f"  ERROR  {origen}: {error}")
            fallos += 1
            continue
        (DESTINO / destino).write_text(xml, encoding="utf-8", newline="\n")
        print(f"  OK     {origen} -> res/drawable/{destino} ({len(xml)} bytes)")

    return 1 if fallos else 0


if __name__ == "__main__":
    raise SystemExit(main())
