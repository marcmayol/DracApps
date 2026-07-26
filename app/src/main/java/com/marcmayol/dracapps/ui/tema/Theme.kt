package com.marcmayol.dracapps.ui.tema

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * El tema de DracApps.
 *
 * `colorDinamico` viene apagado a propósito, y el diseño explica por qué: esta tienda
 * instala software fuera de Google Play, y que se vea siempre igual es parte de la
 * seguridad. Alguien mayor reconoce la pantalla dorada del dragón y sabe que está en
 * el sitio bueno; si el fondo de pantalla la repinta de azul, esa ancla desaparece.
 * Además, el lenguaje de estados depende de que "actualizable" sea siempre del mismo
 * color, y Material You reasigna justo el primario.
 *
 * Del sistema sí se respeta todo lo demás: claro/oscuro, tamaño de fuente, escala de
 * pantalla, alto contraste, reducción de movimiento e idioma.
 */
@Composable
fun DracAppsTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    colorDinamico: Boolean = false,
    textoGrande: Boolean = false,
    content: @Composable () -> Unit,
) {
    val esquema = when {
        colorDinamico -> esquemaDinamico(oscuro) ?: esquemaDeMarca(oscuro)
        else -> esquemaDeMarca(oscuro)
    }

    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val ventana = (vista.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(ventana, vista)
                .isAppearanceLightStatusBars = !oscuro
        }
    }

    MaterialTheme(
        colorScheme = esquema,
        typography = if (textoGrande) TipografiaDracApps.aumentada() else TipografiaDracApps,
        shapes = FormasDracApps,
        content = content,
    )
}

private fun esquemaDeMarca(oscuro: Boolean) = if (oscuro) EsquemaOscuro else EsquemaClaro

@Composable
private fun esquemaDinamico(oscuro: Boolean) =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val contexto = androidx.compose.ui.platform.LocalContext.current
        if (oscuro) {
            androidx.compose.material3.dynamicDarkColorScheme(contexto)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(contexto)
        }
    } else {
        null
    }
