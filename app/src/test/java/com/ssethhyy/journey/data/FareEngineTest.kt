package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.fare.DayKind
import com.ssethhyy.journey.data.fare.FareConfig
import com.ssethhyy.journey.data.fare.FareDefaults
import com.ssethhyy.journey.data.fare.FareEngine
import com.ssethhyy.journey.data.fare.FareLeg
import com.ssethhyy.journey.data.fare.FareReason
import com.ssethhyy.journey.data.fare.FareRule
import com.ssethhyy.journey.data.fare.FareTrip
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.fare.PriorTrip
import com.ssethhyy.journey.data.fare.StopFare
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiFareType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FareEngineTest {
    // Standard 2026 fares with every discount switched off, so tests state exactly which rule they exercise.
    private val plain = FareConfig(rules = FareDefaults.rules.map { it.copy(enabled = false) })
    private fun only(vararg ids: String) = FareConfig(rules = FareDefaults.rules.map { it.copy(enabled = it.id in ids) })

    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").atZone(FareEngine.MELBOURNE).toInstant().toEpochMilli()

    private fun trip(date: String, start: String, end: String = start, mode: TransportMode = TransportMode.TRAIN, zone: FareZone = FareZone.ZONE_1_2) =
        FareTrip(at(date, start), at(date, end), listOf(FareLeg(mode, zone)))

    private fun prior(date: String, time: String, zone: FareZone = FareZone.ZONE_1_2, charged: Long, paid: Boolean = true) =
        PriorTrip(at(date, time), zone, charged, paid)

    // 2026-03-10 is a Tuesday, outside the half-price period.
    private val tue = "2026-03-10"
    private val sat = "2026-03-14"

    @Test
    fun aNewFareCostsTheTwoHourFare() {
        val q = FareEngine.quote(plain, MykiFareType.FULL, trip(tue, "08:00", "08:40"), emptyList())
        assertEquals(570L, q.chargeCents)
        assertEquals(FareReason.NEW_FARE, q.reason)
        assertEquals(285L, FareEngine.quote(plain, MykiFareType.CONCESSION, trip(tue, "08:00"), emptyList()).chargeCents)
    }

    @Test
    fun tripsWithinTwoHoursAreCoveredThenAnotherFareStarts() {
        val first = prior(tue, "08:00", charged = 570)
        val covered = FareEngine.quote(plain, MykiFareType.FULL, trip(tue, "09:30"), listOf(first))
        assertEquals(0L, covered.chargeCents)
        assertEquals(FareReason.WITHIN_TWO_HOURS, covered.reason)

        val next = FareEngine.quote(plain, MykiFareType.FULL, trip(tue, "10:00"), listOf(first))
        assertEquals("two hours after the first touch on is a new fare", 570L, next.chargeCents)
    }

    @Test
    fun theDailyCapStopsFurtherCharges() {
        val history = listOf(prior(tue, "08:00", charged = 570), prior(tue, "10:30", charged = 570))
        val q = FareEngine.quote(plain, MykiFareType.FULL, trip(tue, "14:00"), history)
        assertEquals(0L, q.chargeCents)
        assertEquals(FareReason.DAILY_CAP, q.reason)
        // Partly capped: 800 weekend cap, 570 already spent.
        val weekend = FareEngine.quote(plain, MykiFareType.FULL, trip(sat, "13:00"), listOf(prior(sat, "08:00", charged = 570)))
        assertEquals(230L, weekend.chargeCents)
        assertEquals(FareReason.DAILY_CAP, weekend.reason)
    }

    @Test
    fun zoneTwoOnlyIsCheaperAndUpgradesPayTheDifference() {
        assertEquals(360L, FareEngine.quote(plain, MykiFareType.FULL, trip(tue, "08:00", zone = FareZone.ZONE_2), emptyList()).chargeCents)
        val upgrade = FareEngine.quote(
            plain, MykiFareType.FULL, trip(tue, "08:30"),
            listOf(prior(tue, "08:00", FareZone.ZONE_2, 360)),
        )
        assertEquals(210L, upgrade.chargeCents)
        assertEquals(FareReason.UPGRADE, upgrade.reason)
        // Going back to Zone 2 inside a Zone 1+2 fare costs nothing.
        val covered = FareEngine.quote(
            plain, MykiFareType.FULL, trip(tue, "08:30", zone = FareZone.ZONE_2),
            listOf(prior(tue, "08:00", FareZone.ZONE_1_2, 570)),
        )
        assertEquals(0L, covered.chargeCents)
    }

    @Test
    fun halfPriceHalvesFaresAndCaps() {
        val config = only(FareDefaults.HALF_PRICE_ID)
        val oct = "2026-10-23" // a Friday inside the half-price period
        assertEquals(285L, FareEngine.quote(config, MykiFareType.FULL, trip(oct, "08:00"), emptyList()).chargeCents)
        assertEquals("concession rounds down to 5c, as myki does", 140L, FareEngine.quote(config, MykiFareType.CONCESSION, trip(oct, "08:00"), emptyList()).chargeCents)
        val capped = FareEngine.quote(config, MykiFareType.FULL, trip(oct, "14:00"), listOf(prior(oct, "08:00", charged = 285), prior(oct, "10:30", charged = 285)))
        assertEquals(0L, capped.chargeCents)
        assertEquals("half-price daily cap is $5.70", FareReason.DAILY_CAP, capped.reason)
        // Not active before it starts or after it ends.
        assertEquals(570L, FareEngine.quote(config, MykiFareType.FULL, trip("2026-05-29", "08:00"), emptyList()).chargeCents)
        assertEquals(570L, FareEngine.quote(config, MykiFareType.FULL, trip("2027-01-04", "08:00"), emptyList()).chargeCents)
    }

    @Test
    fun aGovernmentFreeTravelRuleCanBeAddedAndTheBiggestDiscountWins() {
        val free = FareRule(id = "free_month", label = "Free public transport", percentOff = 100, from = "2026-04-01", to = "2026-06-01")
        val config = FareConfig(rules = only(FareDefaults.HALF_PRICE_ID).rules + free)
        val q = FareEngine.quote(config, MykiFareType.FULL, trip("2026-05-12", "08:00"), emptyList())
        assertEquals(0L, q.chargeCents)
        assertEquals(FareReason.FREE_RULE, q.reason)
        assertEquals("Free public transport", q.discount?.label)
        assertFalse(q.isPaid)
    }

    @Test
    fun childrenTravelFreeAndSeniorsOnWeekends() {
        val config = only(FareDefaults.CHILD_FREE_ID, FareDefaults.SENIORS_WEEKEND_ID)
        assertEquals(0L, FareEngine.quote(config, MykiFareType.CHILD, trip(tue, "08:00"), emptyList()).chargeCents)
        assertEquals("seniors pay concession on weekdays", 285L, FareEngine.quote(config, MykiFareType.SENIORS, trip(tue, "08:00"), emptyList()).chargeCents)
        assertEquals(0L, FareEngine.quote(config, MykiFareType.SENIORS, trip(sat, "08:00"), emptyList()).chargeCents)
        assertEquals("full fare is unaffected", 570L, FareEngine.quote(config, MykiFareType.FULL, trip(tue, "08:00"), emptyList()).chargeCents)
        // Public holiday counts as a weekend (Monday 9 March 2026 is Labour Day).
        assertEquals(0L, FareEngine.quote(config, MykiFareType.SENIORS, trip("2026-03-09", "08:00"), emptyList()).chargeCents)
    }

    @Test
    fun earlyBirdIsFreeOnlyForTrainsFinishingBeforeSeven() {
        val config = only(FareDefaults.EARLY_BIRD_ID)
        assertEquals(0L, FareEngine.quote(config, MykiFareType.FULL, trip(tue, "06:10", "06:50"), emptyList()).chargeCents)
        assertEquals(570L, FareEngine.quote(config, MykiFareType.FULL, trip(tue, "06:40", "07:10"), emptyList()).chargeCents)
        assertEquals(570L, FareEngine.quote(config, MykiFareType.FULL, trip(tue, "06:10", "06:50", mode = TransportMode.BUS), emptyList()).chargeCents)
        assertEquals("not on weekends", 570L, FareEngine.quote(config, MykiFareType.FULL, trip(sat, "06:10", "06:50"), emptyList()).chargeCents)
    }

    @Test
    fun freeTripsDoNotStartAFare() {
        val config = only(FareDefaults.EARLY_BIRD_ID)
        val early = FareEngine.quote(config, MykiFareType.FULL, trip(tue, "06:10", "06:50"), emptyList())
        assertFalse(early.isPaid)
        // History only holds paid trips, so the next one starts its own fare.
        assertEquals(570L, FareEngine.quote(config, MykiFareType.FULL, trip(tue, "07:30"), emptyList()).chargeCents)
    }

    @Test
    fun freeTramZoneAndRegionalTrips() {
        val inside = FareTrip(at(tue, "09:00"), at(tue, "09:10"), listOf(FareLeg(TransportMode.TRAM, FareZone.FREE_TRAM)))
        val q = FareEngine.quote(plain, MykiFareType.FULL, inside, emptyList())
        assertEquals(0L, q.chargeCents)
        assertEquals(FareReason.FREE_ZONE, q.reason)
        // A free tram leg plus a train is just the train fare.
        val mixed = FareTrip(at(tue, "09:00"), at(tue, "09:40"), listOf(FareLeg(TransportMode.TRAM, FareZone.FREE_TRAM), FareLeg(TransportMode.TRAIN, FareZone.ZONE_1_2)))
        assertEquals(570L, FareEngine.quote(plain, MykiFareType.FULL, mixed, emptyList()).chargeCents)

        val vline = FareEngine.quote(plain, MykiFareType.FULL, trip(tue, "09:00", mode = TransportMode.VLINE), emptyList())
        assertNull(vline.chargeCents)
        assertEquals(FareReason.MANUAL, vline.reason)
    }

    @Test
    fun theMykiDayRunsFromThreeAm() {
        // A trip at 1:30 am on Wednesday still belongs to Tuesday's fare day and cap.
        val history = listOf(prior(tue, "08:00", charged = 570), prior(tue, "10:30", charged = 570))
        assertEquals(0L, FareEngine.quote(plain, MykiFareType.FULL, trip("2026-03-11", "01:30"), history).chargeCents)
        assertEquals("after 3 am it is a new day", 570L, FareEngine.quote(plain, MykiFareType.FULL, trip("2026-03-11", "03:30"), history).chargeCents)
    }

    @Test
    fun zonesFromStops() {
        assertEquals(FareZone.ZONE_2, FareEngine.legZone(TransportMode.TRAIN, listOf(StopFare(setOf(2), false), StopFare(setOf(2), false))))
        assertEquals(FareZone.ZONE_1_2, FareEngine.legZone(TransportMode.TRAIN, listOf(StopFare(setOf(2), false), StopFare(setOf(1, 2), false))))
        assertEquals(FareZone.FREE_TRAM, FareEngine.legZone(TransportMode.TRAM, listOf(StopFare(setOf(1), true), StopFare(setOf(1), true))))
        assertEquals("only trams use the free zone", FareZone.ZONE_1_2, FareEngine.legZone(TransportMode.BUS, listOf(StopFare(setOf(1), true))))
        assertEquals(FareZone.ZONE_1_2, FareEngine.legZone(TransportMode.TRAIN, emptyList()))
    }

    @Test
    fun discountRounding() {
        assertEquals(140L, FareEngine.discounted(285, 50))
        assertEquals(285L, FareEngine.discounted(570, 50))
        assertEquals(570L, FareEngine.discounted(1140, 50))
        assertEquals(570L, FareEngine.discounted(570, 0))
        assertEquals(0L, FareEngine.discounted(570, 100))
    }

    @Test
    fun rulesRespectDatesAndDayKinds() {
        val rule = FareRule("x", "Weekend sale", percentOff = 25, from = "2026-03-01", to = "2026-04-01", days = DayKind.WEEKEND)
        val config = FareConfig(rules = listOf(rule))
        assertEquals(425L, FareEngine.quote(config, MykiFareType.FULL, trip(sat, "08:00"), emptyList()).chargeCents)
        assertEquals(570L, FareEngine.quote(config, MykiFareType.FULL, trip(tue, "08:00"), emptyList()).chargeCents)
        assertTrue(rule.isActiveOn(java.time.LocalDate.parse("2026-03-31")))
        assertFalse(rule.isActiveOn(java.time.LocalDate.parse("2026-04-01")))
    }
}
