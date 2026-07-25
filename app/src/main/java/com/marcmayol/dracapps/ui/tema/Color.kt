package com.marcmayol.dracapps.ui.tema

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Los colores de DracApps, tal cual los fija el diseño (sección 05 del paquete).
 *
 * Este es el ÚNICO fichero del proyecto donde se escribe un color. Todo lo demás pide
 * roles al tema: `MaterialTheme.colorScheme.primary`, nunca un hexadecimal suelto. Hay
 * un test que falla si alguien se salta la regla, porque en cuanto un color se escapa
 * del tema deja de responder al modo oscuro y el lenguaje de estados se rompe.
 *
 * El esquema salió del color fuente #B98A1F (oro de tesoro) con Tonal Spot, ajustando
 * a mano el terciario hacia la brasa de Ladón (#8F4A38) para que la familia de marca
 * asome en los detalles.
 */

// --- Claro -----------------------------------------------------------------------

private val PrimarioClaro = Color(0xFF7A5900)
private val SobrePrimarioClaro = Color(0xFFFFFFFF)
private val ContenedorPrimarioClaro = Color(0xFFFFDF9E)
private val SobreContenedorPrimarioClaro = Color(0xFF261A00)

private val SecundarioClaro = Color(0xFF6C5C3F)
private val SobreSecundarioClaro = Color(0xFFFFFFFF)
private val ContenedorSecundarioClaro = Color(0xFFF5E0BB)
private val SobreContenedorSecundarioClaro = Color(0xFF241A04)

private val TerciarioClaro = Color(0xFF8F4A38)
private val SobreTerciarioClaro = Color(0xFFFFFFFF)
private val ContenedorTerciarioClaro = Color(0xFFFFDBD1)
private val SobreContenedorTerciarioClaro = Color(0xFF3A0B01)

private val ErrorClaro = Color(0xFFBA1A1A)
private val SobreErrorClaro = Color(0xFFFFFFFF)
private val ContenedorErrorClaro = Color(0xFFFFDAD6)
private val SobreContenedorErrorClaro = Color(0xFF410002)

private val SuperficieClara = Color(0xFFFFF8F0)
private val SobreSuperficieClara = Color(0xFF1E1B13)
private val VarianteSuperficieClara = Color(0xFFEDE1CF)
private val SobreVarianteSuperficieClara = Color(0xFF4D4639)

private val ContenedorMasBajoClaro = Color(0xFFFFFFFF)
private val ContenedorBajoClaro = Color(0xFFFBF3E5)
private val ContenedorClaro = Color(0xFFF5EDDF)
private val ContenedorAltoClaro = Color(0xFFEFE7D9)
private val ContenedorMasAltoClaro = Color(0xFFE9E1D3)

private val ContornoClaro = Color(0xFF7F7667)
private val VarianteContornoClaro = Color(0xFFD0C5B4)
private val SuperficieInversaClara = Color(0xFF33302A)
private val SobreSuperficieInversaClara = Color(0xFFF7EFE2)
private val PrimarioInversoClaro = Color(0xFFF0BE48)

// --- Oscuro ----------------------------------------------------------------------

private val PrimarioOscuro = Color(0xFFF0BE48)
private val SobrePrimarioOscuro = Color(0xFF402D00)
private val ContenedorPrimarioOscuro = Color(0xFF5C4200)
private val SobreContenedorPrimarioOscuro = Color(0xFFFFDF9E)

private val SecundarioOscuro = Color(0xFFD8C4A0)
private val SobreSecundarioOscuro = Color(0xFF3B2E14)
private val ContenedorSecundarioOscuro = Color(0xFF53452A)
private val SobreContenedorSecundarioOscuro = Color(0xFFF5E0BB)

private val TerciarioOscuro = Color(0xFFFFB4A1)
private val SobreTerciarioOscuro = Color(0xFF561F10)
private val ContenedorTerciarioOscuro = Color(0xFF723727)
private val SobreContenedorTerciarioOscuro = Color(0xFFFFDBD1)

private val ErrorOscuro = Color(0xFFFFB4AB)
private val SobreErrorOscuro = Color(0xFF690005)
private val ContenedorErrorOscuro = Color(0xFF93000A)
private val SobreContenedorErrorOscuro = Color(0xFFFFDAD6)

private val SuperficieOscura = Color(0xFF17130B)
private val SobreSuperficieOscura = Color(0xFFECE1D4)
private val VarianteSuperficieOscura = Color(0xFF4D4639)
private val SobreVarianteSuperficieOscura = Color(0xFFD0C5B4)

private val ContenedorMasBajoOscuro = Color(0xFF100C06)
private val ContenedorBajoOscuro = Color(0xFF1F1B12)
private val ContenedorOscuro = Color(0xFF231F16)
private val ContenedorAltoOscuro = Color(0xFF2E2920)
private val ContenedorMasAltoOscuro = Color(0xFF39342A)

private val ContornoOscuro = Color(0xFF999080)
private val VarianteContornoOscuro = Color(0xFF4D4639)
private val SuperficieInversaOscura = Color(0xFFECE1D4)
private val SobreSuperficieInversaOscura = Color(0xFF33302A)
private val PrimarioInversoOscuro = Color(0xFF7A5900)

val EsquemaClaro: ColorScheme = lightColorScheme(
    primary = PrimarioClaro,
    onPrimary = SobrePrimarioClaro,
    primaryContainer = ContenedorPrimarioClaro,
    onPrimaryContainer = SobreContenedorPrimarioClaro,
    inversePrimary = PrimarioInversoClaro,
    secondary = SecundarioClaro,
    onSecondary = SobreSecundarioClaro,
    secondaryContainer = ContenedorSecundarioClaro,
    onSecondaryContainer = SobreContenedorSecundarioClaro,
    tertiary = TerciarioClaro,
    onTertiary = SobreTerciarioClaro,
    tertiaryContainer = ContenedorTerciarioClaro,
    onTertiaryContainer = SobreContenedorTerciarioClaro,
    error = ErrorClaro,
    onError = SobreErrorClaro,
    errorContainer = ContenedorErrorClaro,
    onErrorContainer = SobreContenedorErrorClaro,
    background = SuperficieClara,
    onBackground = SobreSuperficieClara,
    surface = SuperficieClara,
    onSurface = SobreSuperficieClara,
    surfaceVariant = VarianteSuperficieClara,
    onSurfaceVariant = SobreVarianteSuperficieClara,
    surfaceContainerLowest = ContenedorMasBajoClaro,
    surfaceContainerLow = ContenedorBajoClaro,
    surfaceContainer = ContenedorClaro,
    surfaceContainerHigh = ContenedorAltoClaro,
    surfaceContainerHighest = ContenedorMasAltoClaro,
    outline = ContornoClaro,
    outlineVariant = VarianteContornoClaro,
    inverseSurface = SuperficieInversaClara,
    inverseOnSurface = SobreSuperficieInversaClara,
    scrim = Color(0xFF000000),
)

val EsquemaOscuro: ColorScheme = darkColorScheme(
    primary = PrimarioOscuro,
    onPrimary = SobrePrimarioOscuro,
    primaryContainer = ContenedorPrimarioOscuro,
    onPrimaryContainer = SobreContenedorPrimarioOscuro,
    inversePrimary = PrimarioInversoOscuro,
    secondary = SecundarioOscuro,
    onSecondary = SobreSecundarioOscuro,
    secondaryContainer = ContenedorSecundarioOscuro,
    onSecondaryContainer = SobreContenedorSecundarioOscuro,
    tertiary = TerciarioOscuro,
    onTertiary = SobreTerciarioOscuro,
    tertiaryContainer = ContenedorTerciarioOscuro,
    onTertiaryContainer = SobreContenedorTerciarioOscuro,
    error = ErrorOscuro,
    onError = SobreErrorOscuro,
    errorContainer = ContenedorErrorOscuro,
    onErrorContainer = SobreContenedorErrorOscuro,
    background = SuperficieOscura,
    onBackground = SobreSuperficieOscura,
    surface = SuperficieOscura,
    onSurface = SobreSuperficieOscura,
    surfaceVariant = VarianteSuperficieOscura,
    onSurfaceVariant = SobreVarianteSuperficieOscura,
    surfaceContainerLowest = ContenedorMasBajoOscuro,
    surfaceContainerLow = ContenedorBajoOscuro,
    surfaceContainer = ContenedorOscuro,
    surfaceContainerHigh = ContenedorAltoOscuro,
    surfaceContainerHighest = ContenedorMasAltoOscuro,
    outline = ContornoOscuro,
    outlineVariant = VarianteContornoOscuro,
    inverseSurface = SuperficieInversaOscura,
    inverseOnSurface = SobreSuperficieInversaOscura,
    scrim = Color(0xFF000000),
)
