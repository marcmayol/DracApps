package com.marcmayol.actualizador.instalacion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Recoge lo que el sistema responde al confirmar una sesión.
 *
 * `commit()` no instala nada por sí solo: solo entrega la sesión y contesta por este
 * `PendingIntent`. Sin alguien escuchando, el caso importante se pierde en silencio:
 * cuando la app no es el instalador registrado del paquete, Android **pide
 * confirmación** y espera a que alguien abra su pantalla. Nadie la abría, así que la
 * sesión se quedaba a medias y acababa abandonada mientras la tienda daba la
 * actualización por hecha.
 *
 * El broadcast es explícito (el `PendingIntent` apunta a esta clase), así que no le
 * afectan las restricciones de broadcasts implícitos.
 */
class ResultadoInstalacionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmacion = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                // Viene de fuera de una Activity: sin esta bandera el sistema la rechaza.
                confirmacion?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmacion != null) context.startActivity(confirmacion)
            }

            PackageInstaller.STATUS_SUCCESS ->
                EventosInstalacion.emitir(ResultadoInstalacion(exito = true))

            else -> EventosInstalacion.emitir(
                ResultadoInstalacion(
                    exito = false,
                    codigo = estado,
                    mensaje = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                )
            )
        }
    }
}

/** Lo que el sistema acabó haciendo con una sesión ya confirmada. */
data class ResultadoInstalacion(
    val exito: Boolean,
    val codigo: Int = PackageInstaller.STATUS_SUCCESS,
    val mensaje: String? = null,
)

/** Puente entre el receptor, que vive fuera de la UI, y quien quiera enterarse. */
object EventosInstalacion {

    @Volatile
    var alResolverse: ((ResultadoInstalacion) -> Unit)? = null

    fun emitir(resultado: ResultadoInstalacion) {
        alResolverse?.invoke(resultado)
    }
}
