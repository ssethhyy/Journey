package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.PatternStop
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.model.TripProgress
import com.ssethhyy.journey.data.model.TripWindowItem
import com.ssethhyy.journey.data.model.VehiclePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TripProgressTest {
    // Stops roughly 1 km apart heading east along one latitude.
    private val stops = List(6) { -37.80 to 144.90 + it * 0.0113 }

    @Test
    fun locatesAVehicleBetweenStops() {
        val progress = TripProgress.locate(stops, -37.80, 144.90 + 2.5 * 0.0113)!!
        assertEquals(2, progress.afterIndex)
        assertEquals(0.5, progress.fraction, 0.02)
        assertNull("midway is not at a stop", progress.atIndex)
        assertTrue(TripProgress.isOnRoute(progress))
    }

    @Test
    fun recognisesAVehicleAtAStop() {
        val progress = TripProgress.locate(stops, -37.80003, 144.90 + 3 * 0.0113 + 0.0002)!!
        assertEquals(3, progress.atIndex)
        assertEquals(3, progress.anchorIndex)
    }

    @Test
    fun flagsAVehicleFarFromTheRoute() {
        val progress = TripProgress.locate(stops, -37.70, 144.93)!!
        assertFalse(TripProgress.isOnRoute(progress))
        assertNull(TripProgress.locate(listOf(-37.8 to 144.9), -37.8, 144.9))
    }

    @Test
    fun clampsBeforeTheFirstAndAfterTheLastStop() {
        assertEquals(0, TripProgress.locate(stops, -37.80, 144.80)!!.afterIndex)
        val end = TripProgress.locate(stops, -37.80, 145.20)!!
        assertEquals(stops.lastIndex - 1, end.afterIndex)
        assertEquals(1.0, end.fraction, 0.001)
    }

    @Test
    fun usesTheGeopathThatFollowsTheStopsAndFallsBack() {
        val onRoute = stops.flatMapIndexed { i, s -> listOf(s, s.first to s.second + 0.005) }
        val otherBranch = List(10) { -37.90 to 144.90 + it * 0.01 }
        assertEquals(onRoute, TripProgress.bestPath(stops, listOf(otherBranch, onRoute)))
        assertEquals("no match falls back to the stops", stops, TripProgress.bestPath(stops, listOf(otherBranch)))
        assertEquals(stops, TripProgress.bestPath(stops, emptyList()))
    }

    @Test
    fun shortensTheStopList() {
        // 30 stops, you board at 12, the vehicle is between 9 and 10.
        val window = TripProgress.window(30, fromIndex = 12, vehicleIndex = 9, expanded = false)
        assertEquals(TripWindowItem.Collapsed(0, 8), window.first())
        assertEquals(listOf(8, 9, 10, 11, 12, 13, 14, 15, 16), window.filterIsInstance<TripWindowItem.Stop>().dropLast(1).map { it.index })
        assertEquals(TripWindowItem.Collapsed(17, 12), window[window.size - 2])
        assertEquals(TripWindowItem.Stop(29), window.last())
        assertTrue("far fewer rows than stops", window.size < 15)
    }

    @Test
    fun windowEdgeCases() {
        assertTrue(TripProgress.window(0, 0, null, false).isEmpty())
        assertEquals(6, TripProgress.window(6, 2, null, false).size)
        assertEquals(30, TripProgress.window(30, 12, 9, expanded = true).size)
        // A single hidden stop is shown rather than collapsed into "1 stop".
        val window = TripProgress.window(14, fromIndex = 2, vehicleIndex = null, expanded = false)
        assertTrue(window.none { it is TripWindowItem.Collapsed && it.count == 1 })
        assertEquals(TripWindowItem.Stop(13), window.last())
    }

    @Test
    fun vehiclePositionsGoStale() {
        val now = Instant.parse("2026-10-23T08:00:00Z")
        fun position(recorded: Instant?, expires: Instant?) = VehiclePosition(-37.8, 144.9, null, recorded, expires)
        assertTrue(position(now.minusSeconds(30), null).isFresh(now))
        assertFalse(position(now.minusSeconds(600), null).isFresh(now))
        assertTrue("expiry wins when given", position(now.minusSeconds(600), now.plusSeconds(60)).isFresh(now))
        assertFalse(position(now, now.minusSeconds(1)).isFresh(now))
        assertTrue(position(null, null).isFresh(now))
    }

    @Test
    fun delayIsPredictedMinusScheduled() {
        val stop = Stop(1, TransportMode.TRAIN, "A", null, -37.8, 144.9)
        val scheduled = Instant.parse("2026-10-23T08:00:00Z")
        assertEquals(120L, PatternStop(stop, scheduled, scheduled.plusSeconds(120), null, 0).delaySeconds)
        assertEquals(-60L, PatternStop(stop, scheduled, scheduled.minusSeconds(60), null, 0).delaySeconds)
        assertNull(PatternStop(stop, scheduled, null, null, 0).delaySeconds)
    }
}
