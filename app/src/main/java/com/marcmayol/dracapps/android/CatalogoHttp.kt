package com.marcmayol.dracapps.android

import com.marcmayol.dracapps.dominio.modelo.AppDelCatalogo
import com.marcmayol.dracapps.dominio.modelo.Catalogo
import com.marcmayol.dracapps.dominio.puertos.CatalogoRemoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Trae el catálogo de la URL publicada. La ÚNICA fuente de la tienda.
 *
 * Aquí no se consulta la API de GitHub ni nada que se le parezca: toda la inteligencia
 * (leer Releases, comprobar firmas, calcular hashes) ya ocurrió en la máquina del
 * admin cuando se generó el catálogo.
 */
class CatalogoHttp(
    private val url: String,
    private val cliente: OkHttpClient = clientePorDefecto(),
) : CatalogoRemoto {

    override suspend fun obtener(): Catalogo = withContext(Dispatchers.IO) {
        val peticion = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        cliente.newCall(peticion).execute().use { respuesta ->
            if (!respuesta.isSuccessful) {
                error("El catálogo respondió HTTP ${respuesta.code}")
            }
            val cuerpo = respuesta.body?.string().orEmpty()
            json.decodeFromString<CatalogoJson>(cuerpo).aDominio()
        }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /**
         * Sin seguir redirecciones de https a http.
         *
         * La URL del catálogo apunta al dominio propio, pero la de github.io responde
         * un 301 hacia http sin cifrar. Si el cliente lo siguiera, alguien en la misma
         * red podría cambiar el JSON y, con él, los hashes de todos los APKs: la
         * verificación dejaría de proteger nada.
         */
        fun clientePorDefecto() = OkHttpClient.Builder()
            .followSslRedirects(false)
            .build()
    }
}

@Serializable
private data class CatalogoJson(
    val titulo: String = "DracApps",
    val generado: String = "",
    val apps: List<AppJson> = emptyList(),
) {
    fun aDominio() = Catalogo(
        titulo = titulo,
        generado = generado,
        apps = apps.map { it.aDominio() },
    )
}

@Serializable
private data class AppJson(
    val id: String,
    val nombre: String = "",
    val descripcion: String = "",
    @SerialName("iconoUrl") val iconoUrl: String = "",
    val versionCode: Int,
    val versionName: String = "",
    val apkUrl: String,
    val sha256: String,
    val firmaSha256: String = "",
    val tamanoBytes: Long = 0,
    val notas: String = "",
    val canal: String? = null,
    val minSdk: Int? = null,
) {
    fun aDominio() = AppDelCatalogo(
        id = id,
        nombre = nombre.ifBlank { id },
        descripcion = descripcion,
        iconoUrl = iconoUrl,
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = apkUrl,
        sha256 = sha256,
        firmaSha256 = firmaSha256,
        tamanoBytes = tamanoBytes,
        notas = notas,
        canal = canal,
        minSdk = minSdk,
    )
}
