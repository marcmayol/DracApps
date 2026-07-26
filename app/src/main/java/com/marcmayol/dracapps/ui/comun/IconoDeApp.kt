package com.marcmayol.dracapps.ui.comun

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage

/**
 * De dónde salen los iconos de lo que ya está instalado.
 *
 * Es una interfaz y no la clase de Android para que las pantallas se puedan montar en un
 * test sin PackageManager: por defecto no hay iconos y se ve el marcador, igual que en
 * un móvil donde la app todavía no está.
 */
fun interface IconosInstalados {
    fun de(id: String): ImageBitmap?
}

val LocalIconosInstalados = staticCompositionLocalOf { IconosInstalados { null } }

/**
 * El icono de una app, con tres orígenes por orden de preferencia.
 *
 * 1. El del móvil, si la app está instalada: es el que se ve en la pantalla de inicio,
 *    con la forma que le da este móvil y sin gastar red.
 * 2. El que publica el catálogo, para lo que aún no se tiene, que es justo cuando el
 *    icono ayuda a decidir.
 * 3. El marcador de la tienda —la inicial sobre una ficha del tesoro— cuando no hay
 *    ninguno de los dos, o mientras el del catálogo viaja.
 */
@Composable
fun IconoDeApp(
    id: String,
    nombre: String,
    iconoUrl: String,
    tamano: Dp,
    modifier: Modifier = Modifier,
    atenuado: Boolean = false,
    estiloDeInicial: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val delMovil = LocalIconosInstalados.current.de(id)
    val marcador: @Composable () -> Unit = { Inicial(nombre, estiloDeInicial) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(tamano)
            .alpha(if (atenuado) 0.55f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        when {
            delMovil != null -> Image(
                bitmap = delMovil,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            iconoUrl.isNotBlank() -> SubcomposeAsyncImage(
                model = iconoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { marcador() },
                error = { marcador() },
                modifier = Modifier.fillMaxSize(),
            )

            else -> marcador()
        }
    }
}

@Composable
private fun Inicial(nombre: String, estilo: TextStyle) {
    Text(
        text = nombre.take(1).uppercase(),
        style = estilo,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
