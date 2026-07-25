package com.marcmayol.dracapps.ui.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.ui.comun.EstadoDeError
import com.marcmayol.dracapps.ui.comun.EstadoVacio
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.ui.tema.Espaciado

object EtiquetasCatalogo {
    const val LISTA = "lista-catalogo"
    const val SUBTITULO = "subtitulo-catalogo"
}

/** Lo que la pantalla del catálogo puede estar enseñando. */
sealed interface EstadoPantallaCatalogo {
    data object Cargando : EstadoPantallaCatalogo
    data class Listo(val apps: List<AppConEstado>) : EstadoPantallaCatalogo
    data object Vacio : EstadoPantallaCatalogo
    data class SinConexion(val instaladas: List<AppConEstado>) : EstadoPantallaCatalogo
}

/**
 * La lista del catálogo, donde conviven los cuatro estados.
 *
 * Es la pantalla que ve la familia al abrir. Todo lo demás de la app existe para que
 * esta se entienda de un vistazo.
 */
@Composable
fun PantallaCatalogo(
    estado: EstadoPantallaCatalogo,
    alPulsarApp: (AppConEstado) -> Unit,
    alAccionar: (AppConEstado) -> Unit,
    alReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (estado) {
        EstadoPantallaCatalogo.Cargando -> Column(modifier.fillMaxSize()) {}

        EstadoPantallaCatalogo.Vacio -> EstadoVacio(
            titulo = "Todavía no hay apps",
            explicacion = "Aquí irán apareciendo las aplicaciones que publique. " +
                "El dragón está echado sobre un tesoro vacío, de momento.",
            textoDelBoton = "Volver a mirar",
            alPulsar = alReintentar,
            modifier = modifier,
        )

        is EstadoPantallaCatalogo.SinConexion -> EstadoDeError(
            titulo = "No he podido conectar",
            explicacion = "Revisa el Wi-Fi o los datos y vuelve a intentarlo. " +
                "Las apps que ya tienes instaladas siguen funcionando igual.",
            alReintentar = alReintentar,
            modifier = modifier,
        )

        is EstadoPantallaCatalogo.Listo -> Lista(
            apps = estado.apps,
            alPulsarApp = alPulsarApp,
            alAccionar = alAccionar,
            modifier = modifier,
        )
    }
}

@Composable
private fun Lista(
    apps: List<AppConEstado>,
    alPulsarApp: (AppConEstado) -> Unit,
    alAccionar: (AppConEstado) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendientes = apps.count { it.tieneActualizacion }

    LazyColumn(
        modifier = modifier
            .testTag(EtiquetasCatalogo.LISTA)
            .fillMaxSize(),
        contentPadding = PaddingValues(Espaciado.margenPantalla),
        verticalArrangement = Arrangement.spacedBy(Espaciado.entreTarjetas),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Text(
                    text = "Tus apps",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitulo(apps.size, pendientes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(EtiquetasCatalogo.SUBTITULO),
                )
            }
        }

        items(apps, key = { it.id }) { app ->
            FilaDeApp(
                conEstado = app,
                alPulsar = { alPulsarApp(app) },
                alAccionar = { alAccionar(app) },
            )
        }
    }
}

/** Singular y plural cuidados: se lo va a leer gente, no un log. */
private fun subtitulo(total: Int, pendientes: Int): String {
    val apps = if (total == 1) "1 app" else "$total apps"
    val esperando = when (pendientes) {
        0 -> "todo al día"
        1 -> "1 actualización esperando"
        else -> "$pendientes actualizaciones esperando"
    }
    return "$apps · $esperando"
}
