package com.marcmayol.dracapps.dominio.casos

import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.modelo.EstadoApp
import com.marcmayol.dracapps.dominio.modelo.MotivoNoGestionada

/**
 * En qué estado está una app del catálogo en este móvil.
 *
 * El orden de las comprobaciones importa y no es casual:
 *
 * 1. Si no está instalada, no hay más que mirar.
 * 2. Si la firma no coincide con la del catálogo, es **otra app** aunque comparta
 *    nombre y applicationId. Instalar encima fallaría, así que la tienda solo informa.
 * 3. Si la firma coincide pero la instaló otro, tampoco es suya: se informa, y el
 *    detalle ofrecerá ponerla bajo la tienda.
 * 4. Ya con una app propia, se compara por versionCode **entero**. Jamás por texto:
 *    "1.10" es menor que "1.9" comparando cadenas, y eso dejaría móviles sin actualizar
 *    sin que nadie se entere.
 */
fun calcularEstado(
    delCatalogo: AppDelCatalogo,
    instalada: AppInstalada?,
    paqueteDeLaTienda: String,
): EstadoApp {
    if (instalada == null) return EstadoApp.NoInstalada

    if (!mismaFirma(delCatalogo.firmaSha256, instalada.firmaSha256)) {
        return EstadoApp.NoGestionada(
            versionInstalada = instalada.versionName,
            motivo = MotivoNoGestionada.OTRA_FIRMA,
        )
    }

    if (!laInstaloLaTienda(instalada, paqueteDeLaTienda)) {
        return EstadoApp.NoGestionada(
            versionInstalada = instalada.versionName,
            motivo = MotivoNoGestionada.OTRO_ORIGEN,
        )
    }

    return if (delCatalogo.versionCode > instalada.versionCode) {
        EstadoApp.Actualizable(
            versionInstalada = instalada.versionName,
            versionNueva = delCatalogo.versionName,
            versionCodeInstalado = instalada.versionCode,
            versionCodeNuevo = delCatalogo.versionCode,
        )
    } else {
        EstadoApp.InstaladaAlDia(
            versionCode = instalada.versionCode,
            versionName = instalada.versionName,
        )
    }
}

/**
 * Si el catálogo no publica firma, no se puede afirmar que sea otra: se deja pasar y
 * decide el origen. Esto solo ocurre con catálogos viejos, anteriores a que el
 * generador empezara a publicar `firmaSha256`.
 */
private fun mismaFirma(delCatalogo: String, instalada: String): Boolean {
    if (delCatalogo.isBlank()) return true
    return delCatalogo.equals(instalada, ignoreCase = true)
}

/**
 * Android no siempre sabe quién instaló una app: en las preinstaladas, o tras
 * restaurar una copia de seguridad, no hay instalador registrado. En ese caso se
 * considera propia si la firma cuadra, porque negarlo marcaría como ajenas apps que sí
 * lo son y dejaría a la familia sin actualizaciones.
 *
 * Y una app que se ha actualizado **a sí misma** (las que llevan auto-actualizador
 * propio) queda registrada con su propio paquete como instalador. Con la firma ya
 * verificada eso no es un origen ajeno, es la misma app de siempre: negarlo expulsaría
 * de la tienda a cualquier app en cuanto se actualice sola una vez.
 */
private fun laInstaloLaTienda(instalada: AppInstalada, paqueteDeLaTienda: String): Boolean {
    val instalador = instalada.instaladaPor
    return instalador == null ||
        instalador == paqueteDeLaTienda ||
        instalador == instalada.id
}
