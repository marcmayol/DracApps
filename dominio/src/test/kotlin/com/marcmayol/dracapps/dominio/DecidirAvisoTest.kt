package com.marcmayol.dracapps.dominio

import com.marcmayol.dracapps.dominio.casos.DecidirAviso
import com.marcmayol.dracapps.dominio.casos.Pendiente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que se decide antes de molestar a nadie.
 *
 * El caso que más se prueba aquí no es el de avisar, sino el de callarse: una tienda que
 * repite el mismo aviso cada doce horas se convierte en ruido y deja de leerse.
 */
class DecidirAvisoTest {

    private val decidir = DecidirAviso()

    @Test
    fun `sin nada pendiente no hay aviso`() {
        assertNull(decidir(pendientes = emptyList(), yaAvisado = emptySet()))
    }

    @Test
    fun `una sola actualizacion se anuncia por su nombre`() {
        val aviso = decidir(listOf(kuse()), yaAvisado = emptySet())

        assertEquals("Kuse tiene una versión nueva", aviso?.titulo)
        assertEquals("Kuse", aviso?.texto)
    }

    @Test
    fun `varias se cuentan y se enumeran`() {
        val aviso = decidir(listOf(kuse(), grimorio()), yaAvisado = emptySet())

        assertEquals("2 actualizaciones esperando", aviso?.titulo)
        assertEquals("Kuse, Grimorio de Salud", aviso?.texto)
    }

    @Test
    fun `la propia tienda es una mas y no lleva aviso aparte`() {
        val aviso = decidir(listOf(tienda(), kuse()), yaAvisado = emptySet())

        assertEquals("2 actualizaciones esperando", aviso?.titulo)
        assertTrue("la tienda aparece en el mismo aviso", aviso!!.texto.contains("DracApps"))
    }

    @Test
    fun `lo ya avisado no se repite`() {
        val yaAvisado = setOf("com.marcm.cadencia@5")

        assertNull(decidir(listOf(kuse()), yaAvisado))
    }

    @Test
    fun `una version mas nueva de lo mismo si vuelve a avisar`() {
        // Se avisó de la 5; ha salido la 6. Es otra noticia, aunque sea la misma app.
        val yaAvisado = setOf("com.marcm.cadencia@5")

        val aviso = decidir(listOf(kuse(versionCode = 6)), yaAvisado)

        assertEquals("Kuse tiene una versión nueva", aviso?.titulo)
    }

    @Test
    fun `si entre lo ya avisado aparece algo nuevo, se avisa de todo junto`() {
        // Kuse ya se avisó ayer, pero hoy también espera el Grimorio: el aviso sale y
        // las nombra a las dos, porque lo que se enseña es lo que hay pendiente, no
        // solo lo que acaba de llegar.
        val yaAvisado = setOf("com.marcm.cadencia@5")

        val aviso = decidir(listOf(kuse(), grimorio()), yaAvisado)

        assertEquals("2 actualizaciones esperando", aviso?.titulo)
        assertEquals("Kuse, Grimorio de Salud", aviso?.texto)
    }

    @Test
    fun `el aviso trae lo que habra que recordar para no repetirlo`() {
        val aviso = decidir(listOf(kuse(), grimorio()), yaAvisado = emptySet())

        assertEquals(
            setOf("com.marcm.cadencia@5", "com.marcm.grimoriodepociones@11"),
            aviso?.huellas,
        )
    }

    private fun kuse(versionCode: Int = 5) =
        Pendiente("com.marcm.cadencia", "Kuse", versionCode)

    private fun grimorio() =
        Pendiente("com.marcm.grimoriodepociones", "Grimorio de Salud", 11)

    private fun tienda() =
        Pendiente("com.marcmayol.dracapps", "DracApps", 5)
}
