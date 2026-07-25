package com.marcmayol.dracapps.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Las formas de DracApps, tal cual las fija el diseño (sección 05).
 *
 * Radios más generosos que los de Ladón a propósito: "la tienda acoge, la herramienta
 * corta". Es la misma casa, con otro tono.
 */
val FormasDracApps = Shapes(
    // Insignia, barra de progreso
    extraSmall = RoundedCornerShape(4.dp),
    // Chip de estado, menú contextual
    small = RoundedCornerShape(8.dp),
    // Icono de app en la lista
    medium = RoundedCornerShape(16.dp),
    // Tarjeta de app, campo de búsqueda
    large = RoundedCornerShape(20.dp),
    // Hoja modal, diálogo, tarjeta de actualización
    extraLarge = RoundedCornerShape(28.dp),
)

/** Rejilla base de 4 dp del diseño, para no repartir números sueltos por la UI. */
object Espaciado {
    val margenPantalla = 16.dp
    val entreTarjetas = 12.dp
    val dentroDeTarjeta = 16.dp
    val altoFilaApp = 88.dp
    val iconoEnLista = 40.dp
    val iconoEnDetalle = 72.dp
    val areaTactilMinima = 48.dp
}
