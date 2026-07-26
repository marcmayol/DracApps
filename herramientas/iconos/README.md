# Iconos de las apps del catálogo

Herramienta de admin. Nada de aquí entra en el APK de DracApps.

Los iconos de las apps no se pueden sacar de sus APKs: las apps modernas los llevan
como vector adaptativo y R8 les cambia el nombre a los recursos. Pero en el **código
fuente** están intactos, así que se cogen de ahí.

## Cómo se añade el icono de una app nueva

    python scripts/iconos_desde_repos.py
    gradlew :herramientas:iconos:testDebugUnitTest

El script mira el repo de cada app activa. Si publica un PNG cuadrado grande (el típico
`play/icono_512.png`), lo usa tal cual. Si solo tiene los VectorDrawable del icono
adaptativo, los deja en `src/main/res/drawable/` y el segundo comando los rasteriza.

Los rasteriza **el motor de dibujo de Android**, no un conversor propio: es el mismo
código que pinta el icono en el móvil, así que gradientes, grupos y rotaciones salen
exactos.

El resultado va a `docs/iconos/<applicationId>.png`, que es donde los busca el generador
del catálogo.

## Encuadre

Se dibuja el lienzo completo de 108 dp, sin recortar a los 72 dp centrales que enseña el
launcher. Recortar sería fiel a la pantalla de inicio, pero corta los iconos cuyo dibujo
llega al borde de la zona segura. En una ficha de tienda interesa ver el icono entero,
que es además lo que hace Google Play; la tienda ya le aplica su propia forma redondeada
al pintarlo.
