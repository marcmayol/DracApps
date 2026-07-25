package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.casos.ObtenerCatalogo
import com.marcmayol.dracapps.dominio.casos.ResultadoCatalogo
import com.marcmayol.dracapps.dominio.modelo.Catalogo
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El catálogo, ya cruzado con lo que hay en el móvil. */
class ObtenerCatalogoTest {

    private val instaladas = AppsInstaladasFalsas()

    private fun catalogoCon(vararg apps: com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo) =
        Catalogo(titulo = "DracApps", generado = "2026-07-25T10:00:00Z", apps = apps.toList())

    private fun caso(catalogo: Catalogo? = null, error: Exception? = null) = ObtenerCatalogo(
        catalogo = CatalogoFalso(catalogo, error),
        instaladas = instaladas,
        paqueteDeLaTienda = PAQUETE_TIENDA,
    )

    @Test
    fun `pone a cada app el estado que le toca`() = runTest {
        instaladas.poner(appInstalada(id = "com.aldia", versionCode = 3))
        instaladas.poner(appInstalada(id = "com.vieja", versionCode = 1))
        instaladas.poner(appInstalada(id = "com.ajena", firmaSha256 = FIRMA_AJENA))

        val resultado = caso(
            catalogoCon(
                appDelCatalogo(id = "com.aldia", versionCode = 3),
                appDelCatalogo(id = "com.vieja", versionCode = 4),
                appDelCatalogo(id = "com.ajena"),
                appDelCatalogo(id = "com.nueva"),
            )
        )() as ResultadoCatalogo.Listo

        val porId = resultado.apps.associateBy { it.id }
        assertTrue(porId["com.aldia"]!!.estado is EstadoApp.InstaladaAlDia)
        assertTrue(porId["com.vieja"]!!.estado is EstadoApp.Actualizable)
        assertTrue(porId["com.ajena"]!!.estado is EstadoApp.NoGestionada)
        assertTrue(porId["com.nueva"]!!.estado is EstadoApp.NoInstalada)
    }

    @Test
    fun `cuenta solo las actualizables`() = runTest {
        instaladas.poner(appInstalada(id = "com.vieja", versionCode = 1))
        instaladas.poner(appInstalada(id = "com.aldia", versionCode = 9))

        val resultado = caso(
            catalogoCon(
                appDelCatalogo(id = "com.vieja", versionCode = 4),
                appDelCatalogo(id = "com.aldia", versionCode = 9),
                appDelCatalogo(id = "com.nueva"),
            )
        )() as ResultadoCatalogo.Listo

        assertEquals(1, resultado.actualizaciones)
    }

    @Test
    fun `las actualizables van primero`() = runTest {
        instaladas.poner(appInstalada(id = "com.zeta", versionCode = 1))

        val resultado = caso(
            catalogoCon(
                appDelCatalogo(id = "com.alfa", nombre = "Alfa"),
                appDelCatalogo(id = "com.zeta", nombre = "Zeta", versionCode = 9),
            )
        )() as ResultadoCatalogo.Listo

        assertEquals(
            "lo único que pide algo tiene que verse primero",
            "com.zeta",
            resultado.apps.first().id,
        )
    }

    @Test
    fun `dentro del mismo estado se ordena por nombre`() = runTest {
        val resultado = caso(
            catalogoCon(
                appDelCatalogo(id = "com.c", nombre = "Zeta"),
                appDelCatalogo(id = "com.a", nombre = "Alfa"),
                appDelCatalogo(id = "com.b", nombre = "beta"),
            )
        )() as ResultadoCatalogo.Listo

        assertEquals(listOf("Alfa", "beta", "Zeta"), resultado.apps.map { it.nombre })
    }

    @Test
    fun `un catalogo vacio se nota, y no es un error`() = runTest {
        val resultado = caso(catalogoCon())() as ResultadoCatalogo.Listo

        assertTrue(resultado.vacio)
    }

    @Test
    fun `sin red no revienta, devuelve el estado de sin catalogo`() = runTest {
        val resultado = caso(error = java.io.IOException("sin red"))()

        assertTrue(
            "la tienda de casa no puede romperse porque falle el wifi",
            resultado is ResultadoCatalogo.SinCatalogo,
        )
    }

    @Test
    fun `un catalogo ilegible tampoco revienta`() = runTest {
        val resultado = caso(error = IllegalStateException("json roto"))()

        assertTrue(resultado is ResultadoCatalogo.SinCatalogo)
    }
}
