package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.fare.FareEngine
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiActivity
import com.ssethhyy.journey.data.myki.MykiActivityKind
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiTripRecord
import com.ssethhyy.journey.data.myki.ProfileStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProfileStatsTest {
    private fun at(date: String, time: String = "09:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(FareEngine.MELBOURNE).toInstant().toEpochMilli()

    private val now = java.time.Instant.ofEpochMilli(at("2026-10-24", "12:00"))

    private fun trip(date: String, cents: Long, vararg modes: TransportMode) = MykiActivity(
        id = "t$date$cents${modes.joinToString()}",
        kind = MykiActivityKind.TRIP,
        amountCents = -cents,
        atMillis = at(date),
        chargedCents = cents,
        trip = MykiTripRecord(at(date), at(date, "09:30"), FareZone.ZONE_1_2, modes.toList(), paid = cents > 0),
    )

    private fun charge(date: String, cents: Long) =
        MykiActivity("c$date$cents", MykiActivityKind.CHARGE, -cents, at(date), chargedCents = cents, title = "Coffee")

    private fun card(vararg activity: MykiActivity) = MykiCard("1", "308425123456780", "Work", activity = activity.toList())

    @Test
    fun noTripsGivesEmptyStats() {
        val stats = ProfileStatsCalculator.compute(listOf(card()), now)
        assertFalse(stats.hasTrips)
        assertEquals(0, stats.tripsAllTime)
        assertNull(stats.topMode)
        assertNull(stats.busiestDay)
        assertEquals(7, stats.last7Days.size)
        assertEquals(1, stats.cardCount)
        assertEquals(0, ProfileStatsCalculator.compute(emptyList(), now).cardCount)
    }

    @Test
    fun countsTripsAndSpendingByMonth() {
        val stats = ProfileStatsCalculator.compute(
            listOf(
                card(
                    trip("2026-10-24", 285, TransportMode.TRAIN),
                    trip("2026-10-20", 285, TransportMode.TRAM),
                    trip("2026-09-30", 570, TransportMode.TRAIN),
                    charge("2026-10-22", 450),
                    charge("2026-09-01", 100),
                ),
            ),
            now,
        )
        assertEquals(2, stats.tripsThisMonth)
        assertEquals(3, stats.tripsAllTime)
        assertEquals("trips and custom deductions", 285L + 285 + 450, stats.spentThisMonthCents)
        assertEquals(285L + 285 + 570 + 450 + 100, stats.spentAllTimeCents)
    }

    @Test
    fun findsTheTopModeAndFreeTripsAcrossCards() {
        val a = card(trip("2026-10-24", 285, TransportMode.TRAIN), trip("2026-10-23", 0, TransportMode.TRAIN), trip("2026-10-22", 285, TransportMode.TRAM))
        val b = MykiCard("2", "308425000011112", "Spare", activity = listOf(trip("2026-10-21", 285, TransportMode.TRAIN, TransportMode.TRAM)))
        val stats = ProfileStatsCalculator.compute(listOf(a, b), now)
        assertEquals(TransportMode.TRAIN, stats.topMode)
        assertEquals(3, stats.modeCounts[TransportMode.TRAIN])
        assertEquals(2, stats.modeCounts[TransportMode.TRAM])
        assertEquals(1, stats.freeTrips)
        assertEquals(2, stats.cardCount)
    }

    @Test
    fun lastSevenDaysEndToday() {
        val stats = ProfileStatsCalculator.compute(
            listOf(card(trip("2026-10-24", 285, TransportMode.BUS), trip("2026-10-24", 0, TransportMode.TRAIN), trip("2026-10-19", 285, TransportMode.TRAM), trip("2026-10-17", 285, TransportMode.TRAM))),
            now,
        )
        assertEquals("2026-10-18", stats.last7Days.first().date.toString())
        assertEquals("2026-10-24", stats.last7Days.last().date.toString())
        assertEquals(2, stats.last7Days.last().trips)
        assertEquals("2026-10-24", stats.busiestDay?.date.toString())
        assertTrue("a trip older than a week is not in the chart", stats.last7Days.sumOf { it.trips } == 3)
    }

    @Test
    fun unpaidManualTripsWithAnAmountAreNotFree() {
        val custom = trip("2026-10-24", 330, TransportMode.TRAIN)
        assertEquals(0, ProfileStatsCalculator.compute(listOf(card(custom)), now).freeTrips)
    }
}
