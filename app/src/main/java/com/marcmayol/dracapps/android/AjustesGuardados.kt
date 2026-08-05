package com.marcmayol.dracapps.android

import android.content.Context
import com.marcmayol.dracapps.dominio.puertos.MemoriaDeAvisos
import com.marcmayol.dracapps.ui.ajustes.Ajustes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Los ajustes, guardados en el móvil.
 *
 * `SharedPreferences` y no DataStore a propósito: son cuatro valores sueltos y hay que
 * leerlos **antes** de pintar nada, porque de ellos dependen el color y el tamaño de
 * la letra. Una lectura asíncrona haría que la app arrancara con un tema y cambiara a
 * otro a la vista de todos.
 *
 * También hace de [MemoriaDeAvisos]: de qué se avisó ya vive en el mismo sitio y por el
 * mismo motivo (tiene que seguir ahí cuando el sistema despierte la comprobación de
 * fondo con la tienda cerrada), y no merece un fichero propio.
 */
class AjustesGuardados(contexto: Context) : Ajustes, MemoriaDeAvisos {

    private val disco = contexto.getSharedPreferences(FICHERO, Context.MODE_PRIVATE)

    private val _textoGrande = MutableStateFlow(disco.getBoolean(TEXTO_GRANDE, false))
    private val _colorDelSistema = MutableStateFlow(disco.getBoolean(COLOR_DEL_SISTEMA, false))
    private val _avisar = MutableStateFlow(disco.getBoolean(AVISAR, false))
    private val _ultimaComprobacion = MutableStateFlow(
        disco.getLong(ULTIMA_COMPROBACION, 0L).takeIf { it > 0L }
    )

    override val textoGrande: StateFlow<Boolean> = _textoGrande.asStateFlow()
    override val colorDelSistema: StateFlow<Boolean> = _colorDelSistema.asStateFlow()
    override val avisarDeActualizaciones: StateFlow<Boolean> = _avisar.asStateFlow()
    override val ultimaComprobacion: StateFlow<Long?> = _ultimaComprobacion.asStateFlow()

    override fun cambiarTextoGrande(activado: Boolean) {
        _textoGrande.value = activado
        disco.edit().putBoolean(TEXTO_GRANDE, activado).apply()
    }

    override fun cambiarColorDelSistema(activado: Boolean) {
        _colorDelSistema.value = activado
        disco.edit().putBoolean(COLOR_DEL_SISTEMA, activado).apply()
    }

    override fun cambiarAvisarDeActualizaciones(activado: Boolean) {
        _avisar.value = activado
        disco.edit().putBoolean(AVISAR, activado).apply()
    }

    override fun anotarComprobacion(instante: Long) {
        _ultimaComprobacion.value = instante
        disco.edit().putLong(ULTIMA_COMPROBACION, instante).apply()
    }

    override fun avisadas(): Set<String> = disco.getStringSet(AVISADAS, emptySet()).orEmpty()

    /**
     * Se guarda **lo que hay pendiente ahora**, no lo de siempre más lo nuevo.
     *
     * Así la lista no crece sin fin con versiones que ya nadie va a instalar, y una app
     * que se actualiza sale de ella sola: si algún día vuelve a estar pendiente, será
     * con otro versionCode y con otra huella.
     */
    override fun recordar(huellas: Set<String>) {
        disco.edit().putStringSet(AVISADAS, huellas).apply()
    }

    private companion object {
        const val FICHERO = "ajustes"
        const val TEXTO_GRANDE = "texto_grande"
        const val COLOR_DEL_SISTEMA = "color_del_sistema"
        const val AVISAR = "avisar_de_actualizaciones"
        const val AVISADAS = "avisadas"
        const val ULTIMA_COMPROBACION = "ultima_comprobacion"
    }
}
