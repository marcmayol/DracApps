"""Errores del generador del catálogo.

Un principio y solo uno: si algo no cuadra, se aborta con un mensaje que le diga al
admin qué pasa y qué hacer. Nunca se publica un catálogo a medias ni se adivina.
"""

from __future__ import annotations


class ErrorDeCatalogo(Exception):
    """Error que aborta la generación con un mensaje pensado para leerse.

    `remedio` es la frase que le dice al admin qué hacer para arreglarlo.
    """

    def __init__(self, mensaje: str, remedio: str | None = None) -> None:
        super().__init__(mensaje)
        self.mensaje = mensaje
        self.remedio = remedio

    def __str__(self) -> str:
        # Solo ASCII en los adornos: la consola de Windows va en cp1252 por defecto
        # y se atraganta con flechas y demás tipografía bonita.
        if self.remedio:
            return f"{self.mensaje}\n  -> {self.remedio}"
        return self.mensaje


class ErrorDeAltas(ErrorDeCatalogo):
    """apps.yaml no se puede leer o no cumple el formato."""


class ErrorDeRelease(ErrorDeCatalogo):
    """La Release de un repo no existe o no sirve para publicar."""


class ErrorDeApk(ErrorDeCatalogo):
    """El APK no está firmado o no se puede leer su identidad."""


class ErrorDeVersion(ErrorDeCatalogo):
    """El versionCode no crece respecto a lo ya publicado."""


class ErrorDePublicacion(ErrorDeCatalogo):
    """La publicación no llegó a la URL pública."""


class ErrorDeHerramienta(ErrorDeCatalogo):
    """Falta una herramienta del entorno (gh, apksigner, aapt2)."""
