/*
 * Feature Module: Fares
 * File: FareEngine.kt
 * Description: Works out what a trip costs on a myki given what was already charged that day:
 * the 2-hour fare window (a zone upgrade pays the difference), the daily cap, weekend caps,
 * the Free Tram Zone and any discount rules. Pure Kotlin with no Android dependencies.
 *
 * Known simplifications: a trip's zone comes from the stops at its ends (a Zone 2 to Zone 2 trip
 * that passes through Zone 1 is charged as Zone 2 unless the user changes it), the weekly cap is
 * not modelled, and regional V/Line fares are entered by hand.
 */

package com.ssethhyy.journey.data.fare

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiFareType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What PTV says about one stop's fare zones. */
data class StopFare(val zones: Set<Int>, val freeTramZone: Boolean)

data class FareLeg(val mode: TransportMode, val zone: FareZone)

data class FareTrip(val startMillis: Long, val endMillis: Long, val legs: List<FareLeg>) {
    val zone: FareZone get() = FareEngine.tripZone(legs)
    val modes: List<TransportMode> get() = legs.map { it.mode }.distinct()
}

/** A trip already charged to this myki, as far as the engine needs to know. */
data class PriorTrip(val startMillis: Long, val zone: FareZone, val chargedCents: Long, val paid: Boolean)

enum class FareReason {
    NEW_FARE,
    WITHIN_TWO_HOURS,
    UPGRADE,
    DAILY_CAP,
    FREE_ZONE,
    FREE_RULE,
    /** The fare can't be worked out (regional V/Line), so the user enters it. */
    MANUAL,
}

data class FareQuote(
    /** What to deduct, or null when [reason] is MANUAL. */
    val chargeCents: Long?,
    /** The 2-hour fare at full price with no discount, for showing what a discount saved. */
    val listPriceCents: Long?,
    val zone: FareZone,
    val reason: FareReason,
    val discount: FareRule?,
    val notes: List<String>,
) {
    /** Whether this trip took part in the day's fares (free trips and manual amounts don't open a 2-hour fare). */
    val isPaid: Boolean get() = reason == FareReason.NEW_FARE || reason == FareReason.UPGRADE || reason == FareReason.DAILY_CAP ||
        reason == FareReason.WITHIN_TWO_HOURS
}

object FareEngine {
    val MELBOURNE: ZoneId = ZoneId.of("Australia/Melbourne")
    private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
    private const val DAY_STARTS_AT_HOUR = 3

    fun legZone(mode: TransportMode, stops: List<StopFare>): FareZone = when {
        stops.isEmpty() -> FareZone.ZONE_1_2
        mode == TransportMode.TRAM && stops.all { it.freeTramZone } -> FareZone.FREE_TRAM
        stops.all { it.zones == setOf(2) } -> FareZone.ZONE_2
        else -> FareZone.ZONE_1_2
    }

    fun tripZone(legs: List<FareLeg>): FareZone {
        val paying = legs.filter { it.zone != FareZone.FREE_TRAM }
        return when {
            paying.isEmpty() -> FareZone.FREE_TRAM
            paying.all { it.zone == FareZone.ZONE_2 } -> FareZone.ZONE_2
            else -> FareZone.ZONE_1_2
        }
    }

    fun fareClass(type: MykiFareType): FareClass = if (type == MykiFareType.FULL) FareClass.FULL else FareClass.CONCESSION

