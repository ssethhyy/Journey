/*
 * Feature Module: Theme
 * File: Color.kt
 * Description: Fallback brand palette (used without dynamic colour) and transport mode colours.
 */

package com.ssethhyy.journey.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ssethhyy.journey.data.model.TransportMode

// Tonal palette generated around a PTV-inspired blue seed (#0072CE).
val LightColors = lightColorScheme(
    primary = Color(0xFF285EA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF3B6939),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCF0B4),
    onTertiaryContainer = Color(0xFF002204),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    inversePrimary = Color(0xFFA9C7FF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003064),
    primaryContainer = Color(0xFF00468D),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFA1D39A),
    onTertiary = Color(0xFF0A390F),
    tertiaryContainer = Color(0xFF235024),
    onTertiaryContainer = Color(0xFFBCF0B4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    inversePrimary = Color(0xFF285EA8),
)

/**
 * Brand colours for each transport mode. These intentionally stay constant across themes so
 * pictograms and route badges remain recognisable, matching Victorian network signage.
 */
object ModeColors {
    val Train = Color(0xFF0072CE)
    val Tram = Color(0xFF78BE20)
    val Bus = Color(0xFFFF8200)
    val VLine = Color(0xFF8F1A95)
    val NightBus = Color(0xFF1B3A6B)

    fun of(mode: TransportMode): Color = when (mode) {
        TransportMode.TRAIN -> Train
        TransportMode.TRAM -> Tram
        TransportMode.BUS -> Bus
        TransportMode.VLINE -> VLine
        TransportMode.NIGHT_BUS -> NightBus
    }

    /** Content colour with sufficient contrast on [of]. Tram green and bus orange need dark text. */
    fun onColor(mode: TransportMode): Color = when (mode) {
        TransportMode.TRAM, TransportMode.BUS -> Color(0xFF1A1A1A)
        else -> Color.White
    }
}

val TransportMode.color: Color get() = ModeColors.of(this)
val TransportMode.onColor: Color get() = ModeColors.onColor(this)
