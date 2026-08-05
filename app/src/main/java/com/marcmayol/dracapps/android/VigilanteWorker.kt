package com.marcmayol.dracapps.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marcmayol.dracapps.DracAppsApp
import java.util.concurrent.TimeUnit

/**
 * Lo que despierta a la tienda cuando nadie la está mirando.
 *
 * Aquí no hay criterio ninguno: eso vive en `VigilarActualizaciones`, en el dominio, que
 * se prueba sin móvil. Este fichero solo se ocupa de que Android lo llame cada tantas
 * horas y de no quejarse nunca.
 *
 * Devuelve siempre `success`, incluso cuando algo falla: un `retry` encadenaría intentos
 * en segundo plano por una red que no va, y esto no tiene ninguna prisa. Si esta ronda
 * no pudo mirar, mira la siguiente.
 */
class VigilanteWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DracAppsApp ?: return Result.success()

        // El trabajo se cancela al apagar el ajuste, pero se vuelve a mirar aquí: puede
        // quedar una ronda ya encolada por el sistema cuando se apaga.
        if (!app.piezas.ajustes.avisarDeActualizaciones.value) return Result.success()

        runCatching { app.vigilarActualizaciones() }
        return Result.success()
    }

    companion object {
        private const val NOMBRE = "dracapps_vigilante_actualizaciones"

        /** Cada doce horas y solo con red. Dos rondas al día bastan y no gastan batería. */
        const val CADA_HORAS = 12L

        fun programar(context: Context) {
            val peticion = PeriodicWorkRequestBuilder<VigilanteWorker>(
                CADA_HORAS, TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).build()

            // KEEP y no UPDATE: si ya estaba programado, reprogramarlo cada vez que se
            // abre la tienda reiniciaría la cuenta atrás y la ronda no llegaría nunca en
            // un móvil que se usa a diario.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NOMBRE,
                ExistingPeriodicWorkPolicy.KEEP,
                peticion,
            )
        }

        fun cancelar(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOMBRE)
        }
    }
}
