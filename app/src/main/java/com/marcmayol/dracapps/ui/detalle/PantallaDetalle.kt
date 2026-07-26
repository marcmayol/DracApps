package com.marcmayol.dracapps.ui.detalle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.modelo.MotivoNoGestionada
import com.marcmayol.dracapps.ui.comun.ChipDeEstado
import com.marcmayol.dracapps.ui.comun.IconoDeApp
import com.marcmayol.dracapps.ui.comun.textoDeAccion
import com.marcmayol.dracapps.ui.tema.Espaciado

object EtiquetasDetalle {
    const val PANTALLA = "pantalla-detalle"
    const val BOTON_PRINCIPAL = "boton-principal-detalle"
    const val BOTON_ABRIR = "boton-abrir-detalle"
    const val BOTON_VOLVER = "boton-volver-detalle"
    const val BOTON_BUSCAR = "boton-buscar-actualizaciones"
    const val NOTAS = "notas-version"
}

/**
 * El detalle de una app.
 *
 * Manda el botón contextual: cuando hay algo nuevo, Actualizar va relleno y Abrir con
 * contorno; si está al día, Abrir pasa a relleno y no hay nada más que ofrecer. Las
 * notas de versión se enseñan tal como las escribió quien publicó la Release, que para
 * eso se escriben en cristiano y no como un changelog.
 */
@Composable
fun PantallaDetalle(
    conEstado: AppConEstado,
    alAccionar: () -> Unit,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
    alBuscarActualizaciones: () -> Unit = {},
    comprobando: Boolean = false,
) {
    val esquema = MaterialTheme.colorScheme
    val app = conEstado.app
    val hayAlgoNuevo = conEstado.estado is EstadoApp.Actualizable
    val instalada = conEstado.estado !is EstadoApp.NoInstalada

    Column(
        modifier = modifier
            .testTag(EtiquetasDetalle.PANTALLA)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Espaciado.margenPantalla),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // La salida, donde se busca: arriba a la izquierda. El gesto de atrás también
        // vale, pero hay quien no lo usa nunca y se queda encallado en la ficha.
        IconButton(
            onClick = alVolver,
            modifier = Modifier
                .testTag(EtiquetasDetalle.BOTON_VOLVER)
                .size(Espaciado.areaTactilMinima),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver a la lista de apps",
                tint = esquema.onSurface,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoDeApp(
                id = app.id,
                nombre = app.nombre,
                iconoUrl = app.iconoUrl,
                tamano = Espaciado.iconoEnDetalle,
                atenuado = !instalada,
                estiloDeInicial = MaterialTheme.typography.headlineMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = app.nombre,
                    style = MaterialTheme.typography.headlineMedium,
                    color = esquema.onSurface,
                )
                ChipDeEstado(conEstado.estado)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = alAccionar,
                modifier = Modifier
                    .testTag(EtiquetasDetalle.BOTON_PRINCIPAL)
                    .weight(1f)
                    .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
            ) {
                Text(textoDeAccion(conEstado.estado))
            }

            if (hayAlgoNuevo && instalada) {
                OutlinedButton(
                    onClick = alAbrir,
                    modifier = Modifier
                        .testTag(EtiquetasDetalle.BOTON_ABRIR)
                        .weight(1f)
                        .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
                ) {
                    Text("Abrir")
                }
            }
        }

        // Cuando ya está al día, el botón principal solo abre. Esto es lo que contesta a
        // «¿seguro que no hay nada nuevo?»: vuelve a mirar el catálogo en el momento, sin
        // tener que irse a Ajustes.
        if (instalada && !hayAlgoNuevo) {
            OutlinedButton(
                onClick = alBuscarActualizaciones,
                enabled = !comprobando,
                modifier = Modifier
                    .testTag(EtiquetasDetalle.BOTON_BUSCAR)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
            ) {
                Text(if (comprobando) "Comprobando…" else "Buscar actualizaciones")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Dato("Versión", versionMostrada(conEstado), Modifier.weight(1f))
            Dato("Tamaño", tamanoLegible(app.tamanoBytes), Modifier.weight(1f))
        }

        if (app.notas.isNotBlank()) {
            Seccion("Novedades de esta versión")
            Text(
                text = app.notas,
                style = MaterialTheme.typography.bodyMedium,
                color = esquema.onSurfaceVariant,
                modifier = Modifier.testTag(EtiquetasDetalle.NOTAS),
            )
        }

        if (app.descripcion.isNotBlank()) {
            Seccion("Qué hace")
            Text(
                text = app.descripcion,
                style = MaterialTheme.typography.bodyLarge,
                color = esquema.onSurfaceVariant,
            )
        }

        avisoDeEstado(conEstado)?.let { aviso ->
            Text(
                text = aviso,
                style = MaterialTheme.typography.bodyMedium,
                color = esquema.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(esquema.surfaceContainerLow, MaterialTheme.shapes.large)
                    .padding(Espaciado.dentroDeTarjeta),
            )
        }
    }
}

@Composable
private fun Seccion(titulo: String) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = valor,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun versionMostrada(conEstado: AppConEstado): String =
    when (val estado = conEstado.estado) {
        is EstadoApp.Actualizable -> "${estado.versionInstalada} → ${estado.versionNueva}"
        else -> conEstado.app.versionName
    }

/** Lo que hay que contarle a quien mira, según en qué estado esté. */
private fun avisoDeEstado(conEstado: AppConEstado): String? = when (val estado = conEstado.estado) {
    is EstadoApp.Actualizable ->
        "Tienes instalada la ${estado.versionInstalada}. Tus datos se conservan."

    is EstadoApp.NoGestionada -> when (estado.motivo) {
        MotivoNoGestionada.OTRA_FIRMA ->
            "Tienes instalada una versión que no viene de aquí (la ${estado.versionInstalada}). " +
                "Para que la tienda pueda actualizarla habría que desinstalarla antes, y eso " +
                "borraría sus datos. Mejor no tocar nada sin hablarlo."

        MotivoNoGestionada.OTRO_ORIGEN ->
            "Esta app la instalaste por tu cuenta. La tienda no la actualiza, pero puede " +
                "encargarse a partir de ahora si quieres."
    }

    else -> null
}

/** Tamaños como los diría una persona, no como los escupe un ordenador. */
internal fun tamanoLegible(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) {
        "%.1f MB".format(mb).replace('.', ',')
    } else {
        "%.0f KB".format(bytes / 1024.0)
    }
}
