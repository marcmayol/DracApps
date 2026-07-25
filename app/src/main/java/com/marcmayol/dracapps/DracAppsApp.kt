package com.marcmayol.dracapps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.marcmayol.dracapps.android.AlmacenPrivado
import com.marcmayol.dracapps.android.AppsDelMovil
import com.marcmayol.dracapps.android.CatalogoHttp
import com.marcmayol.dracapps.android.DescargaHttp
import com.marcmayol.dracapps.android.InstaladorDeSesiones
import com.marcmayol.dracapps.android.RegistroEnDisco
import com.marcmayol.dracapps.android.VerificadorSha256
import com.marcmayol.dracapps.dominio.casos.InstalarApp
import com.marcmayol.dracapps.dominio.casos.ObtenerCatalogo
import com.marcmayol.dracapps.dominio.casos.ReanudarInstalaciones

/**
 * Aquí se enchufan los adaptadores a los casos de uso, y en ningún otro sitio.
 *
 * Montado a mano y no con un inyector de dependencias: son siete piezas que se juntan
 * una sola vez. Un framework de inyección aquí añadiría configuración y tiempo de
 * compilación a cambio de ahorrar veinte líneas que se leen de un vistazo.
 */
class DracAppsApp : Application() {

    val piezas by lazy { Piezas(this) }
}

class Piezas(private val contexto: Context) {

    private val almacen = AlmacenPrivado(contexto)
    private val instalador = InstaladorDeSesiones(contexto)
    private val registro = RegistroEnDisco(contexto)
    private val verificador = VerificadorSha256()
    private val appsInstaladas = AppsDelMovil(contexto)

    val obtenerCatalogo = ObtenerCatalogo(
        catalogo = CatalogoHttp(BuildConfig.URL_CATALOGO),
        instaladas = appsInstaladas,
        paqueteDeLaTienda = contexto.packageName,
    )

    val instalarApp = InstalarApp(
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
        instaladas = appsInstaladas,
    )

    /** ¿Nos deja Android instalar otras apps? */
    fun hayPermisoParaInstalar(): Boolean =
        contexto.packageManager.canRequestPackageInstalls()

    /** Lleva a la pantalla de Android donde se concede ese permiso. */
    fun intencionDeAjustesDeOrigenes(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${contexto.packageName}"))
}
