package com.marcmayol.dracapps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.marcm.actualizador.Actualizador
import com.marcm.actualizador.ActualizadorConfig
import com.marcm.actualizador.EstadoActualizacion
import com.marcm.actualizador.Modo
import com.marcmayol.actualizador.instalacion.AlmacenPrivado
import com.marcmayol.actualizador.instalacion.DescargaHttp
import com.marcmayol.actualizador.instalacion.InstalarPaquete
import com.marcmayol.actualizador.instalacion.InstaladorDeSesiones
import com.marcmayol.actualizador.instalacion.ReanudarInstalaciones
import com.marcmayol.actualizador.instalacion.RegistroEnDisco
import com.marcmayol.actualizador.instalacion.VerificadorSha256
import com.marcmayol.actualizador.instalacion.VersionInstalada
import com.marcmayol.dracapps.android.AjustesGuardados
import com.marcmayol.dracapps.android.AppsDelMovil
import com.marcmayol.dracapps.android.CatalogoHttp
import com.marcmayol.dracapps.android.Notificador
import com.marcmayol.dracapps.dominio.casos.ObtenerCatalogo
import com.marcmayol.dracapps.dominio.casos.Pendiente
import com.marcmayol.dracapps.dominio.casos.VigilarActualizaciones

/**
 * Aquí se enchufan los adaptadores a los casos de uso, y en ningún otro sitio.
 *
 * Montado a mano y no con un inyector de dependencias: son siete piezas que se juntan
 * una sola vez. Un framework de inyección aquí añadiría configuración y tiempo de
 * compilación a cambio de ahorrar veinte líneas que se leen de un vistazo.
 */
class DracAppsApp : Application() {

    val piezas by lazy { Piezas(this) }

    /**
     * La tienda se actualiza a sí misma con el mismo módulo que llevan las demás apps
     * de la casa. No usa su propio catálogo a propósito: si una versión rota impidiera
     * leerlo, la tienda se quedaría sin forma de repararse.
     */
    val autoactualizador: Actualizador by lazy {
        Actualizador(
            app = this,
            config = ActualizadorConfig(
                manifiestoUrl = BuildConfig.URL_ACTUALIZACIONES,
                versionCodeActual = BuildConfig.VERSION_CODE,
                checkHorasPorDefecto = 24,
            ),
        )
    }

    private val notificador by lazy { Notificador(this) }

    /**
     * La ronda de segundo plano, montada entera.
     *
     * Se arma aquí y no en [Piezas] porque necesita el autoactualizador, que es de la
     * Application: la tienda se entera de sí misma por su manifiesto, no por el catálogo.
     */
    private val vigilar by lazy {
        VigilarActualizaciones(
            obtenerCatalogo = piezas.obtenerCatalogo,
            memoria = piezas.ajustes,
            laTienda = {
                autoactualizador.comprobar(Modo.AUTOMATICO)
                (autoactualizador.estado.value as? EstadoActualizacion.Disponible)?.let {
                    Pendiente(
                        id = packageName,
                        nombre = getString(R.string.app_name),
                        versionCodeNuevo = it.info.versionCode,
                    )
                }
            },
            avisar = { aviso -> notificador.avisar(aviso) },
        )
    }

    suspend fun vigilarActualizaciones() = vigilar()
}

class Piezas(private val contexto: Context) {

    private val almacen = AlmacenPrivado(contexto)
    private val instalador = InstaladorDeSesiones(contexto)
    private val registro = RegistroEnDisco(contexto)
    private val verificador = VerificadorSha256()
    private val appsInstaladas = AppsDelMovil(contexto)

    /** Lo que la persona ha elegido que se recuerde: tamaño de letra, color, comprobaciones. */
    val ajustes = AjustesGuardados(contexto)

    val obtenerCatalogo = ObtenerCatalogo(
        catalogo = CatalogoHttp(BuildConfig.URL_CATALOGO),
        instaladas = appsInstaladas,
        paqueteDeLaTienda = contexto.packageName,
    )

    val instalarPaquete = InstalarPaquete(
        almacen = almacen,
        descargador = DescargaHttp(),
        verificador = verificador,
        instalador = instalador,
        registro = registro,
    )

    val reanudarInstalaciones = ReanudarInstalaciones(
        registro = registro,
        almacen = almacen,
        instalador = instalador,
        verificador = verificador,
        // El módulo solo necesita saber qué versión hay puesta, no toda la ficha.
        instaladas = VersionInstalada { id -> appsInstaladas.buscar(id)?.versionCode },
    )

    /** ¿Nos deja Android instalar otras apps? */
    fun hayPermisoParaInstalar(): Boolean =
        contexto.packageManager.canRequestPackageInstalls()

    /** Lleva a la pantalla de Android donde se concede ese permiso. */
    fun intencionDeAjustesDeOrigenes(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${contexto.packageName}"))
}
