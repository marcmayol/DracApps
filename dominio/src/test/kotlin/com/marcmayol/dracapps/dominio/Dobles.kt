package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.modelo.Catalogo
import com.marcmayol.dracapps.dominio.puertos.AppsInstaladas
import com.marcmayol.dracapps.dominio.puertos.CatalogoRemoto

/**
 * Dobles de todo lo que el dominio necesita del mundo.
 *
 * Ni red, ni SDK de Android, ni emulador: los tests corren en la JVM en menos de un
 * segundo, y por eso pueden cubrir cosas que contra un móvil real serían imposibles de
 * provocar a voluntad, como que el proceso muera justo con una sesión abierta.
 */

const val PAQUETE_TIENDA = "com.marcmayol.dracapps"
val HASH_BUENO = "aa".repeat(32)
const val FIRMA_MARC = "0e4410d009fa18e09f0b92197693305d4725d01aeaa437b1cc690b8f633e523c"
const val FIRMA_AJENA = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

fun appDelCatalogo(
    id: String = "com.ejemplo.app",
    nombre: String = "App de ejemplo",
    versionCode: Int = 2,
    versionName: String = "1.1",
    sha256: String = HASH_BUENO,
    firmaSha256: String = FIRMA_MARC,
    tamanoBytes: Long = 1_000_000,
) = AppDelCatalogo(
    id = id,
    nombre = nombre,
    descripcion = "Descripción de $nombre",
    iconoUrl = "",
    versionCode = versionCode,
    versionName = versionName,
    apkUrl = "https://github.com/marcmayol/$id/releases/download/v$versionName/app.apk",
    sha256 = sha256,
    firmaSha256 = firmaSha256,
    tamanoBytes = tamanoBytes,
    notas = "Novedades de $versionName",
)

fun appInstalada(
    id: String = "com.ejemplo.app",
    versionCode: Int = 1,
    versionName: String = "1.0",
    firmaSha256: String = FIRMA_MARC,
    instaladaPor: String? = PAQUETE_TIENDA,
) = AppInstalada(id, versionCode, versionName, firmaSha256, instaladaPor)

class CatalogoFalso(
    private val catalogo: Catalogo? = null,
    private val error: Exception? = null,
) : CatalogoRemoto {
    var vecesPedido = 0
        private set

    override suspend fun obtener(): Catalogo {
        vecesPedido++
        error?.let { throw it }
        return catalogo!!
    }
}

class AppsInstaladasFalsas(
    private val apps: MutableMap<String, AppInstalada> = mutableMapOf(),
) : AppsInstaladas {
    override suspend fun buscar(id: String) = apps[id]

    override suspend fun todas(ids: Collection<String>) =
        apps.filterKeys { it in ids }

    fun poner(app: AppInstalada) {
        apps[app.id] = app
    }

    fun quitar(id: String) {
        apps.remove(id)
    }
}
