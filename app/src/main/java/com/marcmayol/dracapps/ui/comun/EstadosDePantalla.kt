package com.marcmayol.dracapps.ui.comun

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.R
import com.marcmayol.dracapps.ui.tema.Espaciado

object EtiquetasEstados {
    const val VACIO = "estado-vacio"
    const val ERROR = "estado-error"
    const val BOTON_VACIO = "boton-volver-a-mirar"
    const val BOTON_REINTENTAR = "boton-reintentar"
    const val BOTON_SOLO_INSTALADAS = "boton-solo-instaladas"
}

/**
 * Cuando todavía no hay nada que enseñar.
 *
 * El propio logo hace de ilustración: el dragón echado sobre un tesoro que aún no
 * existe. No es un error, así que no se pinta como tal.
 */
@Composable
fun EstadoVacio(
    titulo: String,
    explicacion: String,
    textoDelBoton: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .testTag(EtiquetasEstados.VACIO)
            .fillMaxSize()
            .padding(Espaciado.margenPantalla * 2),
    ) {
        Image(
            painter = painterResource(R.drawable.logo_dracapps),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = titulo,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = explicacion,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = alPulsar,
            modifier = Modifier
                .testTag(EtiquetasEstados.BOTON_VACIO)
                .padding(top = 24.dp)
                .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text(textoDelBoton)
        }
    }
}

/**
 * El único sitio de la app donde entra el rojo.
 *
 * Aun así el mensaje tranquiliza: lo que ya está instalado sigue funcionando igual, y
 * se ofrece verlo. Quedarse sin catálogo no es quedarse sin apps.
 */
@Composable
fun EstadoDeError(
    titulo: String,
    explicacion: String,
    alReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    alVerInstaladas: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .testTag(EtiquetasEstados.ERROR)
            .fillMaxSize()
            .padding(Espaciado.margenPantalla * 2),
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = explicacion,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = alReintentar,
            modifier = Modifier
                .testTag(EtiquetasEstados.BOTON_REINTENTAR)
                .padding(top = 24.dp)
                .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text("Reintentar")
        }
        if (alVerInstaladas != null) {
            TextButton(
                onClick = alVerInstaladas,
                modifier = Modifier
                    .testTag(EtiquetasEstados.BOTON_SOLO_INSTALADAS)
                    .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
            ) {
                Text("Ver solo las instaladas")
            }
        }
    }
}
