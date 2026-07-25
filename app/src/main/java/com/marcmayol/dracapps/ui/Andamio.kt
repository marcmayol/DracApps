package com.marcmayol.dracapps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Las tres secciones de la tienda, como en las maquetas. */
enum class Seccion(val etiqueta: String, val icono: ImageVector, val prueba: String) {
    APPS("Apps", Icons.Rounded.Apps, "pestana-apps"),
    NOVEDADES("Novedades", Icons.Rounded.NewReleases, "pestana-novedades"),
    AJUSTES("Ajustes", Icons.Rounded.Settings, "pestana-ajustes"),
}

/**
 * El armazón con la barra de tres pestañas.
 *
 * La barra está desde el principio porque aparece en todas las maquetas y porque
 * cambiarla más tarde obligaría a rehacer la navegación entera. Novedades y Ajustes
 * llegan en las Fases 3 y 4; hasta entonces dicen honestamente que aún no están, en vez
 * de fingir que funcionan.
 */
@Composable
fun Andamio(
    seccion: Seccion,
    alCambiarDeSeccion: (Seccion) -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Seccion.entries.forEach { candidata ->
                    NavigationBarItem(
                        selected = candidata == seccion,
                        onClick = { alCambiarDeSeccion(candidata) },
                        icon = { Icon(candidata.icono, contentDescription = null) },
                        label = { Text(candidata.etiqueta) },
                        modifier = Modifier.testTag(candidata.prueba),
                    )
                }
            }
        },
    ) { relleno ->
        contenido(Modifier.padding(relleno))
    }
}

/** Lo que se enseña en una sección que todavía no toca. */
@Composable
fun EnConstruccion(seccion: Seccion, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .testTag("en-construccion")
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            text = seccion.etiqueta,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Esta parte todavía no está hecha. Llegará en cuanto le toque.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
