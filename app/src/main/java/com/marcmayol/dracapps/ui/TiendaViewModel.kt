package com.marcmayol.dracapps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.actualizador.instalacion.InstalarPaquete
import com.marcmayol.actualizador.instalacion.ReanudarInstalaciones
import com.marcmayol.actualizador.modelo.EstadoActualizacion
import com.marcmayol.actualizador.modelo.MotivoFallo
import com.marcmayol.actualizador.modelo.Paquete
import com.marcmayol.dracapps.dominio.casos.ObtenerCatalogo
import com.marcmayol.dracapps.dominio.casos.ResultadoCatalogo
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.ui.catalogo.EstadoPantallaCatalogo
import com.marcmayol.dracapps.ui.instalacion.EstadoHoja
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Todo lo que la interfaz necesita saber en un momento dado. */
data class EstadoTienda(
    val seccion: Seccion = Seccion.APPS,
    val catalogo: EstadoPantallaCatalogo = EstadoPantallaCatalogo.Cargando,
    val detalle: AppConEstado? = null,
    val hoja: EstadoHoja? = null,
    val pidiendoPermiso: Boolean = false,
)

/**
 * El estado de la tienda, en un sitio y no repartido por las pantallas.
 *
 * No conoce Android: recibe los casos de uso ya montados y una función que dice si hay
 * permiso para instalar. Así se puede probar entero sin emulador.
 */
class TiendaViewModel(
    private val obtenerCatalogo: ObtenerCatalogo,
    private val instalarPaquete: InstalarPaquete,
    private val reanudar: ReanudarInstalaciones,
    private val hayPermisoParaInstalar: () -> Boolean,
    private val alcance: CoroutineScope? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoTienda())
    val estado: StateFlow<EstadoTienda> = _estado.asStateFlow()

    private val ambito: CoroutineScope get() = alcance ?: viewModelScope

    /**
     * Al arrancar: primero se recoge lo que quedó a medias, después se pide el
     * catálogo. En ese orden, porque si una instalación anterior terminó sin que nadie
     * se enterara, el estado de esa app cambia.
     */
    fun alArrancar() {
        ambito.launch {
            runCatching { reanudar() }
            refrescar()
        }
    }

    fun refrescar() {
        ambito.launch {
            _estado.update { it.copy(catalogo = EstadoPantallaCatalogo.Cargando) }
            val resultado = obtenerCatalogo()
            _estado.update { actual ->
                actual.copy(
                    catalogo = when (resultado) {
                        is ResultadoCatalogo.Listo ->
                            if (resultado.vacio) {
                                EstadoPantallaCatalogo.Vacio
                            } else {
                                EstadoPantallaCatalogo.Listo(resultado.apps)
                            }

                        is ResultadoCatalogo.SinCatalogo ->
                            EstadoPantallaCatalogo.SinConexion(resultado.instaladas)
                    }
                )
            }
        }
    }

    fun irA(seccion: Seccion) = _estado.update { it.copy(seccion = seccion, detalle = null) }

    fun abrirDetalle(app: AppConEstado) = _estado.update { it.copy(detalle = app) }

    fun cerrarDetalle() = _estado.update { it.copy(detalle = null) }

    fun cerrarHoja() = _estado.update { it.copy(hoja = null) }

    fun cerrarPermiso() = _estado.update { it.copy(pidiendoPermiso = false) }

    /**
     * Instalar o actualizar.
     *
     * Si todavía no hay permiso de orígenes desconocidos, no se empieza a descargar
     * para acabar chocando con un diálogo del sistema: primero se explica, con la
     * pantalla que el diseño escribió para eso.
     */
    fun instalar(app: AppConEstado) {
        if (!hayPermisoParaInstalar()) {
            _estado.update { it.copy(pidiendoPermiso = true, detalle = null) }
            return
        }

        ambito.launch {
            val resultado = instalarPaquete(app.app.aPaquete()) { avance ->
                _estado.update { it.copy(hoja = avance.aHoja(app)) }
            }
            _estado.update { it.copy(hoja = resultado.aHoja(app), detalle = null) }
            if (resultado is EstadoActualizacion.Confirmada) refrescar()
        }
    }

    private fun EstadoActualizacion.aHoja(app: AppConEstado): EstadoHoja = when (this) {
        is EstadoActualizacion.Descargando -> EstadoHoja.Descargando(
            nombre = app.nombre,
            descargados = descargados,
            total = total,
            porcentaje = porcentaje,
        )

        is EstadoActualizacion.Verificando -> EstadoHoja.Verificando(app.nombre)
        is EstadoActualizacion.Instalando -> EstadoHoja.Instalando(app.nombre)
        is EstadoActualizacion.Confirmada -> EstadoHoja.Hecho(app.nombre, app.app.versionName)
        is EstadoActualizacion.Fallo -> EstadoHoja.Fallo(app.nombre, explicacionDe(motivo))
        else -> EstadoHoja.Instalando(app.nombre)
    }

    /**
     * Del modelo de la tienda al del módulo actualizador.
     *
     * El módulo es genérico y no sabe nada de catálogos: solo necesita saber qué bajar y
     * cómo comprobar que es lo que dice ser.
     */
    private fun com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo.aPaquete() = Paquete(
        id = id,
        nombre = nombre,
        versionCode = versionCode,
        versionName = versionName,
        url = apkUrl,
        sha256 = sha256,
        tamanoBytes = tamanoBytes,
        notas = notas,
    )

    /** Los fallos se cuentan en cristiano, sin códigos ni jerga. */
    private fun explicacionDe(motivo: MotivoFallo): String = when (motivo) {
        MotivoFallo.COMPROBACION ->
            "No he podido comprobar si hay novedades. Revisa la conexión."

        MotivoFallo.DESCARGA ->
            "No he podido traerme el archivo. Revisa la conexión y prueba otra vez."

        MotivoFallo.HASH ->
            "El archivo descargado no es el que esperaba, así que lo he borrado sin " +
                "instalarlo. Vuelve a intentarlo dentro de un rato."

        MotivoFallo.INSTALACION ->
            "El móvil no ha dejado terminar la instalación. Prueba otra vez."
    }
}
