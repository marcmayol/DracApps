package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.casos.Aviso
import com.marcmayol.dracapps.dominio.casos.ObtenerCatalogo
import com.marcmayol.dracapps.dominio.casos.Pendiente
import com.marcmayol.dracapps.dominio.casos.VigilarActualizaciones
import com.marcmayol.dracapps.dominio.modelo.Catalogo
import com.marcmayol.dracapps.dominio.puertos.MemoriaDeAvisos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * La comprobación que corre con la tienda cerrada.
 *
 * Lo que se prueba aquí es sobre todo cuándo NO hay que molestar: sin red, sin novedades
 * o cuando ya se avisó de lo mismo. Un aviso de más en un móvil ajeno se paga con que se
 * apaguen las notificaciones para siempre.
 */
class VigilarActualizacionesTest {

    @Test
    fun `avisa de las apps del catalogo que tienen version nueva`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", nombre = "Kuse", versionCode = 5)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            avisos = avisos,
        )

        vigilar()

        assertEquals(1, avisos.size)
        assertEquals("Kuse tiene una versión nueva", avisos.single().titulo)
    }

    @Test
    fun `lo que esta al dia no genera aviso`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", versionCode = 4)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            avisos = avisos,
        )

        vigilar()

        assertTrue("nada que decir, ninguna notificación", avisos.isEmpty())
    }

    @Test
    fun `sin red no se avisa ni se rompe`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val vigilar = vigilante(
            catalogoRoto = IOException("sin red"),
            avisos = avisos,
        )

        vigilar()

        assertTrue(avisos.isEmpty())
    }

    @Test
    fun `la tienda entra en el mismo aviso que las apps`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", nombre = "Kuse", versionCode = 5)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            tienda = Pendiente(PAQUETE_TIENDA, "DracApps", 6),
            avisos = avisos,
        )

        vigilar()

        assertEquals("2 actualizaciones esperando", avisos.single().titulo)
        assertEquals("DracApps, Kuse", avisos.single().texto)
    }

    @Test
    fun `si la tienda no sabe de si misma, las apps se avisan igual`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", nombre = "Kuse", versionCode = 5)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            tiendaRota = IllegalStateException("el manifiesto no contesta"),
            avisos = avisos,
        )

        vigilar()

        assertEquals("Kuse tiene una versión nueva", avisos.single().titulo)
    }

    @Test
    fun `dos rondas seguidas con lo mismo pendiente avisan una sola vez`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val memoria = MemoriaEnMemoria()
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", nombre = "Kuse", versionCode = 5)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            memoria = memoria,
            avisos = avisos,
        )

        vigilar()
        vigilar()

        assertEquals("la segunda ronda se calla", 1, avisos.size)
        assertEquals(setOf("com.kuse@5"), memoria.avisadas())
    }

    @Test
    fun `si sale una version mas nueva vuelve a avisar`() = runTest {
        val avisos = mutableListOf<Aviso>()
        val memoria = MemoriaEnMemoria(setOf("com.kuse@5"))
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", nombre = "Kuse", versionCode = 6)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            memoria = memoria,
            avisos = avisos,
        )

        vigilar()

        assertEquals(1, avisos.size)
        assertEquals(setOf("com.kuse@6"), memoria.avisadas())
    }

    @Test
    fun `si el aviso no llega a verse, no se da por avisado`() = runTest {
        // Pasa cuando el permiso de notificaciones está denegado: si lo apuntáramos como
        // avisado, ese aviso no volvería a salir nunca aunque luego se concediera.
        val avisos = mutableListOf<Aviso>()
        val memoria = MemoriaEnMemoria()
        val vigilar = vigilante(
            catalogo = catalogoCon(appDelCatalogo(id = "com.kuse", nombre = "Kuse", versionCode = 5)),
            instaladas = conInstalada("com.kuse", versionCode = 4),
            memoria = memoria,
            avisos = avisos,
            seEnseña = false,
        )

        vigilar()
        vigilar()

        assertTrue("se sigue intentando en cada ronda", avisos.size == 2)
        assertTrue("nada apuntado como dicho", memoria.avisadas().isEmpty())
    }

    // --- Andamiaje -------------------------------------------------------------------

    private class MemoriaEnMemoria(inicial: Set<String> = emptySet()) : MemoriaDeAvisos {
        private var guardadas = inicial
        override fun avisadas() = guardadas
        override fun recordar(huellas: Set<String>) { guardadas = huellas }
    }

    private fun catalogoCon(vararg apps: com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo) =
        Catalogo(titulo = "DracApps", generado = "2026-08-05T10:00:00Z", apps = apps.toList())

    private fun conInstalada(id: String, versionCode: Int) = AppsInstaladasFalsas().apply {
        poner(appInstalada(id = id, versionCode = versionCode))
    }

    private fun vigilante(
        catalogo: Catalogo? = null,
        catalogoRoto: Exception? = null,
        instaladas: AppsInstaladasFalsas = AppsInstaladasFalsas(),
        memoria: MemoriaDeAvisos = MemoriaEnMemoria(),
        tienda: Pendiente? = null,
        tiendaRota: Exception? = null,
        avisos: MutableList<Aviso>,
        seEnseña: Boolean = true,
    ) = VigilarActualizaciones(
        obtenerCatalogo = ObtenerCatalogo(
            catalogo = CatalogoFalso(catalogo, catalogoRoto),
            instaladas = instaladas,
            paqueteDeLaTienda = PAQUETE_TIENDA,
        ),
        memoria = memoria,
        laTienda = { tiendaRota?.let { throw it } ?: tienda },
        avisar = { avisos += it; seEnseña },
    )
}
