/*
 * Feature Module: myki
 * File: MykiCardArt.kt
 * Description: The myki card face, drawn from Open Transit's myki artwork (the line pattern, the myki wordmark and
 * the PTV logo, by Aston Bolwell: https://github.com/AstonBolwell/Open-Transit-App).
 *
 * With colours from the wallpaper, the card sits just a step lighter than the page, with the pattern, logos and text in
 * the matching colours and the wordmark in the accent. Without, it's the familiar myki look: charcoal with the green
 * wordmark.
 */

package com.ssethhyy.journey.ui.screens.myki

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssethhyy.journey.R
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.MykiNumber
import com.ssethhyy.journey.ui.theme.LocalWallpaperColours

/** ISO/IEC 7810 ID-1: 85.60 × 53.98 mm. */
const val MYKI_CARD_RATIO = 85.60f / 53.98f

/** The colours of a myki card face, and of anything drawn to match it (like the NFC animation's card). */
@Immutable
data class MykiCardColours(
    val card: Color,
    val pattern: Color,
    val wordmark: Color,
    val logo: Color,
    val text: Color,
) {
    companion object {
        /** The real card: charcoal, a faint white pattern and the green wordmark. */
        val Myki = MykiCardColours(
            card = Color(0xFF333434),
            pattern = Color.White.copy(alpha = 0.2f),
            wordmark = Color(0xFFC2D840),
            logo = Color.White.copy(alpha = 0.9f),
            text = Color.White,
        )

        /** Wallpaper colours when they're in use, otherwise [Myki]. */
        @Composable
        fun current(): MykiCardColours {
            if (!LocalWallpaperColours.current) return Myki
            val scheme = MaterialTheme.colorScheme
            // Just a step up from the page behind it, so it reads as a card without looking tinted.
            return MykiCardColours(
                card = scheme.surfaceContainer,
                pattern = scheme.onSurface.copy(alpha = 0.10f),
                wordmark = scheme.primary,
                logo = scheme.onSurfaceVariant,
                text = scheme.onSurface,
            )
        }
    }
}

@Composable
fun MykiCardArt(card: MykiCard, modifier: Modifier = Modifier, cornerRadius: Dp = 20.dp) {
    MykiCardFace(
        number = card.number,
        fareLabel = card.fareType.takeIf { it != MykiFareType.FULL }?.label,
        modifier = modifier.semantics { contentDescription = "${card.nickname} myki, ${card.fareType.label}" },
        cornerRadius = cornerRadius,
    )
}

/**
 * The card face on its own: Open Transit's layout, with the pattern across the card, the PTV logo top right, the myki
 * wordmark bottom left and the card number in the middle.
 */
@Composable
fun MykiCardFace(
    number: String?,
    modifier: Modifier = Modifier,
    fareLabel: String? = null,
    cornerRadius: Dp = 20.dp,
    colours: MykiCardColours = MykiCardColours.current(),
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(MYKI_CARD_RATIO)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colours.card),
    ) {
        val w = maxWidth
        val h = maxHeight
        Image(
            painterResource(R.drawable.myki_card_pattern),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(colours.pattern),
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painterResource(R.drawable.myki_ptv_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colours.logo),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = h * 0.08f, end = w * 0.06f).width(w * 0.17f),
        )
        Image(
            painterResource(R.drawable.myki_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colours.wordmark),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = w * 0.06f, bottom = h * 0.08f).width(w * 0.27f),
        )
        if (!number.isNullOrBlank()) {
            Text(
                MykiNumber.format(number),
                color = colours.text,
                fontSize = (h.value * 0.075f).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (h.value * 0.006f).sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (fareLabel != null) {
            Surface(
                shape = CircleShape,
                color = colours.text.copy(alpha = 0.14f),
                contentColor = colours.text,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = w * 0.05f, bottom = h * 0.08f),
            ) {
                Text(fareLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}
