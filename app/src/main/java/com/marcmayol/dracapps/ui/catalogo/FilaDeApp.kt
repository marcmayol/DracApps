package com.marcmayol.dracapps.ui.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.ui.comun.ChipDeEstado
import com.marcmayol.dracapps.ui.comun.IconoDeApp
import com.marcmayol.dracapps.ui.comun.InsigniaDeActualizacion
import com.marcmayol.dracapps.ui.comun.textoDeAccion
import com.marcmayol.dracapps.ui.tema.Espaciado

/** Etiquetas para encontrar las cosas desde los tests, con la misma UI que ve la gente. */
object Etiquetas {
    const val FILA = "fila-app"
    const val BOTON_ACCION = "boton-accion"
    const val ICONO = "icono-app"
}

/**
 * Una app en la lista del catálogo.
 *
 * El estado lo decide el dominio; aquí solo se dibuja. La actualizable rompe la
 * retícula a propósito: contenedor un tono más alto, filo dorado, insignia en el icono
 * y el único botón relleno de la pantalla. Todo lo demás es tranquilo.
 */
@Composable
fun FilaDeApp(
    conEstado: AppConEstado,
    alPulsar: () -> Unit,
    alAccionar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val esquema = MaterialTheme.colorScheme
    val actualizable = conEstado.estado is EstadoApp.Actualizable

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaciado.dentroDeTarjeta),
        modifier = modifier
            .testTag(Etiquetas.FILA)
            .fillMaxWidth()
            .heightIn(min = Espaciado.altoFilaApp)
            .background(
                if (actualizable) esquema.surfaceContainerHigh else esquema.surfaceContainerLow,
                MaterialTheme.shapes.large,
            )
            .then(
                if (actualizable) {
                    Modifier.border(1.5.dp, esquema.primary, MaterialTheme.shapes.large)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = alPulsar)
            .padding(Espaciado.dentroDeTarjeta),
    ) {
        IconoConInsignia(conEstado)

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = conEstado.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = esquema.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conEstado.app.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = esquema.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ChipDeEstado(conEstado.estado)
        }

        BotonDeAccion(conEstado.estado, alAccionar)
    }
}

@Composable
private fun IconoConInsignia(conEstado: AppConEstado) {
    // Lo instalado se pinta con su icono del móvil; lo demás, con el del catálogo. Sin
    // ninguno de los dos queda el marcador de la tienda: la inicial sobre una ficha del
    // tesoro, que es lo que dice el diseño.
    val instalada = conEstado.estado !is EstadoApp.NoInstalada

    Box(contentAlignment = Alignment.TopEnd) {
        IconoDeApp(
            id = conEstado.id,
            nombre = conEstado.nombre,
            iconoUrl = conEstado.app.iconoUrl,
            tamano = Espaciado.iconoEnLista,
            atenuado = !instalada,
            modifier = Modifier.testTag(Etiquetas.ICONO),
        )

        if (conEstado.estado is EstadoApp.Actualizable) {
            InsigniaDeActualizacion(Modifier.padding(top = 0.dp))
        }
    }
}

@Composable
private fun BotonDeAccion(estado: EstadoApp, alAccionar: () -> Unit) {
    val texto = textoDeAccion(estado)
    val modificador = Modifier
        .testTag(Etiquetas.BOTON_ACCION)
        .defaultMinSize(minHeight = Espaciado.areaTactilMinima)

    when (estado) {
        // El único botón relleno de la lista.
        is EstadoApp.Actualizable -> Button(onClick = alAccionar, modifier = modificador) {
            Text(texto)
        }

        // Tonal: se ve que existe, sin urgencia. Nadie tiene que instalar nada.
        EstadoApp.NoInstalada -> FilledTonalButton(onClick = alAccionar, modifier = modificador) {
            Text(texto)
        }

        else -> TextButton(onClick = alAccionar, modifier = modificador) {
            Text(texto)
        }
    }
}
