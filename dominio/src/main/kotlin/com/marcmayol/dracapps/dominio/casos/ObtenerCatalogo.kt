package com.marcmayol.dracapps.dominio.casos

import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.puertos.AppsInstaladas
import com.marcmayol.dracapps.dominio.puertos.CatalogoRemoto

/** Lo que la pantalla de catálogo necesita saber. */
sealed interface ResultadoCatalogo {

    data class Listo(
        val titulo: String,
        val apps: List<AppConEstado>,
    ) : ResultadoCatalogo {
        val actualizaciones: Int get() = apps.count { it.tieneActualizacion }
        val vacio: Boolean get() = apps.isEmpty()
    }

    /**
     * No se pudo leer el catálogo.
     *
     * Trae las apps instaladas que sí se conocen, porque el diseño lo pide así: el
     * mensaje de error tranquiliza recordando que lo instalado sigue funcionando, y
     * ofrece "ver solo las instaladas".
     */
    data class SinCatalogo(val instaladas: List<AppConEstado>) : ResultadoCatalogo
}

/**
 * Trae el catálogo y le pone estado a cada app comparándolo con lo instalado.
 *
 * Un fallo de red no es una excepción que suba: es un estado de la pantalla. La tienda
 * de casa no puede romperse porque el wifi vaya mal.
 */
class ObtenerCatalogo(
    private val catalogo: CatalogoRemoto,
    private val instaladas: AppsInstaladas,
    private val paqueteDeLaTienda: String,
) {

    suspend operator fun invoke(): ResultadoCatalogo {
        val leido = try {
            catalogo.obtener()
        } catch (error: Exception) {
            return ResultadoCatalogo.SinCatalogo(instaladas = emptyList())
        }

        val enElMovil = instaladas.todas(leido.apps.map { it.id })

        val apps = leido.apps.map { app ->
            AppConEstado(
                app = app,
                estado = calcularEstado(app, enElMovil[app.id], paqueteDeLaTienda),
            )
        }

        return ResultadoCatalogo.Listo(titulo = leido.titulo, apps = ordenar(apps))
    }

    /**
     * Primero lo que pide algo, después lo demás por nombre.
     *
     * El diseño lo dice claro: las actualizables rompen la retícula a propósito, son lo
     * único que reclama atención. Ponerlas arriba es la misma idea llevada al orden.
     */
    private fun ordenar(apps: List<AppConEstado>): List<AppConEstado> =
        apps.sortedWith(
            compareBy(
                { orden(it.estado) },
                { it.nombre.lowercase() },
            )
        )

    private fun orden(estado: EstadoApp): Int = when (estado) {
        is EstadoApp.Actualizable -> 0
        is EstadoApp.NoInstalada -> 1
        is EstadoApp.InstaladaAlDia -> 2
        is EstadoApp.NoGestionada -> 3
    }
}
