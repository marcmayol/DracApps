package com.marcmayol.dracapps.dominio.modelo

/**
 * Una app tal como la anuncia el catálogo publicado.
 *
 * Es exactamente lo que produce el generador de la Fase 1. El cliente no sabe de dónde
 * sale ni consulta jamás la API de GitHub: solo lee este catálogo.
 */
data class AppDelCatalogo(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val iconoUrl: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val firmaSha256: String,
    val tamanoBytes: Long,
    val notas: String,
    val canal: String? = null,
    val minSdk: Int? = null,
)

/** El catálogo entero. */
data class Catalogo(
    val titulo: String,
    val generado: String,
    val apps: List<AppDelCatalogo>,
)

/** Lo que el móvil dice de una app ya instalada. */
data class AppInstalada(
    val id: String,
    val versionCode: Int,
    val versionName: String,
    val firmaSha256: String,
    /** Paquete que la instaló, si Android lo sabe. */
    val instaladaPor: String?,
)

/**
 * Los cuatro estados, que son el lenguaje visual de la tienda.
 *
 * La UI deriva de aquí el chip, el botón y la insignia; nunca lo decide la vista.
 * Es la regla del diseño: "un vistazo tiene que bastar", y para eso el estado tiene
 * que calcularse en un solo sitio.
 */
sealed interface EstadoApp {

    /** Está en la tienda, no en el móvil. */
    data object NoInstalada : EstadoApp

    /** Todo en orden. El estado más silencioso. */
    data class InstaladaAlDia(val versionCode: Int, val versionName: String) : EstadoApp

    /** El único estado que pide algo. */
    data class Actualizable(
        val versionInstalada: String,
        val versionNueva: String,
        val versionCodeInstalado: Int,
        val versionCodeNuevo: Int,
    ) : EstadoApp

    /**
     * Instalada por fuera de la tienda.
     *
     * No es un error ni algo que esté mal: es algo que DracApps no controla, y por eso
     * solo informa. Nunca ofrece actualizarla, porque instalar encima con otra firma
     * fallaría, y con la misma firma pero otro origen no le corresponde decidirlo.
     */
    data class NoGestionada(
        val versionInstalada: String,
        val motivo: MotivoNoGestionada,
    ) : EstadoApp
}

/** Por qué una app instalada no está bajo el control de la tienda. */
enum class MotivoNoGestionada {
    /** Firmada por otra persona: es otra app aunque se llame igual. */
    OTRA_FIRMA,

    /** Misma firma, pero la instaló otro. */
    OTRO_ORIGEN,
}

/** Una app del catálogo junto al estado en que está en este móvil. */
data class AppConEstado(
    val app: AppDelCatalogo,
    val estado: EstadoApp,
) {
    val id: String get() = app.id
    val nombre: String get() = app.nombre

    /** Solo las actualizables cuentan para "N actualizaciones disponibles". */
    val tieneActualizacion: Boolean get() = estado is EstadoApp.Actualizable
}
