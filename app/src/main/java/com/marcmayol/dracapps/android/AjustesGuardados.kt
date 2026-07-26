package com.marcmayol.dracapps.android

import android.content.Context
import com.marcmayol.dracapps.ui.ajustes.Ajustes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Los ajustes, guardados en el móvil.
 *
 * `SharedPreferences` y no DataStore a propósito: son tres valores sueltos y hay que
 * leerlos **antes** de pintar nada, porque de ellos dependen el color y el tamaño de
 * la letra. Una lectura asíncrona haría que la app arrancara con un tema y cambiara a
 * otro a la vista de todos.
 */
class AjustesGuardados(contexto: Context) : Ajustes {

    private val disco = contexto.getSharedPreferences(FICHERO, Context.MODE_PRIVATE)

    private val _textoGrande = MutableStateFlow(disco.getBoolean(TEXTO_GRANDE, false))
    private val _colorDelSistema = MutableStateFlow(disco.getBoolean(COLOR_DEL_SISTEMA, false))
    private val _ultimaComprobacion = MutableStateFlow(
        disco.getLong(ULTIMA_COMPROBACION, 0L).takeIf { it > 0L }
    )

    override val textoGrande: StateFlow<Boolean> = _textoGrande.asStateFlow()
    override val colorDelSistema: StateFlow<Boolean> = _colorDelSistema.asStateFlow()
    override val ultimaComprobacion: StateFlow<Long?> = _ultimaComprobacion.asStateFlow()

    override fun cambiarTextoGrande(activado: Boolean) {
        _textoGrande.value = activado
        disco.edit().putBoolean(TEXTO_GRANDE, activado).apply()
    }

    override fun cambiarColorDelSistema(activado: Boolean) {
        _colorDelSistema.value = activado
        disco.edit().putBoolean(COLOR_DEL_SISTEMA, activado).apply()
    }

    override fun anotarComprobacion(instante: Long) {
        _ultimaComprobacion.value = instante
        disco.edit().putLong(ULTIMA_COMPROBACION, instante).apply()
    }

    private companion object {
        const val FICHERO = "ajustes"
        const val TEXTO_GRANDE = "texto_grande"
        const val COLOR_DEL_SISTEMA = "color_del_sistema"
        const val ULTIMA_COMPROBACION = "ultima_comprobacion"
    }
}
