/*
 * Feature Module: Theme
 * File: Type.kt
 * Description: Typography built on the Google Sans Flex variable font (res/font/google_sans_flex.ttf).
 */

package com.ssethhyy.journey.ui.theme

import com.ssethhyy.journey.R
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Builds the app typography on the Google Sans Flex variable font. */
@Suppress("UNUSED_PARAMETER")
fun buildTypography(context: Context): Typography {
    val res = R.font.google_sans_flex
    val base = Typography()

    fun weighted(weight: FontWeight, rounded: Boolean = false) = Font(
        resId = res,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.Setting("ROND", if (rounded) 100f else 0f),
        ),
    )

    val text = FontFamily(
        weighted(FontWeight.Normal),
        weighted(FontWeight.Medium),
        weighted(FontWeight.SemiBold),
        weighted(FontWeight.Bold),
    )
    // Rounded variant (ROND axis) for display/headline styles, as in the Essentials design language.
    val rounded = FontFamily(
        weighted(FontWeight.Normal, rounded = true),
        weighted(FontWeight.Medium, rounded = true),
        weighted(FontWeight.SemiBold, rounded = true),
        weighted(FontWeight.Bold, rounded = true),
    )

    fun TextStyle.with(family: FontFamily) = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.with(rounded),
        displayMedium = base.displayMedium.with(rounded),
        displaySmall = base.displaySmall.with(rounded),
        headlineLarge = base.headlineLarge.with(rounded),
        headlineMedium = base.headlineMedium.with(rounded),
        headlineSmall = base.headlineSmall.with(rounded),
        titleLarge = base.titleLarge.with(rounded),
        titleMedium = base.titleMedium.with(text),
        titleSmall = base.titleSmall.with(text),
        bodyLarge = base.bodyLarge.with(text),
        bodyMedium = base.bodyMedium.with(text),
        bodySmall = base.bodySmall.with(text),
        labelLarge = base.labelLarge.with(text),
        labelMedium = base.labelMedium.with(text),
        labelSmall = base.labelSmall.with(text),
    ).emphasise()
}

/** Slightly heavier headlines and titles for a clearer expressive hierarchy. */
private fun Typography.emphasise(): Typography = copy(
    displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
)
