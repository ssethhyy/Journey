/*
 * Feature Module: myki
 * File: MykiCardArt.kt
 * Description: The myki card face. Uses your artwork from res/drawable-nodpi when present
 * (myki_card_art, or myki_card_art_<fare type> for a specific fare type), otherwise draws a
 * stand-in with the same layout: polygon pattern, wordmark top right, mode badges bottom left.
 */

package com.ssethhyy.journey.ui.screens.myki

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.ui.components.Sym
import kotlin.random.Random

/** ISO/IEC 7810 ID-1: 85.60 × 53.98 mm. */
const val MYKI_CARD_RATIO = 85.60f / 53.98f

private val CardGrey = Color(0xFF3B3C3E)
private val LineColour = Color(0x2EFFFFFF)
private val MykiGreen = Color(0xFFC6D82E)

/** The art for [fareType] (0 = none) and whether it is fare-specific (so it already names the fare type). */
@SuppressLint("DiscouragedApi")
private fun artRes(context: Context, fareType: MykiFareType): Pair<Int, Boolean> {
    val res = context.resources
    fun find(name: String) = res.getIdentifier(name, "drawable", context.packageName)
    val specific = if (fareType == MykiFareType.FULL) 0 else find("myki_card_art_${fareType.name.lowercase()}")
    return if (specific != 0) specific to true else find("myki_card_art") to false
}

@Composable
fun MykiCardArt(card: MykiCard, modifier: Modifier = Modifier, cornerRadius: androidx.compose.ui.unit.Dp = 20.dp) {
    val context = LocalContext.current
    val (art, artNamesFare) = remember(card.fareType) { artRes(context, card.fareType) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(MYKI_CARD_RATIO)
            .clip(RoundedCornerShape(cornerRadius))
            .background(CardGrey)
            .semantics { contentDescription = "${card.nickname} myki, ${card.fareType.label}" },
    ) {
        if (art != 0) {
            Image(painterResource(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            StandInArt(card.number)
        }
        if (card.fareType != MykiFareType.FULL && !artNamesFare) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            ) {
                Text(card.fareType.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun StandInArt(seedText: String) {
    // A stable low-poly network, seeded by the card number so each card looks a little different.
    val points = remember(seedText) {
        val random = Random(seedText.hashCode())
        val cols = 7
        val rows = 5
        List(rows) { r ->
            List(cols) { c ->
                Offset(
                    ((c + random.nextFloat() * 0.7f - 0.35f) / (cols - 1)).coerceIn(-0.05f, 1.05f),
                    ((r + random.nextFloat() * 0.7f - 0.35f) / (rows - 1)).coerceIn(-0.05f, 1.05f),
                )
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        fun p(o: Offset) = Offset(o.x * size.width, o.y * size.height)
        val stroke = 1.dp.toPx()
        points.forEachIndexed { r, row ->
            row.forEachIndexed { c, point ->
                row.getOrNull(c + 1)?.let { drawLine(LineColour, p(point), p(it), stroke) }
                points.getOrNull(r + 1)?.let { next ->
                    drawLine(LineColour, p(point), p(next[c]), stroke)
                    next.getOrNull(c + 1)?.let { drawLine(LineColour, p(point), p(it), stroke) }
                }
            }
        }
        listOf(points[1][0], points[4][3]).forEach { drawCircle(LineColour.copy(alpha = 0.5f), 3.dp.toPx(), p(it)) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth.value / 340f
        Text(
            "myki",
            color = MykiGreen,
            fontSize = (58 * scale).sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2 * scale).sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = (12 * scale).dp, end = (22 * scale).dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = (22 * scale).dp, bottom = (26 * scale).dp),
        ) {
            ModeBadge(Sym.Train, (38 * scale).dp)
            ModeBadge(Sym.Tram, (38 * scale).dp)
            ModeBadge(Sym.DirectionsBus, (38 * scale).dp)
        }
    }
}

@Composable
private fun ModeBadge(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = CardGrey, modifier = Modifier.size(size * 0.62f))
    }
}
