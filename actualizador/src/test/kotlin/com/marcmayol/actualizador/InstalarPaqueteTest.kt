package com.marcmayol.actualizador

import com.marcmayol.actualizador.instalacion.InstalacionEnCurso
import com.marcmayol.actualizador.instalacion.InstalarPaquete
import com.marcmayol.actualizador.instalacion.PasoInstalacion
import com.marcmayol.actualizador.instalacion.ReanudarInstalaciones
import com.marcmayol.actualizador.modelo.EstadoActualizacion
import com.marcmayol.actualizador.modelo.MotivoFallo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La instalación, que es donde la tienda se juega la confianza.
 *
 * El catálogo viaja por HTTPS, pero el APK viene de otro sitio. Lo único que garantiza
 * que se instala lo que se anuncia es el SHA-256, y por eso se comprueba SIEMPRE antes
 * de tocar el instalador.
 */
class InstalarPaqueteTest {

    private val almacen = AlmacenFalso()

    private fun caso(
        hashCalculado: String = HASH_BUENO,
        errorDeDescarga: Exception? = null,
        fallaAlCrear: Boolean = false,
        fallaAlConfirmar: Boolean = false,
    ): Triple<InstalarPaquete, InstaladorFalso, RegistroFalso> {
        val instalador = InstaladorFalso(fallaAlCrear, fallaAlConfirmar)
        val registro = RegistroFalso()
        val caso = InstalarPaquete(
            almacen = almacen,
            descargador = DescargadorFalso(almacen, errorDeDescarga),
            verificador = VerificadorFalso(hashCalculado),
            instalador = instalador,
            registro = registro,
        )
        return Triple(caso, instalador, registro)
    }

    @Test
    fun `con el hash correcto se crea la sesion y se confirma`() = runTest {
        val (instalar, instalador, _) = caso(hashCalculado = HASH_BUENO)

        val resultado = instalar(paquete())

        assertTrue(resultado is EstadoActualizacion.Confirmada)
        assertEquals(listOf("com.ejemplo.app"), instalador.creadas)
        assertEquals(1, instalador.confirmadas.size)
    }

    @Test
    fun `con el hash incorrecto no se llega a crear ninguna sesion`() = runTest {
        val (instalar, instalador, _) = caso(hashCalculado = "bb".repeat(32))

        val resultado = instalar(paquete(sha256 = HASH_BUENO))

        assertTrue(resultado is EstadoActualizacion.Fallo)
        assertEquals(MotivoFallo.HASH, (resultado as EstadoActualizacion.Fallo).motivo)
        assertTrue(
            "un APK que no cuadra no puede llegar jamás al instalador",
            instalador.creadas.isEmpty(),
        )
    }

    @Test
    fun `con el hash incorrecto el apk se borra`() = runTest {
        val (instalar, _, _) = caso(hashCalculado = "bb".repeat(32))
        val app = paquete()

        instalar(app)

        val ruta = almacen.rutaDe(app.id, app.versionCode)
        assertTrue("el APK sospechoso tiene que desaparecer", ruta in almacen.borrados)
        assertFalse(almacen.hay(ruta))
    }

    @Test
    fun `con el hash incorrecto no queda nada apuntado que reintentar`() = runTest {
        val (instalar, _, registro) = caso(hashCalculado = "bb".repeat(32))

        instalar(paquete())

        assertTrue(registro.todas().isEmpty())
    }

    @Test
    fun `el hash se compara sin distinguir mayusculas`() = runTest {
        val (instalar, instalador, _) = caso(hashCalculado = HASH_BUENO.uppercase())

        val resultado = instalar(paquete(sha256 = HASH_BUENO))

        assertTrue(resultado is EstadoActualizacion.Confirmada)
        assertEquals(1, instalador.creadas.size)
    }

    @Test
    fun `verifica antes de instalar, nunca al reves`() = runTest {
        val (instalar, instalador, registro) = caso()

        instalar(paquete())

        val pasos = registro.pasosPorLosQuePaso
        assertTrue(
            "VERIFICADA tiene que quedar apuntada antes que SESION_CREADA",
            pasos.indexOf("VERIFICADA") < pasos.indexOf("SESION_CREADA"),
        )
        assertTrue(instalador.creadas.isNotEmpty())
    }

    @Test
    fun `si falla la descarga no se instala nada y no queda basura`() = runTest {
        val (instalar, instalador, registro) = caso(
            errorDeDescarga = java.io.IOException("sin red"),
        )

        val resultado = instalar(paquete())

        assertEquals(
            MotivoFallo.DESCARGA,
            (resultado as EstadoActualizacion.Fallo).motivo,
        )
        assertTrue(instalador.creadas.isEmpty())
        assertTrue(registro.todas().isEmpty())
    }

    @Test
    fun `si el sistema no deja abrir sesion se limpia todo`() = runTest {
        val (instalar, _, registro) = caso(fallaAlCrear = true)
        val app = paquete()

        val resultado = instalar(app)

        assertEquals(
            MotivoFallo.INSTALACION,
            (resultado as EstadoActualizacion.Fallo).motivo,
        )
        assertTrue(registro.todas().isEmpty())
        assertTrue(almacen.rutaDe(app.id, app.versionCode) in almacen.borrados)
    }

    @Test
    fun `si falla al confirmar se abandona la sesion abierta`() = runTest {
        val (instalar, instalador, _) = caso(fallaAlConfirmar = true)

        instalar(paquete())

        assertEquals(
            "dejar sesiones colgando acaba topando con el límite del sistema",
            1,
            instalador.abandonadas.size,
        )
    }

    @Test
    fun `informa del avance de la descarga con su porcentaje`() = runTest {
        val (instalar, _, _) = caso()
        val avisos = mutableListOf<EstadoActualizacion>()

        instalar(paquete()) { avisos += it }

        val descargas = avisos.filterIsInstance<EstadoActualizacion.Descargando>()
        assertEquals(listOf(50, 100), descargas.map { it.porcentaje })
        assertTrue(avisos.any { it is EstadoActualizacion.Verificando })
        assertTrue(avisos.any { it is EstadoActualizacion.Instalando })
    }

    @Test
    fun `un total desconocido no revienta el porcentaje`() {
        assertEquals(0, EstadoActualizacion.Descargando(paquete(), 500, 0).porcentaje)
    }

    @Test
    fun `apunta cada paso antes de darlo`() = runTest {
        val (instalar, _, registro) = caso()

        instalar(paquete())

        assertEquals(
            listOf(
                "PENDIENTE",
                "DESCARGANDO",
                "DESCARGADA",
                "VERIFICADA",
                "SESION_CREADA",
                "CONFIRMANDO",
            ),
            registro.pasosPorLosQuePaso,
        )
    }
}
