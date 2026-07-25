package com.marcmayol.dracapps.ui.permiso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.ui.tema.Espaciado

object EtiquetasPermiso {
    const val PANTALLA = "pantalla-permiso"
    const val BOTON_AJUSTES = "boton-abrir-ajustes"
    const val BOTON_AHORA_NO = "boton-ahora-no"
}

/**
 * La pantalla del primer día.
 *
 * Está escrita para alguien que no es técnico y que se asusta si le hablan de riesgos.
 * Por eso, deliberadamente: nada de "advertencia", ni "riesgo", ni triángulos amarillos.
 * Se explica por qué Android pregunta, se enumeran tres pasos cortos y se ofrece una
 * salida sin culpa. El escudo va en dorado, no en rojo, porque el permiso no es un
 * peligro: es el móvil haciendo bien su trabajo.
 */
@Composable
fun PantallaPermiso(
    alAbrirAjustes: () -> Unit,
    alDejarloParaLuego: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val esquema = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .testTag(EtiquetasPermiso.PANTALLA)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Espaciado.margenPantalla * 1.5f),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(esquema.primaryContainer, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = esquema.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            text = "Un permiso, una sola vez",
            style = MaterialTheme.typography.headlineMedium,
            color = esquema.onSurface,
        )

        Text(
            text = "Android pregunta antes de dejar que una app instale otras. Es normal " +
                "y es bueno: significa que tu móvil está protegido. Solo hay que decirle " +
                "que de DracApps sí te fías.",
            style = MaterialTheme.typography.bodyLarge,
            color = esquema.onSurfaceVariant,
        )

        Paso(1, "Toca el botón de abajo. Se abrirán los ajustes de Android.")
        Paso(2, "Verás una línea que dice «Permitir desde esta fuente». Actívala.")
        Paso(3, "Vuelve atrás con la flecha. Ya está, no hay que hacerlo nunca más.")

        Text(
            text = "Si algo no cuadra, cierra y llámame. No pasa nada por dejarlo para luego.",
            style = MaterialTheme.typography.bodyMedium,
            color = esquema.onSurfaceVariant,
        )

        Button(
            onClick = alAbrirAjustes,
            modifier = Modifier
                .testTag(EtiquetasPermiso.BOTON_AJUSTES)
                .fillMaxWidth()
                .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text("Abrir los ajustes de Android")
        }

        TextButton(
            onClick = alDejarloParaLuego,
            modifier = Modifier
                .testTag(EtiquetasPermiso.BOTON_AHORA_NO)
                .fillMaxWidth()
                .defaultMinSize(minHeight = Espaciado.areaTactilMinima),
        ) {
            Text("Ahora no")
        }
    }
}

@Composable
private fun Paso(numero: Int, texto: String) {
    val esquema = MaterialTheme.colorScheme

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(esquema.secondaryContainer, CircleShape),
        ) {
            Text(
                text = "$numero",
                style = MaterialTheme.typography.titleSmall,
                color = esquema.onSecondaryContainer,
            )
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = esquema.onSurface,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
