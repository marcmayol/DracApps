package com.marcmayol.dracapps.dominio.casos

/**
 * Algo que tiene versión nueva esperando: una app del catálogo o la propia tienda.
 *
 * La tienda entra aquí como una más a propósito. Para quien recibe el aviso no hay
 * diferencia entre "Kuse tiene versión nueva" y "DracApps tiene versión nueva", y
 * mandar dos notificaciones por lo mismo sería el doble de molestia por el mismo aviso.
 */
data class Pendiente(
    val id: String,
    val nombre: String,
    val versionCodeNuevo: Int,
) {
    /**
     * Cómo se reconoce este aviso concreto.
     *
     * Lleva la versión dentro: si mañana sale otra versión de la misma app, la huella
     * cambia y vuelve a avisarse. Si es la misma de ayer, no.
     */
    val huella: String get() = "$id@$versionCodeNuevo"
}

/** Lo que hay que enseñar, ya redactado. */
data class Aviso(
    val titulo: String,
    val texto: String,
    /** Lo que habrá que recordar como ya avisado si esto llega a enseñarse. */
    val huellas: Set<String>,
)

/**
 * Decide si hay algo que avisar y con qué palabras.
 *
 * Vive en el dominio y no en el Worker porque es la única parte con criterio: el resto
 * es fontanería de Android. Aquí se puede probar sin móvil.
 *
 * **La regla que importa es no repetirse.** La tienda mira el catálogo cada pocas horas,
 * así que sin memoria de lo ya avisado la misma actualización pendiente saldría dos
 * veces al día hasta que alguien la instalara. Una notificación que se repite se aprende
 * a ignorar, y entonces ya no sirve para la vez que sí importa.
 */
class DecidirAviso {

    operator fun invoke(pendientes: List<Pendiente>, yaAvisado: Set<String>): Aviso? {
        if (pendientes.isEmpty()) return null

        val huellas = pendientes.map { it.huella }.toSet()

        // Si de todo lo que espera ya se avisó en su día, esto no es noticia.
        if (huellas.all { it in yaAvisado }) return null

        return Aviso(
            titulo = titulo(pendientes),
            texto = pendientes.joinToString(", ") { it.nombre },
            huellas = huellas,
        )
    }

    /**
     * Con una sola se dice cuál es; con varias, cuántas son.
     *
     * Nombrarla cuando es una sola ahorra abrir la tienda para descubrir de qué iba, y
     * es lo único que cabe en la línea del título sin cortarse.
     */
    private fun titulo(pendientes: List<Pendiente>): String = when (pendientes.size) {
        1 -> "${pendientes.first().nombre} tiene una versión nueva"
        else -> "${pendientes.size} actualizaciones esperando"
    }
}
