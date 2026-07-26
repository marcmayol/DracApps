package com.marcmayol.dracapps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.ui.ajustes.PantallaAjustes
import com.marcmayol.dracapps.ui.catalogo.PantallaCatalogo
import com.marcmayol.dracapps.ui.comun.laAccionEsAbrir
import com.marcmayol.dracapps.ui.detalle.PantallaDetalle
import com.marcmayol.dracapps.ui.instalacion.HojaDeInstalacion
import com.marcmayol.dracapps.ui.permiso.PantallaPermiso

/**
 * La tienda entera, montada a partir de un solo estado.
 *
 * Recibe el estado ya calculado y devuelve eventos: no llama a nadie por su cuenta.
 * Por eso los tests pueden montarla con cualquier estado, incluidos los que en un móvil
 * de verdad costaría muchísimo provocar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaTienda(
    estado: EstadoTienda,
    alCambiarDeSeccion: (Seccion) -> Unit,
    alPulsarApp: (AppConEstado) -> Unit,
    alAccionar: (AppConEstado) -> Unit,
    alRefrescar: () -> Unit,
    alCerrarDetalle: () -> Unit,
    alCerrarHoja: () -> Unit,
    alAbrirAjustesDeAndroid: () -> Unit,
    alDejarPermisoParaLuego: () -> Unit,
    alAbrirApp: (AppConEstado) -> Unit,
    alCambiarTextoGrande: (Boolean) -> Unit = {},
    alCambiarColorDelSistema: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Estas dos se pintan sin el andamio, así que nadie les aparta la barra de estado ni
    // la de gestos: se lo hacemos aquí, o el título acaba debajo del reloj.
    val aSalvoDelSistema = modifier.windowInsetsPadding(WindowInsets.safeDrawing)

    // El botón principal hace lo que dice que hace: si pone «Abrir», abre.
    val alPulsarElBoton: (AppConEstado) -> Unit = { app ->
        if (laAccionEsAbrir(app.estado)) alAbrirApp(app) else alAccionar(app)
    }

    if (estado.pidiendoPermiso) {
        PantallaPermiso(
            alAbrirAjustes = alAbrirAjustesDeAndroid,
            alDejarloParaLuego = alDejarPermisoParaLuego,
            modifier = aSalvoDelSistema,
        )
        return
    }

    val detalle = estado.detalle
    if (detalle != null) {
        // El gesto de atrás cierra la ficha en vez de la app entera, que es lo que
        // espera cualquiera que haya entrado desde la lista.
        BackHandler(onBack = alCerrarDetalle)

        PantallaDetalle(
            conEstado = detalle,
            alAccionar = { alPulsarElBoton(detalle) },
            alAbrir = { alAbrirApp(detalle) },
            alVolver = alCerrarDetalle,
            alBuscarActualizaciones = alRefrescar,
            comprobando = estado.ajustes.comprobando,
            modifier = aSalvoDelSistema,
        )
        return
    }

    Andamio(
        seccion = estado.seccion,
        alCambiarDeSeccion = alCambiarDeSeccion,
        modifier = modifier,
    ) { relleno ->
        Box(Modifier.fillMaxSize()) {
            when (estado.seccion) {
                Seccion.APPS -> PantallaCatalogo(
                    estado = estado.catalogo,
                    alPulsarApp = alPulsarApp,
                    alAccionar = alPulsarElBoton,
                    alReintentar = alRefrescar,
                    modifier = relleno,
                )

                Seccion.NOVEDADES -> EnConstruccion(Seccion.NOVEDADES, relleno)

                Seccion.AJUSTES -> PantallaAjustes(
                    estado = estado.ajustes,
                    alComprobarAhora = alRefrescar,
                    alCambiarTextoGrande = alCambiarTextoGrande,
                    alCambiarColorDelSistema = alCambiarColorDelSistema,
                    alAbrirAjustesDeAndroid = alAbrirAjustesDeAndroid,
                    modifier = relleno,
                )
            }

            val hoja = estado.hoja
            if (hoja != null) {
                ModalBottomSheet(
                    onDismissRequest = alCerrarHoja,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    HojaDeInstalacion(
                        estado = hoja,
                        alCancelar = alCerrarHoja,
                        alOcultar = alCerrarHoja,
                        alAbrir = { estado.appDeLaHoja?.let(alAbrirApp) ?: alCerrarHoja() },
                        alCerrar = alCerrarHoja,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            }
        }
    }
}
