package com.marcmayol.dracapps

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marcmayol.dracapps.dominio.modelo.AppConEstado
import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.modelo.MotivoNoGestionada
import com.marcmayol.dracapps.ui.EstadoTienda
import com.marcmayol.dracapps.ui.PantallaTienda
import com.marcmayol.dracapps.ui.Seccion
import com.marcmayol.dracapps.ui.catalogo.EstadoPantallaCatalogo
import com.marcmayol.dracapps.ui.catalogo.Etiquetas
import com.marcmayol.dracapps.ui.comun.EtiquetasEstados
import com.marcmayol.dracapps.ui.detalle.EtiquetasDetalle
import com.marcmayol.dracapps.ui.instalacion.EstadoHoja
import com.marcmayol.dracapps.ui.instalacion.EtiquetasInstalacion
import com.marcmayol.dracapps.ui.instalacion.HojaDeInstalacion
import com.marcmayol.dracapps.ui.permiso.EtiquetasPermiso
import com.marcmayol.dracapps.ui.tema.DracAppsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Inventario de pantallas y acciones de la Fase 2.
 *
 * Todo lo que se comprueba aquí se comprueba **contra la UI de verdad**: se pinta la
 * pantalla, se busca lo que vería una persona y se pulsa donde pulsaría ella. En ningún
 * sitio se llama a un método interno para dar algo por bueno, porque eso demostraría
 * que el código se llama a sí mismo, no que la pantalla funcione.
 *
 * La tabla que cubre este fichero está en app/INVENTARIO.md.
 */
@RunWith(RobolectricTestRunner::class)
class InventarioDePantallasTest {

    @get:Rule
    val compose = createComposeRule()

    // --- Catálogo: los cuatro estados ---------------------------------------------

    @Test
    fun `catalogo - la app no instalada ofrece Instalar y lo dice con su chip`() {
        pintarCatalogo(listOf(noInstalada()))

        compose.onNodeWithText("No instalada").assertIsDisplayed()
        compose.onNodeWithText("Instalar").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `catalogo - la app al dia ofrece Abrir y lo dice con su chip`() {
        pintarCatalogo(listOf(alDia()))

        compose.onNodeWithText("Al día").assertIsDisplayed()
        compose.onNodeWithText("Abrir").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `catalogo - la app actualizable ofrece Actualizar y lo dice con su chip`() {
        pintarCatalogo(listOf(actualizable()))

        compose.onNodeWithText("Actualización").assertIsDisplayed()
        compose.onNodeWithText("Actualizar").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `catalogo - la app instalada por fuera se distingue y solo ofrece Abrir`() {
        pintarCatalogo(listOf(noGestionada()))

        compose.onNodeWithText("Instalada por fuera").assertIsDisplayed()
        compose.onNodeWithText("Abrir").assertIsDisplayed()
    }

    @Test
    fun `catalogo - los cuatro estados se distinguen en una misma lista`() {
        pintarCatalogo(listOf(actualizable(), alDia(), noInstalada(), noGestionada()))

        // Cada estado dice lo suyo. Se recorre la lista desplazandose, que es lo que
        // hace una persona con el dedo cuando no le caben todas en la pantalla.
        listOf("Actualización", "Al día", "No instalada", "Instalada por fuera")
            .forEach { chip ->
                compose.onNodeWithTag(EtiquetasCatalogoTest.LISTA)
                    .performScrollToNode(hasText(chip))
                compose.onNodeWithText(chip).assertIsDisplayed()
            }
    }

    @Test
    fun `catalogo - el subtitulo cuenta las actualizaciones que esperan`() {
        pintarCatalogo(listOf(actualizable(), alDia(), noInstalada()))

        compose.onNodeWithTag(EtiquetasCatalogoTest.SUBTITULO)
            .assertIsDisplayed()
        compose.onNodeWithText("3 apps · 1 actualización esperando").assertIsDisplayed()
    }

    @Test
    fun `catalogo - pulsar una fila lleva a su detalle`() {
        var abierta: AppConEstado? = null
        pintarCatalogo(listOf(actualizable()), alPulsarApp = { abierta = it })

        compose.onAllNodesWithTag(Etiquetas.FILA).onFirst().performClick()

        assertEquals("com.actualizable", abierta?.id)
    }

    @Test
    fun `catalogo - pulsar el boton dispara la accion de esa app`() {
        var accionada: AppConEstado? = null
        pintarCatalogo(listOf(noInstalada()), alAccionar = { accionada = it })

        compose.onNodeWithText("Instalar").performClick()

        assertEquals("com.noinstalada", accionada?.id)
    }

    // --- Estados vacío y de error ---------------------------------------------------

    @Test
    fun `vacio - se explica y ofrece volver a mirar`() {
        var reintentos = 0
        pintar(EstadoTienda(catalogo = EstadoPantallaCatalogo.Vacio), alRefrescar = { reintentos++ })

        compose.onNodeWithTag(EtiquetasEstados.VACIO).assertIsDisplayed()
        compose.onNodeWithText("Todavía no hay apps").assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasEstados.BOTON_VACIO).performClick()

        assertEquals(1, reintentos)
    }

    @Test
    fun `error - se explica, tranquiliza y ofrece reintentar`() {
        var reintentos = 0
        pintar(
            EstadoTienda(catalogo = EstadoPantallaCatalogo.SinConexion(emptyList())),
            alRefrescar = { reintentos++ },
        )

        compose.onNodeWithTag(EtiquetasEstados.ERROR).assertIsDisplayed()
        compose.onNodeWithText("No he podido conectar").assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasEstados.BOTON_REINTENTAR).performClick()

        assertEquals(1, reintentos)
    }

    // --- Detalle ---------------------------------------------------------------------

    @Test
    fun `detalle - el boton principal dice Actualizar cuando hay algo nuevo`() {
        pintar(EstadoTienda(detalle = actualizable()))

        compose.onNodeWithTag(EtiquetasDetalle.PANTALLA).assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasDetalle.BOTON_PRINCIPAL).assertHasClickAction()
        compose.onNodeWithText("Actualizar").assertIsDisplayed()
        // Y también deja abrir la que ya está instalada.
        compose.onNodeWithTag(EtiquetasDetalle.BOTON_ABRIR).assertIsDisplayed()
    }

