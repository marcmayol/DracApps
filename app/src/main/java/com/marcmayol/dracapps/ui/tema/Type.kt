package com.marcmayol.dracapps.ui.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.marcmayol.dracapps.R

/**
 * La tipografía de DracApps, tal cual la fija el diseño (sección 05).
 *
 * Dos familias, las dos OFL y empaquetadas en la app: nunca se piden por red, así que
 * la tienda se ve igual sin conexión y no le cuenta a nadie qué está mirando.
 *
 * - Bricolage Grotesque pone el carácter en los títulos.
 * - Figtree hace todo lo demás: humanista y de ojo grande, se lee a 14 sp en el móvil
 *   de alguien de setenta años, que es exactamente para quien está hecha esta tienda.
 *
 * Regla del diseño que se respeta aquí: ningún texto por debajo de 14 sp, salvo
 * labelSmall, que solo se usa en insignias numéricas.
 */

@OptIn(ExperimentalTextApi::class)
private fun figtree(peso: Int) = Font(
    R.font.figtree,
    FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

@OptIn(ExperimentalTextApi::class)
private fun bricolage(peso: Int) = Font(
    R.font.bricolage_grotesque,
    FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val Figtree = FontFamily(figtree(400), figtree(600), figtree(700))
val Bricolage = FontFamily(bricolage(600), bricolage(700))

val TipografiaDracApps = Typography(
    // Cifra grande de "3 actualizaciones"
    displayLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W400,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    // Título del estado vacío
    displaySmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W400,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    // Nombre de app en el detalle
    headlineMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    // Título de pantalla grande
    headlineSmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.W700,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    // Barra superior
    titleLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Nombre de app en la lista
    titleMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    // Cabecera de sección
    titleSmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Descripción de la app
    bodyLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    // Notas de la versión
    bodyMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    // Metadatos: tamaño, fecha
    bodySmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Texto de botón
    labelLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Texto de chip
    labelMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    // Contador de la insignia: el único sitio donde se baja de 14 sp
    labelSmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Cuánto crece la letra con el ajuste «Texto grande», tal y como lo fija el diseño. */
const val AUMENTO_DE_TEXTO_GRANDE = 1.15f

/**
 * La misma escala, un punto más grande.
 *
 * Se multiplica sobre los sp, que ya vienen escalados por el tamaño de fuente del
 * sistema: quien lo tenga subido en Android parte de más alto, y este ajuste suma. Solo
 * se toca el cuerpo y el interlineado; el espaciado entre letras se queda como está,
 * porque agrandarlo separaría las palabras sin hacerlas más legibles.
 */
fun Typography.aumentada(factor: Float = AUMENTO_DE_TEXTO_GRANDE): Typography = copy(
    displayLarge = displayLarge.aumentado(factor),
    displayMedium = displayMedium.aumentado(factor),
    displaySmall = displaySmall.aumentado(factor),
    headlineLarge = headlineLarge.aumentado(factor),
    headlineMedium = headlineMedium.aumentado(factor),
    headlineSmall = headlineSmall.aumentado(factor),
    titleLarge = titleLarge.aumentado(factor),
    titleMedium = titleMedium.aumentado(factor),
    titleSmall = titleSmall.aumentado(factor),
    bodyLarge = bodyLarge.aumentado(factor),
    bodyMedium = bodyMedium.aumentado(factor),
    bodySmall = bodySmall.aumentado(factor),
    labelLarge = labelLarge.aumentado(factor),
    labelMedium = labelMedium.aumentado(factor),
    labelSmall = labelSmall.aumentado(factor),
)

private fun TextStyle.aumentado(factor: Float) = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor,
)