    /** The myki "day" for caps runs from 3 am to 3 am. */
    fun fareDate(millis: Long, zone: ZoneId = MELBOURNE): LocalDate {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        return if (local.hour < DAY_STARTS_AT_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY || VicHolidays.isHoliday(date)

    /** Price after a percentage discount, rounded down to the nearest 5 cents like myki does. */
    fun discounted(baseCents: Long, percentOff: Int): Long = (baseCents * (100 - percentOff) / 100) / 5 * 5

    /** The discount that applies to this trip, or null. The biggest one wins. */
    fun bestRule(config: FareConfig, type: MykiFareType, trip: FareTrip, zone: ZoneId = MELBOURNE): FareRule? {
        val date = fareDate(trip.startMillis, zone)
        val weekend = isWeekend(date)
        val endLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(trip.endMillis), zone)
        val endMinute = endLocal.hour * 60 + endLocal.minute
        return config.rules
            .filter { rule ->
                rule.enabled && rule.percentOff > 0 && rule.isActiveOn(date) &&
                    (rule.fareTypes.isEmpty() || type in rule.fareTypes) &&
                    (rule.modes.isEmpty() || trip.modes.all { it in rule.modes }) &&
                    when (rule.days) {
                        DayKind.ANY -> true
                        DayKind.WEEKDAY -> !weekend
                        DayKind.WEEKEND -> weekend
                    } &&
                    (rule.beforeMinute == null || (endLocal.toLocalDate() == date && endMinute < rule.beforeMinute))
            }
            .maxByOrNull { it.percentOff }
    }

    fun quote(
        config: FareConfig,
        type: MykiFareType,
        trip: FareTrip,
        history: List<PriorTrip>,
        zone: ZoneId = MELBOURNE,
    ): FareQuote {
        val tripZone = trip.zone
        if (trip.legs.any { it.mode == TransportMode.VLINE }) {
            return FareQuote(null, null, tripZone, FareReason.MANUAL, null, listOf("V/Line fares depend on the zones travelled. Enter the amount from your ticket."))
        }
        val prices = config.table.prices(fareClass(type))
        val date = fareDate(trip.startMillis, zone)
        val weekend = isWeekend(date)
        val listPrice = if (tripZone == FareZone.ZONE_2) prices.twoHourZone2 else prices.twoHourZone12
        if (tripZone == FareZone.FREE_TRAM) {
            return FareQuote(0, listPrice, tripZone, FareReason.FREE_ZONE, null, listOf("Trams inside the Free Tram Zone are free"))
        }
        val rule = bestRule(config, type, trip, zone)
        val percent = rule?.percentOff ?: 0
        if (percent >= 100) {
            return FareQuote(0, listPrice, tripZone, FareReason.FREE_RULE, rule, listOf("${rule!!.label}: free"))
        }

        fun twoHour(z: FareZone) = discounted(if (z == FareZone.ZONE_2) prices.twoHourZone2 else prices.twoHourZone12, percent)
        fun cap(z: FareZone): Long {
            val base = when {
                z == FareZone.ZONE_2 && weekend -> minOf(prices.weekendCapZone12, prices.dailyCapZone2)
                z == FareZone.ZONE_2 -> prices.dailyCapZone2
                weekend -> prices.weekendCapZone12
                else -> prices.dailyCapZone12
            }
            return discounted(base, percent)
        }

        // Today's paid trips, oldest first, decide whether this one rides on an open 2-hour fare.
        val today = history.filter { it.paid && fareDate(it.startMillis, zone) == date }.sortedBy { it.startMillis }
        var windowStart: Long? = null
        var covered = FareZone.ZONE_2
        for (prior in today) {
            if (windowStart == null || prior.startMillis >= windowStart + TWO_HOURS_MS) {
                windowStart = prior.startMillis
                covered = prior.zone
            } else if (prior.zone == FareZone.ZONE_1_2) {
                covered = FareZone.ZONE_1_2
            }
        }
        val notes = mutableListOf<String>()
        rule?.let { notes += "${it.label}: ${it.percentOff}% off" }
        val inWindow = windowStart != null && trip.startMillis >= windowStart && trip.startMillis < windowStart + TWO_HOURS_MS
        val (additional, base) = if (inWindow) {
            val union = if (covered == FareZone.ZONE_1_2 || tripZone == FareZone.ZONE_1_2) FareZone.ZONE_1_2 else FareZone.ZONE_2
            val extra = (twoHour(union) - twoHour(covered)).coerceAtLeast(0)
            extra to (if (extra > 0) FareReason.UPGRADE else FareReason.WITHIN_TWO_HOURS)
        } else {
            twoHour(tripZone) to FareReason.NEW_FARE
        }
        when (base) {
            FareReason.WITHIN_TWO_HOURS -> notes += "Covered by your 2-hour fare"
            FareReason.UPGRADE -> notes += "Pays the difference to extend your 2-hour fare to Zone 1+2"
            else -> Unit
        }

        val capZone = if (today.any { it.zone == FareZone.ZONE_1_2 } || tripZone == FareZone.ZONE_1_2) FareZone.ZONE_1_2 else FareZone.ZONE_2
        val spent = today.sumOf { it.chargedCents }
        val remaining = (cap(capZone) - spent).coerceAtLeast(0)
        val charge = minOf(additional, remaining)
        val reason = if (additional > 0 && remaining < additional) {
            notes += if (remaining == 0L) "Daily cap reached: no more to pay today" else "Daily cap reached"
            FareReason.DAILY_CAP
        } else {
            base
        }
        return FareQuote(charge, listPrice, tripZone, reason, rule, notes)
    }
}