    @Test
    fun `detalle - el boton principal dice Instalar cuando no esta instalada`() {
        pintar(EstadoTienda(detalle = noInstalada()))

        compose.onNodeWithText("Instalar").assertIsDisplayed()
    }

    @Test
    fun `detalle - el boton principal dice Abrir cuando esta al dia`() {
        pintar(EstadoTienda(detalle = alDia()))

        compose.onNodeWithText("Abrir").assertIsDisplayed()
    }

    @Test
    fun `detalle - enseña las notas de la version`() {
        pintar(EstadoTienda(detalle = actualizable()))

        compose.onNodeWithTag(EtiquetasDetalle.NOTAS).assertIsDisplayed()
    }

    @Test
    fun `detalle - pulsar el boton principal dispara la accion`() {
        var accionada: AppConEstado? = null
        pintar(EstadoTienda(detalle = noInstalada()), alAccionar = { accionada = it })

        compose.onNodeWithTag(EtiquetasDetalle.BOTON_PRINCIPAL).performClick()

        assertEquals("com.noinstalada", accionada?.id)
    }

    // --- Permiso de orígenes desconocidos --------------------------------------------

    @Test
    fun `permiso - explica los tres pasos sin asustar`() {
        pintar(EstadoTienda(pidiendoPermiso = true))

        compose.onNodeWithTag(EtiquetasPermiso.PANTALLA).assertIsDisplayed()
        compose.onNodeWithText("Un permiso, una sola vez").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun `permiso - el boton lleva a los ajustes de Android`() {
        var abiertos = 0
        pintar(EstadoTienda(pidiendoPermiso = true), alAbrirAjustes = { abiertos++ })

        compose.onNodeWithTag(EtiquetasPermiso.BOTON_AJUSTES)
            .performScrollTo()
            .performClick()

        assertEquals(1, abiertos)
    }

    @Test
    fun `permiso - siempre hay salida sin culpa`() {
        var dejado = 0
        pintar(EstadoTienda(pidiendoPermiso = true), alDejarPermisoParaLuego = { dejado++ })

        compose.onNodeWithTag(EtiquetasPermiso.BOTON_AHORA_NO)
            .performScrollTo()
            .performClick()

        assertEquals(1, dejado)
    }

    // --- Instalación -----------------------------------------------------------------

    @Test
    fun `instalacion - la descarga enseña porcentaje, barra y las dos salidas`() {
        var cancelaciones = 0
        compose.setContent {
            DracAppsTheme {
                HojaDeInstalacion(
                    estado = EstadoHoja.Descargando("Caloría", 14_200_000, 22_700_000, 63),
                    alCancelar = { cancelaciones++ },
                    alOcultar = {},
                    alAbrir = {},
                    alCerrar = {},
                )
            }
        }

        compose.onNodeWithTag(EtiquetasInstalacion.HOJA).assertIsDisplayed()
        compose.onNodeWithText("63 %").assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasInstalacion.BARRA).assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasInstalacion.BOTON_OCULTAR).assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasInstalacion.BOTON_CANCELAR).performClick()

        assertEquals(1, cancelaciones)
    }

    @Test
    fun `instalacion - al terminar confirma y ofrece abrir`() {
        var aperturas = 0
        compose.setContent {
            DracAppsTheme {
                HojaDeInstalacion(
                    estado = EstadoHoja.Hecho("Caloría", "1.2.0"),
                    alCancelar = {},
                    alOcultar = {},
                    alAbrir = { aperturas++ },
                    alCerrar = {},
                )
            }
        }

        compose.onNodeWithTag(EtiquetasInstalacion.HECHO).assertIsDisplayed()
        compose.onNodeWithText("Ya tienes Caloría").assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasInstalacion.BOTON_AHORA_NO_HECHO).assertIsDisplayed()
        compose.onNodeWithTag(EtiquetasInstalacion.BOTON_ABRIR_HECHO).performClick()

        assertEquals(1, aperturas)
    }

    @Test
    fun `instalacion - un fallo se cuenta en cristiano`() {
        compose.setContent {
            DracAppsTheme {
                HojaDeInstalacion(
                    estado = EstadoHoja.Fallo(
                        "Caloría",
                        "El archivo descargado no es el que esperaba, así que lo he " +
                            "borrado sin instalarlo.",
                    ),
                    alCancelar = {},
                    alOcultar = {},
                    alAbrir = {},
                    alCerrar = {},
                )
            }
        }

        compose.onNodeWithTag(EtiquetasInstalacion.FALLO).assertIsDisplayed()
        val textos = compose.onNodeWithTag(EtiquetasInstalacion.FALLO)
            .fetchSemanticsNode().config.toString()
        assertTrue(
            "ni códigos de error ni jerga: lo lee gente",
            !textos.contains("Exception") && !textos.contains("HTTP"),
        )
    }

    // --- Navegación --------------------------------------------------------------------

    @Test
    fun `navegacion - estan las tres pestañas del diseño`() {
        pintarCatalogo(listOf(alDia()))

        Seccion.entries.forEach { seccion ->
            compose.onNodeWithTag(seccion.prueba).assertIsDisplayed().assertHasClickAction()
        }
    }

    @Test
    fun `navegacion - pulsar una pestaña cambia de seccion`() {
        var destino: Seccion? = null
        pintarCatalogo(listOf(alDia()), alCambiarDeSeccion = { destino = it })

        compose.onNodeWithTag(Seccion.NOVEDADES.prueba).performClick()

        assertEquals(Seccion.NOVEDADES, destino)
    }

    @Test
    fun `navegacion - lo que aun no esta hecho lo dice, no finge`() {
        pintar(EstadoTienda(seccion = Seccion.AJUSTES))

        compose.onNodeWithTag("en-construccion").assertIsDisplayed()
    }

    // --- Andamiaje de los tests ---------------------------------------------------------

    private object EtiquetasCatalogoTest {
        const val SUBTITULO = "subtitulo-catalogo"
        const val LISTA = "lista-catalogo"
    }

    private fun pintarCatalogo(
        apps: List<AppConEstado>,
        alPulsarApp: (AppConEstado) -> Unit = {},
        alAccionar: (AppConEstado) -> Unit = {},
        alCambiarDeSeccion: (Seccion) -> Unit = {},
    ) = pintar(
        EstadoTienda(catalogo = EstadoPantallaCatalogo.Listo(apps)),
        alPulsarApp = alPulsarApp,
        alAccionar = alAccionar,
        alCambiarDeSeccion = alCambiarDeSeccion,
    )

    private fun pintar(
        estado: EstadoTienda,
        alPulsarApp: (AppConEstado) -> Unit = {},
        alAccionar: (AppConEstado) -> Unit = {},
        alRefrescar: () -> Unit = {},
        alAbrirAjustes: () -> Unit = {},
        alDejarPermisoParaLuego: () -> Unit = {},
        alCambiarDeSeccion: (Seccion) -> Unit = {},
    ) {
        compose.setContent {
            DracAppsTheme {
                PantallaTienda(
                    estado = estado,
                    alCambiarDeSeccion = alCambiarDeSeccion,
                    alPulsarApp = alPulsarApp,
                    alAccionar = alAccionar,
                    alRefrescar = alRefrescar,
                    alCerrarDetalle = {},
                    alCerrarHoja = {},
                    alAbrirAjustesDeAndroid = alAbrirAjustes,
                    alDejarPermisoParaLuego = alDejarPermisoParaLuego,
                    alAbrirApp = {},
                )
            }
        }
    }
}

