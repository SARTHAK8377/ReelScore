package com.reelscore.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The one accent. Lime is too light for text on the light theme, so there `primary` is a deep olive. */
val Volt = Color(0xFFC8F25A)
val OnVolt = Color(0xFF13180A)

internal val DarkColors = darkColorScheme(
    primary = Volt,
    onPrimary = OnVolt,
    primaryContainer = Volt,
    onPrimaryContainer = OnVolt,
    secondary = Color(0xFFB9BDC4),
    onSecondary = Color(0xFF1A1C20),
    secondaryContainer = Color(0xFF2A2D33),
    onSecondaryContainer = Color(0xFFE2E4E8),
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF212429),
    onSurfaceVariant = Color(0xFFA3A7AE),
    surfaceContainerLowest = Color(0xFF0A0B0C),
    surfaceContainerLow = Color(0xFF15171A),
    surfaceContainer = Color(0xFF1A1C20),
    surfaceContainerHigh = Color(0xFF212429),
    surfaceContainerHighest = Color(0xFF2A2D33),
    outline = Color(0xFF4A4E56),
    outlineVariant = Color(0xFF2C2F35),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF3B0A05),
    errorContainer = Color(0xFF3A1813),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFECEDEF),
    inverseOnSurface = Color(0xFF1A1C20),
    inversePrimary = Color(0xFF4A6600),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF4A6600),
    onPrimary = Color(0xFFF7FFE6),
    primaryContainer = Volt,
    onPrimaryContainer = OnVolt,
    secondary = Color(0xFF50555C),
    onSecondary = Color(0xFFF5F6F4),
    secondaryContainer = Color(0xFFE1E3DF),
    onSecondaryContainer = Color(0xFF1A1C20),
    background = Color(0xFFEDEEEB),
    onBackground = Color(0xFF121315),
    surface = Color(0xFFEDEEEB),
    onSurface = Color(0xFF121315),
    surfaceVariant = Color(0xFFE4E6E2),
    onSurfaceVariant = Color(0xFF565B62),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF3F4F1),
    surfaceContainerHigh = Color(0xFFE9EBE8),
    surfaceContainerHighest = Color(0xFFE3E5E2),
    outline = Color(0xFF878C93),
    outlineVariant = Color(0xFFD3D6D1),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE3DE),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF1A1C20),
    inverseOnSurface = Color(0xFFECEDEF),
    inversePrimary = Volt,
)

/** Colors outside Material's roles. The nav bar stays graphite in both themes so the lime pill always reads. */
@Immutable
data class BrandColors(
    val navBar: Color,
    val navContent: Color,
    val navIndicator: Color,
    val onNavIndicator: Color,
)

internal val DarkBrand = BrandColors(
    navBar = Color(0xFF1F2126),
    navContent = Color(0xFFA3A7AE),
    navIndicator = Volt,
    onNavIndicator = OnVolt,
)

internal val LightBrand = BrandColors(
    navBar = Color(0xFF17191C),
    navContent = Color(0xFFA3A7AE),
    navIndicator = Volt,
    onNavIndicator = OnVolt,
)

val LocalBrandColors = staticCompositionLocalOf { DarkBrand }
