/*
 * Feature Module: Map
 * File: MapControls.kt
 * Description: Lays out the map's control pills (Nearby, Roll a stop, My location, ...).
 *
 * The pills read left to right and sit to the left, so a hidden pill leaves no gap. When they don't all fit on one
 * line they wrap, never scroll: the spacing tightens first, then the overflow goes onto a new row placed *above* the
 * first, so the row nearest the panel below stays put and new rows grow upward into the map.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Packs [content] left to right; rows that don't fit stack upward.
 *
 * @param spacing gap between pills (and between rows) when everything fits on one line.
 * @param tightSpacing the gap used once the pills need more than one line.
 */
@Composable
fun MapControlFlow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    tightSpacing: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxWidth)) }
        val wide = spacing.roundToPx()
        val tight = tightSpacing.roundToPx()
        val rows = MapControlRows.pack(placeables.map { it.width }, maxWidth, wide, tight)
        val gap = if (rows.size > 1) tight else wide
        val rowHeights = rows.map { row -> row.maxOf { placeables[it].height } }
        val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else rows.maxOfOrNull { row -> row.sumOf { placeables[it].width } + gap * (row.size - 1) } ?: 0
        layout(width, height.coerceAtLeast(constraints.minHeight)) {
            // Row 0 is the bottom row; each later row goes above the one before.
            var bottom = height
            rows.forEachIndexed { r, row ->
                val top = bottom - rowHeights[r]
                var x = 0
                row.forEach { i ->
                    val p = placeables[i]
                    p.placeRelative(x, top + (rowHeights[r] - p.height) / 2)
                    x += p.width + gap
                }
                bottom = top - gap
            }
        }
    }
}

/** The packing itself, kept apart so it can be tested. */
object MapControlRows {
    /**
     * Splits items of the given [widths] into rows that fit [maxWidth], bottom row first. Uses [spacing] when one row
     * holds them all, otherwise [tightSpacing]. An item wider than the line gets a row of its own.
     */
    fun pack(widths: List<Int>, maxWidth: Int, spacing: Int, tightSpacing: Int): List<List<Int>> {
        if (widths.isEmpty()) return emptyList()
        val oneLine = widths.sum() + spacing * (widths.size - 1)
        if (oneLine <= maxWidth) return listOf(widths.indices.toList())
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0
        widths.forEachIndexed { i, w ->
            val row = rows.lastOrNull()
            if (row != null && used + tightSpacing + w <= maxWidth) {
                row += i
                used += tightSpacing + w
            } else {
                rows += mutableListOf(i)
                used = w
            }
        }
        return rows
    }
}
