package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.casos.calcularEstado
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.modelo.MotivoNoGestionada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los cuatro estados, que son lo que la familia ve de un vistazo.
 *
 * Si esto se equivoca, alguien se queda sin una actualización sin enterarse, o le
 * ofrecemos instalar encima de una app que no es la nuestra.
 */
class CalcularEstadoTest {

    private fun estado(
        enCatalogo: Int = 2,
        instalada: com.marcmayol.dracapps.dominio.modelo.AppInstalada? = appInstalada(),
        firmaCatalogo: String = FIRMA_MARC,
    ) = calcularEstado(
        delCatalogo = appDelCatalogo(versionCode = enCatalogo, firmaSha256 = firmaCatalogo),
        instalada = instalada,
        paqueteDeLaTienda = PAQUETE_TIENDA,
    )

    @Test
    fun `sin instalar es NoInstalada`() {
        assertEquals(EstadoApp.NoInstalada, estado(instalada = null))
    }

    @Test
    fun `con versionCode mayor en el catalogo es Actualizable`() {
        val resultado = estado(enCatalogo = 5, instalada = appInstalada(versionCode = 4))

        assertTrue(resultado is EstadoApp.Actualizable)
        with(resultado as EstadoApp.Actualizable) {
            assertEquals(4, versionCodeInstalado)
            assertEquals(5, versionCodeNuevo)
        }
    }

    @Test
    fun `con el mismo versionCode esta al dia`() {
        val resultado = estado(enCatalogo = 4, instalada = appInstalada(versionCode = 4))

        assertTrue(resultado is EstadoApp.InstaladaAlDia)
    }

    @Test
    fun `con una version instalada mas nueva que el catalogo esta al dia, no se degrada`() {
        val resultado = estado(enCatalogo = 3, instalada = appInstalada(versionCode = 7))

        assertTrue(
            "instalar hacia atrás fallaría y confundiría a quien lo viera",
            resultado is EstadoApp.InstaladaAlDia,
        )
    }

    @Test
    fun `la comparacion es por entero, nunca por texto`() {
        // Comparando como cadenas, "1.10" es menor que "1.9" y esta app se quedaría
        // sin actualizar para siempre.
        val resultado = calcularEstado(
            delCatalogo = appDelCatalogo(versionCode = 10, versionName = "1.10"),
            instalada = appInstalada(versionCode = 9, versionName = "1.9"),
            paqueteDeLaTienda = PAQUETE_TIENDA,
        )

        assertTrue(resultado is EstadoApp.Actualizable)
    }

    @Test
    fun `otra firma es NoGestionada aunque el applicationId coincida`() {
        val resultado = estado(instalada = appInstalada(firmaSha256 = FIRMA_AJENA))

        assertTrue(resultado is EstadoApp.NoGestionada)
        assertEquals(
            MotivoNoGestionada.OTRA_FIRMA,
            (resultado as EstadoApp.NoGestionada).motivo,
        )
    }

    @Test
    fun `otra firma manda sobre haber version nueva`() {
        val resultado = estado(
            enCatalogo = 99,
            instalada = appInstalada(versionCode = 1, firmaSha256 = FIRMA_AJENA),
        )

        assertTrue(
            "ofrecer actualizar una app ajena fallaría al instalar",
            resultado is EstadoApp.NoGestionada,
        )
    }

    @Test
    fun `instalada por otro con la misma firma es NoGestionada por origen`() {
        val resultado = estado(instalada = appInstalada(instaladaPor = "com.android.vending"))

        assertTrue(resultado is EstadoApp.NoGestionada)
        assertEquals(
            MotivoNoGestionada.OTRO_ORIGEN,
            (resultado as EstadoApp.NoGestionada).motivo,
        )
    }

    @Test
    fun `sin instalador conocido se considera propia si la firma cuadra`() {
        // Android no siempre sabe quién instaló algo: preinstaladas, restauraciones de
        // copia de seguridad. Negarlo dejaría a la familia sin actualizaciones.
        val resultado = estado(
            enCatalogo = 5,
            instalada = appInstalada(versionCode = 4, instaladaPor = null),
        )

        assertTrue(resultado is EstadoApp.Actualizable)
    }

    @Test
    fun `una app que se actualizo a si misma sigue siendo propia`() {
        // Las apps con auto-actualizador quedan registradas con su propio paquete como
        // instalador. Con la firma verificada no es un origen ajeno: es la de siempre.
        val resultado = estado(
            enCatalogo = 5,
            instalada = appInstalada(
                id = "com.marc.gymplan100",
                versionCode = 4,
                instaladaPor = "com.marc.gymplan100",
            ),
        )

        assertTrue(
            "expulsar de la tienda a una app por actualizarse sola la dejaría sin futuras actualizaciones",
            resultado is EstadoApp.Actualizable,
        )
    }

    @Test
    fun `un catalogo viejo sin firma publicada no marca todo como ajeno`() {
        val resultado = estado(enCatalogo = 5, firmaCatalogo = "")

        assertTrue(resultado is EstadoApp.Actualizable)
    }

    @Test
    fun `la firma se compara sin distinguir mayusculas`() {
        val resultado = estado(
            enCatalogo = 5,
            instalada = appInstalada(versionCode = 4, firmaSha256 = FIRMA_MARC.uppercase()),
        )

        assertTrue(resultado is EstadoApp.Actualizable)
    }
}
