package com.marcmayol.dracapps.ui.tienda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.ui.ajustes.EstadoDeLaTienda
import com.marcmayol.dracapps.ui.tema.Espaciado

const val ETIQUETA_BANNER_TIENDA = "banner-tienda"

/**
 * Aviso de que hay una versión nueva de **la tienda**, encima de la lista de apps.
 *
 * No bloquea ni se pone delante de nada: la tienda sirve para actualizar las apps de la
 * casa, y quedarse mirando su propia actualización sería ponerse por delante del
 * trabajo. Solo aparece cuando hay novedad o cuando ya está bajándose.
 */
@Composable
fun BannerDeLaTienda(
    estado: EstadoDeLaTienda,
    alActualizar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = estado.novedad ?: return

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .testTag(ETIQUETA_BANNER_TIENDA)
            .fillMaxWidth()
            .padding(horizontal = Espaciado.margenPantalla)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.shapes.large,
            )
            .padding(Espaciado.dentroDeTarjeta),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (estado.notas.isNotBlank()) {
                    Text(
                        text = estado.notas,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            FilledTonalButton(
                onClick = alActualizar,
                modifier = Modifier.defaultMinSize(minHeight = Espaciado.areaTactilMinima),
            ) {
                Text("Actualizar")
            }
        }
        // Mientras baja o instala, el mensaje sustituye al silencio: es la única pista
        // de que la tienda está a punto de cerrarse sola.
        val mensaje = estado.mensaje
        if (mensaje != null && !estado.esError) {
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
