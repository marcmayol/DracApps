package com.marcmayol.dracapps.ui.novedades

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.R
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.ui.ajustes.EstadoDeLaTienda
import com.marcmayol.dracapps.ui.catalogo.FilaDeApp
import com.marcmayol.dracapps.ui.catalogo.EstadoPantallaCatalogo
import com.marcmayol.dracapps.ui.comun.EstadoVacio
import com.marcmayol.dracapps.ui.detalle.tamanoLegible
import com.marcmayol.dracapps.ui.tema.Espaciado

object EtiquetasNovedades {
    const val LISTA = "lista-novedades"
    const val SUBTITULO = "subtitulo-novedades"
    const val ACTUALIZAR_TODO = "boton-actualizar-todo"
    const val FILA_TIENDA = "fila-esta-tienda"
    const val SEPARADOR_AL_DIA = "separador-al-dia"
}

/**
 * Novedades: lo que se puede actualizar, y nada más que estorbe.
 *
 * El diseño lo resume así: «una sola acción arriba que resuelve el 90 % de las visitas;
 * debajo, lo que está al día se colapsa para que no compita». Por eso mandan el botón
 * de arriba y el orden: primero lo que pide algo, después lo que ya está en su sitio.
 *
 * Lo que no está instalado no aparece: esto no es el catálogo, es la lista de lo que
 * tienes y puede mejorar.
 */
@Composable
fun PantallaNovedades(
    estado: EstadoPantallaCatalogo,
    tienda: EstadoDeLaTienda,
    alPulsarApp: (AppConEstado) -> Unit,
    alAccionar: (AppConEstado) -> Unit,
    alActualizarTodo: () -> Unit,
    alActualizarLaTienda: () -> Unit,
    alComprobar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val instaladas = when (estado) {
        is EstadoPantallaCatalogo.Listo -> estado.apps
        is EstadoPantallaCatalogo.SinConexion -> estado.instaladas
        else -> emptyList()
    }.filter { it.estado !is EstadoApp.NoInstalada }

    val pendientes = instaladas.filter { it.tieneActualizacion }
    val alDia = instaladas - pendientes.toSet()
    val laTiendaTieneNovedad = tienda.novedad != null && !tienda.esError

    if (pendientes.isEmpty() && !laTiendaTieneNovedad && alDia.isEmpty()) {
        EstadoVacio(
            titulo = "Nada que actualizar",
            explicacion = "Cuando alguna de tus apps tenga versión nueva, aparecerá aquí.",
            textoDelBoton = "Volver a mirar",
            alPulsar = alComprobar,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .testTag(EtiquetasNovedades.LISTA)
            .fillMaxSize(),
        contentPadding = PaddingValues(Espaciado.margenPantalla),
        verticalArrangement = Arrangement.spacedBy(Espaciado.entreTarjetas),
    ) {
        item {
            Cabecera(
                pendientes = pendientes,
                laTiendaTambien = laTiendaTieneNovedad,
                alActualizarTodo = alActualizarTodo,
            )
        }

        // La tienda va primero cuando le toca: es la que trae las demás, y actualizarla
        // se lleva por delante lo que estuviera a medias.
        if (laTiendaTieneNovedad) {
            item { FilaDeLaTienda(tienda, alActualizarLaTienda) }
        }

        items(pendientes, key = { it.id }) { app ->
            FilaDeApp(
                conEstado = app,
                alPulsar = { alPulsarApp(app) },
                alAccionar = { alAccionar(app) },
            )
        }

        if (alDia.isNotEmpty()) {
            item {
                Text(
                    text = "Al día",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(EtiquetasNovedades.SEPARADOR_AL_DIA)
                        .padding(top = 12.dp, bottom = 2.dp),
                )
            }

            items(alDia, key = { it.id }) { app ->
                FilaDeApp(
                    conEstado = app,
                    alPulsar = { alPulsarApp(app) },
                    alAccionar = { alAccionar(app) },
                )
            }
        }
    }
}

@Composable
private fun Cabecera(
    pendientes: List<AppConEstado>,
    laTiendaTambien: Boolean,
    alActualizarTodo: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(
            text = "Novedades",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitulo(pendientes, laTiendaTambien),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(EtiquetasNovedades.SUBTITULO),
        )

        // Una sola acción arriba, y solo cuando hay más de una cosa que actualizar: con
        // una sola, el botón de su fila ya lo resuelve y este sobra. La tienda cuenta,
        // porque el botón también se ocupa de ella (la última, que al instalarse cierra).
        if (pendientes.size + (if (laTiendaTambien) 1 else 0) > 1) {
            Button(
                onClick = alActualizarTodo,
                modifier = Modifier
                    .testTag(EtiquetasNovedades.ACTUALIZAR_TODO)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Espaciado.areaTactilMinima)
                    .padding(top = 8.dp),
            ) {
                Text("Actualizar todo")
            }
        }
    }
}

/**
 * La propia tienda, cuando tiene versión nueva.
 *
 * Se parece a una fila de app pero no lo es: no sale del catálogo ni se puede abrir su
 * ficha, así que se dibuja aparte en vez de forzarla dentro del modelo del catálogo.
 */
@Composable
private fun FilaDeLaTienda(tienda: EstadoDeLaTienda, alActualizar: () -> Unit) {
    val esquema = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaciado.dentroDeTarjeta),
        modifier = Modifier
            .testTag(EtiquetasNovedades.FILA_TIENDA)
            .fillMaxWidth()
            .heightIn(min = Espaciado.altoFilaApp)
            .background(esquema.surfaceContainerHigh, MaterialTheme.shapes.large)
            .border(1.5.dp, esquema.primary, MaterialTheme.shapes.large)
            .clickable(onClick = alActualizar)
            .padding(Espaciado.dentroDeTarjeta),
    ) {
        Icon(
            painter = painterResource(R.drawable.logo_dracapps),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(Espaciado.iconoEnLista),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Esta tienda",
                style = MaterialTheme.typography.titleMedium,
                color = esquema.onSurface,
                maxLines = 1,
            )
            Text(
                text = tienda.novedad.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = esquema.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Button(
            onClick = alActualizar,
            modifier = Modifier.defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text("Actualizar")
        }
    }
}

/**
 * Cuántas esperan y cuánto ocupan.
 *
 * El tamaño va delante porque es la pregunta de quien tiene datos contados: no «¿qué
 * hay?», sino «¿cuánto me va a costar?».
 */
private fun subtitulo(pendientes: List<AppConEstado>, laTiendaTambien: Boolean): String {
    val cuantas = pendientes.size + if (laTiendaTambien) 1 else 0
    if (cuantas == 0) return "Todo al día"

    val apps = if (cuantas == 1) "1 app por actualizar" else "$cuantas apps por actualizar"
    val bytes = pendientes.sumOf { it.app.tamanoBytes }
    return if (bytes > 0) "$apps · ${tamanoLegible(bytes)} en total" else apps
}
