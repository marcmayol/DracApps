# DracApps

Tienda de apps Android propia: Marc como admin cura un catálogo de sus apps (repos de GitHub con APKs firmados en Releases); familia y allegados las instalan y actualizan desde la tienda sin fricción de "orígenes externos" por app. Cliente nativo en Kotlin + Jetpack Compose. Sin backend propio: el catálogo vive en GitHub Pages y los APKs en las Releases de cada repo. Identidad visual: diseño "DracApps" de Claude Design (fuente de verdad de la UI), de la misma familia de marca que DracPDF/Ladón.

## Decisiones de arquitectura (fijas)
- El cliente solo conoce la URL del catálogo (catalogo.json en GitHub Pages); JAMÁS consulta la API de GitHub
- catalogo.json: lista de apps con {id (applicationId), nombre, descripcion, iconoUrl, versionCode, versionName, apkUrl, sha256, notas, tamanoBytes}; campos de extensión declarados sin implementar (canal, minSdk)
- El catálogo se genera, no se edita a mano: fuente admin apps.yaml (lista de repos curada por Marc) + script/Action que lee la última Release de cada repo y regenera el JSON con hashes verificados
- Comparación por versionCode entero contra el instalado (PackageManager); jamás strings
- Toda instalación verifica SHA-256 del APK descargado (almacenamiento privado) ANTES de instalar; hash incorrecto = borrar y avisar, jamás instalar
- Instalación con PackageInstaller por sesiones; setRequireUserAction(false) para actualizaciones de apps cuyo instalador registrado sea DracApps; flujo de permiso REQUEST_INSTALL_PACKAGES una sola vez
- Tolerancia a fallos absoluta en todo lo automático (sin red/JSON roto/HTTP error → silencio); solo las acciones manuales informan de errores
- Arquitectura limpia: dominio y casos de uso sin Android framework, adaptadores para red/instalación/catálogo, tests con fakes
- Reglas de aceptación de UI: cada fase declara su tabla de acciones/pantallas y un test de inventario verifica que existen y están conectadas; prohibido demostrar accesibilidad invocando métodos internos

## Fase 1: Catálogo y publicación (sin app todavía)
1. Repo del catálogo con Pages activo; apps.yaml con el formato de entrada del admin (repo, rama, notas opcionales)
2. scripts/generar_catalogo.py: lee apps.yaml, consulta la última Release de cada repo (aquí sí gh/API, esto corre en máquina del admin o en Action, no en el cliente), descarga cada APK, verifica que está firmado y extrae versionCode/versionName/applicationId reales (apkanalyzer/aapt), calcula sha256, y escribe catalogo.json; aborta con mensaje claro si un APK no cumple (sin firma, sin release, versionCode no creciente respecto al catálogo anterior)
3. Modo --dry-run y publicación por push a Pages con verificación post-publicación (la URL pública sirve el catálogo nuevo, con reintentos por caché del CDN)
4. GitHub Action opcional en el repo del catálogo que regenera al cambiar apps.yaml o manualmente (workflow_dispatch)
5. Dar de alta las primeras apps reales en apps.yaml

**Criterio de aceptación F1:** con dos repos reales dados de alta, generar_catalogo.py produce un catalogo.json válido cuyos sha256 y versionCodes coinciden con los APKs de sus Releases (verificado por el propio script y mostrado); el dry-run no publica; la publicación real deja la URL pública sirviendo el catálogo (mostrado con curl); un repo sin Release o con APK sin firma aborta con el mensaje esperado; todo con exit 0 en los casos buenos.

## Fase 2: Cliente, instalar

### Parte 0: identidad (diseño DracApps)
0a. Importar el diseño (paquete de handoff de Claude Design, versionado en `diseno/` dentro del repo; el MCP claude_design como origen y para futuras revisiones) y extraer los tokens a un tema Compose/Material 3: esquemas de color claro y oscuro del diseño, tipografía y formas; nada de colores sueltos por el código
0b. Icono adaptativo montado desde las capas SVG del paquete de diseño (ic-launcher-background, ic-launcher-foreground, ic-launcher-monochrome), más ic-notification-24 para notificaciones y logo-mark/ic-store-512 donde el diseño los use
0c. Las pantallas maquetadas del diseño (lista con los cuatro estados, detalle, actualizaciones, flujo de instalación, permiso de orígenes explicado para no técnicos, ajustes, vacío y error) son la referencia obligada de todas las tareas de UI de las Fases 2 y 3

