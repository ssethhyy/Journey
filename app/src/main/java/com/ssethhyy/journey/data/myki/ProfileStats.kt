/*
 * Feature Module: Profile
 * File: ProfileStats.kt
 * Description: Numbers for the Profile page, worked out from the trips and deductions logged on your
 * mykis: how many trips this month and ever, what they cost, free trips, the mode you use most and
 * a count for each of the last seven days. Pure Kotlin.
 */

package com.ssethhyy.journey.data.myki

import com.ssethhyy.journey.data.fare.FareEngine
import com.ssethhyy.journey.data.model.TransportMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DayCount(val date: LocalDate, val trips: Int)

data class ProfileStats(
    val tripsThisMonth: Int,
    val tripsAllTime: Int,
    /** Trips and custom deductions, in cents. */
    val spentThisMonthCents: Long,
    val spentAllTimeCents: Long,
    val freeTrips: Int,
    val modeCounts: Map<TransportMode, Int>,
    /** Oldest first, ending today. */
    val last7Days: List<DayCount>,
    val cardCount: Int,
) {
    val topMode: TransportMode? get() = modeCounts.maxByOrNull { it.value }?.key
    val busiestDay: DayCount? get() = last7Days.maxByOrNull { it.trips }?.takeIf { it.trips > 0 }
    val hasTrips: Boolean get() = tripsAllTime > 0
}

object ProfileStatsCalculator {
    fun compute(cards: List<MykiCard>, now: Instant, zone: ZoneId = FareEngine.MELBOURNE): ProfileStats {
        val today = now.atZone(zone).toLocalDate()
        val month = YearMonth.from(today)
        val activity = cards.flatMap { it.activity }

        fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        val trips = activity.filter { it.kind == MykiActivityKind.TRIP && it.trip != null }
        val tripDates = trips.map { dateOf(it.trip!!.startMillis) }
        val spending = activity.filter { it.kind == MykiActivityKind.TRIP || it.kind == MykiActivityKind.CHARGE }

        val modeCounts = trips
            .flatMap { it.trip!!.modes.distinct() }
            .groupingBy { it }
            .eachCount()

        val last7 = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            DayCount(day, tripDates.count { it == day })
        }

        return ProfileStats(
            tripsThisMonth = tripDates.count { YearMonth.from(it) == month },
            tripsAllTime = trips.size,
            spentThisMonthCents = spending.filter { YearMonth.from(dateOf(it.trip?.startMillis ?: it.atMillis)) == month }.sumOf { it.chargedCents ?: 0L },
            spentAllTimeCents = spending.sumOf { it.chargedCents ?: 0L },
            freeTrips = trips.count { (it.chargedCents ?: 0L) == 0L },
            modeCounts = modeCounts,
            last7Days = last7,
            cardCount = cards.size,
        )
    }
}
