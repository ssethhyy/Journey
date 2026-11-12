package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.location.LatLng
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.GeoPoint
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.tracking.RingMode
import com.ssethhyy.journey.tracking.StopAlertLogic
import com.ssethhyy.journey.tracking.StopAlertVibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StopAlertsTest {
    private val t0 = Instant.parse("2026-11-12T08:00:00Z")
    private val board = GeoPoint(-37.7790, 145.0000)
    private val alight = GeoPoint(-37.7300, 145.0400) // several km away

    private fun leg(kind: LegKind, mode: TransportMode?, startMin: Long, endMin: Long, path: List<GeoPoint>, to: String) = JourneyLeg(
        kind = kind, mode = mode, rawMode = "X", fromName = "From", toName = to, start = t0.plusSeconds(startMin * 60), end = t0.plusSeconds(endMin * 60),
        scheduledStart = null, scheduledEnd = null, realTime = false, headsign = null, routeShortName = "Hurstbridge", routeLongName = null,
        routeColor = null, routeTextColor = null, distanceMeters = null, platform = null, arrivalPlatform = "2", cancelled = false,
        intermediateStops = emptyList(), path = path,
    )

    private val journey = Journey(
        "j", t0, t0.plusSeconds(30 * 60), 0,
        listOf(
            leg(LegKind.WALK, null, 0, 5, listOf(GeoPoint(-37.78, 144.99), board), "Westgarth Station"),
            leg(LegKind.TRANSIT, TransportMode.TRAIN, 5, 25, listOf(board, alight), "Macleod Station"),
            leg(LegKind.WALK, null, 25, 30, listOf(alight, GeoPoint(-37.729, 145.041)), ""),
        ),
    )

    @Test
    fun noAlertBeforeOrEarlyInTheRide() {
        assertNull(StopAlertLogic.due(journey, null, t0, emptySet()))
        assertNull(StopAlertLogic.due(journey, LatLng(board.latitude, board.longitude), t0.plusSeconds(10 * 60), emptySet()))
    }

    @Test
    fun alertsWhenTheTrainIsAboutToArrive() {
        val alert = StopAlertLogic.due(journey, null, t0.plusSeconds(25 * 60 - 90), emptySet())
        assertNotNull(alert)
        assertEquals("Macleod Station", alert!!.stopName)
        assertEquals("Get ready, your stop is next", alert.instruction)
        assertTrue(alert.detail.contains("Platform 2"))
    }

    @Test
    fun alertsEarlyWhenYouAreAlreadyNearTheStop() {
        val nearStop = LatLng(-37.7330, 145.0390) // about 400 m away
        assertNotNull(StopAlertLogic.due(journey, nearStop, t0.plusSeconds(15 * 60), emptySet()))
    }

    @Test
    fun alertsOnlyOncePerRide() {
        val first = StopAlertLogic.due(journey, null, t0.plusSeconds(24 * 60), emptySet())!!
        assertNull(StopAlertLogic.due(journey, null, t0.plusSeconds(24 * 60 + 30), setOf(first.key)))
        assertNull("too late after arriving", StopAlertLogic.due(journey, null, t0.plusSeconds(30 * 60), emptySet()))
    }

    @Test
    fun busesAndTramsSayPressTheStopButton() {
        assertEquals("Press the stop button", StopAlertLogic.instruction(TransportMode.TRAM))
        assertEquals("Press the stop button", StopAlertLogic.instruction(TransportMode.BUS))
    }

    @Test
    fun vibratesOnlyWhenAllowedAndNotSilent() {
        assertTrue(StopAlertVibration.shouldVibrate(true, RingMode.NORMAL))
        assertTrue(StopAlertVibration.shouldVibrate(true, RingMode.VIBRATE))
        assertFalse(StopAlertVibration.shouldVibrate(true, RingMode.SILENT))
        assertFalse(StopAlertVibration.shouldVibrate(false, RingMode.NORMAL))
    }
}
