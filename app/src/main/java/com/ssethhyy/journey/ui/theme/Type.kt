/*
 * Feature Module: Theme
 * File: Type.kt
 * Description: Material 3 Expressive typography on the Google Sans Flex variable font (res/font/google_sans_flex.ttf).
 *
 * Every style is fully rounded (ROND 100). Each role gets its own optical size and width: big display and
 * headline text is set wider and sized for its optical size, titles a touch wide, body text at its natural width
 * for reading, labels slightly wide so short button and chip text has presence. The emphasized styles (Material 3
 * Expressive's second type scale) go heavier and wider again, and the largest ones lean a little using the slant
 * axis, for hero moments like the greeting and "Leave now".
 */

package com.ssethhyy.journey.ui.theme

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.ssethhyy.journey.R

/** How one role of the type scale sets the font's axes. */
data class FlexAxes(
    /** Optical size: small sizes get sturdier shapes and more space, large ones finer detail. */
    val opticalSize: Float,
    /** 100 is normal; above stretches the letters wider. */
    val width: Float,
    /** 0 is upright; negative leans forward (the font goes to -10). */
    val slant: Float = 0f,
    val roundness: Float = 100f,
)

object FlexType {
    val Display = FlexAxes(opticalSize = 48f, width = 112f)
    val DisplayEmphasized = FlexAxes(opticalSize = 48f, width = 122f, slant = -6f)
    val Headline = FlexAxes(opticalSize = 30f, width = 108f)
    val HeadlineEmphasized = FlexAxes(opticalSize = 30f, width = 118f, slant = -3f)
    val Title = FlexAxes(opticalSize = 20f, width = 103f)
    val TitleEmphasized = FlexAxes(opticalSize = 20f, width = 110f)
    val Body = FlexAxes(opticalSize = 14f, width = 100f)
    val BodyEmphasized = FlexAxes(opticalSize = 14f, width = 102f)
    val Label = FlexAxes(opticalSize = 12f, width = 104f)
    val LabelEmphasized = FlexAxes(opticalSize = 12f, width = 110f)

    private val WEIGHTS = listOf(400, 500, 600, 700, 800)

    /** A family with these axes at each weight, so `fontWeight` on a style or Text still picks a real instance. */
    fun family(axes: FlexAxes): FontFamily = FontFamily(
        WEIGHTS.map { w ->
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.width(axes.width),
                    FontVariation.slant(axes.slant),
                    FontVariation.Setting("opsz", axes.opticalSize),
                    FontVariation.Setting("ROND", axes.roundness),
                ),
            )
        },
    )
}

/**
 * One exact instance of the font, for hero text that needs a weight or width outside the type scale (the For You
 * temperature is thin and narrow). Use it with `fontWeight = FontWeight(weight)`.
 */
fun FlexType.single(axes: FlexAxes, weight: Int): FontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(axes.width),
            FontVariation.slant(axes.slant),
            FontVariation.Setting("opsz", axes.opticalSize),
            FontVariation.Setting("ROND", axes.roundness),
        ),
    ),
)

/** Builds the app typography on the Google Sans Flex variable font. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("UNUSED_PARAMETER")
fun buildTypography(context: Context): Typography {
    val base = Typography()
    val display = FlexType.family(FlexType.Display)
    val displayEm = FlexType.family(FlexType.DisplayEmphasized)
    val headline = FlexType.family(FlexType.Headline)
    val headlineEm = FlexType.family(FlexType.HeadlineEmphasized)
    val title = FlexType.family(FlexType.Title)
    val titleEm = FlexType.family(FlexType.TitleEmphasized)
    val body = FlexType.family(FlexType.Body)
    val bodyEm = FlexType.family(FlexType.BodyEmphasized)
    val label = FlexType.family(FlexType.Label)
    val labelEm = FlexType.family(FlexType.LabelEmphasized)

    fun TextStyle.set(family: FontFamily, weight: Int, tracking: Float? = null) =
        copy(fontFamily = family, fontWeight = FontWeight(weight), letterSpacing = tracking?.em ?: letterSpacing)

    return Typography(
        // Wider letters need a little less tracking at large sizes.
        displayLarge = base.displayLarge.set(display, 500, -0.02f),
        displayMedium = base.displayMedium.set(display, 500, -0.015f),
        displaySmall = base.displaySmall.set(display, 500, -0.01f),
        headlineLarge = base.headlineLarge.set(headline, 600, -0.01f),
        headlineMedium = base.headlineMedium.set(headline, 600),
        headlineSmall = base.headlineSmall.set(headline, 600),
        titleLarge = base.titleLarge.set(title, 600),
        titleMedium = base.titleMedium.set(title, 600),
        titleSmall = base.titleSmall.set(title, 500),
        bodyLarge = base.bodyLarge.set(body, 400),
        bodyMedium = base.bodyMedium.set(body, 400),
        bodySmall = base.bodySmall.set(body, 400),
        labelLarge = base.labelLarge.set(label, 600),
        labelMedium = base.labelMedium.set(label, 600),
        labelSmall = base.labelSmall.set(label, 500),

        displayLargeEmphasized = base.displayLarge.set(displayEm, 700, -0.025f),
        displayMediumEmphasized = base.displayMedium.set(displayEm, 700, -0.02f),
        displaySmallEmphasized = base.displaySmall.set(displayEm, 700, -0.015f),
        headlineLargeEmphasized = base.headlineLarge.set(headlineEm, 700, -0.01f),
        headlineMediumEmphasized = base.headlineMedium.set(headlineEm, 700),
        headlineSmallEmphasized = base.headlineSmall.set(headlineEm, 700),
        titleLargeEmphasized = base.titleLarge.set(titleEm, 700),
        titleMediumEmphasized = base.titleMedium.set(titleEm, 700),
        titleSmallEmphasized = base.titleSmall.set(titleEm, 700),
        bodyLargeEmphasized = base.bodyLarge.set(bodyEm, 500),
        bodyMediumEmphasized = base.bodyMedium.set(bodyEm, 500),
        bodySmallEmphasized = base.bodySmall.set(bodyEm, 500),
        labelLargeEmphasized = base.labelLarge.set(labelEm, 700),
        labelMediumEmphasized = base.labelMedium.set(labelEm, 700),
        labelSmallEmphasized = base.labelSmall.set(labelEm, 700),
    )
}
