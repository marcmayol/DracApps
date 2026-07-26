package com.marcmayol.actualizador.instalacion

/**
 * Todo lo que el flujo de instalación necesita del sistema, y nada más.
 *
 * Ninguna de estas interfaces menciona Android: eso vive en los adaptadores. Así el
 * flujo se prueba con dobles, sin emulador ni red, incluidos los casos que en un móvil
 * de verdad costaría muchísimo provocar, como que el proceso muera justo con una sesión
 * de instalación abierta.
 */

/** Dónde se guardan los APKs mientras se instalan. */
interface AlmacenApks {
    fun rutaDe(id: String, versionCode: Int): String
    fun existe(id: String, versionCode: Int): Boolean
    fun tamanoDe(id: String, versionCode: Int): Long
    fun borrar(id: String, versionCode: Int)

    /** Borra descargas de versiones que ya no le interesan a nadie. */
    fun limpiarSobrantes(vigentes: Set<Pair<String, Int>>)
}

/** Cómo se trae un APK. */
interface Descargador {
    /**
     * Descarga [url] en [destino] informando del avance.
     *
     * Si algo falla, deja el destino sin fichero: nunca un APK a medias que luego
     * parezca completo.
     */
    suspend fun descargar(
        url: String,
        destino: String,
        alAvanzar: (descargados: Long, total: Long) -> Unit,
    ): Long
}

/** Quién comprueba que el APK descargado es el que anuncia el manifiesto. */
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

/** Qué versión hay instalada de un paquete, según el sistema. */
fun interface VersionInstalada {
    /** Null si no está instalado. */
    suspend fun versionCodeDe(id: String): Int?
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
 * ellos: el sistema puede matar la app durante la descarga, y cuando una app se
 * actualiza a sí misma la muerte no es un accidente, es el procedimiento.
 */
enum class PasoInstalacion {
    PENDIENTE,
    DESCARGANDO,
    DESCARGADA,
    VERIFICADA,
    SESION_CREADA,
    CONFIRMANDO,
}
