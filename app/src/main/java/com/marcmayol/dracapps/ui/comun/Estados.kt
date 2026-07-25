package com.marcmayol.dracapps.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.dominio.modelo.EstadoApp

/**
 * El chip que dice en qué estado está una app.
 *
 * La regla del diseño, y por eso está todo en un único sitio: solo un estado tiene
 * derecho a botón relleno y solo uno a insignia. Los demás se distinguen por el chip y
 * por la saturación del icono. Llamativo por acumulación de señales suaves, nunca por
 * color de alarma: el rojo se reserva para los errores de verdad.
 */
@Composable
fun ChipDeEstado(estado: EstadoApp, modifier: Modifier = Modifier) {
    val esquema = MaterialTheme.colorScheme

    when (estado) {
        is EstadoApp.Actualizable -> Chip(
            texto = "Actualización",
            fondo = esquema.primaryContainer,
            textoColor = esquema.onPrimaryContainer,
            icono = Icons.Rounded.ArrowUpward,
            modifier = modifier,
        )

        is EstadoApp.InstaladaAlDia -> Chip(
            texto = "Al día",
            fondo = esquema.secondaryContainer,
            textoColor = esquema.onSecondaryContainer,
            icono = Icons.Rounded.Check,
            modifier = modifier,
        )

        EstadoApp.NoInstalada -> Chip(
            texto = "No instalada",
            fondo = Color.Transparent,
            textoColor = esquema.onSurfaceVariant,
            borde = esquema.outlineVariant,
            modifier = modifier,
        )

        // El borde discontinuo dice "esto no lo controlo yo" sin decir que esté mal.
        is EstadoApp.NoGestionada -> Chip(
            texto = "Instalada por fuera",
            fondo = Color.Transparent,
            textoColor = esquema.onSurfaceVariant,
            borde = esquema.outline,
            modifier = modifier,
        )
    }
}

@Composable
private fun Chip(
    texto: String,
    fondo: Color,
    textoColor: Color,
    modifier: Modifier = Modifier,
    borde: Color? = null,
    icono: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(fondo, MaterialTheme.shapes.small)
            .then(
                if (borde != null) {
                    Modifier.border(1.dp, borde, MaterialTheme.shapes.small)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (icono != null) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = textoColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = texto, style = MaterialTheme.typography.labelMedium, color = textoColor)
    }
}

/**
 * La insignia con la flecha sobre el icono de la app.
 *
 * Solo la lleva el estado actualizable: es una de las cuatro señales que lo hacen
 * evidente de un vistazo, junto al contenedor más alto, el filo dorado y el botón.
 */
@Composable
fun InsigniaDeActualizacion(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            // El chip ya dice "Actualización": repetirlo aquí solo alargaría lo que
            // TalkBack lee de cada fila sin añadir nada.
            .clearAndSetSemantics { contentDescription = "" },
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** El texto del botón que le corresponde a cada estado. */
fun textoDeAccion(estado: EstadoApp): String = when (estado) {
    EstadoApp.NoInstalada -> "Instalar"
    is EstadoApp.Actualizable -> "Actualizar"
    is EstadoApp.InstaladaAlDia -> "Abrir"
    is EstadoApp.NoGestionada -> "Abrir"
}
