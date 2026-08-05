package com.marcmayol.dracapps.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marcmayol.dracapps.MainActivity
import com.marcmayol.dracapps.R
import com.marcmayol.dracapps.dominio.casos.Aviso
import com.marcmayol.dracapps.ui.tema.EsquemaOscuro

/**
 * El único sitio desde el que la tienda interrumpe a nadie.
 *
 * Una sola notificación, siempre la misma: si en la siguiente ronda hay algo más, se
 * sustituye en vez de apilarse. Nadie quiere encontrarse cinco avisos de la tienda en la
 * pantalla de bloqueo.
 *
 * Tocarla abre Novedades, que es la pantalla donde eso se resuelve. Llevar a la lista
 * general obligaría a buscar a mano lo que el aviso ya sabía.
 */
class Notificador(private val contexto: Context) {

    /**
     * Enseña el aviso. Devuelve si de verdad se enseñó.
     *
     * Puede no enseñarse porque el permiso de notificaciones esté denegado (en Android
     * 13+ es un permiso aparte, y se puede revocar desde los ajustes del sistema en
     * cualquier momento). En ese caso no se da por avisado: si algún día se concede, el
     * aviso pendiente saldrá en la ronda siguiente.
     */
    fun avisar(aviso: Aviso): Boolean {
        if (!sePuedeNotificar()) return false

        crearCanal()

        val abrirNovedades = Intent(contexto, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_IR_A_NOVEDADES, true)

        val pendiente = PendingIntent.getActivity(
            contexto,
            0,
            abrirNovedades,
            // IMMUTABLE es obligatorio desde Android 12 y aquí no estorba: no hay nada
            // que un tercero deba poder rellenar en esta intención.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(aviso.titulo)
            .setContentText(aviso.texto)
            // Con varias apps la lista no cabe en una línea; desplegada sí se lee entera.
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.texto))
            .setColor(EsquemaOscuro.primary.toArgb())
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(contexto).notify(ID, notificacion)
            true
        } catch (e: SecurityException) {
            // El permiso se puede quitar entre la comprobación de arriba y esta línea.
            false
        }
    }

    private fun sePuedeNotificar(): Boolean {
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * El canal se crea al vuelo y no al arrancar la app: quien no active los avisos no
     * tiene por qué encontrarse un canal vacío en los ajustes de Android.
     */
    private fun crearCanal() {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            CANAL,
            "Actualizaciones",
            // DEFAULT y no HIGH: suena una vez, pero no se planta encima de lo que
            // estés haciendo. Una actualización nunca es urgente.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisa cuando tus apps o la propia tienda tienen versión nueva."
        }
        gestor.createNotificationChannel(canal)
    }

    private companion object {
        const val CANAL = "actualizaciones"

        /** Fijo a propósito: el aviso nuevo sustituye al anterior. */
        const val ID = 1
    }
}
