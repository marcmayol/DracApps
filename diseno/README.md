# Identidad visual de DracApps

**Fuente de verdad de la UI del cliente.** Todo lo que se pinte en la app sale de aquí: colores,
tipografía, formas, iconografía y el propio comportamiento visual de cada estado.

Origen: proyecto de [Claude Design](https://claude.ai/design)
`e30ee559-2301-44de-bfb1-7905d810b71d`, exportado como paquete de handoff. El proyecto nació con
el nombre provisional *DragonStore*; el nombre definitivo es **DracApps** y así se ha normalizado
aquí.

## Contenido

| Fichero | Qué es |
|---|---|
| `DracApps.dc.html` | El diseño completo: 10 secciones, de la familia de marca a las pantallas a 412 dp |
| `NOTAS-SESION.md` | Decisiones tomadas durante el diseño y por qué |
| `support.js` | Runtime del prototipo (necesario para abrir el HTML) |
| `README-handoff.md` | Instrucciones originales del paquete de Claude Design |
| `assets/ic-launcher-background.svg` | Capa de fondo del icono adaptativo |
| `assets/ic-launcher-foreground.svg` | Capa de primer plano del icono adaptativo |
| `assets/ic-launcher-monochrome.svg` | Capa monocroma (temática, Android 13+) |
| `assets/ic-notification-24.svg` | Icono de notificación a 24 dp |
| `assets/ic-store-512.svg` | Icono de tienda a 512 px |
| `assets/logo-mark.svg` | Marca: el dragón sobre su tesoro |
| `assets/dragon.svg` | Silueta del dragón vectorizada, `fill: currentColor` |
| `assets/dragon-src.png` | PNG original del que salió la silueta |

## Cómo se usa

Estas maquetas son prototipos en HTML/CSS, **no** código de producción. En la Fase 2 sus tokens se
extraen a un tema Compose/Material 3 (esquemas claro y oscuro, tipografía y formas) y las pantallas
se recrean en Compose. Regla firme del plan: **ningún color suelto por el código**; todo pasa por
el tema.

Las maquetas cubren lista con los cuatro estados, detalle, actualizaciones, flujo de instalación,
permiso de orígenes desconocidos explicado para no técnicos, ajustes, vacío y error. Son la
referencia obligada de todas las tareas de UI de las Fases 2 y 3.

Para revisarlo, abre `DracApps.dc.html` en un navegador (necesita `support.js` al lado).
