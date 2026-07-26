package com.marcmayol.dracapps.ui.ajustes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.BuildConfig
import com.marcmayol.dracapps.R
import com.marcmayol.dracapps.ui.tema.Espaciado
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object EtiquetasAjustes {
    const val PANTALLA = "pantalla-ajustes"
    const val COMPROBAR = "boton-comprobar"
    const val PERMISO = "boton-permiso"
    const val TEXTO_GRANDE = "interruptor-texto-grande"
    const val COLOR_DEL_SISTEMA = "interruptor-color-del-sistema"
}

/** Lo que Ajustes necesita saber, ya cocinado por el modelo. */
data class EstadoAjustes(
    val textoGrande: Boolean = false,
    val colorDelSistema: Boolean = false,
    val ultimaComprobacion: Long? = null,
    val comprobando: Boolean = false,
    val hayPermisoParaInstalar: Boolean = false,
    val actualizacionesPendientes: Int = 0,
    val appsEnElCatalogo: Int = 0,
)

/**
 * Ajustes.
 *
 * Aquí solo hay cosas que hacen algo. La tienda la usa gente que no quiere administrar
 * nada, así que cada interruptor cambia algo que se nota, y lo que todavía no existe
 * —la comprobación automática— se dice en voz alta en vez de fingirse con un mando que
 * no gobierna nada.
 */
@Composable
fun PantallaAjustes(
    estado: EstadoAjustes,
    alComprobarAhora: () -> Unit,
    alCambiarTextoGrande: (Boolean) -> Unit,
    alCambiarColorDelSistema: (Boolean) -> Unit,
    alAbrirAjustesDeAndroid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(EtiquetasAjustes.PANTALLA)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Espaciado.margenPantalla),
        verticalArrangement = Arrangement.spacedBy(Espaciado.entreTarjetas),
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Tarjeta("Actualizaciones") {
            Text(
                text = resumenDeActualizaciones(estado),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = ultimaVezQueSeMiro(estado),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = alComprobarAhora,
                enabled = !estado.comprobando,
                modifier = Modifier
                    .testTag(EtiquetasAjustes.COMPROBAR)
                    .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
            ) {
                Text(if (estado.comprobando) "Comprobando…" else "Comprobar ahora")
            }
            Text(
                text = "La tienda todavía no mira sola si hay novedades: se entera al " +
                    "abrirla y cuando lo pides aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Tarjeta("Instalar apps") {
            Text(
                text = if (estado.hayPermisoParaInstalar) {
                    "Android deja que la tienda instale y actualice tus apps."
                } else {
                    "Android todavía no deja que la tienda instale nada. Sin ese permiso " +
                        "puedes mirar el catálogo, pero no instalar."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = alAbrirAjustesDeAndroid,
                modifier = Modifier
                    .testTag(EtiquetasAjustes.PERMISO)
                    .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
            ) {
                Text(if (estado.hayPermisoParaInstalar) "Revisar el permiso" else "Dar permiso")
            }
        }

        Tarjeta("Cómo se ve") {
            Interruptor(
                titulo = "Texto grande",
                explicacion = "Sube todas las letras de la tienda un 15 %.",
                activado = estado.textoGrande,
                alCambiar = alCambiarTextoGrande,
                etiqueta = EtiquetasAjustes.TEXTO_GRANDE,
            )
        }

        Tarjeta("Avanzado") {
            Interruptor(
                titulo = "Usar los colores del sistema",
                explicacion = "Viene apagado a propósito: la tienda se reconoce por su " +
                    "dorado, y con los colores del fondo de pantalla cambiaría de aspecto " +
                    "cada vez.",
                activado = estado.colorDelSistema,
                alCambiar = alCambiarColorDelSistema,
                etiqueta = EtiquetasAjustes.COLOR_DEL_SISTEMA,
            )
        }

        AcercaDe(estado.appsEnElCatalogo)
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.large,
            )
            .padding(Espaciado.dentroDeTarjeta),
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        contenido()
    }
}

/**
 * Un interruptor con su explicación.
 *
 * Toda la fila conmuta, no solo el mando: es un objetivo mucho más grande y quien tiene
 * pulso poco fino lo agradece.
 */
@Composable
private fun Interruptor(
    titulo: String,
    explicacion: String,
    activado: Boolean,
    alCambiar: (Boolean) -> Unit,
    etiqueta: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaciado.dentroDeTarjeta),
        modifier = Modifier
            .testTag(etiqueta)
            .fillMaxWidth()
            .defaultMinSize(minHeight = Espaciado.areaTactilMinima)
            .toggleable(
                value = activado,
                role = Role.Switch,
                onValueChange = alCambiar,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = explicacion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // El mando no escucha por su cuenta: quien decide es la fila entera.
        Switch(checked = activado, onCheckedChange = null)
    }
}

@Composable
private fun AcercaDe(appsEnElCatalogo: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaciado.dentroDeTarjeta),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = Espaciado.dentroDeTarjeta),
    ) {
        Image(
            painter = painterResource(R.drawable.logo_dracapps),
            contentDescription = null,
            modifier = Modifier.size(Espaciado.iconoEnLista),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "DracApps ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$appsEnElCatalogo apps · ${servidorDelCatalogo()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** De dónde vienen las apps, dicho corto: el dominio, sin la ruta del fichero. */
private fun servidorDelCatalogo(): String =
    BuildConfig.URL_CATALOGO
        .substringAfter("://")
        .substringBefore("/")

private fun resumenDeActualizaciones(estado: EstadoAjustes): String = when {
    estado.actualizacionesPendientes == 0 -> "Todo al día."
    estado.actualizacionesPendientes == 1 -> "1 actualización esperando."
    else -> "${estado.actualizacionesPendientes} actualizaciones esperando."
}

/**
 * Cuándo se miró por última vez, en cristiano.
 *
 * Lo de hoy se cuenta por la hora, que es lo que se recuerda; lo de otro día, por la
 * fecha. Nada de «hace 2 h 14 min».
 */
private fun ultimaVezQueSeMiro(estado: EstadoAjustes): String {
    val instante = estado.ultimaComprobacion ?: return "Todavía no se ha comprobado."
    val zona = ZoneId.systemDefault()
    val momento = Instant.ofEpochMilli(instante).atZone(zona)
    val hoy = LocalDate.now(zona)

    return when (momento.toLocalDate()) {
        hoy -> "Comprobado hoy a las ${momento.format(HORA)}."
        hoy.minusDays(1) -> "Comprobado ayer a las ${momento.format(HORA)}."
        else -> "Comprobado el ${momento.format(FECHA)}."
    }
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FECHA = DateTimeFormatter.ofPattern("d/M/yyyy")
