package com.marcmayol.dracapps

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.modelo.MotivoNoGestionada
import com.marcmayol.dracapps.ui.EstadoTienda
import com.marcmayol.dracapps.ui.PantallaTienda
import com.marcmayol.dracapps.ui.catalogo.EstadoPantallaCatalogo
import com.marcmayol.dracapps.ui.instalacion.EstadoHoja
import com.marcmayol.dracapps.ui.instalacion.HojaDeInstalacion
import com.marcmayol.dracapps.ui.tema.DracAppsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de las pantallas, en claro y en oscuro, para poder compararlas con las
 * maquetas del diseño.
 *
 * Se renderizan de verdad (Robolectric en modo nativo), no son mockups: lo que sale del
 * PNG es lo que pinta Compose. Van a `app/build/capturas/`.
 *
 * El lienzo es de 412 dp de ancho, el mismo que usan las maquetas del paquete de diseño,
 * para que la comparación sea justa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class CapturasTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val carpeta = File("build/capturas").apply { mkdirs() }

    @Test
    fun `catalogo con los cuatro estados, en claro y en oscuro`() {
        capturar("catalogo") { oscuro ->
            Tienda(
                oscuro,
                EstadoTienda(
                    catalogo = EstadoPantallaCatalogo.Listo(
                        listOf(actualizable(), alDia(), noInstalada(), noGestionada())
                    )
                ),
            )
        }
    }

    @Test
    fun `detalle de una app con actualizacion`() {
        capturar("detalle") { oscuro -> Tienda(oscuro, EstadoTienda(detalle = actualizable())) }
    }

    @Test
    fun `permiso de origenes desconocidos`() {
        capturar("permiso") { oscuro -> Tienda(oscuro, EstadoTienda(pidiendoPermiso = true)) }
    }

    @Test
    fun `estado vacio`() {
        capturar("vacio") { oscuro ->
            Tienda(oscuro, EstadoTienda(catalogo = EstadoPantallaCatalogo.Vacio))
        }
    }

    @Test
    fun `error de red`() {
        capturar("error") { oscuro ->
            Tienda(oscuro, EstadoTienda(catalogo = EstadoPantallaCatalogo.SinConexion(emptyList())))
        }
    }

    @Test
    fun `instalacion descargando`() {
        capturar("instalacion-descarga") { oscuro ->
            Hoja(oscuro, EstadoHoja.Descargando("Caloría", 14_200_000, 22_700_000, 63))
        }
    }

    @Test
    fun `instalacion terminada`() {
        capturar("instalacion-hecha") { oscuro ->
            Hoja(oscuro, EstadoHoja.Hecho("Caloría", "1.2.0"))
        }
    }

    // --- Cómo se captura ---------------------------------------------------------------

    @Composable
    private fun Tienda(oscuro: Boolean, estado: EstadoTienda) {
        DracAppsTheme(oscuro = oscuro) {
            Surface(modifier = Modifier.fillMaxSize()) {
                PantallaTienda(
                    estado = estado,
                    alCambiarDeSeccion = {},
                    alPulsarApp = {},
                    alAccionar = {},
                    alRefrescar = {},
                    alCerrarDetalle = {},
                    alCerrarHoja = {},
                    alAbrirAjustesDeAndroid = {},
                    alDejarPermisoParaLuego = {},
                    alAbrirApp = {},
                )
            }
        }
    }

    @Composable
    private fun Hoja(oscuro: Boolean, estado: EstadoHoja) {
        DracAppsTheme(oscuro = oscuro) {
            Surface(modifier = Modifier.size(412.dp, 420.dp)) {
                HojaDeInstalacion(
                    estado = estado,
                    alCancelar = {},
                    alOcultar = {},
                    alAbrir = {},
                    alCerrar = {},
                )
            }
        }
    }

    private fun capturar(nombre: String, contenido: @Composable (oscuro: Boolean) -> Unit) {
        // Un solo setContent por test, y el tema se cambia con estado: es además la
        // forma honesta de comprobar que la misma pantalla responde al modo oscuro.
        val oscuro = mutableStateOf(false)
        compose.setContent { contenido(oscuro.value) }

        listOf(false to "claro", true to "oscuro").forEach { (enOscuro, sufijo) ->
            oscuro.value = enOscuro
            compose.waitForIdle()

            val fichero = File(carpeta, "$nombre-$sufijo.png")
            dibujar().let { imagen ->
                fichero.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(
                    "la captura de $nombre en $sufijo ha salido en blanco",
                    tienePintura(imagen),
                )
            }
        }
    }

    /**
     * Se pinta la vista de la Activity sobre un lienzo propio.
     *
     * `captureToImage` de Compose usa PixelCopy, que fuera de un dispositivo real no
     * devuelve nada; dibujar la vista directamente sí funciona y da el mismo resultado:
     * lo que sale del PNG es lo que Compose ha pintado.
     */
    private fun dibujar(): Bitmap {
        val vista = compose.activity.window.decorView
        val imagen = Bitmap.createBitmap(
            vista.width.coerceAtLeast(1),
            vista.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        vista.draw(Canvas(imagen))
        return imagen
    }

    /** Una captura en la que todo es del mismo color no demuestra nada. */
    private fun tienePintura(imagen: Bitmap): Boolean {
        val muestras = mutableSetOf<Int>()
        var y = 0
        while (y < imagen.height) {
            var x = 0
            while (x < imagen.width) {
                muestras += imagen.getPixel(x, y)
                if (muestras.size > 3) return true
                x += 7
            }
            y += 7
        }
        return false
    }
}

// --- Apps de ejemplo, las mismas que en las maquetas -------------------------------------

private fun app(id: String, nombre: String, descripcion: String) = AppDelCatalogo(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    iconoUrl = "",
    versionCode = 5,
    versionName = "2.5.1",
    apkUrl = "https://ejemplo/$id.apk",
    sha256 = "aa".repeat(32),
    firmaSha256 = "bb".repeat(32),
    tamanoBytes = 8_400_000,
    notas = "Las esferas del reloj ya no se reinician al cambiar de día.\n" +
        "Nuevo objetivo semanal: 70 000 pasos.",
)

private fun noInstalada() = AppConEstado(
    app("com.noinstalada", "Building my future", "Metas y hábitos a largo plazo"),
    EstadoApp.NoInstalada,
)

private fun alDia() = AppConEstado(
    app("com.aldia", "Yo Crónica", "Entrenamientos con temporizador"),
    EstadoApp.InstaladaAlDia(5, "2.5.1"),
)

private fun actualizable() = AppConEstado(
    app("com.actualizable", "GymPlan100", "Pasos y esferas para Wear OS"),
    EstadoApp.Actualizable("2.4.0", "2.5.1", 4, 5),
)

private fun noGestionada() = AppConEstado(
    app("com.nogestionada", "Aseo Diario", "Rutina de cuidado personal"),
    EstadoApp.NoGestionada("1.0", MotivoNoGestionada.OTRO_ORIGEN),
)
