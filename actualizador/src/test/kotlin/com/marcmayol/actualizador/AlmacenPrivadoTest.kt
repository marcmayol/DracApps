package com.marcmayol.actualizador

import androidx.test.core.app.ApplicationProvider
import com.marcmayol.actualizador.instalacion.AlmacenPrivado
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * La carpeta de descargas, que es la única parte del almacén con reglas propias.
 *
 * Se prueba contra ficheros de verdad porque lo que falló en el móvil fue justo eso: qué
 * ficheros sobreviven a una limpieza, incluido el temporal que nadie tenía en cuenta.
 */
@RunWith(RobolectricTestRunner::class)
class AlmacenPrivadoTest {

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val almacen = AlmacenPrivado(contexto)
    private val carpeta = File(contexto.filesDir, "descargas")

    private fun crear(nombre: String) = File(carpeta, nombre).apply { writeText("apk") }

    @Test
    fun `la limpieza respeta el apk de una version vigente y borra el resto`() {
        val vigente = crear("com.ejemplo.app-3.apk")
        val sobrante = crear("com.ejemplo.app-2.apk")
        val ajeno = crear("com.otra.app-7.apk")

        almacen.limpiarSobrantes(setOf("com.ejemplo.app" to 3))

        assertTrue(vigente.exists())
        assertFalse(sobrante.exists())
        assertFalse(ajeno.exists())
    }

    @Test
    fun `la limpieza respeta el temporal de una descarga en marcha`() {
        val enMarcha = crear("com.ejemplo.app-3.apk.parcial")

        almacen.limpiarSobrantes(setOf("com.ejemplo.app" to 3))

        assertTrue(
            "borrar el .parcial deja la descarga sin fichero que renombrar",
            enMarcha.exists(),
        )
    }

    @Test
    fun `la limpieza si se lleva el temporal de una version que ya no interesa`() {
        val abandonado = crear("com.ejemplo.app-2.apk.parcial")

        almacen.limpiarSobrantes(setOf("com.ejemplo.app" to 3))

        assertFalse(abandonado.exists())
    }
}
