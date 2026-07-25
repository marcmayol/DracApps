package com.marcmayol.dracapps

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcmayol.dracapps.ui.tema.EsquemaClaro
import com.marcmayol.dracapps.ui.tema.EsquemaOscuro
import com.marcmayol.dracapps.ui.tema.FormasDracApps
import com.marcmayol.dracapps.ui.tema.TipografiaDracApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El tema tiene que ser el del diseño, no uno parecido.
 *
 * Cada valor de aquí está copiado de la sección 05 del paquete de diseño. Si alguien
 * "mejora" un color a ojo, esto lo caza.
 */
class TemaTest {

    private fun hex(color: Color): String =
        "#%08X".format(color.value.shr(32).toLong().and(0xFFFFFFFF)).replace("#FF", "#")

    // --- Colores ---------------------------------------------------------------------

    @Test
    fun `el esquema claro es el del diseño`() {
        assertEquals("#7A5900", hex(EsquemaClaro.primary))
        assertEquals("#FFDF9E", hex(EsquemaClaro.primaryContainer))
        assertEquals("#261A00", hex(EsquemaClaro.onPrimaryContainer))
        assertEquals("#F5E0BB", hex(EsquemaClaro.secondaryContainer))
        assertEquals("#8F4A38", hex(EsquemaClaro.tertiary))
        assertEquals("#BA1A1A", hex(EsquemaClaro.error))
        assertEquals("#FFF8F0", hex(EsquemaClaro.surface))
        assertEquals("#1E1B13", hex(EsquemaClaro.onSurface))
        assertEquals("#FBF3E5", hex(EsquemaClaro.surfaceContainerLow))
        assertEquals("#EFE7D9", hex(EsquemaClaro.surfaceContainerHigh))
        assertEquals("#7F7667", hex(EsquemaClaro.outline))
        assertEquals("#D0C5B4", hex(EsquemaClaro.outlineVariant))
    }

    @Test
    fun `el esquema oscuro es el del diseño`() {
        assertEquals("#F0BE48", hex(EsquemaOscuro.primary))
        assertEquals("#5C4200", hex(EsquemaOscuro.primaryContainer))
        assertEquals("#FFDF9E", hex(EsquemaOscuro.onPrimaryContainer))
        assertEquals("#53452A", hex(EsquemaOscuro.secondaryContainer))
        assertEquals("#FFB4A1", hex(EsquemaOscuro.tertiary))
        assertEquals("#FFB4AB", hex(EsquemaOscuro.error))
        assertEquals("#17130B", hex(EsquemaOscuro.surface))
        assertEquals("#ECE1D4", hex(EsquemaOscuro.onSurface))
        assertEquals("#1F1B12", hex(EsquemaOscuro.surfaceContainerLow))
        assertEquals("#2E2920", hex(EsquemaOscuro.surfaceContainerHigh))
        assertEquals("#999080", hex(EsquemaOscuro.outline))
    }

    @Test
    fun `los dos esquemas existen y son distintos`() {
        assertTrue(EsquemaClaro.surface != EsquemaOscuro.surface)
        assertTrue(EsquemaClaro.primary != EsquemaOscuro.primary)
    }

    // --- Tipografía -------------------------------------------------------------------

    @Test
    fun `las escalas tipograficas son las del diseño`() {
        assertEquals(57.sp, TipografiaDracApps.displayLarge.fontSize)
        assertEquals(36.sp, TipografiaDracApps.displaySmall.fontSize)
        assertEquals(28.sp, TipografiaDracApps.headlineMedium.fontSize)
        assertEquals(22.sp, TipografiaDracApps.titleLarge.fontSize)
        assertEquals(16.sp, TipografiaDracApps.titleMedium.fontSize)
        assertEquals(14.sp, TipografiaDracApps.bodyMedium.fontSize)
        assertEquals(12.sp, TipografiaDracApps.labelMedium.fontSize)
        assertEquals(11.sp, TipografiaDracApps.labelSmall.fontSize)
    }

    @Test
    fun `ningun texto baja de 14 sp salvo la insignia`() {
        // Regla del diseño: esta tienda la usa gente mayor. Lo único que puede ser más
        // pequeño es el contador de una insignia, que no hay que leer para entender nada.
        val excepciones = setOf(
            TipografiaDracApps.labelSmall.fontSize,
            TipografiaDracApps.bodySmall.fontSize,
        )
        val estilos = listOf(
            TipografiaDracApps.bodyLarge,
            TipografiaDracApps.bodyMedium,
            TipografiaDracApps.titleMedium,
            TipografiaDracApps.titleSmall,
            TipografiaDracApps.labelLarge,
        )

        estilos.forEach { estilo ->
            assertTrue(
                "ningún texto de lectura puede bajar de 14 sp: ${estilo.fontSize}",
                estilo.fontSize.value >= 14f || estilo.fontSize in excepciones,
            )
        }
    }

    // --- Formas -----------------------------------------------------------------------

    @Test
    fun `las formas son las del diseño`() {
        // Radios más generosos que los de Ladón: la tienda acoge, la herramienta corta.
        assertEquals(RoundedCornerShape(4.dp), FormasDracApps.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), FormasDracApps.small)
        assertEquals(RoundedCornerShape(16.dp), FormasDracApps.medium)
        assertEquals(RoundedCornerShape(20.dp), FormasDracApps.large)
        assertEquals(RoundedCornerShape(28.dp), FormasDracApps.extraLarge)
    }

    // --- La regla de oro ----------------------------------------------------------------

    @Test
    fun `no hay ni un solo color suelto fuera del tema`() {
        val fuentes = File("src/main/java/com/marcmayol/dracapps")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').contains("/ui/tema/") }
            .toList()

        assertTrue("no se han encontrado fuentes que revisar", fuentes.isNotEmpty())

        val sospechosos = mutableListOf<String>()
        val patronColor = Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)|Color\.(Red|Blue|Green|Yellow|Magenta|Cyan|Gray|LightGray|DarkGray|Black|White)\b""")

        fuentes.forEach { fichero ->
            fichero.readLines().forEachIndexed { indice, linea ->
                patronColor.find(linea)?.let { encontrado ->
                    sospechosos += "${fichero.name}:${indice + 1}  ${encontrado.value}"
                }
            }
        }

        assertTrue(
            "Un color escrito fuera del tema deja de responder al modo oscuro y rompe el " +
                "lenguaje de estados. Todo color sale de MaterialTheme.colorScheme:\n" +
                sospechosos.joinToString("\n"),
            sospechosos.isEmpty(),
        )
    }
}
