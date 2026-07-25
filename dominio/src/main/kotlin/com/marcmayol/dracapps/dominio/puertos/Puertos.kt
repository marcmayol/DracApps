package com.marcmayol.dracapps.dominio.puertos

import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.modelo.Catalogo

/**
 * Todo lo que el dominio necesita del mundo exterior, y nada más.
 *
 * Ninguna de estas interfaces menciona Android, HTTP ni ficheros: eso vive en los
 * adaptadores. Así los casos de uso se prueban con dobles, sin emulador ni red, que es
 * justo lo que exige el criterio de aceptación de la fase.
 */

/** De dónde sale el catálogo. La única fuente de verdad de la tienda. */
fun interface CatalogoRemoto {
    /** Descarga y parsea el catálogo. Lanza excepción si no se puede. */
    suspend fun obtener(): Catalogo
}

/** Qué hay instalado en este móvil. */
interface AppsInstaladas {
    suspend fun buscar(id: String): AppInstalada?
    suspend fun todas(ids: Collection<String>): Map<String, AppInstalada>
}

/** Dónde se guardan los APKs mientras se instalan. */
interface AlmacenApks {
    /** Ruta donde vive (o vivirá) el APK de esta versión. */
    fun rutaDe(id: String, versionCode: Int): String
    fun existe(id: String, versionCode: Int): Boolean
    fun tamanoDe(id: String, versionCode: Int): Long
    fun borrar(id: String, versionCode: Int)
    /** Borra descargas de versiones que ya no interesan a nadie. */
    fun limpiarSobrantes(vigentes: Set<Pair<String, Int>>)
}

/** Cómo se trae un APK. */
interface Descargador {
    /**
     * Descarga [url] a [destino] informando del avance.
     *
     * Devuelve los bytes escritos. Si algo falla, deja el destino sin fichero: nunca
     * un APK a medias que luego parezca completo.
     */
    suspend fun descargar(
        url: String,
        destino: String,
        alAvanzar: (descargados: Long, total: Long) -> Unit,
    ): Long
}

/** Quién comprueba que el APK descargado es el que anuncia el catálogo. */
fun interface VerificadorDeHash {
    suspend fun sha256De(ruta: String): String
}

/** Quién instala de verdad. */
interface Instalador {
    /** Abre una sesión y escribe el APK. Devuelve el identificador de sesión. */
    suspend fun crearSesion(id: String, apk: String, tamano: Long): Int

    /** Confirma la sesión. A partir de aquí manda el sistema. */
    suspend fun confirmar(sesion: Int, id: String)

    /** Sesiones abiertas por esta app. */
    suspend fun sesionesAbiertas(): List<Int>

    /** Tira una sesión que ya no interesa. */
    suspend fun abandonar(sesion: Int)
}

/** Dónde se apunta lo que hay a medio instalar, para sobrevivir a la muerte del proceso. */
interface RegistroInstalaciones {
    suspend fun guardar(instalacion: InstalacionEnCurso)
    suspend fun borrar(id: String)
    suspend fun todas(): List<InstalacionEnCurso>
    suspend fun buscar(id: String): InstalacionEnCurso?
}

/** Una instalación a medias, con el punto exacto en el que se quedó. */
data class InstalacionEnCurso(
    val id: String,
    val versionCode: Int,
    val versionName: String,
    val nombre: String,
    val apkUrl: String,
    val sha256: String,
    val tamanoBytes: Long,
    val paso: PasoInstalacion,
    val sesion: Int? = null,
)

/**
 * Los pasos por los que pasa una instalación.
 *
 * Se guardan en disco, no en memoria, porque el proceso puede morir en cualquiera de
 * ellos: el sistema puede matar la app durante la descarga, y en la auto-actualización
 * de la propia tienda la muerte es segura.
 */
enum class PasoInstalacion {
    PENDIENTE,
    DESCARGANDO,
    DESCARGADA,
    VERIFICADA,
    SESION_CREADA,
    CONFIRMANDO,
}
