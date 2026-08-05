package com.marcmayol.dracapps.dominio.casos

import com.marcmayol.dracapps.dominio.puertos.MemoriaDeAvisos

/**
 * Mirar si hay novedades cuando no hay nadie delante, y avisar solo si toca.
 *
 * Es lo que ejecuta la comprobación de fondo cada pocas horas. Vive en el dominio
 * porque todo lo que decide —qué cuenta como pendiente, si eso ya se avisó, qué se
 * escribe— se puede probar sin móvil; el Worker de Android que lo despierta es una
 * cáscara de diez líneas.
 *
 * Como es automático, aquí **nada informa de errores**: si no hay red o el catálogo no
 * se puede leer, esta ronda simplemente no avisa de nada y la siguiente lo intentará.
 * La regla de la casa es que solo lo que se pide a mano contesta con un fallo.
 */
class VigilarActualizaciones(
    private val obtenerCatalogo: ObtenerCatalogo,
    private val memoria: MemoriaDeAvisos,
    /** La propia tienda, que no sale del catálogo sino de su manifiesto. */
    private val laTienda: suspend () -> Pendiente?,
    /** Enseña el aviso y dice si llegó a verse. */
    private val avisar: (Aviso) -> Boolean,
    private val decidir: DecidirAviso = DecidirAviso(),
) {

    suspend operator fun invoke() {
        val pendientes = buildList {
            // La tienda primero: es la que hay que actualizar antes que nada, porque al
            // hacerlo se cierra y cortaría cualquier otra instalación por la mitad.
            runCatching { laTienda() }.getOrNull()?.let(::add)

            val resultado = obtenerCatalogo()
            if (resultado is ResultadoCatalogo.Listo) {
                resultado.apps
                    .filter { it.tieneActualizacion }
                    .forEach { add(Pendiente(it.id, it.nombre, it.app.versionCode)) }
            }
        }

        val aviso = decidir(pendientes, memoria.avisadas()) ?: return

        // Solo se da por avisado lo que de verdad se enseñó. Si el permiso de
        // notificaciones estaba denegado, esto no se apunta y el aviso saldrá en la
        // primera ronda en que se pueda: darlo por dicho lo perdería para siempre.
        if (avisar(aviso)) memoria.recordar(aviso.huellas)
    }
}
