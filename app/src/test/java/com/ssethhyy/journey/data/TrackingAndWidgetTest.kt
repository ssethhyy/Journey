package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.JourneyMapper
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.tracking.TrackingLogic
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.widget.WidgetFormat
import com.ssethhyy.journey.widget.WidgetSnapshotBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrackingAndWidgetTest {
    private val now = Instant.parse("2026-10-18T06:00:00Z")
    private fun at(min: Long) = now.plusSeconds(min * 60)

    private val target = TrackingTarget.Service("955123", TransportMode.TRAIN, 1071, "Westgarth", "Hurstbridge line", "Flinders Street")

    private fun departure(minutes: Long?, scheduledMinutes: Long? = minutes, cancelled: Boolean = false, platform: String? = "1") = Departure(
        stopId = 1071,
        route = Route(8, TransportMode.TRAIN, "Hurstbridge", ""),
        runRef = "955123",
        directionId = 1,
        directionName = "City",
        destination = "Flinders Street",
        scheduled = scheduledMinutes?.let(::at),
        estimated = minutes?.let(::at),
        platform = platform,
        atPlatform = false,
        flags = null,
        disruptionIds = emptyList(),
        isExpress = false,
        isCancelled = cancelled,
        vehicle = null,
    )

    @Test
    fun serviceStatusCountsDownWithPlatformAndDelay() {
        val status = TrackingLogic.serviceStatus(target, departure(minutes = 5, scheduledMinutes = 3), startedAt = at(-5), now = now)
        assertEquals("Hurstbridge line in 5 min", status.title)
        assertEquals("Westgarth · Platform 1 · to Flinders Street · 2 min late", status.detail)
        assertEquals("5 min", status.chip)
        assertTrue(status.isLive)
        assertFalse(status.finished)
        assertEquals(0.5f, status.progress, 0.01f)
    }

    @Test
    fun serviceStatusHandlesDepartingCancelledAndGone() {
        assertEquals("Hurstbridge line is departing now", TrackingLogic.serviceStatus(target, departure(0), now, now).title)
        assertTrue(TrackingLogic.serviceStatus(target, departure(4, cancelled = true), now, now).finished)
        val gone = TrackingLogic.serviceStatus(target, null, now, now)
        assertTrue(gone.finished)
        assertEquals("Hurstbridge line has departed", gone.title)
        assertTrue(TrackingLogic.serviceStatus(target, departure(-3), now, now).finished)
    }

    private fun leg(kind: LegKind, label: String, from: String, to: String, start: Long, end: Long, platform: String? = null) = JourneyLeg(
        kind = kind,
        mode = if (kind == LegKind.TRANSIT) TransportMode.TRAIN else null,
        rawMode = if (kind == LegKind.TRANSIT) "SUBWAY" else "WALK",
        fromName = from,
        toName = to,
        start = at(start),
        end = at(end),
        scheduledStart = at(start),
        scheduledEnd = at(end),
        realTime = kind == LegKind.TRANSIT,
        headsign = if (kind == LegKind.TRANSIT) "Flinders Street" else null,
        routeShortName = if (kind == LegKind.TRANSIT) label else null,
        routeLongName = null,
        routeColor = null,
        routeTextColor = null,
        distanceMeters = 200.0,
        platform = platform,
        arrivalPlatform = null,
        cancelled = false,
        intermediateStops = emptyList(),
        path = emptyList(),
    )

    private val journey = Journey(
        id = "j",
        start = at(0),
        end = at(40),
        transfers = 1,
        legs = listOf(
            leg(LegKind.WALK, "", "", "Westgarth", 0, 4),
            leg(LegKind.TRANSIT, "Hurstbridge", "Westgarth", "Flinders Street", 6, 22, platform = "1"),
            leg(LegKind.WALK, "", "Flinders Street", "Swanston St", 22, 25),
            leg(LegKind.TRANSIT, "16", "Swanston St", "St Kilda", 27, 37),
            leg(LegKind.WALK, "", "St Kilda", "", 37, 40),
        ),
    )

    @Test
    fun journeyStatusFollowsEachPhase() {
        val before = TrackingLogic.journeyStatus(journey, "Beach", at(1))
        assertEquals("Hurstbridge in 5 min", before.title)
        assertTrue(before.detail.startsWith("Westgarth · Platform 1"))

        val onTrain = TrackingLogic.journeyStatus(journey, "Beach", at(10))
        assertEquals("On Hurstbridge · get off at Flinders Street", onTrain.title)

        val changing = TrackingLogic.journeyStatus(journey, "Beach", at(23))
        assertEquals("Walk to Swanston St · 16 in 4 min", changing.title)

        val lastWalk = TrackingLogic.journeyStatus(journey, "Beach", at(38))
        assertEquals("Walk to Beach", lastWalk.title)

        val arrived = TrackingLogic.journeyStatus(journey, "Beach", at(41))
        assertTrue(arrived.finished)
    }

    @Test
    fun countdownFormats() {
        assertEquals("Now", TrackingLogic.countdown(now, now))
        assertEquals("1 hr", TrackingLogic.countdown(at(60), now))
        assertEquals("1 hr 5 min", TrackingLogic.countdown(at(65), now))
        assertEquals("Now", WidgetFormat.countdown(at(0), now))
        assertEquals("1 min", WidgetFormat.countdown(at(1), now))
        assertEquals("22 mins", WidgetFormat.countdown(at(22), now))
        assertEquals("1 hour", WidgetFormat.countdown(at(95), now))
        assertEquals("2 hours", WidgetFormat.countdown(at(130), now))
    }

    @Test
    fun widgetDistanceAndRows() {
        java.util.Locale.setDefault(java.util.Locale.US)
        assertEquals("400m away", WidgetFormat.distance(397.0))
        assertEquals("5.2km away", WidgetFormat.distance(5_234.0))
        val fav = FavouriteStop(1071, 0, "Westgarth Station", suburb = "Northcote", latitude = -37.78, longitude = 144.99, nickname = "Home stop")
        val row = WidgetSnapshotBuilder.row(fav, departure(minutes = 2), distanceMeters = 400.0)
        assertEquals("Home stop", row.name)
        assertEquals("400m away", row.subtitle)
        assertEquals("Flinders Street", row.headsign)
        assertTrue(row.isLive)
        val noLocation = WidgetSnapshotBuilder.row(fav, null, null)
        assertEquals("Northcote", noLocation.subtitle)
        assertNull(noLocation.departureEpochSec)
    }

    @Test
    fun journeyMatchKeyIgnoresLiveDelaysAndPlatformFilterRejectsText() {
        val delayed = journey.copy(id = "other", legs = journey.legs.map { if (it.kind == LegKind.TRANSIT) it.copy(start = it.start.plusSeconds(120)) else it })
        assertEquals(journey.matchKey, delayed.matchKey)
        assertEquals("6", JourneyMapper.platformOrNull("6"))
        assertEquals("12A", JourneyMapper.platformOrNull("12A"))
        assertNull(JourneyMapper.platformOrNull("Replacement bus"))
        assertNull(JourneyMapper.platformOrNull(" "))
    }
}
