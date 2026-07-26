package com.marcmayol.actualizador.instalacion

import com.marcmayol.actualizador.modelo.EstadoActualizacion
import com.marcmayol.actualizador.modelo.MotivoFallo
import com.marcmayol.actualizador.modelo.Paquete

/**
 * Descarga, verifica e instala un paquete.
 *
 * El orden es sagrado: **primero se verifica el hash, después se abre la sesión**. Un
 * APK que no cuadra con lo que anuncia el manifiesto se borra y no llega jamás al
 * instalador. Es la única defensa real, porque el manifiesto viaja por HTTPS pero el
 * APK viene de otro sitio.
 *
 * Cada paso se apunta en el registro **antes** de darlo, para que si el proceso muere se
 * sepa exactamente dónde estaba. [ReanudarInstalaciones] se encarga de recogerlo.
 */
class InstalarPaquete(
    private val almacen: AlmacenApks,
    private val descargador: Descargador,
    private val verificador: VerificadorDeHash,
    private val instalador: Instalador,
    private val registro: RegistroInstalaciones,
) {

    suspend operator fun invoke(
        paquete: Paquete,
        alAvanzar: (EstadoActualizacion) -> Unit = {},
    ): EstadoActualizacion {
        val enCurso = InstalacionEnCurso(
            id = paquete.id,
            versionCode = paquete.versionCode,
            versionName = paquete.versionName,
            nombre = paquete.nombre,
            apkUrl = paquete.url,
            sha256 = paquete.sha256,
            tamanoBytes = paquete.tamanoBytes,
            paso = PasoInstalacion.PENDIENTE,
        )
        registro.guardar(enCurso)

        val destino = almacen.rutaDe(paquete.id, paquete.versionCode)

        // 1. Descarga
        registro.guardar(enCurso.copy(paso = PasoInstalacion.DESCARGANDO))
        try {
            descargador.descargar(paquete.url, destino) { descargados, total ->
                alAvanzar(EstadoActualizacion.Descargando(paquete, descargados, total))
            }
        } catch (error: Exception) {
            return abandonar(paquete, MotivoFallo.DESCARGA, error.message.orEmpty(), alAvanzar)
        }
        registro.guardar(enCurso.copy(paso = PasoInstalacion.DESCARGADA))

        // 2. Verificación. Antes de esto no se toca el instalador.
        alAvanzar(EstadoActualizacion.Verificando(paquete))
        val calculado = verificador.sha256De(destino)
        if (!calculado.equals(paquete.sha256, ignoreCase = true)) {
            return abandonar(
                paquete,
                MotivoFallo.HASH,
                "esperaba ${paquete.sha256.take(12)}… y ha salido ${calculado.take(12)}…",
                alAvanzar,
            )
        }
        registro.guardar(enCurso.copy(paso = PasoInstalacion.VERIFICADA))

        // 3. Sesión de instalación
        alAvanzar(EstadoActualizacion.Instalando(paquete))
        return try {
            val tamano = almacen.tamanoDe(paquete.id, paquete.versionCode)
            val sesion = instalador.crearSesion(paquete.id, destino, tamano)
            registro.guardar(enCurso.copy(paso = PasoInstalacion.SESION_CREADA, sesion = sesion))

            instalador.confirmar(sesion, paquete.id)
            registro.guardar(enCurso.copy(paso = PasoInstalacion.CONFIRMANDO, sesion = sesion))

            EstadoActualizacion.Confirmada(paquete).also(alAvanzar)
        } catch (error: Exception) {
            registro.buscar(paquete.id)?.sesion?.let { instalador.abandonar(it) }
            abandonar(paquete, MotivoFallo.INSTALACION, error.message.orEmpty(), alAvanzar)
        }
    }

    /**
     * Se limpia todo rastro: ni APK a medias ni fila que invite a reintentar a ciegas.
     *
     * El fallo se marca como no silencioso porque instalar siempre lo pide una persona,
     * aunque la comprobación que lo descubrió fuera automática.
     */
    private suspend fun abandonar(
        paquete: Paquete,
        motivo: MotivoFallo,
        detalle: String,
        alAvanzar: (EstadoActualizacion) -> Unit,
    ): EstadoActualizacion {
        almacen.borrar(paquete.id, paquete.versionCode)
        registro.borrar(paquete.id)
        return EstadoActualizacion.Fallo(motivo, detalle, silencioso = false).also(alAvanzar)
    }
}
