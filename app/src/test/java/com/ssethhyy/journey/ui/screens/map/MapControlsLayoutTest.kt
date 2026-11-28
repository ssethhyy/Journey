package com.ssethhyy.journey.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapControlsLayoutTest {
    @Test
    fun pillsThatFitStayOnOneRow() {
        assertEquals(listOf(listOf(0, 1, 2)), MapControlRows.pack(listOf(100, 120, 110), 400, 8, 6))
    }

    @Test
    fun overflowWrapsWithTighterSpacingBottomRowFirst() {
        // 100 + 120 + 110 + 2*8 = 346 > 340, so it wraps; with 6 px gaps the first two share the bottom row.
        assertEquals(listOf(listOf(0, 1), listOf(2)), MapControlRows.pack(listOf(100, 120, 110), 340, 8, 6))
    }

    @Test
    fun tighterSpacingCanSaveARow() {
        // 3 * 100 + 2 * 8 = 316 > 310 but 3 * 100 + 2 * 5 = 310 fits only after wrapping is considered row by row.
        assertEquals(listOf(listOf(0, 1, 2)), MapControlRows.pack(listOf(100, 100, 100), 316, 8, 5))
        assertEquals(listOf(listOf(0, 1, 2)), MapControlRows.pack(listOf(100, 100, 100), 310, 8, 5))
    }

    @Test
    fun aPillWiderThanTheLineGetsItsOwnRow() {
        assertEquals(listOf(listOf(0), listOf(1)), MapControlRows.pack(listOf(500, 100), 300, 8, 6))
        assertEquals(emptyList<List<Int>>(), MapControlRows.pack(emptyList(), 300, 8, 6))
    }

    @Test
    fun sheetSettlesToTheNearestStepOrOnWithAFlick() {
        val anchors = listOf(0f, 300f, 900f)
        assertEquals(300f, MapSheetSettle.target(350f, 0f, anchors))
        assertEquals(900f, MapSheetSettle.target(350f, -2_000f, anchors))
        assertEquals(0f, MapSheetSettle.target(250f, 2_000f, anchors))
        assertEquals(900f, MapSheetSettle.target(900f, -2_000f, anchors))
        assertEquals(0.5f, MapSheetSettle.fullProgress(600f, 300f, 900f))
        assertEquals(0f, MapSheetSettle.fullProgress(600f, 300f, 300f))
    }
}
