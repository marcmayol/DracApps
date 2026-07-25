package com.marcmayol.dracapps

import com.marcmayol.dracapps.android.CatalogoHttp
import com.marcmayol.dracapps.dominio.casos.ObtenerCatalogo
import com.marcmayol.dracapps.dominio.casos.ResultadoCatalogo
import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.puertos.AppsInstaladas
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lectura del catálogo, contra un servidor local. Sin red de verdad.
 *
 * El fichero que se sirve es **el catálogo real publicado** (copiado a los recursos de
 * test), así que esto comprueba que el cliente entiende exactamente lo que produce el
 * generador de la Fase 1. Si algún día el formato cambiara por un lado y no por el
 * otro, este test se entera antes que la familia.
 */
class CatalogoHttpTest {

    private lateinit var servidor: MockWebServer

    private val catalogoReal: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("catalogo-real.json")!!
            .bufferedReader().readText()
    }

    @Before
    fun levantarServidor() {
        servidor = MockWebServer()
        servidor.start()
    }

    @After
    fun apagarServidor() {
        servidor.shutdown()
    }

    private fun url() = servidor.url("/catalogo.json").toString()

    private fun sirve(cuerpo: String, codigo: Int = 200) {
        servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(cuerpo))
    }

    @Test
    fun `entiende el catalogo real que publica el generador`() = runTest {
        sirve(catalogoReal)

        val catalogo = CatalogoHttp(url()).obtener()

        assertEquals("DracApps", catalogo.titulo)
        assertEquals(2, catalogo.apps.size)

        val gym = catalogo.apps.first { it.id == "com.marc.gymplan100" }
        assertEquals("Building My Future", gym.nombre)
        assertEquals(4, gym.versionCode)
        assertEquals("1.3", gym.versionName)
        assertEquals(64, gym.sha256.length)
        assertEquals(64, gym.firmaSha256.length)
        assertTrue(gym.apkUrl.startsWith("https://"))
        assertTrue(gym.tamanoBytes > 0)
    }

    @Test
    fun `los campos de extension llegan declarados y vacios`() = runTest {
        sirve(catalogoReal)

        val catalogo = CatalogoHttp(url()).obtener()

        assertTrue(catalogo.apps.all { it.canal == null })
        assertTrue(catalogo.apps.all { it.minSdk == null })
    }

    @Test
    fun `un catalogo con campos de mas no rompe nada`() = runTest {
        // El generador podrá añadir campos en el futuro sin dejar tirados a los móviles
        // que aún no se hayan actualizado.
        sirve(
            """
            {
              "version": 1, "titulo": "DracApps", "generado": "2026-07-25T10:00:00Z",
              "inventadoManana": true,
              "apps": [{
                "id": "com.a", "nombre": "A", "versionCode": 1,
                "apkUrl": "https://x/a.apk", "sha256": "aa", "otroCampoNuevo": 42
              }]
            }
            """.trimIndent()
        )

        val catalogo = CatalogoHttp(url()).obtener()

        assertEquals(1, catalogo.apps.size)
        assertEquals("com.a", catalogo.apps[0].id)
    }

    @Test
    fun `un error del servidor se nota`() = runTest {
        sirve("", codigo = 503)

        val fallo = runCatching { CatalogoHttp(url()).obtener() }.exceptionOrNull()

        assertTrue(fallo != null)
        assertTrue(fallo!!.message!!.contains("503"))
    }

    @Test
    fun `un json roto se nota`() = runTest {
        sirve("{ esto no es json")

        val fallo = runCatching { CatalogoHttp(url()).obtener() }.exceptionOrNull()

        assertTrue(fallo != null)
    }

    /**
     * De punta a punta con el catálogo real: se lee, se cruza con lo que "hay instalado"
     * y cada app acaba con el estado que le toca. Es el criterio de la fase entero,
     * salvo la instalación misma.
     */
    @Test
    fun `del catalogo publicado a los estados por versionCode`() = runTest {
        sirve(catalogoReal)

        val instaladas = object : AppsInstaladas {
            // GymPlan100 instalada con el versionCode 3; el catálogo anuncia el 4.
            private val enElMovil = mapOf(
                "com.marc.gymplan100" to AppInstalada(
                    id = "com.marc.gymplan100",
                    versionCode = 3,
                    versionName = "1.2",
                    firmaSha256 =
                    "0e4410d009fa18e09f0b92197693305d4725d01aeaa437b1cc690b8f633e523c",
                    instaladaPor = "com.marcmayol.dracapps",
                )
            )

            override suspend fun buscar(id: String) = enElMovil[id]
            override suspend fun todas(ids: Collection<String>) = enElMovil.filterKeys { it in ids }
        }

        val resultado = ObtenerCatalogo(
            catalogo = CatalogoHttp(url()),
            instaladas = instaladas,
            paqueteDeLaTienda = "com.marcmayol.dracapps",
        )() as ResultadoCatalogo.Listo

        val porId = resultado.apps.associateBy { it.id }
        val gym = porId["com.marc.gymplan100"]!!.estado
        assertTrue("el catálogo anuncia el 4 y hay el 3 instalado", gym is EstadoApp.Actualizable)
        assertEquals(4, (gym as EstadoApp.Actualizable).versionCodeNuevo)

        assertTrue(porId["com.marcm.cronicasapetito"]!!.estado is EstadoApp.NoInstalada)
        assertEquals(1, resultado.actualizaciones)
        assertEquals(
            "lo que pide algo va primero",
            "com.marc.gymplan100",
            resultado.apps.first().id,
        )
    }

    @Test
    fun `sin servidor la tienda no revienta`() = runTest {
        servidor.shutdown()

        val resultado = ObtenerCatalogo(
            catalogo = CatalogoHttp(url()),
            instaladas = object : AppsInstaladas {
                override suspend fun buscar(id: String): AppInstalada? = null
                override suspend fun todas(ids: Collection<String>) = emptyMap<String, AppInstalada>()
            },
            paqueteDeLaTienda = "com.marcmayol.dracapps",
        )()

        assertTrue(resultado is ResultadoCatalogo.SinCatalogo)
    }
}
