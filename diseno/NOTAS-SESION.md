# DracApps — notas de sesión (para reanudar)

## Estado
- assets/dragon.svg: silueta vectorizada FIEL del PNG del usuario (viewBox 0 0 512 512, fill currentColor, evenodd). Verificada: recolorea bien (oro sobre carbón) y lee a 64px.
- assets/dragon-src.png: copia del PNG original.
- scratch/: pruebas, desechables.

## Decisiones del usuario (form)
- NO heredar valores de Ladón: DracApps tiene color PROPIO (propongo yo). Familia = silueta dragón + sobriedad con carácter, nada corporativo. Ladón/DracPDF = rojo brasa sobre grafito (citar como hermana).
- Motivo tienda: retícula 2×2 + tesoro custodiado (combinar: las fichas de apps SON el hoard).
- Apps catálogo: GymPlan100 (pasos/esferas Wear OS), Building my future, Yo Crónica (registro entrenos + temporizador), [estimador calorías por foto, IA local — inventar nombre], [tracker aseo personal — inventar nombre], + DracApps (detalle recursivo). DracPDF NO va (escritorio).
- Idioma UI: ambas (pantallas en ES + tabla strings ES/EN).
- Tono: cálido/tuteo + guiños dragón discretos. Wordmark: "DracApps". 3 direcciones.

## Plan de diseño decidido
- Marca: dragón emerge rompiendo la ficha sup-dcha de una retícula 2×2 de fichas redondeadas (3 fichas + dragón mayor) = tienda+dragón, legible en recortes y a 24dp.
- Direcciones: 1a "Tesoro" oro/ámbar sobre carbón cálido (RECOMENDADA, hermana del fuego de Ladón), 1b jade, 1c ciruela cálida.
- Paleta 1a M3 — light: primary #7A5900/onP #FFF/prC #FFDF9E/onPrC #261A00; secondary #6C5C3F/secC #F5E0BB; tertiary #8F4A38 (guiño brasa)/terC #FFDBD1; error #BA1A1A; surface #FFF8F0/onSurf #1E1B13/surfVar #EDE1CF/onSV #4D4639/containers #FBF3E5,#F5EDDF,#EFE7D9; outline #7F7667/olVar #D0C5B4.
  Dark: primary #F0BE48/onP #402D00/prC #5C4200/onPrC #FFDF9E; secC #53452A/onSecC #F5E0BB; tertiary #FFB4A1/terC #723727; error #FFB4AB/errC #93000A; surface #17130B/onSurf #ECE1D4/containers #1F1B12,#231F16,#2E2920; outline #999080/olVar #4D4639.
- Color dinámico: identidad de marca (no Material You), argumentar: reconocimiento para no técnicos, catálogo multicolor necesita ancla, oro=sello; ofrecer respeto a contraste/tamaño del sistema.
- Tipografía: Figtree (OFL) escala M3; wordmark Bricolage Grotesque (OFL).
- Estados: ACTUALIZABLE=chip primaryContainer + badge flecha en icono + botón filled (más llamativo, no alarmante); NO INSTALADA=botón tonal "Instalar" sin chip; INSTALADA=chip surfaceVariant "Al día" ✓ + text button "Abrir"; NO GESTIONADA=chip outline discontinuo "Instalada por fuera".
- Pantallas (412px, en vars CSS --sf --on --pr etc. sobre wrapper para clonar tema): Catálogo (4 estados), Detalle GymPlan100, Actualizaciones (+"Actualizar todo" + card fija autoactualización "la tienda muda de piel"), Instalación (descarga+confirmación, 2 minis), Permiso orígenes desconocidos (para mi padre, sin miedo), Ajustes, Vacío, Error red. Escribir sección clara → clonar a oscuro con run_script cambiando solo las vars del wrapper.
- Notificación: icono mono dragón+retícula, "3 actualizaciones disponibles", acciones "Actualizar todo"/"Ver".
- Entregable: DracApps.dc.html (canvas mode, secciones dv-turn, ids 1a/1b/1c) + assets SVG exportables enlazados.
