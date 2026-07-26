package com.marcmayol.dracapps

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcmayol.dracapps.android.IconosDelMovil
import com.marcmayol.dracapps.ui.PantallaTienda
import com.marcmayol.dracapps.ui.TiendaViewModel
import com.marcmayol.dracapps.ui.comun.LocalIconosInstalados
import com.marcmayol.dracapps.ui.tema.DracAppsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val piezas = (application as DracAppsApp).piezas
        // Los iconos de lo instalado los tiene el propio móvil: se leen aquí, donde hay
        // Context, y bajan a las pantallas sin pasar por el modelo, que no sabe de Android.
        val iconosDelMovil = IconosDelMovil(applicationContext)

        setContent {
            // El tema se decide con lo guardado, antes de pintar: si esto llegara más
            // tarde, la app arrancaría con un aspecto y cambiaría a otro a la vista.
            val textoGrande by piezas.ajustes.textoGrande.collectAsStateWithLifecycle()
            val colorDelSistema by piezas.ajustes.colorDelSistema.collectAsStateWithLifecycle()

            DracAppsTheme(colorDinamico = colorDelSistema, textoGrande = textoGrande) {
                val modelo: TiendaViewModel = viewModel(factory = fabrica(piezas))
                val estado by modelo.estado.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalIconosInstalados provides iconosDelMovil) {
                    PantallaTienda(
                        estado = estado,
                        alCambiarDeSeccion = modelo::irA,
                        alPulsarApp = modelo::abrirDetalle,
                        alAccionar = modelo::instalar,
                        alRefrescar = modelo::refrescar,
                        alCerrarDetalle = modelo::cerrarDetalle,
                        alCerrarHoja = modelo::cerrarHoja,
                        alAbrirAjustesDeAndroid = {
                            startActivity(piezas.intencionDeAjustesDeOrigenes())
                        },
                        alDejarPermisoParaLuego = modelo::cerrarPermiso,
                        alAbrirApp = { app -> abrir(app.id) },
                        alCambiarTextoGrande = modelo::cambiarTextoGrande,
                        alCambiarColorDelSistema = modelo::cambiarColorDelSistema,
                    )
                }
            }
        }
    }

    private fun abrir(id: String) {
        val intencion: Intent? = packageManager.getLaunchIntentForPackage(id)
        if (intencion == null) {
            Toast.makeText(this, "Esa app no se puede abrir desde aquí", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(intencion)
        }
    }

    private fun fabrica(piezas: Piezas) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(clase: Class<T>): T = TiendaViewModel(
            obtenerCatalogo = piezas.obtenerCatalogo,
            instalarPaquete = piezas.instalarPaquete,
            reanudar = piezas.reanudarInstalaciones,
            hayPermisoParaInstalar = piezas::hayPermisoParaInstalar,
            ajustesGuardados = piezas.ajustes,
        ).also { it.alArrancar() } as T
    }
}
