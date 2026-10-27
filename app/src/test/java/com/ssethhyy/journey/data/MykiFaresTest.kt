package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.fare.FareConfig
import com.ssethhyy.journey.data.fare.FareConfigs
import com.ssethhyy.journey.data.fare.FareDefaults
import com.ssethhyy.journey.data.fare.FareRule
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.fare.StoredFares
import com.ssethhyy.journey.data.fare.VicHolidays
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiActivityKind
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiLedger
import com.ssethhyy.journey.data.myki.MykiTripRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MykiFaresTest {
    private fun card(id: String, number: String = "30842512345678$id", default: Boolean = false, balance: Long? = null, low: Long = 1_000) =
        MykiCard(id, number, "Card $id", balanceCents = balance, isDefault = default, lowBalanceCents = low)

    @Test
    fun theFirstCardBecomesTheDefaultAndItAppearsFirst() {
        val one = MykiLedger.add(emptyList(), card("1"))
        assertTrue(one.single().isDefault)
        val two = MykiLedger.add(one, card("2"))
        assertEquals(listOf("1"), two.filter { it.isDefault }.map { it.id })

        val switched = MykiLedger.setDefault(two, "2")
        assertEquals(listOf("2", "1"), MykiLedger.ordered(switched).map { it.id })
        assertEquals("exactly one default", 1, switched.count { it.isDefault })
        assertEquals("2", MykiLedger.defaultCard(switched)?.id)
    }

    @Test
    fun removingTheDefaultPromotesAnotherAndBadDataIsRepaired() {
        val list = MykiLedger.setDefault(MykiLedger.add(MykiLedger.add(emptyList(), card("1")), card("2")), "2")
        val after = MykiLedger.remove(list, "2")
        assertTrue(after.single().isDefault)
        val broken = listOf(card("1", default = true), card("2", default = true), card("3"))
        assertEquals(listOf("1"), MykiLedger.normalizeDefault(broken).filter { it.isDefault }.map { it.id })
        assertEquals(listOf("1", "2", "3").size, MykiLedger.normalizeDefault(listOf(card("1"), card("2"), card("3"))).size)
        assertEquals("a list with no default gets one", "1", MykiLedger.ordered(listOf(card("1"), card("2"))).first().id)
    }

    @Test
    fun deductingATripLowersTheBalanceAndIsRemembered() {
        val trip = MykiTripRecord(1_000, 2_000, FareZone.ZONE_1_2, listOf(TransportMode.TRAIN), paid = true)
        val after = MykiLedger.deduct(card("1", balance = 2_616), 285, 5_000, MykiActivityKind.TRIP, "Flinders Street to Westgarth", "Train", trip)
        assertEquals(2_331L, after.balanceCents)
        val entry = after.activity.single()
        assertEquals(-285L, entry.amountCents)
        assertEquals(2_331L, entry.balanceAfterCents)
        assertEquals("Flinders Street to Westgarth", entry.title)
        val prior = MykiLedger.priorTrips(after).single()
        assertEquals(285L, prior.chargedCents)
        assertEquals(FareZone.ZONE_1_2, prior.zone)
        assertTrue(prior.paid)
        assertTrue("custom deductions are not trips", MykiLedger.priorTrips(MykiLedger.deduct(after, 100, 6_000, MykiActivityKind.CHARGE, "Coffee")).size == 1)
    }

    @Test
    fun anUnknownBalanceStaysUnknownButTheFareIsStillRecorded() {
        val trip = MykiTripRecord(1_000, 2_000, FareZone.ZONE_1_2, listOf(TransportMode.TRAM), paid = true)
        val after = MykiLedger.deduct(card("1", balance = null), 285, 5_000, MykiActivityKind.TRIP, "Trip", trip = trip)
        assertNull(after.balanceCents)
        assertEquals(285L, MykiLedger.priorTrips(after).single().chargedCents)
    }

    @Test
    fun freeTripsAreLoggedButFlaggedUnpaid() {
        val trip = MykiTripRecord(1_000, 2_000, FareZone.FREE_TRAM, listOf(TransportMode.TRAM), paid = false)
        val after = MykiLedger.deduct(card("1", balance = 500), 0, 5_000, MykiActivityKind.TRIP, "Free tram", trip = trip)
        assertEquals(500L, after.balanceCents)
        assertEquals(0L, after.activity.single().amountCents)
        assertFalse(MykiLedger.priorTrips(after).single().paid)
    }

    @Test
    fun lowBalanceAlertsOncePerLowSpell() {
        val topped = card("1", balance = 1_200)
        // Still above $10: nothing.
        assertEquals(false, MykiLedger.evaluateLowBalance(topped).second)
        // Drops below: alert, and the flag is set.
        val low = MykiLedger.deduct(topped, 400, 1, MykiActivityKind.TRIP, "t")
        val (flagged, alert) = MykiLedger.evaluateLowBalance(low)
        assertTrue(alert)
        assertTrue(flagged.lowBalanceAlerted)
        // Staying low does not alert again.
        val lower = MykiLedger.deduct(flagged, 100, 2, MykiActivityKind.TRIP, "t")
        assertEquals(false, MykiLedger.evaluateLowBalance(lower).second)
        // Topping up clears the flag so the next dip alerts again.
        val recovered = MykiLedger.evaluateLowBalance(MykiLedger.topUp(lower, 5_000, 3)).first
        assertFalse(recovered.lowBalanceAlerted)
        assertTrue(MykiLedger.evaluateLowBalance(MykiLedger.deduct(recovered, 5_500, 4, MykiActivityKind.TRIP, "t")).second)
    }

    @Test
    fun lowBalanceCanBeSwitchedOffAndIgnoresUnknownBalances() {
        assertFalse(card("1", balance = 50, low = 0).isLow)
        assertFalse(card("1", balance = null).isLow)
        assertTrue(card("1", balance = 999).isLow)
        assertFalse(card("1", balance = 1_000).isLow)
    }

    @Test
    fun oldStoredCardsStillLoad() {
        val old = """[{"id":"a","number":"308425123456780","nickname":"Old","fareType":"FULL","balanceCents":500,"activity":[{"id":"x","kind":"TOP_UP","amountCents":500,"atMillis":1}]}]"""
        val decoded = MykiLedger.decode(old).single()
        assertEquals(500L, decoded.balanceCents)
        assertEquals(MykiCard.DEFAULT_LOW_BALANCE_CENTS, decoded.lowBalanceCents)
        assertFalse(decoded.isDefault)
        assertTrue("repaired on read", MykiLedger.ordered(MykiLedger.decode(old)).single().isDefault)
    }

    @Test
    fun builtInRulesFollowTheAppWhileUserChoicesStick() {
        val custom = FareRule("free_week", "Free week", percentOff = 100, from = "2026-12-01", to = "2026-12-08")
        val stored = StoredFares(disabledBuiltIns = setOf(FareDefaults.HALF_PRICE_ID), customRules = listOf(custom))
        val config = FareConfigs.resolve(FareConfigs.decode(FareConfigs.encode(stored)))
        assertFalse(config.rules.first { it.id == FareDefaults.HALF_PRICE_ID }.enabled)
        assertTrue(config.rules.first { it.id == FareDefaults.CHILD_FREE_ID }.enabled)
        assertEquals("free_week", config.rules.last().id)
        assertEquals(FareDefaults.table, config.table)
        assertEquals(FareConfig().rules.size + 1, config.rules.size)
        assertEquals(FareConfig(), FareConfigs.resolve(FareConfigs.decode("not json")).copy(confirmedOn = null))
    }

    @Test
    fun theAppAsksForAPriceCheckAfterEachNewYear() {
        val config = FareConfig()
        assertFalse(FareConfigs.needsReview(config, LocalDate.parse("2026-10-23")))
        assertTrue(FareConfigs.needsReview(config, LocalDate.parse("2027-01-01")))
        assertFalse(FareConfigs.needsReview(config.copy(confirmedOn = "2027-01-05"), LocalDate.parse("2027-02-01")))
        assertTrue(FareConfigs.needsReview(config.copy(confirmedOn = "2026-12-20"), LocalDate.parse("2027-02-01")))
    }

    @Test
    fun publicHolidays() {
        assertTrue(VicHolidays.isHoliday(LocalDate.parse("2026-12-25")))
        assertFalse(VicHolidays.isHoliday(LocalDate.parse("2026-12-24")))
    }
}
