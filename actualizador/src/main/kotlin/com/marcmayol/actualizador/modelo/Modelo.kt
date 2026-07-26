package com.marcmayol.actualizador.modelo

/**
 * Una versión concreta de una app, lista para instalarse.
 *
 * Es todo lo que el módulo necesita saber: de dónde bajarla, cómo comprobar que es la
 * buena y qué versión trae. De dónde salió esa información —un `updates.json`, el
 * catálogo de una tienda, lo que sea— no es asunto suyo.
 */
data class Paquete(
    val id: String,
    val nombre: String,
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val tamanoBytes: Long = 0,
    val notas: String = "",
)

/**
 * Lo que anuncia la fuente de actualizaciones.
 *
 * `cadaHoras` la decide quien publica, no la app: así se puede espaciar o apretar la
 * comprobación sin sacar versión nueva.
 */
data class Manifiesto(
    val paquete: Paquete,
    val cadaHoras: Int = HORAS_POR_DEFECTO,
) {
    companion object {
        const val HORAS_POR_DEFECTO = 24

        /**
         * Comprobar más a menudo que esto es ruido para una app doméstica, y gasta
         * batería y datos de alguien que no lo ha pedido.
         */
        const val HORAS_MINIMO = 6
    }
}

/** Por qué se está comprobando. Decide si un fallo se cuenta o se calla. */
enum class Motivo {
    /** Al abrir la app, tras unos segundos. Silencio absoluto ante cualquier fallo. */
    AL_ABRIR,

    /** Comprobación periódica en segundo plano. Silencio absoluto. */
    PERIODICA,

    /** La ha pedido una persona. Es el ÚNICO caso que informa: de errores y de "estás al día". */
    MANUAL,
    ;

    /**
     * Solo lo que ha pedido una persona merece respuesta.
     *
     * Un fallo de red en una comprobación automática no es noticia para nadie: la app no
     * puede ponerse a dar avisos porque el wifi vaya mal.
     */
    val informa: Boolean get() = this == MANUAL
}

/** En qué punto está la comprobación. */
sealed interface EstadoActualizacion {

    data object Inactivo : EstadoActualizacion

    data object Comprobando : EstadoActualizacion

    /** Comprobado y no hay nada nuevo. */
    data class AlDia(val versionCode: Int) : EstadoActualizacion

    /** Hay versión nueva esperando. */
    data class Disponible(val manifiesto: Manifiesto) : EstadoActualizacion

    data class Descargando(val paquete: Paquete, val descargados: Long, val total: Long) :
        EstadoActualizacion {
        val porcentaje: Int
            get() = if (total <= 0) 0 else ((descargados * 100) / total).toInt().coerceIn(0, 100)
    }

    data class Verificando(val paquete: Paquete) : EstadoActualizacion

    data class Instalando(val paquete: Paquete) : EstadoActualizacion

    /** Falta el permiso de instalar desde esta fuente. */
    data class NecesitaPermiso(val paquete: Paquete) : EstadoActualizacion

    /** Sesión confirmada: a partir de aquí manda el sistema. */
    data class Confirmada(val paquete: Paquete) : EstadoActualizacion

    /**
     * Algo salió mal.
     *
     * `silencioso` marca los fallos que no se enseñan porque venían de una comprobación
     * automática. Se guardan igual, para poder mirarlos si alguien pregunta.
     */
    data class Fallo(
        val motivo: MotivoFallo,
        val detalle: String = "",
        val silencioso: Boolean = true,
    ) : EstadoActualizacion
}

enum class MotivoFallo {
    /** No se pudo leer el manifiesto: sin red, DNS, HTTP distinto de 200, JSON roto. */
    COMPROBACION,

    /** No se pudo traer el APK. */
    DESCARGA,

    /**
     * El APK no es el que anuncia el manifiesto.
     *
     * El más grave y el único sin reintento automático: o el manifiesto está desfasado o
     * alguien ha cambiado el fichero por el camino. Ni se instala ni se guarda.
     */
    HASH,

    /** El sistema no dejó abrir o confirmar la sesión. */
    INSTALACION,
}
