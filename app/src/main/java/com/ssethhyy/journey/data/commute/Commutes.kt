/*
 * Feature Module: Commutes
 * File: Commutes.kt
 * Description: Commutes shown on For You: ones the user adds, plus ones learned from the journeys they plan.
 * This file is pure logic (no Android types) so it is unit tested directly.
 */

package com.ssethhyy.journey.data.commute

import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.PlanLocation
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** One end of a commute. "Current location" has no fixed coordinates, so it is flagged instead. */
@Serializable
data class CommuteEnd(
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val subtitle: String? = null,
    val isCurrent: Boolean = false,
) {
    fun toPlan() = PlanLocation(
        name = name,
        latitude = if (isCurrent) Double.NaN else latitude,
        longitude = if (isCurrent) Double.NaN else longitude,
        subtitle = subtitle,
        isCurrentLocation = isCurrent,
    )

    /** Same place, ignoring tiny differences in coordinates. */
    val keyPart: String get() = if (isCurrent) "here" else String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude)

    companion object {
        fun from(location: PlanLocation) = CommuteEnd(
            name = location.name,
            latitude = if (location.isCurrentLocation) 0.0 else location.latitude,
            longitude = if (location.isCurrentLocation) 0.0 else location.longitude,
            subtitle = location.subtitle,
            isCurrent = location.isCurrentLocation,
        )
    }
}

enum class CommuteKind { HOME, WORK, OTHER }

@Serializable
data class Commute(
    val id: String,
    val title: String,
    val from: CommuteEnd,
    val to: CommuteEnd,
    /** True when the user created it; false when Journey learned it. */
    val manual: Boolean = false,
    /** For a learned commute, the hour of day it is usually made (0-23), when that is clear enough to say. */
    val usualHour: Int? = null,
) {
    val key: String get() = "${from.keyPart}>${to.keyPart}"

    val kind: CommuteKind
        get() = when (to.name.trim().lowercase()) {
            "home" -> CommuteKind.HOME
            "work" -> CommuteKind.WORK
            else -> CommuteKind.OTHER
        }

    val fromLabel: String get() = if (from.isCurrent) "your location" else from.name
}

/** How often a trip has been planned, and at what times of day. */
@Serializable
data class TripStat(
    val key: String,
    val from: CommuteEnd,
    val to: CommuteEnd,
    val count: Int,
    val lastEpochSec: Long,
    val hours: List<Int> = List(24) { 0 },
)

@Serializable
data class CommuteData(
    val manual: List<Commute> = emptyList(),
    val stats: List<TripStat> = emptyList(),
    /** Learned commutes the user said they don't want to see. */
    val hidden: List<String> = emptyList(),
)

object CommuteLogic {
    /** A trip must have been planned on this many separate occasions before it is called a commute. */
    const val MIN_COUNT = 2

    /** Planning the same trip again within this window is the same occasion (re-plans, tweaking the time). */
    const val SAME_OCCASION_SECONDS = 30 * 60L

    const val MAX_STATS = 60
    const val MAX_SHOWN = 5
    private const val STALE_SECONDS = 90L * 24 * 3600

    fun title(to: CommuteEnd): String = "To ${to.name}"

    fun record(data: CommuteData, from: CommuteEnd, to: CommuteEnd, now: Instant, zone: ZoneId): CommuteData {
        val key = "${from.keyPart}>${to.keyPart}"
        if (from.keyPart == to.keyPart) return data
        val existing = data.stats.firstOrNull { it.key == key }
        if (existing != null && now.epochSecond - existing.lastEpochSec < SAME_OCCASION_SECONDS) return data
        val hour = now.atZone(zone).hour
        val updated = if (existing == null) {
            TripStat(key, from, to, 1, now.epochSecond, List(24) { if (it == hour) 1 else 0 })
        } else {
            existing.copy(
                from = from,
                to = to,
                count = existing.count + 1,
                lastEpochSec = now.epochSecond,
                hours = existing.hours.mapIndexed { h, n -> if (h == hour) n + 1 else n },
            )
        }
        val stats = (data.stats.filterNot { it.key == key } + updated)
            .sortedWith(compareByDescending<TripStat> { it.count }.thenByDescending { it.lastEpochSec })
            .take(MAX_STATS)
        return data.copy(stats = stats)
    }

    /** The hour a trip is usually made, if at least three trips agree to within an hour. */
    fun usualHour(stat: TripStat): Int? {
        if (stat.count < 3) return null
        val best = (0..23).maxByOrNull { h -> window(stat.hours, h) } ?: return null
        val share = window(stat.hours, best).toFloat() / stat.hours.sum().coerceAtLeast(1)
        if (share < 0.6f) return null
        return listOf((best + 23) % 24, best, (best + 1) % 24).maxByOrNull { stat.hours[it] }
    }

    private fun window(hours: List<Int>, hour: Int): Int =
        hours[(hour + 23) % 24] + hours[hour] + hours[(hour + 1) % 24]

    /** The commutes to show: the user's own first, then the ones they make most, preferring this time of day. */
    fun visible(data: CommuteData, now: Instant, zone: ZoneId, max: Int = MAX_SHOWN): List<Commute> {
        val hour = now.atZone(zone).hour
        val manualKeys = data.manual.map { it.key }.toSet()
        val learned = data.stats
            .filter { it.count >= MIN_COUNT && it.key !in manualKeys && it.key !in data.hidden && now.epochSecond - it.lastEpochSec < STALE_SECONDS }
            .sortedByDescending { score(it, hour, now) }
            .map { stat ->
                Commute(
                    id = "learned:${stat.key}",
                    title = title(stat.to),
                    from = stat.from,
                    to = stat.to,
                    manual = false,
                    usualHour = usualHour(stat),
                )
            }
        return (data.manual + learned).take(max)
    }

    private fun score(stat: TripStat, hour: Int, now: Instant): Double {
        val affinity = window(stat.hours, hour).toDouble() / stat.count.coerceAtLeast(1)
        val recent = if (now.epochSecond - stat.lastEpochSec < 14L * 24 * 3600) 1.0 else 0.0
        return stat.count + 3 * affinity + recent
    }

    fun hide(data: CommuteData, key: String): CommuteData = data.copy(hidden = (data.hidden + key).distinct())

    fun upsertManual(data: CommuteData, commute: Commute): CommuteData {
        val manual = commute.copy(manual = true)
        val index = data.manual.indexOfFirst { it.id == manual.id }
        val list = if (index < 0) data.manual + manual else data.manual.mapIndexed { i, c -> if (i == index) manual else c }
        // A commute that was hidden comes back when the user adds it themselves.
        return data.copy(manual = list, hidden = data.hidden - manual.key)
    }

    fun removeManual(data: CommuteData, id: String): CommuteData = data.copy(manual = data.manual.filterNot { it.id == id })

    /** "around 8 AM" for a learned commute's usual hour. */
    fun usualText(hour: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "around $h12 ${if (hour < 12) "AM" else "PM"}"
    }

    /** The journey to leave for now and the one to take if that one is missed. */
    data class Pick(val primary: Journey, val backup: Journey?)

    fun pick(journeys: List<Journey>, now: Instant): Pick? {
        val usable = journeys.filter { !it.hasCancellation }.sortedBy { it.start }
        val primary = usable.firstOrNull { !it.start.isBefore(now.minusSeconds(60)) } ?: return null
        val backup = usable.firstOrNull { it.start >= primary.start.plusSeconds(120) && it.matchKey != primary.matchKey }
        return Pick(primary, backup)
    }
}
