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
import com.marcmayol.dracapps.ui.ajustes.Ajustes
import com.marcmayol.dracapps.ui.ajustes.AjustesEnMemoria
import com.marcmayol.dracapps.ui.ajustes.EstadoAjustes
import com.marcmayol.dracapps.ui.catalogo.EstadoPantallaCatalogo
import com.marcmayol.dracapps.ui.instalacion.EstadoHoja
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Todo lo que la interfaz necesita saber en un momento dado. */
data class EstadoTienda(
    val seccion: Seccion = Seccion.APPS,
    val catalogo: EstadoPantallaCatalogo = EstadoPantallaCatalogo.Cargando,
    val detalle: AppConEstado? = null,
    val hoja: EstadoHoja? = null,
    val pidiendoPermiso: Boolean = false,
    /**
     * De qué app va la hoja abierta.
     *
     * Hace falta porque al terminar se cierra el detalle, y sin esto el «Abrir» de
     * «Ya está» no sabía a quién abrir y se limitaba a cerrar la hoja.
     */
    val appDeLaHoja: AppConEstado? = null,
    val ajustes: EstadoAjustes = EstadoAjustes(),
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
    private val ajustesGuardados: Ajustes = AjustesEnMemoria(),
    private val reloj: () -> Long = System::currentTimeMillis,
    private val alcance: CoroutineScope? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoTienda())
    val estado: StateFlow<EstadoTienda> = _estado.asStateFlow()

    private val ambito: CoroutineScope get() = alcance ?: viewModelScope

    init {
        // Lo que se guardó la última vez entra en el estado en cuanto cambia, sin que
        // ninguna pantalla tenga que ir a buscarlo al disco.
        ambito.launch {
            combine(
                ajustesGuardados.textoGrande,
                ajustesGuardados.colorDelSistema,
                ajustesGuardados.avisarDeActualizaciones,
                ajustesGuardados.ultimaComprobacion,
            ) { textoGrande, colorDelSistema, avisar, ultima ->
                EstadoAjustes(
                    textoGrande = textoGrande,
                    colorDelSistema = colorDelSistema,
                    avisarDeActualizaciones = avisar,
                    ultimaComprobacion = ultima,
                )
            }.collect { guardado ->
                _estado.update {
                    it.copy(
                        ajustes = it.ajustes.copy(
                            textoGrande = guardado.textoGrande,
                            colorDelSistema = guardado.colorDelSistema,
                            avisarDeActualizaciones = guardado.avisarDeActualizaciones,
                            ultimaComprobacion = guardado.ultimaComprobacion,
                        )
                    )
                }
            }
        }
    }

    fun cambiarTextoGrande(activado: Boolean) = ajustesGuardados.cambiarTextoGrande(activado)

    fun cambiarColorDelSistema(activado: Boolean) =
        ajustesGuardados.cambiarColorDelSistema(activado)

    /**
     * Guardar el ajuste es lo único que hace el modelo.
     *
     * Programar o cancelar la comprobación de fondo es cosa de quien sí conoce Android:
     * el ViewModel no sabe qué es WorkManager y no va a empezar a saberlo por esto.
     */
    fun cambiarAvisos(activado: Boolean) =
        ajustesGuardados.cambiarAvisarDeActualizaciones(activado)

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
            _estado.update {
                it.copy(
                    catalogo = EstadoPantallaCatalogo.Cargando,
                    ajustes = it.ajustes.copy(comprobando = true),
                )
            }
            val resultado = obtenerCatalogo()
            val apps = (resultado as? ResultadoCatalogo.Listo)?.apps.orEmpty()

            // Solo cuenta como comprobación la que ha llegado a ver el catálogo: si no
            // hubo red, decir «comprobado hace un momento» sería mentira.
            if (resultado is ResultadoCatalogo.Listo) ajustesGuardados.anotarComprobacion(reloj())

            _estado.update { actual ->
                actual.copy(
                    // Si hay una ficha abierta, se queda con lo recién comprobado: es
                    // desde donde se pidió mirar, y ahí tiene que verse el resultado.
                    detalle = actual.detalle?.let { abierta ->
                        apps.firstOrNull { it.id == abierta.id } ?: abierta
                    },
                    catalogo = when (resultado) {
                        is ResultadoCatalogo.Listo ->
                            if (resultado.vacio) {
                                EstadoPantallaCatalogo.Vacio
                            } else {
                                EstadoPantallaCatalogo.Listo(resultado.apps)
                            }

                        is ResultadoCatalogo.SinCatalogo ->
                            EstadoPantallaCatalogo.SinConexion(resultado.instaladas)
                    },
                    ajustes = actual.ajustes.copy(
                        comprobando = false,
                        hayPermisoParaInstalar = hayPermisoParaInstalar(),
                        actualizacionesPendientes = apps.count { it.tieneActualizacion },
                        appsEnElCatalogo = apps.size,
                    ),
                )
            }
        }
    }

    /**
     * Cambiar de pestaña.
     *
     * Al entrar en Ajustes se vuelve a preguntar por el permiso de instalación: es lo
     * que se acaba de ir a conceder a los ajustes de Android, y volver y encontrarlo sin
     * enterarse sería desconcertante.
     */
    fun irA(seccion: Seccion) = _estado.update {
        it.copy(
            seccion = seccion,
            detalle = null,
            ajustes = if (seccion == Seccion.AJUSTES) {
                it.ajustes.copy(hayPermisoParaInstalar = hayPermisoParaInstalar())
            } else {
                it.ajustes
            },
        )
    }

    fun abrirDetalle(app: AppConEstado) = _estado.update { it.copy(detalle = app) }

    fun cerrarDetalle() = _estado.update { it.copy(detalle = null) }

    fun cerrarHoja() = _estado.update { it.copy(hoja = null, appDeLaHoja = null) }

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
            if (instalarUna(app) is EstadoActualizacion.Confirmada) refrescar()
        }
    }

    /**
     * Actualizar de una tacada todo lo que tenga versión nueva.
     *
     * Van una detrás de otra, no a la vez: cada instalación pide su confirmación al
     * sistema, y lanzarlas en paralelo llenaría la pantalla de diálogos encimados.
     *
     * La tienda va la última, y por eso se pasa como `alTerminar` en vez de meterla en
     * la cola: al instalarse se cierra a sí misma, así que si fuera antes se llevaría
     * por delante todo lo que quedara pendiente.
     */
    fun actualizarTodo(alTerminar: () -> Unit = {}) {
        if (!hayPermisoParaInstalar()) {
            _estado.update { it.copy(pidiendoPermiso = true, detalle = null) }
            return
        }

        val pendientes = appsDelCatalogo().filter { it.tieneActualizacion }

        ambito.launch {
            val hechas = pendientes.count { instalarUna(it) is EstadoActualizacion.Confirmada }
            if (hechas > 0) refrescar()
            alTerminar()
        }
    }

    private suspend fun instalarUna(app: AppConEstado): EstadoActualizacion {
        val resultado = instalarPaquete(app.app.aPaquete()) { avance ->
            _estado.update { it.copy(hoja = avance.aHoja(app), appDeLaHoja = app) }
        }
        _estado.update { it.copy(hoja = resultado.aHoja(app), appDeLaHoja = app, detalle = null) }
        return resultado
    }

    private fun appsDelCatalogo(): List<AppConEstado> =
        (_estado.value.catalogo as? EstadoPantallaCatalogo.Listo)?.apps.orEmpty()

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
