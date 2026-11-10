package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.tracking.JourneySteps
import com.ssethhyy.journey.tracking.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JourneyStepsTest {
    private val t0 = Instant.parse("2026-11-10T07:00:00Z")

    private fun leg(kind: LegKind, from: String, to: String, startMin: Long, endMin: Long, meters: Double? = null, label: String? = null, headsign: String? = null, platform: String? = null) = JourneyLeg(
        kind = kind, mode = if (kind == LegKind.TRANSIT) TransportMode.TRAIN else null, rawMode = if (kind == LegKind.TRANSIT) "RAIL" else "WALK",
        fromName = from, toName = to, start = t0.plusSeconds(startMin * 60), end = t0.plusSeconds(endMin * 60),
        scheduledStart = null, scheduledEnd = null, realTime = false, headsign = headsign, routeShortName = label, routeLongName = null,
        routeColor = null, routeTextColor = null, distanceMeters = meters, platform = platform, arrivalPlatform = null,
        cancelled = false, intermediateStops = emptyList(), path = emptyList(),
    )

    private val journey = Journey(
        "j", t0, t0.plusSeconds(40 * 60), 0,
        listOf(
            leg(LegKind.WALK, "", "Westgarth Station", 0, 6, meters = 352.0),
            leg(LegKind.TRANSIT, "Westgarth Station", "Parliament", 8, 30, label = "Hurstbridge", headsign = "Flinders Street", platform = "1"),
            leg(LegKind.WALK, "Parliament", "", 30, 40, meters = 600.0),
        ),
    )

    @Test
    fun beforeLeavingEveryStepIsAheadAndNoneIsCurrent() {
        val steps = JourneySteps.upcoming(journey, "Work", t0.minusSeconds(60))
        assertEquals(listOf(StepKind.WALK, StepKind.RIDE, StepKind.WALK, StepKind.ARRIVE), steps.map { it.kind })
        assertEquals("Walk 350 m to Westgarth Station", steps[0].title)
        assertEquals("Hurstbridge to Flinders Street", steps[1].title)
        assertTrue(steps[1].detail.startsWith("From Westgarth Station · Platform 1 · get off at Parliament"))
        assertEquals("Walk 600 m to Work", steps[2].title)
        assertEquals("Arrive at Work", steps[3].title)
        assertTrue(steps.none { it.current })
    }

    @Test
    fun onTheTrainTheRideIsCurrentAndFinishedStepsAreGone() {
        val steps = JourneySteps.upcoming(journey, "Work", t0.plusSeconds(15 * 60))
        assertEquals(StepKind.RIDE, steps.first().kind)
        assertTrue(steps.first().current)
        assertTrue("on board, the platform no longer matters", steps.first().detail.startsWith("Get off at Parliament"))
        assertEquals(3, steps.size)
    }

    @Test
    fun afterArrivingThereIsNothingLeft() {
        assertTrue(JourneySteps.upcoming(journey, "Work", t0.plusSeconds(41 * 60)).isEmpty())
    }
}
