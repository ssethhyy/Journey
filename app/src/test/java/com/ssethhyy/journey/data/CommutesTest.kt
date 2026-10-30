package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.commute.Commute
import com.ssethhyy.journey.data.commute.CommuteData
import com.ssethhyy.journey.data.commute.CommuteEnd
import com.ssethhyy.journey.data.commute.CommuteKind
import com.ssethhyy.journey.data.commute.CommuteLogic
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CommutesTest {
    private val zone = ZoneId.of("Australia/Melbourne")
    private val home = CommuteEnd("Home", -37.90, 145.00)
    private val work = CommuteEnd("Work", -37.81, 144.96)
    private val gym = CommuteEnd("Gym", -37.85, 145.02)
    private val monday8 = Instant.parse("2026-10-26T21:15:00Z") // 8:15 AM Tuesday 27 Oct in Melbourne (AEDT)

    private fun day(n: Long, hour: Long = 0) = monday8.plusSeconds(n * 86_400 + hour * 3_600)

    @Test
    fun aTripBecomesACommuteOnTheSecondOccasion() {
        var data = CommuteData()
        data = CommuteLogic.record(data, home, work, day(0), zone)
        assertTrue("one trip is not a commute", CommuteLogic.visible(data, day(0), zone).isEmpty())
        data = CommuteLogic.record(data, home, work, day(1), zone)
        val shown = CommuteLogic.visible(data, day(1), zone)
        assertEquals(1, shown.size)
        assertEquals("To Work", shown[0].title)
        assertEquals(CommuteKind.WORK, shown[0].kind)
        assertEquals(false, shown[0].manual)
    }

    @Test
    fun replanningWithinHalfAnHourIsTheSameOccasion() {
        var data = CommuteData()
        repeat(5) { data = CommuteLogic.record(data, home, work, monday8.plusSeconds(it * 120L), zone) }
        assertEquals(1, data.stats.single().count)
        assertTrue(CommuteLogic.visible(data, monday8, zone).isEmpty())
    }

    @Test
    fun aTripToWhereYouAreIsIgnored() {
        assertTrue(CommuteLogic.record(CommuteData(), home, home, monday8, zone).stats.isEmpty())
    }

    @Test
    fun usualTimeNeedsThreeConsistentTrips() {
        var data = CommuteData()
        repeat(3) { data = CommuteLogic.record(data, home, work, day(it.toLong()), zone) }
        assertEquals(8, CommuteLogic.usualHour(data.stats.single()))
        assertEquals("around 8 AM", CommuteLogic.usualText(8))
        assertEquals("around 12 PM", CommuteLogic.usualText(12))
        // Spread across the day: no usual time.
        var spread = CommuteData()
        listOf(0L, 5L, 11L, 15L).forEachIndexed { i, h -> spread = CommuteLogic.record(spread, home, work, day(i.toLong(), h), zone) }
        assertNull(CommuteLogic.usualHour(spread.stats.single()))
    }

    @Test
    fun manualCommutesComeFirstAndLearnedOnesAreRankedByUse() {
        var data = CommuteData()
        repeat(4) { data = CommuteLogic.record(data, home, work, day(it.toLong()), zone) }
        repeat(2) { data = CommuteLogic.record(data, home, gym, day(it.toLong(), 6), zone) }
        val mine = Commute("m1", "Mum's", home, CommuteEnd("Mum's", -37.7, 145.1), manual = true)
        data = CommuteLogic.upsertManual(data, mine)
        val shown = CommuteLogic.visible(data, day(5), zone)
        assertEquals(listOf("Mum's", "To Work", "To Gym"), shown.map { it.title })
    }

    @Test
    fun aManualCommuteReplacesTheLearnedOneForTheSameTrip() {
        var data = CommuteData()
        repeat(3) { data = CommuteLogic.record(data, home, work, day(it.toLong()), zone) }
        data = CommuteLogic.upsertManual(data, Commute("m1", "Off to work", home, work))
        val shown = CommuteLogic.visible(data, day(4), zone)
        assertEquals(listOf("Off to work"), shown.map { it.title })
        assertEquals(true, shown.single().manual)
    }

    @Test
    fun hiddenCommutesStayHiddenUntilAddedAgain() {
        var data = CommuteData()
        repeat(3) { data = CommuteLogic.record(data, home, work, day(it.toLong()), zone) }
        val key = CommuteLogic.visible(data, day(3), zone).single().key
        data = CommuteLogic.hide(data, key)
        assertTrue(CommuteLogic.visible(data, day(3), zone).isEmpty())
        data = CommuteLogic.upsertManual(data, Commute("m1", "To Work", home, work))
        assertEquals(1, CommuteLogic.visible(data, day(3), zone).size)
    }

    @Test
    fun staleTripsAreDropped() {
        var data = CommuteData()
        repeat(3) { data = CommuteLogic.record(data, home, work, day(it.toLong()), zone) }
        assertTrue(CommuteLogic.visible(data, day(200), zone).isEmpty())
    }

    @Test
    fun currentLocationStartsAreDistinctFromFixedOnes() {
        val here = CommuteEnd("Current location", isCurrent = true)
        var data = CommuteData()
        repeat(2) { data = CommuteLogic.record(data, here, work, day(it.toLong()), zone) }
        val shown = CommuteLogic.visible(data, day(2), zone).single()
        assertEquals("your location", shown.fromLabel)
        assertTrue(shown.from.toPlan().isCurrentLocation)
    }

    private fun leg(start: Instant, minutes: Long, label: String = "Frankston", cancelled: Boolean = false) = JourneyLeg(
        kind = LegKind.TRANSIT, mode = TransportMode.TRAIN, rawMode = "RAIL", fromName = "A", toName = "B",
        start = start, end = start.plusSeconds(minutes * 60), scheduledStart = start, scheduledEnd = null, realTime = true,
        headsign = null, routeShortName = label, routeLongName = null, routeColor = null, routeTextColor = null,
        distanceMeters = null, platform = null, arrivalPlatform = null, cancelled = cancelled, intermediateStops = emptyList(), path = emptyList(),
    )

    private fun journey(id: String, start: Instant, cancelled: Boolean = false) =
        Journey(id, start, start.plusSeconds(30 * 60), 0, listOf(leg(start, 30, cancelled = cancelled)))

    @Test
    fun picksTheNextTripAndABackupAfterIt() {
        val now = monday8
        val journeys = listOf(
            journey("past", now.minusSeconds(600)),
            journey("a", now.plusSeconds(240)),
            journey("same", now.plusSeconds(260).let { it }), // too close to be a real alternative? it is a different service
            journey("b", now.plusSeconds(900)),
        )
        val pick = CommuteLogic.pick(journeys, now)!!
        assertEquals("a", pick.primary.id)
        assertEquals("b", pick.backup?.id)
    }

    @Test
    fun cancelledTripsAreSkippedAndNothingFoundIsNull() {
        val now = monday8
        val pick = CommuteLogic.pick(listOf(journey("x", now.plusSeconds(60), cancelled = true), journey("y", now.plusSeconds(300))), now)!!
        assertEquals("y", pick.primary.id)
        assertNull(pick.backup)
        assertNull(CommuteLogic.pick(listOf(journey("old", now.minusSeconds(3600))), now))
    }
}
