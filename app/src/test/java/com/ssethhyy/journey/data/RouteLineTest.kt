package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.RouteLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLineTest {
    // A route bending east then north, with points every ~1 km.
    private val shape = (0..10).map { -37.85 + 0.0 to 144.90 + it * 0.012 } + (1..10).map { -37.85 + it * 0.009 to 145.02 }
    private val stops = listOf(shape[2], shape[8], shape[14])

    @Test
    fun theMatchingShapeIsCutToTheFirstAndLastStop() {
        val line = RouteLine.alongStops(stops, listOf(listOf(-38.5 to 146.0, -38.6 to 146.1), shape))!!
        assertEquals(stops.first(), line.first())
        assertEquals(stops.last(), line.last())
        // It follows the shape round the corner rather than cutting straight across.
        assertTrue(line.contains(shape[10]))
    }

    @Test
    fun aServiceRunningTheOtherWayGetsTheShapeReversed() {
        val line = RouteLine.alongStops(stops.reversed(), listOf(shape))!!
        assertEquals(stops.last(), line.first())
        assertEquals(stops.first(), line.last())
    }

    /** PTV sends a line's shape in pieces, out of order; the service's line still follows all of it. */
    @Test
    fun piecesSentOutOfOrderAreStitchedTogether() {
        val pieces = listOf(shape.subList(11, shape.size), shape.subList(0, 11))
        val line = RouteLine.alongStops(stops, pieces)!!
        assertEquals(stops.first(), line.first())
        assertEquals(stops.last(), line.last())
        assertTrue(line.contains(shape[10]))
        assertTrue(line.contains(shape[11]))
    }

    @Test
    fun noShapeNearbyMeansACurveThroughTheStops() {
        assertNull(RouteLine.alongStops(stops, listOf(listOf(-38.5 to 146.0, -38.6 to 146.1))))
        val curve = RouteLine.forStops(stops, emptyList())
        assertEquals(stops.first(), curve.first())
        assertEquals(stops.last(), curve.last())
        assertTrue(curve.size > stops.size)
    }

    @Test
    fun smoothingKeepsTheEnds() {
        val smoothed = RouteLine.smooth(shape)
        assertEquals(shape.first(), smoothed.first())
        assertEquals(shape.last(), smoothed.last())
        assertTrue(smoothed.size > shape.size)
    }
}
