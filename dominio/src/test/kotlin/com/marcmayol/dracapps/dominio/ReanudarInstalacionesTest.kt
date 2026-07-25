package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.casos.ReanudarInstalaciones
import com.marcmayol.dracapps.dominio.puertos.InstalacionEnCurso
import com.marcmayol.dracapps.dominio.puertos.PasoInstalacion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué pasa cuando el proceso muere a media instalación y la app vuelve a arrancar.
 *
 * Esto no se puede probar contra un móvil de verdad a voluntad: habría que matar el
 * proceso en el instante exacto. Con dobles se prueba cada punto de muerte, que es
 * justo para lo que sirven.
 */
class ReanudarInstalacionesTest {

    private val almacen = AlmacenFalso()
    private val instaladas = AppsInstaladasFalsas()

    private fun aMedias(
        paso: PasoInstalacion,
        id: String = "com.ejemplo.app",
        versionCode: Int = 2,
        sesion: Int? = null,
    ) = InstalacionEnCurso(
        id = id,
        versionCode = versionCode,
        versionName = "1.1",
        nombre = "App de ejemplo",
        apkUrl = "https://ejemplo/app.apk",
        sha256 = HASH_BUENO,
        tamanoBytes = 1_000_000,
        paso = paso,
        sesion = sesion,
    )

    private fun caso(
        pendientes: List<InstalacionEnCurso>,
        hashDelFichero: String = HASH_BUENO,
        sesionesAbiertas: List<Int> = emptyList(),
    ): Triple<ReanudarInstalaciones, InstaladorFalso, RegistroFalso> {
        val instalador = InstaladorFalso(abiertas = sesionesAbiertas)
        val registro = RegistroFalso(pendientes)
        val reanudar = ReanudarInstalaciones(
            registro = registro,
            almacen = almacen,
            instalador = instalador,
            verificador = VerificadorFalso(hashDelFichero),
            instaladas = instaladas,
        )
        return Triple(reanudar, instalador, registro)
    }

    @Test
    fun `si murio descargando, se vuelve a empezar`() = runTest {
        val (reanudar, _, _) = caso(listOf(aMedias(PasoInstalacion.DESCARGANDO)))

        val recogida = reanudar()

        assertEquals(listOf("com.ejemplo.app"), recogida.reintentables.map { it.id })
    }

    @Test
    fun `una descarga a medias se borra, no se hereda`() = runTest {
        val pendiente = aMedias(PasoInstalacion.DESCARGANDO)
        almacen.escribir(almacen.rutaDe(pendiente.id, pendiente.versionCode), tamano = 500)
        val (reanudar, _, _) = caso(listOf(pendiente))

        reanudar()

        assertTrue(
            "un APK a medias que parezca completo es justo lo que no puede pasar",
            almacen.rutaDe(pendiente.id, pendiente.versionCode) in almacen.borrados,
        )
    }

    @Test
    fun `si el apk descargado sigue cuadrando, se reintenta`() = runTest {
        val pendiente = aMedias(PasoInstalacion.DESCARGADA)
        almacen.escribir(almacen.rutaDe(pendiente.id, pendiente.versionCode))
        val (reanudar, _, _) = caso(listOf(pendiente), hashDelFichero = HASH_BUENO)

        val recogida = reanudar()

        assertEquals(listOf("com.ejemplo.app"), recogida.reintentables.map { it.id })
    }

    @Test
    fun `si el apk descargado ya no cuadra, se descarta`() = runTest {
        val pendiente = aMedias(PasoInstalacion.DESCARGADA)
        almacen.escribir(almacen.rutaDe(pendiente.id, pendiente.versionCode))
        val (reanudar, _, _) = caso(listOf(pendiente), hashDelFichero = "cc".repeat(32))

        val recogida = reanudar()

        assertEquals(listOf("com.ejemplo.app"), recogida.descartadas)
        assertTrue(recogida.reintentables.isEmpty())
    }

    @Test
    fun `si estaba verificada pero el fichero ya no esta, se descarta`() = runTest {
        val (reanudar, _, _) = caso(listOf(aMedias(PasoInstalacion.VERIFICADA)))

        val recogida = reanudar()

        assertEquals(listOf("com.ejemplo.app"), recogida.descartadas)
    }

    @Test
    fun `si el movil ya tiene la version, la instalacion salio bien aunque nadie lo viera`() =
        runTest {
            instaladas.poner(appInstalada(versionCode = 2))
            val (reanudar, _, registro) = caso(
                listOf(aMedias(PasoInstalacion.CONFIRMANDO, sesion = 7))
            )

            val recogida = reanudar()

            assertEquals(listOf("com.ejemplo.app"), recogida.terminadas)
            assertTrue(registro.todas().isEmpty())
        }

    @Test
    fun `si el movil tiene una version aun mas nueva, tambien se da por terminada`() = runTest {
        instaladas.poner(appInstalada(versionCode = 9))
        val (reanudar, _, _) = caso(listOf(aMedias(PasoInstalacion.CONFIRMANDO, versionCode = 2)))

        assertEquals(listOf("com.ejemplo.app"), reanudar().terminadas)
    }

    @Test
    fun `una sesion confirmada que no llego a instalar se tira`() = runTest {
        val (reanudar, instalador, _) = caso(
            listOf(aMedias(PasoInstalacion.CONFIRMANDO, sesion = 7)),
            sesionesAbiertas = listOf(7),
        )

        val recogida = reanudar()

        assertEquals(listOf("com.ejemplo.app"), recogida.descartadas)
        assertTrue("la sesión tiene que quedar abandonada", 7 in instalador.abandonadas)
    }

    @Test
    fun `las sesiones que no corresponden a nada apuntado se abandonan`() = runTest {
        val (reanudar, instalador, _) = caso(
            pendientes = emptyList(),
            sesionesAbiertas = listOf(11, 12, 13),
        )

        val recogida = reanudar()

        assertEquals(listOf(11, 12, 13), recogida.sesionesHuerfanas)
        assertEquals(listOf(11, 12, 13), instalador.abandonadas)
    }

    @Test
    fun `sin nada pendiente no hay nada que hacer`() = runTest {
        val (reanudar, instalador, _) = caso(emptyList())

        val recogida = reanudar()

        assertEquals(Unit, Unit)
        assertTrue(recogida.terminadas.isEmpty())
        assertTrue(recogida.reintentables.isEmpty())
        assertTrue(recogida.descartadas.isEmpty())
        assertTrue(instalador.abandonadas.isEmpty())
    }

    @Test
    fun `el registro queda limpio pase lo que pase`() = runTest {
        val (reanudar, _, registro) = caso(
            listOf(
                aMedias(PasoInstalacion.DESCARGANDO, id = "com.a"),
                aMedias(PasoInstalacion.VERIFICADA, id = "com.b"),
                aMedias(PasoInstalacion.CONFIRMANDO, id = "com.c", sesion = 3),
            )
        )

        reanudar()

        assertTrue(
            "quedarse con filas viejas haría que la app arrastrase el problema para siempre",
            registro.todas().isEmpty(),
        )
    }

    @Test
    fun `se limpian las descargas que ya no interesan a nadie`() = runTest {
        almacen.escribir("/privado/com.viejo-1.apk")
        val (reanudar, _, _) = caso(emptyList())

        reanudar()

        assertEquals(1, almacen.limpiezas)
        assertTrue(!almacen.hay("/privado/com.viejo-1.apk"))
    }
}
