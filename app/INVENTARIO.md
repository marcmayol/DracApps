# Inventario de pantallas y acciones · Fase 2

Cada fila de esta tabla la comprueba un test **contra la UI real**: se pinta la pantalla,
se busca lo que vería una persona y se pulsa donde pulsaría ella. En ningún caso se
invoca un método interno para dar algo por bueno, porque eso demostraría que el código
se llama a sí mismo, no que la pantalla funcione.

Los tests están en `app/src/test/java/com/marcmayol/dracapps/InventarioDePantallasTest.kt`
y corren sin emulador ni red.

## Catálogo

| Acción | Qué tiene que verse | Test |
|---|---|---|
| App **no instalada** | Chip «No instalada» y botón tonal «Instalar» | `la app no instalada ofrece Instalar y lo dice con su chip` |
| App **al día** | Chip «Al día» y botón de texto «Abrir» | `la app al dia ofrece Abrir y lo dice con su chip` |
| App **actualizable** | Chip «Actualización», insignia y el único botón relleno | `la app actualizable ofrece Actualizar y lo dice con su chip` |
| App **no gestionada** | Chip «Instalada por fuera», sin insignia | `la app instalada por fuera se distingue y solo ofrece Abrir` |
| Los cuatro juntos | Cada estado se distingue recorriendo la lista | `los cuatro estados se distinguen en una misma lista` |
| Contador de la cabecera | «N apps · N actualizaciones esperando», con singular y plural | `el subtitulo cuenta las actualizaciones que esperan` |
| Pulsar una fila | Abre el detalle de **esa** app | `pulsar una fila lleva a su detalle` |
| Pulsar el botón | Dispara la acción de **esa** app | `pulsar el boton dispara la accion de esa app` |

## Detalle

| Acción | Qué tiene que verse | Test |
|---|---|---|
| Con actualización | Botón «Actualizar» y además «Abrir» | `el boton principal dice Actualizar cuando hay algo nuevo` |
| Sin instalar | Botón «Instalar» | `el boton principal dice Instalar cuando no esta instalada` |
| Al día | Botón «Abrir» | `el boton principal dice Abrir cuando esta al dia` |
| Notas de versión | Se muestran tal como se publicaron | `enseña las notas de la version` |
| Pulsar el botón | Dispara la acción de esa app | `pulsar el boton principal dispara la accion` |

## Permiso de orígenes desconocidos

| Acción | Qué tiene que verse | Test |
|---|---|---|
| Explicación | «Un permiso, una sola vez» y los tres pasos numerados | `explica los tres pasos sin asustar` |
| «Abrir los ajustes de Android» | Lanza los ajustes del sistema | `el boton lleva a los ajustes de Android` |
| «Ahora no» | Vuelve sin conceder y sin bloquear nada | `siempre hay salida sin culpa` |

## Instalación

| Acción | Qué tiene que verse | Test |
|---|---|---|
| Descargando | Porcentaje, barra, «Cancelar» y «Ocultar» | `la descarga enseña porcentaje, barra y las dos salidas` |
| Terminada | «Ya tienes X», «Abrir» y «Ahora no» | `al terminar confirma y ofrece abrir` |
| Fallida | Explicación sin códigos ni jerga | `un fallo se cuenta en cristiano` |

## Estados vacío y de error

| Acción | Qué tiene que verse | Test |
|---|---|---|
| Sin apps | «Todavía no hay apps» y «Volver a mirar» | `se explica y ofrece volver a mirar` |
| Sin red | «No he podido conectar» y «Reintentar» | `se explica, tranquiliza y ofrece reintentar` |

## Navegación

| Acción | Qué tiene que verse | Test |
|---|---|---|
| Barra inferior | Las tres pestañas del diseño, pulsables | `estan las tres pestañas del diseño` |
| Cambiar de pestaña | Va a la sección pulsada | `pulsar una pestaña cambia de seccion` |
| Secciones de fases futuras | Dicen que aún no están, no fingen | `lo que aun no esta hecho lo dice, no finge` |

## Lo que esta fase NO cubre

Se dice para que no se dé por hecho:

- **Novedades** y **Ajustes** son de las Fases 3 y 4. Su pestaña existe y avisa.
- La instalación **real en un dispositivo** se hace con Marc delante, no aquí.
- La auto-actualización de DracApps es de la Fase 3.
