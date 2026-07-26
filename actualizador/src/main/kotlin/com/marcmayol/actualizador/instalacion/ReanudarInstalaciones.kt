package com.marcmayol.actualizador.instalacion


/** Qué se hizo con cada instalación a medias al volver a arrancar. */
data class Recogida(
    val terminadas: List<String> = emptyList(),
    val reintentables: List<InstalacionEnCurso> = emptyList(),
    val descartadas: List<String> = emptyList(),
    val sesionesHuerfanas: List<Int> = emptyList(),
)

/**
 * Pone orden al arrancar, después de que el proceso haya muerto a media instalación.
 *
 * El proceso puede morir en cualquier punto: el sistema mata la app por memoria, el
 * usuario la cierra, se apaga el móvil. Y en la auto-actualización de la propia tienda
 * la muerte no es una posibilidad, es el procedimiento. Así que nada se guarda en
 * memoria y todo se decide aquí, mirando qué quedó apuntado y qué hay de verdad en el
 * móvil.
 *
 * Regla de fondo: **en lo automático, ante la duda, se descarta y se limpia**. Una
 * descarga perdida se puede repetir; un APK a medias que se cuele en el instalador, no
 * se puede deshacer.
 */
class ReanudarInstalaciones(
    private val registro: RegistroInstalaciones,
    private val almacen: AlmacenApks,
    private val instalador: Instalador,
    private val verificador: VerificadorDeHash,
    private val instaladas: VersionInstalada,
) {

    suspend operator fun invoke(): Recogida {
        val pendientes = registro.todas()
        val terminadas = mutableListOf<String>()
        val reintentables = mutableListOf<InstalacionEnCurso>()
        val descartadas = mutableListOf<String>()

        for (pendiente in pendientes) {
            when (resolver(pendiente)) {
                Desenlace.TERMINADA -> {
                    terminadas += pendiente.id
                    limpiar(pendiente)
                }

                Desenlace.REINTENTAR -> {
                    reintentables += pendiente
                    limpiar(pendiente)
                }

                Desenlace.DESCARTAR -> {
                    descartadas += pendiente.id
                    limpiar(pendiente)
                }
            }
        }

        val huerfanas = abandonarSesionesHuerfanas(pendientes)
        almacen.limpiarSobrantes(pendientes.map { it.id to it.versionCode }.toSet())

        return Recogida(terminadas, reintentables, descartadas, huerfanas)
    }

    private enum class Desenlace { TERMINADA, REINTENTAR, DESCARTAR }

    private suspend fun resolver(pendiente: InstalacionEnCurso): Desenlace {
        // Lo primero, siempre: preguntarle al móvil. Si la app ya está en la versión
        // esperada, la instalación salió bien aunque nadie llegara a enterarse.
        val enElMovil = instaladas.versionCodeDe(pendiente.id)
        if (enElMovil != null && enElMovil >= pendiente.versionCode) {
            return Desenlace.TERMINADA
        }

        return when (pendiente.paso) {
            // Murió antes de tener nada útil: se vuelve a empezar.
            PasoInstalacion.PENDIENTE,
            PasoInstalacion.DESCARGANDO,
            -> Desenlace.REINTENTAR

            // Hay un fichero, pero nadie ha comprobado que sea el bueno. No se hereda
            // la duda: se verifica ahora o se tira.
            PasoInstalacion.DESCARGADA,
            PasoInstalacion.VERIFICADA,
            -> if (apkSigueSiendoValido(pendiente)) Desenlace.REINTENTAR else Desenlace.DESCARTAR

            // Había sesión abierta y no se sabe si llegó a confirmarse. Como el móvil
            // ya ha dicho que no tiene la versión nueva, no llegó: se tira la sesión y
            // se empieza de cero, que es más barato que adivinar.
            PasoInstalacion.SESION_CREADA,
            PasoInstalacion.CONFIRMANDO,
            -> Desenlace.DESCARTAR
        }
    }

    private suspend fun apkSigueSiendoValido(pendiente: InstalacionEnCurso): Boolean {
        if (!almacen.existe(pendiente.id, pendiente.versionCode)) return false
        val ruta = almacen.rutaDe(pendiente.id, pendiente.versionCode)
        return runCatching { verificador.sha256De(ruta) }
            .getOrNull()
            ?.equals(pendiente.sha256, ignoreCase = true) == true
    }

    /**
     * Toda sesión abierta por esta app que no corresponda a nada apuntado se abandona.
     *
     * Si no, se acumulan sesiones muertas que consumen espacio y que algún día chocan
     * con el límite de sesiones simultáneas del sistema.
     */
    private suspend fun abandonarSesionesHuerfanas(
        pendientes: List<InstalacionEnCurso>,
    ): List<Int> {
        val conocidas = pendientes.mapNotNull { it.sesion }.toSet()
        val huerfanas = instalador.sesionesAbiertas().filterNot { it in conocidas }
        huerfanas.forEach { instalador.abandonar(it) }
        return huerfanas
    }

    private suspend fun limpiar(pendiente: InstalacionEnCurso) {
        pendiente.sesion?.let { instalador.abandonar(it) }
        almacen.borrar(pendiente.id, pendiente.versionCode)
        registro.borrar(pendiente.id)
    }
}
