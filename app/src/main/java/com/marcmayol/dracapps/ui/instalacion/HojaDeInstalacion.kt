package com.marcmayol.dracapps.ui.instalacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.ui.detalle.tamanoLegible
import com.marcmayol.dracapps.ui.tema.Espaciado

object EtiquetasInstalacion {
    const val HOJA = "hoja-instalacion"
    const val PORCENTAJE = "porcentaje-descarga"
    const val BARRA = "barra-descarga"
    const val BOTON_CANCELAR = "boton-cancelar-descarga"
    const val BOTON_OCULTAR = "boton-ocultar-descarga"
    const val HECHO = "instalacion-hecha"
    const val BOTON_ABRIR_HECHO = "boton-abrir-instalada"
    const val BOTON_AHORA_NO_HECHO = "boton-ahora-no-instalada"
    const val FALLO = "instalacion-fallida"
}

/** Lo que la hoja de instalación puede estar enseñando. */
sealed interface EstadoHoja {
    data class Descargando(
        val nombre: String,
        val descargados: Long,
        val total: Long,
        val porcentaje: Int,
    ) : EstadoHoja

    data class Verificando(val nombre: String) : EstadoHoja
    data class Instalando(val nombre: String) : EstadoHoja
    data class Hecho(val nombre: String, val version: String) : EstadoHoja
    data class Fallo(val nombre: String, val explicacion: String) : EstadoHoja
}

/**
 * La instalación, en una hoja inferior y no a pantalla completa.
 *
 * A propósito: descargar no puede secuestrar la app. Porcentaje grande en oro, barra
 * sobria y una frase que quita ansiedad, porque quien la lee no sabe cuánto tarda esto
 * ni tiene por qué saberlo.
 */
@Composable
fun HojaDeInstalacion(
    estado: EstadoHoja,
    alCancelar: () -> Unit,
    alOcultar: () -> Unit,
    alAbrir: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .testTag(EtiquetasInstalacion.HOJA)
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.shapes.extraLarge,
            )
            .padding(Espaciado.margenPantalla * 1.5f),
    ) {
        when (estado) {
            is EstadoHoja.Descargando -> Descargando(estado, alCancelar, alOcultar)
            is EstadoHoja.Verificando -> Trabajando(estado.nombre, "Comprobando que es la buena…")
            is EstadoHoja.Instalando -> Trabajando(estado.nombre, "Instalando…")
            is EstadoHoja.Hecho -> Hecho(estado, alAbrir, alCerrar)
            is EstadoHoja.Fallo -> Fallo(estado, alCerrar)
        }
    }
}

@Composable
private fun Descargando(
    estado: EstadoHoja.Descargando,
    alCancelar: () -> Unit,
    alOcultar: () -> Unit,
) {
    val esquema = MaterialTheme.colorScheme

    Text(
        text = estado.nombre,
        style = MaterialTheme.typography.titleMedium,
        color = esquema.onSurface,
    )
    Text(
        text = "Descargando · ${tamanoLegible(estado.descargados)} de " +
            tamanoLegible(estado.total),
        style = MaterialTheme.typography.bodySmall,
        color = esquema.onSurfaceVariant,
    )
    Text(
        text = "${estado.porcentaje} %",
        style = MaterialTheme.typography.displaySmall,
        color = esquema.primary,
        modifier = Modifier
            .testTag(EtiquetasInstalacion.PORCENTAJE)
            .semantics { contentDescription = "Descargado el ${estado.porcentaje} por ciento" },
    )
    LinearProgressIndicator(
        progress = { estado.porcentaje / 100f },
        color = esquema.primary,
        trackColor = esquema.surfaceContainerHighest,
        modifier = Modifier
            .testTag(EtiquetasInstalacion.BARRA)
            .fillMaxWidth()
            .height(6.dp),
    )
    Text(
        text = "Puedes seguir usando el móvil mientras tanto. Te aviso cuando esté lista.",
        style = MaterialTheme.typography.bodyMedium,
        color = esquema.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(
            onClick = alCancelar,
            modifier = Modifier
                .testTag(EtiquetasInstalacion.BOTON_CANCELAR)
                .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text("Cancelar")
        }
        TextButton(
            onClick = alOcultar,
            modifier = Modifier
                .testTag(EtiquetasInstalacion.BOTON_OCULTAR)
                .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text("Ocultar")
        }
    }
}

@Composable
private fun Trabajando(nombre: String, frase: String) {
    Text(
        text = nombre,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = frase,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .testTag(EtiquetasInstalacion.BARRA)
            .fillMaxWidth()
            .height(6.dp),
    )
}

/**
 * Confirmación breve y un solo camino claro. El círculo dorado con el check reutiliza
 * el mismo primaryContainer del estado "actualizable": es la misma familia de señal,
 * cerrando el ciclo.
 */
@Composable
private fun Hecho(estado: EstadoHoja.Hecho, alAbrir: () -> Unit, alCerrar: () -> Unit) {
    val esquema = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(EtiquetasInstalacion.HECHO)
            .size(64.dp)
            .background(esquema.primaryContainer, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = esquema.onPrimaryContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Text(
        text = "Ya tienes ${estado.nombre}",
        style = MaterialTheme.typography.headlineSmall,
        color = esquema.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Instalada la versión ${estado.version}. La encontrarás también en la " +
            "pantalla de inicio del móvil.",
        style = MaterialTheme.typography.bodyMedium,
        color = esquema.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = alAbrir,
        modifier = Modifier
            .testTag(EtiquetasInstalacion.BOTON_ABRIR_HECHO)
            .fillMaxWidth()
            .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
    ) {
        Text("Abrir")
    }
    TextButton(
        onClick = alCerrar,
        modifier = Modifier
            .testTag(EtiquetasInstalacion.BOTON_AHORA_NO_HECHO)
            .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
    ) {
        Text("Ahora no")
    }
}

@Composable
private fun Fallo(estado: EstadoHoja.Fallo, alCerrar: () -> Unit) {
    Text(
        text = "No he podido instalar ${estado.nombre}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(EtiquetasInstalacion.FALLO),
    )
    Text(
        text = estado.explicacion,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = alCerrar,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
    ) {
        Text("Entendido")
    }
}
