"""Generador del catálogo de DracApps.

Convierte apps.yaml (lo que cura el admin) en docs/catalogo.json (lo que consume el
cliente Android), verificando cada APK antes de publicarlo.

El reparto de responsabilidades es deliberado: `altas` y `catalogo` no saben nada de
GitHub, de HTTP ni del SDK de Android, así que se prueban sin red ni herramientas
externas. Todo lo que toca el mundo vive en los adaptadores (`releases`, `apk`,
`publicacion`) detrás de interfaces que los tests doblan.
"""