// --- Apps de ejemplo para las pantallas -------------------------------------------------

private fun app(id: String, nombre: String) = AppDelCatalogo(
    id = id,
    nombre = nombre,
    descripcion = "Descripción de $nombre",
    iconoUrl = "",
    versionCode = 5,
    versionName = "2.5.1",
    apkUrl = "https://ejemplo/$id.apk",
    sha256 = "aa".repeat(32),
    firmaSha256 = "bb".repeat(32),
    tamanoBytes = 8_400_000,
    notas = "Las esferas del reloj ya no se reinician al cambiar de día.",
)

private fun noInstalada() = AppConEstado(
    app = app("com.noinstalada", "Building my future"),
    estado = EstadoApp.NoInstalada,
)

private fun alDia() = AppConEstado(
    app = app("com.aldia", "Yo Crónica"),
    estado = EstadoApp.InstaladaAlDia(5, "2.5.1"),
)

private fun actualizable() = AppConEstado(
    app = app("com.actualizable", "GymPlan100"),
    estado = EstadoApp.Actualizable("2.4.0", "2.5.1", 4, 5),
)

private fun noGestionada() = AppConEstado(
    app = app("com.nogestionada", "Aseo Diario"),
    estado = EstadoApp.NoGestionada("1.0", MotivoNoGestionada.OTRO_ORIGEN),
)
