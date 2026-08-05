package com.marcmayol.dracapps.ui.ajustes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lo que la persona ha decidido que se recuerde entre una vez y la siguiente.
 *
 * Es una interfaz y no la clase que escribe en el disco para que el modelo se siga
 * pudiendo probar sin Android, igual que el resto de la tienda.
 */
interface Ajustes {
    val textoGrande: StateFlow<Boolean>
    val colorDelSistema: StateFlow<Boolean>

    /**
     * Si la tienda mira sola en segundo plano y avisa cuando encuentra algo.
     *
     * Apagado de fábrica: una tienda que llega instalada en el móvil de otra persona no
     * puede empezar a mandarle notificaciones sin que las haya pedido.
     */
    val avisarDeActualizaciones: StateFlow<Boolean>

    /** Cuándo se miró el catálogo por última vez, en milisegundos. Null si nunca. */
    val ultimaComprobacion: StateFlow<Long?>

    fun cambiarTextoGrande(activado: Boolean)
    fun cambiarColorDelSistema(activado: Boolean)
    fun cambiarAvisarDeActualizaciones(activado: Boolean)
    fun anotarComprobacion(instante: Long)
}

/**
 * Los mismos ajustes, pero sin disco.
 *
 * Sirve de valor por defecto donde no hay móvil detrás: tests y vistas previas.
 */
class AjustesEnMemoria(
    textoGrande: Boolean = false,
    colorDelSistema: Boolean = false,
    avisarDeActualizaciones: Boolean = false,
    ultimaComprobacion: Long? = null,
) : Ajustes {

    private val _textoGrande = MutableStateFlow(textoGrande)
    private val _colorDelSistema = MutableStateFlow(colorDelSistema)
    private val _avisar = MutableStateFlow(avisarDeActualizaciones)
    private val _ultimaComprobacion = MutableStateFlow(ultimaComprobacion)

    override val textoGrande: StateFlow<Boolean> = _textoGrande.asStateFlow()
    override val colorDelSistema: StateFlow<Boolean> = _colorDelSistema.asStateFlow()
    override val avisarDeActualizaciones: StateFlow<Boolean> = _avisar.asStateFlow()
    override val ultimaComprobacion: StateFlow<Long?> = _ultimaComprobacion.asStateFlow()

    override fun cambiarTextoGrande(activado: Boolean) { _textoGrande.value = activado }
    override fun cambiarColorDelSistema(activado: Boolean) { _colorDelSistema.value = activado }
    override fun cambiarAvisarDeActualizaciones(activado: Boolean) { _avisar.value = activado }
    override fun anotarComprobacion(instante: Long) { _ultimaComprobacion.value = instante }
}
