/*
 * Feature Module: Fares
 * File: FareModels.kt
 * Description: Fare tables, discount rules and the defaults for Victoria's metropolitan myki fares.
 * Prices are plain data (not hard-coded in the engine) so they can be edited when the government
 * changes them, and discounts such as free or half-price travel are rules with date ranges.
 *
 * Sources for the defaults (checked 23 Oct 2026): Transport Victoria / PTV fare pages for the
 * 1 January 2026 fares, and the Victorian Government's half-price fare announcement
 * (1 June 2026 to 1 January 2027). There is no official fares feed, so the app asks the user to
 * confirm prices after each 1 January.
 */

package com.ssethhyy.journey.data.fare

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiFareType
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The fare a trip is charged at. Zone 1 on its own costs the same as Zone 1+2. */
enum class FareZone(val label: String) {
    ZONE_1_2("Zone 1+2"),
    ZONE_2("Zone 2 only"),
    /** A tram trip entirely inside the CBD Free Tram Zone. */
    FREE_TRAM("Free Tram Zone"),
}

enum class FareClass { FULL, CONCESSION }

/** Prices in cents for one fare class. */
@Serializable
data class ClassPrices(
    val twoHourZone12: Long,
    val twoHourZone2: Long,
    val dailyCapZone12: Long,
    val dailyCapZone2: Long,
    /** Saturday, Sunday and public holiday cap. */
    val weekendCapZone12: Long,
)

@Serializable
data class FareTable(
    val label: String,
    /** ISO date the prices took effect. */
    val effectiveFrom: String,
    val full: ClassPrices,
    val concession: ClassPrices,
) {
    fun prices(fareClass: FareClass): ClassPrices = if (fareClass == FareClass.FULL) full else concession
}

enum class DayKind(val label: String) {
    ANY("Every day"),
    WEEKDAY("Weekdays"),
    /** Saturday, Sunday and Victorian public holidays. */
    WEEKEND("Weekends and public holidays"),
}

/**
 * A discount: "free", "half price" and so on. The biggest applicable discount wins; they do not stack.
 * Empty [modes] / [fareTypes] mean everything.
 */
@Serializable
data class FareRule(
    val id: String,
    val label: String,
    val note: String = "",
    val percentOff: Int,
    /** Inclusive start date (ISO), or null for no start. */
    val from: String? = null,
    /** Exclusive end date (ISO), or null for no end. */
    val to: String? = null,
    val modes: Set<TransportMode> = emptySet(),
    val fareTypes: Set<MykiFareType> = emptySet(),
    val days: DayKind = DayKind.ANY,
    /** The trip must finish before this minute of the day (e.g. 420 = 7:00 am), or null for any time. */
    val beforeMinute: Int? = null,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        (from == null || !date.isBefore(LocalDate.parse(from))) && (to == null || date.isBefore(LocalDate.parse(to)))
}

@Serializable
data class FareConfig(
    val table: FareTable = FareDefaults.table,
    val rules: List<FareRule> = FareDefaults.rules,
    /** ISO date the user last confirmed the table, so the app can ask again after the new year. */
    val confirmedOn: String? = null,
)

object FareDefaults {
    val table = FareTable(
        label = "Fares from 1 January 2026",
        effectiveFrom = "2026-01-01",
        full = ClassPrices(
            twoHourZone12 = 570,
            twoHourZone2 = 360,
            dailyCapZone12 = 1140,
            dailyCapZone2 = 720,
            weekendCapZone12 = 800,
        ),
        concession = ClassPrices(
            twoHourZone12 = 285,
            twoHourZone2 = 180,
            dailyCapZone12 = 570,
            dailyCapZone2 = 360,
            weekendCapZone12 = 400,
        ),
    )

    const val HALF_PRICE_ID = "half_price_2026"
    const val CHILD_FREE_ID = "child_free"
    const val SENIORS_WEEKEND_ID = "seniors_weekend"
    const val EARLY_BIRD_ID = "early_bird"

    val rules = listOf(
        FareRule(
            id = HALF_PRICE_ID,
            label = "Half-price fares",
            note = "Victorian Government, 1 June 2026 to 1 January 2027, on trains, trams and buses",
            percentOff = 50,
            from = "2026-06-01",
            to = "2027-01-01",
            builtIn = true,
        ),
        FareRule(
            id = CHILD_FREE_ID,
            label = "Free travel for under 18s",
            note = "Children travel free with a Youth myki",
            percentOff = 100,
            fareTypes = setOf(MykiFareType.CHILD),
            builtIn = true,
        ),
        FareRule(
            id = SENIORS_WEEKEND_ID,
            label = "Seniors travel free on weekends",
            note = "Victorian Seniors Card holders, Saturday, Sunday and public holidays",
            percentOff = 100,
            fareTypes = setOf(MykiFareType.SENIORS),
            days = DayKind.WEEKEND,
            builtIn = true,
        ),
        FareRule(
            id = EARLY_BIRD_ID,
            label = "Early Bird",
            note = "Free metro train trips that finish before 7:00 am on weekdays",
            percentOff = 100,
            modes = setOf(TransportMode.TRAIN),
            days = DayKind.WEEKDAY,
            beforeMinute = 7 * 60,
            builtIn = true,
        ),
    )
}

/**
 * Victorian public holidays, used for the weekend caps. Listed by hand for 2026 and 2027;
 * extend this when the next year's dates are published at vic.gov.au.
 */
object VicHolidays {
    private val dates: Set<LocalDate> = listOf(
        // 2026
        "2026-01-01", "2026-01-26", "2026-03-09", "2026-04-03", "2026-04-04", "2026-04-05", "2026-04-06",
        "2026-04-25", "2026-06-08", "2026-09-25", "2026-11-03", "2026-12-25", "2026-12-26", "2026-12-28",
        // 2027
        "2027-01-01", "2027-01-26", "2027-03-08", "2027-03-26", "2027-03-27", "2027-03-28", "2027-03-29",
        "2027-04-25", "2027-06-14", "2027-09-24", "2027-11-02", "2027-12-25", "2027-12-27", "2027-12-28",
    ).map(LocalDate::parse).toSet()

    fun isHoliday(date: LocalDate): Boolean = date in dates
}
