package com.marcmayol.dracapps

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcm.actualizador.EstadoActualizacion
import com.marcm.actualizador.Modo
import com.marcm.actualizador.TipoError
import com.marcmayol.dracapps.ui.ajustes.EstadoDeLaTienda
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.marcmayol.dracapps.android.IconosDelMovil
import com.marcmayol.dracapps.ui.PantallaTienda
import com.marcmayol.dracapps.ui.TiendaViewModel
import com.marcmayol.dracapps.ui.comun.LocalIconosInstalados
import com.marcmayol.dracapps.ui.tema.DracAppsTheme

class MainActivity : ComponentActivity() {

    private val autoactualizador get() = (application as DracAppsApp).autoactualizador

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // La comprobación periódica se programa aquí y no en la Application porque los
        // tests de pantallas instancian la Application de verdad, y allí WorkManager
        // todavía no existe. Al abrir la app es igual de bueno: reprograma si hace falta.
        autoactualizador.programarPeriodica()

        // Unos segundos después de abrir, en silencio: si falla no se entera nadie.
        lifecycleScope.launch {
            delay(3000)
            autoactualizador.comprobar(Modo.AUTOMATICO)
        }

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
                val estadoDelModelo by modelo.estado.collectAsStateWithLifecycle()

                // La actualización de la tienda no pasa por el ViewModel: ese no conoce
                // Android, y el actualizador es puro Android (WorkManager, instalador).
                // Se junta aquí, ya traducido a palabras.
                val autoactualizacion by autoactualizador.estado.collectAsStateWithLifecycle()
                var buscarSola by remember { mutableStateOf(autoactualizador.buscarAutomaticamente) }
                val estado = estadoDelModelo.copy(
                    ajustes = estadoDelModelo.ajustes.copy(
                        tienda = enPalabras(
                            estado = autoactualizacion,
                            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            buscarSola = buscarSola,
                        ),
                    ),
                )

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
                        alCambiarBuscarLaTienda = { activado ->
                            autoactualizador.buscarAutomaticamente = activado
                            buscarSola = activado
                        },
                        alComprobarLaTienda = {
                            lifecycleScope.launch { autoactualizador.comprobar(Modo.MANUAL) }
                        },
                        alActualizarLaTienda = { autoactualizador.actualizarAhora() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Retoma la actualización si acaban de conceder el permiso de instalación.
        autoactualizador.onPermisoQuizaConcedido()
    }

    private fun abrir(id: String) {
        val intencion: Intent? = packageManager.getLaunchIntentForPackage(id)
        if (intencion == null) {
            Toast.makeText(this, "Esa app no se puede abrir desde aquí", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(intencion)
        }
    }

    /**
     * Traduce el estado del actualizador a lo que la pantalla enseña.
     *
     * La regla de la casa: el aviso solo aparece cuando hay novedad o cuando algo está
     * pasando; los errores y el "estás al día" viven en Ajustes, donde se han pedido.
     */
    private fun enPalabras(
        estado: EstadoActualizacion,
        version: String,
        buscarSola: Boolean,
    ): EstadoDeLaTienda {
        val base = EstadoDeLaTienda(version = version, buscarSola = buscarSola)
        return when (estado) {
            is EstadoActualizacion.Disponible -> base.copy(
                novedad = "Versión ${estado.info.versionName} de la tienda disponible",
                notas = estado.info.notas,
            )

            is EstadoActualizacion.Descargando -> base.copy(
                novedad = "Actualizando la tienda",
                mensaje = "Descargando… ${estado.porcentaje} %",
            )

            EstadoActualizacion.Verificando -> base.copy(
                novedad = "Actualizando la tienda",
                mensaje = "Comprobando que la copia es íntegra…",
            )

            EstadoActualizacion.Instalando -> base.copy(
                novedad = "Actualizando la tienda",
                mensaje = "Instalando… la tienda se cerrará un momento",
            )

            EstadoActualizacion.Comprobando -> base.copy(mensaje = "Buscando…")
            EstadoActualizacion.AlDia -> base.copy(mensaje = "La tienda está al día ✓")

            is EstadoActualizacion.Error -> base.copy(
                esError = true,
                mensaje = when (estado.tipo) {
                    TipoError.SIN_RED -> "Sin conexión. Inténtalo más tarde."
                    TipoError.HTTP, TipoError.MANIFIESTO ->
                        "No se pudo comprobar si hay una versión nueva de la tienda."
                    TipoError.DESCARGA -> "Falló la descarga."
                    TipoError.HASH -> "La descarga llegó corrupta y se ha borrado."
                    TipoError.INSTALACION -> estado.mensaje ?: "No se pudo instalar."
                },
            )

            EstadoActualizacion.Inactivo, EstadoActualizacion.PidiendoPermiso -> base
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
