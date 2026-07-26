package com.marcmayol.actualizador.instalacion

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Dónde viven los APKs mientras se instalan: almacenamiento **privado** de la app.
 *
 * `filesDir` y no `cacheDir` a propósito: el sistema puede vaciar la caché cuando le
 * apetezca, incluso a media faena, y nos quedaríamos con una instalación apuntada cuyo
 * fichero ha desaparecido. Además, en privado ninguna otra app puede cambiar el APK
 * entre que se verifica y se instala.
 */
class AlmacenPrivado(contexto: Context) : AlmacenApks {

    private val carpeta = File(contexto.filesDir, "descargas").apply { mkdirs() }

    override fun rutaDe(id: String, versionCode: Int): String =
        File(carpeta, "$id-$versionCode.apk").absolutePath

    override fun existe(id: String, versionCode: Int) = File(rutaDe(id, versionCode)).exists()

    override fun tamanoDe(id: String, versionCode: Int) =
        File(rutaDe(id, versionCode)).let { if (it.exists()) it.length() else 0L }

    override fun borrar(id: String, versionCode: Int) {
        File(rutaDe(id, versionCode)).delete()
    }

    override fun limpiarSobrantes(vigentes: Set<Pair<String, Int>>) {
        // También el temporal: una descarga en marcha escribe en "<nombre>.apk.parcial",
        // que jamás coincidiría con un nombre vigente y se barría a media faena.
        val nombresVigentes = vigentes.flatMap { (id, versionCode) ->
            listOf("$id-$versionCode.apk", "$id-$versionCode.apk.parcial")
        }.toSet()
        carpeta.listFiles()?.forEach { fichero ->
            if (fichero.name !in nombresVigentes) fichero.delete()
        }
    }
}

/** Descarga por HTTP informando del avance. */
class DescargaHttp(
    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .build(),
) : Descargador {

    override suspend fun descargar(
        url: String,
        destino: String,
        alAvanzar: (Long, Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val fichero = File(destino)
        // Se escribe a un temporal y se renombra al final. Así nunca existe un fichero
        // con el nombre bueno que en realidad esté a medias.
        val temporal = File("$destino.parcial")
        temporal.parentFile?.mkdirs()
        temporal.delete()

        try {
            val peticion = Request.Builder().url(url).build()
            cliente.newCall(peticion).execute().use { respuesta ->
                if (!respuesta.isSuccessful) error("La descarga respondió HTTP ${respuesta.code}")
                val cuerpo = respuesta.body ?: error("La descarga vino vacía")
                val total = cuerpo.contentLength()

                var escritos = 0L
                cuerpo.byteStream().use { entrada ->
                    temporal.outputStream().use { salida ->
                        val bloque = ByteArray(64 * 1024)
                        while (true) {
                            val leidos = entrada.read(bloque)
                            if (leidos <= 0) break
                            salida.write(bloque, 0, leidos)
                            escritos += leidos
                            alAvanzar(escritos, total)
                        }
                    }
                }

                fichero.delete()
                if (!temporal.renameTo(fichero)) error("No se pudo guardar el APK descargado")
                escritos
            }
        } catch (error: Exception) {
            temporal.delete()
            fichero.delete()
            throw error
        }
    }
}

/** SHA-256 de un fichero, por trozos para no cargar 13 MB en memoria. */
class VerificadorSha256 : VerificadorDeHash {

    override suspend fun sha256De(ruta: String): String = withContext(Dispatchers.IO) {
        val resumen = MessageDigest.getInstance("SHA-256")
        File(ruta).inputStream().use { entrada ->
            val bloque = ByteArray(1024 * 1024)
            while (true) {
                val leidos = entrada.read(bloque)
                if (leidos <= 0) break
                resumen.update(bloque, 0, leidos)
            }
        }
        resumen.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Instalación por sesiones de PackageInstaller.
 *
 * `setRequireUserAction(false)` solo se pide cuando el sistema lo admite (API 31+) y
 * cuando DracApps es ya el instalador registrado de esa app: en el primer contacto
 * Android siempre pregunta, y a partir de ahí ya no.
 */
class InstaladorDeSesiones(
    private val contexto: Context,
    private val accionResultado: String = ACCION_RESULTADO,
) : Instalador {

    private val instalador: PackageInstaller
        get() = contexto.packageManager.packageInstaller

    override suspend fun crearSesion(id: String, apk: String, tamano: Long): Int =
        withContext(Dispatchers.IO) {
            val parametros = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(id)
                if (tamano > 0) setSize(tamano)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && somosSuInstalador(id)) {
                    setRequireUserAction(
                        PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    )
                }
            }

            val sesion = instalador.createSession(parametros)
            instalador.openSession(sesion).use { abierta ->
                File(apk).inputStream().use { entrada ->
                    abierta.openWrite("apk", 0, tamano.takeIf { it > 0 } ?: -1).use { salida ->
                        entrada.copyTo(salida)
                        abierta.fsync(salida)
                    }
                }
            }
            sesion
        }

    override suspend fun confirmar(sesion: Int, id: String) = withContext(Dispatchers.IO) {
        val intencion = Intent(accionResultado).setPackage(contexto.packageName)
        val pendiente = PendingIntent.getBroadcast(
            contexto,
            sesion,
            intencion,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        instalador.openSession(sesion).use { it.commit(pendiente.intentSender) }
    }

    override suspend fun sesionesAbiertas(): List<Int> = withContext(Dispatchers.IO) {
        instalador.mySessions.map { it.sessionId }
    }

    override suspend fun abandonar(sesion: Int) = withContext(Dispatchers.IO) {
        runCatching { instalador.abandonSession(sesion) }
        Unit
    }

    /**
     * Si esta app la instalamos nosotros, actualizarla no necesita confirmación.
     * En cualquier otro caso el sistema pregunta, y está bien que lo haga.
     */
    private fun somosSuInstalador(id: String): Boolean = try {
        val fuente = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contexto.packageManager.getInstallSourceInfo(id).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            contexto.packageManager.getInstallerPackageName(id)
        }
        fuente == contexto.packageName
    } catch (_: Exception) {
        false
    }

    companion object {
        const val ACCION_RESULTADO = "com.marcmayol.actualizador.RESULTADO_INSTALACION"
    }
}
