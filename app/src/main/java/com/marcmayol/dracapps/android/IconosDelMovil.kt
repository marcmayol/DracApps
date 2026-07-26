package com.marcmayol.dracapps.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.marcmayol.dracapps.ui.comun.IconosInstalados

/**
 * El icono de una app que ya está en el móvil, tal cual lo pinta el sistema.
 *
 * Para lo instalado no hace falta descargar nada: el PackageManager tiene el icono de
 * verdad, con la forma que le da este móvil en concreto. Es el mismo que la familia ve
 * en su pantalla de inicio, así que reconocen la app al instante.
 *
 * Para lo que no está instalado no hay nada que leer, y ahí sí manda el icono que
 * publica el catálogo.
 */
class IconosDelMovil(private val contexto: Context) : IconosInstalados {

    private val cache = mutableMapOf<String, ImageBitmap>()

    /**
     * Null si la app no está instalada o su icono no se puede leer.
     *
     * Solo se guarda lo que se encuentra: si ahora no está instalada, esa respuesta no
     * se cachea. Así, en cuanto se instala desde la tienda, el icono aparece sin tener
     * que reiniciar nada.
     */
    override fun de(id: String): ImageBitmap? {
        cache[id]?.let { return it }
        val icono = try {
            contexto.packageManager.getApplicationIcon(id).aImagen()
        } catch (_: Exception) {
            null
        }
        return icono?.also { cache[id] = it }
    }

    /** Se vacía cuando algo cambia en el móvil, para no enseñar el icono de antes. */
    fun olvidar() = cache.clear()

    private fun Drawable.aImagen(): ImageBitmap {
        // Los iconos adaptativos no traen bitmap: hay que pintarlos.
        if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()

        val lado = intrinsicWidth.takeIf { it > 0 } ?: LADO_POR_DEFECTO
        val alto = intrinsicHeight.takeIf { it > 0 } ?: lado
        val imagen = Bitmap.createBitmap(lado, alto, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, lado, alto)
        draw(Canvas(imagen))
        return imagen.asImageBitmap()
    }

    private companion object {
        const val LADO_POR_DEFECTO = 192
    }
}