### Tareas
1. Proyecto Android (Kotlin, Compose, min SDK a proponer en diseño técnico) con la arquitectura fijada
2. Caso de uso ObtenerCatalogo + estado por app comparando con lo instalado: NO_INSTALADA / INSTALADA_AL_DIA / ACTUALIZABLE (versionCode) / NO_GESTIONADA (instalada pero con otra firma/origen: solo informar)
3. UI: lista del catálogo (icono, nombre, estado según el lenguaje visual del diseño) y detalle (descripción, versión, tamaño, notas, botón contextual Instalar/Actualizar/Abrir)
4. Flujo de instalación completo: permiso de orígenes (una vez, con la pantalla explicativa del diseño) → descarga con progreso → verificación sha256 → sesión de PackageInstaller → resultado; robusto ante muerte del proceso en cada paso (sesiones huérfanas limpiadas)
5. Tabla de acciones/pantallas + test de inventario

**Criterio de aceptación F2:** tests sin red (servidor local/dobles) de: catálogo parseado y estados correctos por versionCode, hash incorrecto borrado sin llegar a sesión, hash correcto creando la sesión (doblada); tema íntegramente derivado de los tokens del diseño en claro y oscuro; capturas de las pantallas en ambos temas junto a sus maquetas; y demostración en emulador/dispositivo de instalar una app real del catálogo de punta a punta (esta última con el usuario delante). Inventario en verde con evidencia desde la UI real.

## Fase 3: Actualizar
1. Comprobación al abrir (corrutina con retardo de unos segundos) + periódica con WorkManager (constraint de red) sobre el catálogo; notificación agrupada "N actualizaciones disponibles" (icono y texto del diseño) que abre la tienda; comprobación manual siempre disponible; ajuste activado por defecto
2. Actualización por app y "Actualizar todo"; setRequireUserAction(false) cuando DracApps es el instalador registrado, confirmación del sistema cuando no (y a partir de esa, ya lo es)
3. Pantalla/sección "Actualizaciones" con lo pendiente y las notas de cada versión, según el diseño
4. Auto-actualización de la propia DracApps con el mismo mecanismo que gestiona para las demás apps. DracApps figura en su propio catálogo y se somete, sin excepción, a las decisiones ya fijadas del módulo actualizador:
   - manifiesto/catálogo como única fuente de verdad; el cliente JAMÁS consulta la API de GitHub, tampoco para sí mismo
   - comparación por versionCode entero contra el instalado; jamás strings
   - las tres vías de comprobación por igual: al abrir, periódica con WorkManager y manual
   - tolerancia a fallos absoluta en lo automático (errores en silencio); solo la comprobación manual informa
   - descarga a almacenamiento privado y verificación SHA-256 ANTES de instalar; hash incorrecto = borrar y avisar
   - PackageInstaller por sesiones con setRequireUserAction(false) cuando DracApps sea el instalador registrado, incluido su propio caso
   - diseño explícito de qué pasa al actualizarse a sí misma con sesiones de instalación en vuelo (según la sección "Cuando la tienda se actualiza a sí misma" del diseño)

**Criterio de aceptación F3:** tests de los estados y del disparo de WorkManager (doblado); demostración real de una app quedando desactualizada en catálogo y actualizándose desde la notificación; y auto-actualización de DracApps demostrada en dispositivo (con el usuario delante). Inventario ampliado en verde.

## Fase 4: Pulido de tienda
1. Ajustes (frecuencia de comprobación, solo-wifi para descargas, comprobar ahora)
2. Estados vacíos y de error con la identidad visual del diseño; icono y nombre definitivos aplicados en launcher, notificaciones y "acerca de"
3. Desinstalar desde la tienda; información de "instalada por fuera" para apps NO_GESTIONADAS
4. publicar_release.py de la propia DracApps (heredero del patrón DracPDF: build firmado, verificación de coherencia de versionCode, gh, --dry-run) y alta de la tienda en su propio catálogo
