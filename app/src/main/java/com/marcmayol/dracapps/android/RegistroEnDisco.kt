package com.marcmayol.dracapps.android

import android.content.Context
import com.marcmayol.dracapps.dominio.puertos.InstalacionEnCurso
import com.marcmayol.dracapps.dominio.puertos.RegistroInstalaciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lo que hay a medio instalar, guardado en disco.
 *
 * En disco y no en memoria porque el proceso puede morir en cualquier momento, y en la
 * auto-actualización de la propia tienda muere seguro. Al arrancar, esto es lo único
 * que sabe por dónde iba cada cosa.
 *
 * Un fichero JSON en vez de una base de datos: son como mucho un puñado de filas que se
 * escriben unas cuantas veces por instalación. Room aquí solo añadiría un procesador de
 * anotaciones y tiempo de compilación a cambio de nada.
 *
 * Se escribe a un temporal y se renombra, para que un corte de luz a media escritura no
 * deje el fichero a medias: o está el de antes o está el nuevo.
 */
class RegistroEnDisco(contexto: Context) : RegistroInstalaciones {

    private val fichero = File(contexto.filesDir, "instalaciones.json")
    private val candado = Mutex()

    override suspend fun guardar(instalacion: InstalacionEnCurso) = candado.withLock {
        val filas = leer().filterNot { it.id == instalacion.id } + instalacion.aJson()
        escribir(filas)
    }

    override suspend fun borrar(id: String) = candado.withLock {
        escribir(leer().filterNot { it.id == id })
    }

    override suspend fun todas(): List<InstalacionEnCurso> = candado.withLock {
        leer().map { it.aDominio() }
    }

    override suspend fun buscar(id: String): InstalacionEnCurso? = candado.withLock {
        leer().firstOrNull { it.id == id }?.aDominio()
    }

    private fun leer(): List<Fila> = try {
        if (!fichero.exists()) {
            emptyList()
        } else {
            json.decodeFromString<List<Fila>>(fichero.readText())
        }
    } catch (_: Exception) {
        // Un registro ilegible no puede impedir que la tienda arranque: se descarta y
        // se empieza limpio. Lo peor que pasa es repetir una descarga.
        emptyList()
    }

    private suspend fun escribir(filas: List<Fila>) = withContext(Dispatchers.IO) {
        val temporal = File("${fichero.absolutePath}.tmp")
        temporal.writeText(json.encodeToString(filas))
        temporal.renameTo(fichero)
        Unit
    }

    @Serializable
    private data class Fila(
        val id: String,
        val versionCode: Int,
        val versionName: String,
        val nombre: String,
        val apkUrl: String,
        val sha256: String,
        val tamanoBytes: Long,
        val paso: String,
        val sesion: Int? = null,
    ) {
        fun aDominio() = InstalacionEnCurso(
            id = id,
            versionCode = versionCode,
            versionName = versionName,
            nombre = nombre,
            apkUrl = apkUrl,
            sha256 = sha256,
            tamanoBytes = tamanoBytes,
            paso = runCatching {
                com.marcmayol.dracapps.dominio.puertos.PasoInstalacion.valueOf(paso)
            }.getOrDefault(com.marcmayol.dracapps.dominio.puertos.PasoInstalacion.PENDIENTE),
            sesion = sesion,
        )
    }

    private fun InstalacionEnCurso.aJson() = Fila(
        id = id,
        versionCode = versionCode,
        versionName = versionName,
        nombre = nombre,
        apkUrl = apkUrl,
        sha256 = sha256,
        tamanoBytes = tamanoBytes,
        paso = paso.name,
        sesion = sesion,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
