package com.marcmayol.dracapps.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.puertos.AppsInstaladas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Qué hay instalado en este móvil, según el PackageManager.
 *
 * De aquí sale la firma con la que se decide si una app es de la tienda o está
 * instalada por fuera, así que se lee el certificado de verdad, no el nombre del
 * paquete.
 */
class AppsDelMovil(private val contexto: Context) : AppsInstaladas {

    private val pm: PackageManager get() = contexto.packageManager

    override suspend fun buscar(id: String): AppInstalada? = withContext(Dispatchers.IO) {
        leer(id)
    }

    override suspend fun todas(ids: Collection<String>): Map<String, AppInstalada> =
        withContext(Dispatchers.IO) {
            ids.mapNotNull { leer(it) }.associateBy { it.id }
        }

    private fun leer(id: String): AppInstalada? = try {
        val info = infoDe(id)
        AppInstalada(
            id = id,
            versionCode = versionCodeDe(info),
            versionName = info.versionName.orEmpty(),
            firmaSha256 = firmaDe(info),
            instaladaPor = instaladorDe(id),
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun infoDe(id: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                id,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(id, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun versionCodeDe(info: PackageInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }

    /**
     * Huella SHA-256 del certificado, en el mismo formato que publica el catálogo
     * (el que imprime `apksigner verify --print-certs`).
     *
     * Con varios firmantes se toma el primero, igual que hace el generador.
     */
    private fun firmaDe(info: PackageInfo): String {
        val firmas = info.signingInfo?.let { firmante ->
            if (firmante.hasMultipleSigners()) {
                firmante.apkContentsSigners
            } else {
                firmante.signingCertificateHistory
            }
        }.orEmpty()

        val primera = firmas.firstOrNull() ?: return ""
        val resumen = MessageDigest.getInstance("SHA-256").digest(primera.toByteArray())
        return resumen.joinToString("") { "%02x".format(it) }
    }

    private fun instaladorDe(id: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(id).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(id)
        }
    } catch (_: Exception) {
        // Sin instalador conocido: lo decide la firma. Es el caso de las apps
        // preinstaladas y de las restauradas desde una copia de seguridad.
        null
    }
}
