package com.marcmayol.dracapps.dominio.casos

import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.puertos.AlmacenApks
import com.marcmayol.dracapps.dominio.puertos.Descargador
import com.marcmayol.dracapps.dominio.puertos.InstalacionEnCurso
import com.marcmayol.dracapps.dominio.puertos.Instalador
import com.marcmayol.dracapps.dominio.puertos.PasoInstalacion
import com.marcmayol.dracapps.dominio.puertos.RegistroInstalaciones
import com.marcmayol.dracapps.dominio.puertos.VerificadorDeHash

/** Lo que la UI necesita saber mientras se instala algo. */
sealed interface AvanceInstalacion {
    data class Descargando(val descargados: Long, val total: Long) : AvanceInstalacion {
        val porcentaje: Int
            get() = if (total <= 0) 0 else ((descargados * 100) / total).toInt().coerceIn(0, 100)
    }

    data object Verificando : AvanceInstalacion
    data object Instalando : AvanceInstalacion

    /** La sesión está confirmada; a partir de aquí decide el sistema. */
    data class Confirmada(val sesion: Int) : AvanceInstalacion

    data class Fallo(val motivo: MotivoFallo, val detalle: String = "") : AvanceInstalacion
}

enum class MotivoFallo {
    /** No se pudo descargar: sin red, servidor caído, descarga cortada. */
    DESCARGA,

    /**
     * El APK descargado no es el que anuncia el catálogo.
     *
     * Es el fallo más grave que puede darse aquí, y el único que no admite reintento
     * automático: o el catálogo está desfasado o alguien ha cambiado el fichero por el
     * camino. Ni se instala ni se guarda.
     */
    HASH,

    /** El sistema no dejó abrir o confirmar la sesión. */
    INSTALACION,
}

/**
 * Instala o actualiza una app del catálogo.
 *
 * El orden es sagrado: **primero se verifica el hash, después se abre la sesión**. Un
 * APK que no cuadra con lo que anuncia el catálogo se borra y no llega jamás al
 * instalador. Es la única defensa real de la familia, porque el catálogo se sirve por
 * HTTPS pero el APK viene de otro sitio.
 *
 * Cada paso se apunta en el registro antes de darlo, para que si el proceso muere se
 * sepa exactamente dónde estaba. [ReanudarInstalaciones] se encarga de recogerlo.
 */
class InstalarApp(
    private val almacen: AlmacenApks,
    private val descargador: Descargador,
    private val verificador: VerificadorDeHash,
    private val instalador: Instalador,
    private val registro: RegistroInstalaciones,
) {

    suspend operator fun invoke(
        app: AppDelCatalogo,
        alAvanzar: (AvanceInstalacion) -> Unit = {},
    ): AvanceInstalacion {
        val enCurso = InstalacionEnCurso(
            id = app.id,
            versionCode = app.versionCode,
            versionName = app.versionName,
            nombre = app.nombre,
            apkUrl = app.apkUrl,
            sha256 = app.sha256,
            tamanoBytes = app.tamanoBytes,
            paso = PasoInstalacion.PENDIENTE,
        )
        registro.guardar(enCurso)

        val destino = almacen.rutaDe(app.id, app.versionCode)

        // 1. Descarga
        registro.guardar(enCurso.copy(paso = PasoInstalacion.DESCARGANDO))
        try {
            descargador.descargar(app.apkUrl, destino) { descargados, total ->
                alAvanzar(AvanceInstalacion.Descargando(descargados, total))
            }
        } catch (error: Exception) {
            almacen.borrar(app.id, app.versionCode)
            registro.borrar(app.id)
            return AvanceInstalacion.Fallo(MotivoFallo.DESCARGA, error.message.orEmpty())
                .also(alAvanzar)
        }
        registro.guardar(enCurso.copy(paso = PasoInstalacion.DESCARGADA))

        // 2. Verificación. Antes de esto no se toca el instalador.
        alAvanzar(AvanceInstalacion.Verificando)
        val calculado = verificador.sha256De(destino)
        if (!calculado.equals(app.sha256, ignoreCase = true)) {
            almacen.borrar(app.id, app.versionCode)
            registro.borrar(app.id)
            return AvanceInstalacion.Fallo(
                MotivoFallo.HASH,
                "esperaba ${app.sha256.take(12)}… y ha salido ${calculado.take(12)}…",
            ).also(alAvanzar)
        }
        registro.guardar(enCurso.copy(paso = PasoInstalacion.VERIFICADA))

        // 3. Sesión de instalación
        alAvanzar(AvanceInstalacion.Instalando)
        return try {
            val sesion = instalador.crearSesion(app.id, destino, almacen.tamanoDe(app.id, app.versionCode))
            registro.guardar(enCurso.copy(paso = PasoInstalacion.SESION_CREADA, sesion = sesion))

            instalador.confirmar(sesion, app.id)
            registro.guardar(enCurso.copy(paso = PasoInstalacion.CONFIRMANDO, sesion = sesion))

            AvanceInstalacion.Confirmada(sesion).also(alAvanzar)
        } catch (error: Exception) {
            registro.buscar(app.id)?.sesion?.let { instalador.abandonar(it) }
            almacen.borrar(app.id, app.versionCode)
            registro.borrar(app.id)
            AvanceInstalacion.Fallo(MotivoFallo.INSTALACION, error.message.orEmpty())
                .also(alAvanzar)
        }
    }
}
