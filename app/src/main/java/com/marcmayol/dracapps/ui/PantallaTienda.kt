package com.marcmayol.dracapps.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.ui.catalogo.PantallaCatalogo
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
    modifier: Modifier = Modifier,
) {
    if (estado.pidiendoPermiso) {
        PantallaPermiso(
            alAbrirAjustes = alAbrirAjustesDeAndroid,
            alDejarloParaLuego = alDejarPermisoParaLuego,
            modifier = modifier,
        )
        return
    }

    val detalle = estado.detalle
    if (detalle != null) {
        PantallaDetalle(
            conEstado = detalle,
            alAccionar = { alAccionar(detalle) },
            alAbrir = { alAbrirApp(detalle) },
            modifier = modifier,
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
                    alAccionar = alAccionar,
                    alReintentar = alRefrescar,
                    modifier = relleno,
                )

                Seccion.NOVEDADES -> EnConstruccion(Seccion.NOVEDADES, relleno)
                Seccion.AJUSTES -> EnConstruccion(Seccion.AJUSTES, relleno)
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
                        alAbrir = { estado.detalle?.let(alAbrirApp) ?: alCerrarHoja() },
                        alCerrar = alCerrarHoja,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            }
        }
    }
}
