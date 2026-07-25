package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.modelo.Catalogo
import com.marcmayol.dracapps.dominio.puertos.AlmacenApks
import com.marcmayol.dracapps.dominio.puertos.AppsInstaladas
import com.marcmayol.dracapps.dominio.puertos.CatalogoRemoto
import com.marcmayol.dracapps.dominio.puertos.Descargador
import com.marcmayol.dracapps.dominio.puertos.InstalacionEnCurso
import com.marcmayol.dracapps.dominio.puertos.Instalador
import com.marcmayol.dracapps.dominio.puertos.RegistroInstalaciones
import com.marcmayol.dracapps.dominio.puertos.VerificadorDeHash

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

class AlmacenFalso : AlmacenApks {
    private val ficheros = mutableMapOf<String, Long>()
    val borrados = mutableListOf<String>()
    var limpiezas = 0
        private set

    override fun rutaDe(id: String, versionCode: Int) = "/privado/$id-$versionCode.apk"

    override fun existe(id: String, versionCode: Int) = rutaDe(id, versionCode) in ficheros

    override fun tamanoDe(id: String, versionCode: Int) = ficheros[rutaDe(id, versionCode)] ?: 0

    override fun borrar(id: String, versionCode: Int) {
        val ruta = rutaDe(id, versionCode)
        if (ficheros.remove(ruta) != null || true) borrados += ruta
    }

    override fun limpiarSobrantes(vigentes: Set<Pair<String, Int>>) {
        limpiezas++
        val rutasVigentes = vigentes.map { rutaDe(it.first, it.second) }.toSet()
        ficheros.keys.retainAll(rutasVigentes)
    }

    fun escribir(ruta: String, tamano: Long = 1_000_000) {
        ficheros[ruta] = tamano
    }

    fun hay(ruta: String) = ruta in ficheros
}

class DescargadorFalso(
    private val almacen: AlmacenFalso,
    private val error: Exception? = null,
    private val tamano: Long = 1_000_000,
) : Descargador {
    val descargadas = mutableListOf<String>()

    override suspend fun descargar(
        url: String,
        destino: String,
        alAvanzar: (Long, Long) -> Unit,
    ): Long {
        error?.let { throw it }
        descargadas += url
        alAvanzar(tamano / 2, tamano)
        alAvanzar(tamano, tamano)
        almacen.escribir(destino, tamano)
        return tamano
    }
}

class VerificadorFalso(
    private val porDefecto: String = HASH_BUENO,
    private val porRuta: Map<String, String> = emptyMap(),
) : VerificadorDeHash {
    val comprobadas = mutableListOf<String>()

    override suspend fun sha256De(ruta: String): String {
        comprobadas += ruta
        return porRuta[ruta] ?: porDefecto
    }
}

class InstaladorFalso(
    private val fallaAlCrear: Boolean = false,
    private val fallaAlConfirmar: Boolean = false,
    abiertas: List<Int> = emptyList(),
) : Instalador {
    private var siguienteSesion = 100
    private val sesiones = abiertas.toMutableList()

    val creadas = mutableListOf<String>()
    val confirmadas = mutableListOf<Int>()
    val abandonadas = mutableListOf<Int>()

    override suspend fun crearSesion(id: String, apk: String, tamano: Long): Int {
        if (fallaAlCrear) error("el sistema no deja abrir sesión")
        creadas += id
        val sesion = siguienteSesion++
        sesiones += sesion
        return sesion
    }

    override suspend fun confirmar(sesion: Int, id: String) {
        if (fallaAlConfirmar) error("el sistema rechazó la sesión")
        confirmadas += sesion
    }

    override suspend fun sesionesAbiertas() = sesiones.toList()

    override suspend fun abandonar(sesion: Int) {
        abandonadas += sesion
        sesiones.remove(sesion)
    }
}

class RegistroFalso(iniciales: List<InstalacionEnCurso> = emptyList()) : RegistroInstalaciones {
    private val filas = iniciales.associateBy { it.id }.toMutableMap()

    /** Todo lo que se ha ido guardando, en orden: sirve para ver por dónde pasó. */
    val historial = mutableListOf<InstalacionEnCurso>()

    override suspend fun guardar(instalacion: InstalacionEnCurso) {
        filas[instalacion.id] = instalacion
        historial += instalacion
    }

    override suspend fun borrar(id: String) {
        filas.remove(id)
    }

    override suspend fun todas() = filas.values.toList()

    override suspend fun buscar(id: String) = filas[id]

    val pasosPorLosQuePaso: List<String>
        get() = historial.map { it.paso.name }
}
