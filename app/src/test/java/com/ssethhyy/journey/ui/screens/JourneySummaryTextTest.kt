package com.ssethhyy.journey.ui.screens

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.ui.screens.plan.JourneySummaryText
import com.ssethhyy.journey.utils.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** The words at the top of the journey panel: when to leave, the times, and the first service to catch. */
class JourneySummaryTextTest {
    private val t0 = Instant.parse("2026-11-10T07:00:00Z")

    private fun leg(kind: LegKind, from: String, to: String, startMin: Long, endMin: Long, label: String? = null, platform: String? = null) = JourneyLeg(
        kind = kind, mode = if (kind == LegKind.TRANSIT) TransportMode.TRAIN else null, rawMode = if (kind == LegKind.TRANSIT) "RAIL" else "WALK",
        fromName = from, toName = to, start = t0.plusSeconds(startMin * 60), end = t0.plusSeconds(endMin * 60),
        scheduledStart = null, scheduledEnd = null, realTime = false, headsign = null, routeShortName = label, routeLongName = null,
        routeColor = null, routeTextColor = null, distanceMeters = 300.0, platform = platform, arrivalPlatform = null,
        cancelled = false, intermediateStops = emptyList(), path = emptyList(),
    )

    private val journey = Journey(
        "j", t0, t0.plusSeconds(40 * 60), 1,
        listOf(
            leg(LegKind.WALK, "", "Westgarth Station", 0, 6),
            leg(LegKind.TRANSIT, "Westgarth Station", "Parliament", 8, 30, label = "Hurstbridge", platform = "1"),
            leg(LegKind.WALK, "Parliament", "", 30, 40),
        ),
    )

    @Test
    fun beforeTheStartItCountsDownToLeaving() {
        assertEquals("Leave in 12 min", JourneySummaryText.leave(journey, t0.minusSeconds(12 * 60)))
        assertEquals("Leave now", JourneySummaryText.leave(journey, t0.minusSeconds(20)))
    }

    @Test
    fun underWayAndAfterwards() {
        assertEquals("On your way", JourneySummaryText.leave(journey, t0.plusSeconds(10 * 60)))
        assertEquals("This trip has finished", JourneySummaryText.leave(journey, t0.plusSeconds(41 * 60)))
    }

    @Test
    fun timesGiveLeavingArrivingAndHowLong() {
        assertEquals(
            "Leave ${TimeFormatter.clock(t0)} · Arrive ${TimeFormatter.clock(t0.plusSeconds(2400))} · 40 min",
            JourneySummaryText.times(journey),
        )
    }

    @Test
    fun theFirstServiceIsNamedWithItsPlatform() {
        assertEquals(
            "Catch the ${TimeFormatter.clock(t0.plusSeconds(480))} Hurstbridge line from platform 1",
            JourneySummaryText.firstService(journey),
        )
        assertNull(JourneySummaryText.firstService(journey.copy(legs = journey.legs.take(1))))
        assertEquals("1 change", JourneySummaryText.changes(journey))
    }
}
