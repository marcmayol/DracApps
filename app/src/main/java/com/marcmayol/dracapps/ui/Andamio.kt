package com.marcmayol.dracapps.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
 * cambiarla más tarde obligaría a rehacer la navegación entera. Las tres secciones ya
 * están hechas: mientras no lo estuvieron, decían que aún no estaban en vez de fingir
 * que funcionaban.
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

