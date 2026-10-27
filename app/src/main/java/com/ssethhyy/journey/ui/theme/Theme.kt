/*
 * Feature Module: Theme
 * File: Theme.kt
 * Description: Material 3 Expressive theme with dynamic colour and Pitch Black (AMOLED) support.
 */

package com.ssethhyy.journey.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ssethhyy.journey.data.repository.ThemeMode

val JourneyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp), // Signature rounded look
)

/** Forces surfaces to (near) pure black while keeping the dynamic accent colours. */
fun ColorScheme.pitchBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    surfaceBright = Color(0xFF161616),
    surfaceVariant = Color(0xFF121212),
)

/**
 * In light schemes `surfaceBright` equals `surface`, so bright list rows would vanish into the page.
 * Tint the page slightly and lift rows to pure white so grouped cards stay legible.
 */
fun ColorScheme.lightSurfaceLift(): ColorScheme = copy(
    background = surfaceContainerLow,
    surface = surfaceContainerLow,
    surfaceBright = surfaceContainerLowest,
)

@Composable
fun isAppInDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JourneyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pitchBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = when {
        darkTheme && pitchBlack -> baseScheme.pitchBlack()
        darkTheme -> baseScheme
        else -> baseScheme.lightSurfaceLift()
    }
    val typography = remember(context) { buildTypography(context) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = JourneyShapes,
        typography = typography,
        content = content,
    )
}
