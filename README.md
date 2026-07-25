# DracApps

Tienda de apps Android propia. Marc cura un catálogo de sus apps; familia y allegados las
instalan y actualizan desde la tienda sin pelearse con el permiso de "orígenes desconocidos"
app por app.

Sin backend propio: **el catálogo vive en GitHub Pages y los APKs en las Releases de cada repo**.

- Catálogo público: `https://marcmayol.com/DracApps/catalogo.json`
- Cliente Android: Kotlin + Jetpack Compose (Fase 2, carpeta `app/`)
- Identidad visual: `diseno/` — fuente de verdad de la UI

El plan completo, por fases y con sus criterios de aceptación, está en [PLAN.md](PLAN.md).

## Estructura

| Carpeta | Qué hay |
|---|---|
| `apps.yaml` | Fuente curada del admin: qué repos entran en la tienda. **Se edita a mano** |
| `scripts/` | `generar_catalogo.py`, que convierte `apps.yaml` en el catálogo publicado |
| `docs/` | Lo que sirve GitHub Pages: `catalogo.json` e iconos de las apps. **Se genera, no se edita** |
| `tests/` | Tests del generador, con dobles: ni red ni SDK de Android |
| `diseno/` | Paquete de handoff de Claude Design: maquetas, tokens y assets de marca |
| `app/` | Cliente Android (Fase 2) |

## Cómo se añade una app a la tienda

1. Añade su repo a `apps.yaml` (dos líneas).
2. Comprueba sin publicar nada:
   ```
   python scripts/generar_catalogo.py --dry-run
   ```
3. Publica:
   ```
   python scripts/generar_catalogo.py --publicar
   ```

El generador solo se fía de los hechos: descarga cada APK de su Release, **verifica que está
firmado**, saca el `applicationId`, el `versionCode` y el `versionName` del propio APK, y calcula
su SHA-256. Nada se deduce del nombre del fichero ni de la etiqueta de la Release. Si un APK no
cumple, aborta y no publica.

## Requisitos

- Python 3.11+
- [`gh`](https://cli.github.com/) autenticado (`gh auth status`)
- Android SDK build-tools (`apksigner`, `aapt2`) — se localizan por `ANDROID_HOME`

## Qué NO hace el cliente

El cliente Android **jamás consulta la API de GitHub**. Solo conoce la URL del catálogo. Toda la
inteligencia (leer Releases, verificar firmas, calcular hashes) ocurre aquí, en el lado del admin.
