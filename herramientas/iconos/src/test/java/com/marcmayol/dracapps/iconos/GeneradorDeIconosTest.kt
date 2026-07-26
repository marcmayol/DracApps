package com.marcmayol.dracapps.iconos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Convierte los iconos vectoriales de las apps del catálogo en PNG para la tienda.
 *
 * Esto no es un test de nada: es una herramienta que aprovecha que Robolectric trae el
 * motor de dibujo de Android de verdad. Los iconos adaptativos llevan grupos, rotaciones
 * y gradientes, y rasterizarlos "a mano" daría algo parecido pero no igual. Aquí los
 * pinta el mismo código que los pintará en el móvil de la familia.
 *
 * Los vectores los deja `scripts/iconos_desde_repos.py` en el res/ de este módulo.
 * El resultado va a `docs/iconos/<applicationId>.png`, que es donde los busca el
 * generador del catálogo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneradorDeIconosTest {

    private val contexto: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `genera los PNG de los iconos pendientes`() {
        val pendientes = leerPendientes()
        assertTrue("no hay iconos que generar; ejecuta antes el script", pendientes.isNotEmpty())

        val destino = File(RAIZ, "docs/iconos").apply { mkdirs() }

        pendientes.forEach { (applicationId, slug) ->
            val imagen = componer(slug)
            val fichero = File(destino, "$applicationId.png")
            fichero.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }

            assertTrue(
                "el icono de $applicationId ha salido de un solo color",
                tieneDibujo(imagen),
            )
            println("  ${fichero.name}  ${LADO}x$LADO  ${fichero.length()} bytes")
        }
    }

    /**
     * Compone el icono con el lienzo entero, sin recortar.
     *
     * Un icono adaptativo mide 108 dp de los que el launcher solo enseña los 72 centrales.
     * Reproducir ese recorte aquí sería fiel a lo que se ve en la pantalla de inicio, pero
     * corta los iconos cuyo dibujo llega al borde de la zona segura — le pasa al tenedor
     * de Crónicas del Apetito, por ejemplo.
     *
     * En una ficha de tienda interesa más ver el icono entero, que es además lo que hace
     * Google Play. Y no hace falta recortarlo: la tienda le aplica su propia forma
     * redondeada al pintarlo, para que todas las apps se vean iguales aunque sus iconos
     * no lo sean.
     */
    private fun componer(slug: String): Bitmap {
        val imagen = Bitmap.createBitmap(LADO, LADO, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(imagen)

        listOf("background", "foreground").forEach { capa ->
            val dibujo = cargar("${slug}_$capa") ?: return@forEach
            dibujo.setBounds(0, 0, LADO, LADO)
            dibujo.draw(lienzo)
        }

        return imagen
    }

    private fun cargar(nombre: String): Drawable? {
        @Suppress("DiscouragedApi")
        val id = contexto.resources.getIdentifier(nombre, "drawable", contexto.packageName)
        return if (id == 0) null else contexto.getDrawable(id)
    }

    private fun leerPendientes(): List<Pair<String, String>> {
        val crudo = contexto.assets.open("iconos.json").bufferedReader().readText()
        val lista = JSONArray(crudo)
        return (0 until lista.length()).map { indice ->
            val fila = lista.getJSONObject(indice)
            fila.getString("applicationId") to fila.getString("slug")
        }
    }

    /** Un icono de un solo color es un icono que no se ha dibujado. */
    private fun tieneDibujo(imagen: Bitmap): Boolean {
        val colores = mutableSetOf<Int>()
        var y = 0
        while (y < imagen.height) {
            var x = 0
            while (x < imagen.width) {
                colores += imagen.getPixel(x, y)
                if (colores.size > 2) return true
                x += 5
            }
            y += 5
        }
        return false
    }

    private companion object {
        const val LADO = 512

        /** La raíz del repositorio, subiendo desde herramientas/iconos. */
        val RAIZ: File = File("").absoluteFile.parentFile.parentFile
    }
}
