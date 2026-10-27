/*
 * Feature Module: Tracking
 * File: Tracking.kt
 * Description: What is being tracked, its live status, and pure status logic for departures and journeys.
 */

package com.ssethhyy.journey.tracking

import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Duration
import java.time.Instant

/** Something the user asked to follow live. */
sealed interface TrackingTarget {
    val id: String

    /** A PTV service (run) departing a stop. */
    data class Service(
        val runRef: String,
        val mode: TransportMode,
        val stopId: Int,
        val stopName: String,
        val routeLabel: String,
        val headsign: String,
    ) : TrackingTarget {
        override val id: String get() = "run:$runRef@$stopId"
    }

    /** A planned multi-leg journey. */
    data class Trip(
        val journeyId: String,
        val fromName: String,
        val toName: String,
    ) : TrackingTarget {
        override val id: String get() = "journey:$journeyId"
    }
}

data class TrackingStatus(
    /** Short headline, e.g. "Hurstbridge line in 4 min". */
    val title: String,
    /** Supporting line, e.g. "Westgarth · Platform 1 · 1 min late". */
    val detail: String,
    /** Very short text for the status bar chip, e.g. "4 min". */
    val chip: String,
    /** 0..1 progress towards the next milestone (departure or arrival). */
    val progress: Float,
    val isLive: Boolean,
    /** True once there is nothing left to track (departed / arrived). */
    val finished: Boolean,
    val mode: TransportMode?,
    val updatedAt: Instant,
)

object TrackingLogic {
    /** "Now", "1 min", "12 min", "1 hr 5 min". */
    fun countdown(target: Instant, now: Instant): String {
        val minutes = TimeFormatter.minutesUntil(target, now)
        return when {
            minutes <= 0 -> "Now"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} hr ${minutes % 60} min".removeSuffix(" 0 min")
        }
    }

    private fun progressTowards(target: Instant, startedAt: Instant, now: Instant): Float {
        val total = Duration.between(startedAt, target).seconds.coerceAtLeast(60)
        val done = Duration.between(startedAt, now).seconds.coerceIn(0, total)
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Status for a tracked service. [departure] is null when the run no longer appears on the
     * stop's departure board (it has left).
     */
    fun serviceStatus(target: TrackingTarget.Service, departure: Departure?, startedAt: Instant, now: Instant): TrackingStatus {
        val time = departure?.bestTime
        if (departure == null || time == null || time.isBefore(now.minusSeconds(90))) {
            return TrackingStatus(
                title = "${target.routeLabel} has departed",
                detail = "${target.stopName} · towards ${target.headsign}",
                chip = "Gone",
                progress = 1f,
                isLive = false,
                finished = true,
                mode = target.mode,
                updatedAt = now,
            )
        }
        if (departure.isCancelled) {
            return TrackingStatus(
                title = "${target.routeLabel} to ${target.headsign} is cancelled",
                detail = target.stopName,
                chip = "Cancelled",
                progress = 1f,
                isLive = departure.isLive,
                finished = true,
                mode = target.mode,
                updatedAt = now,
            )
        }
        val countdown = countdown(time, now)
        val title = if (countdown == "Now") "${target.routeLabel} is departing now" else "${target.routeLabel} in $countdown"
        val detail = buildList {
            add(target.stopName)
            departure.platform?.let { add("Platform $it") }
            add("to ${target.headsign}")
            TimeFormatter.punctuality(departure.delayMinutes)?.let { add(it) }
        }.joinToString(" · ")
        return TrackingStatus(
            title = title,
            detail = detail,
            chip = countdown,
            progress = progressTowards(time, startedAt, now),
            isLive = departure.isLive,
            finished = false,
            mode = target.mode,
            updatedAt = now,
        )
    }

    /** Status for a tracked journey: what to do next, and when. */
    fun journeyStatus(journey: Journey, toName: String, now: Instant): TrackingStatus {
        val firstTransit = journey.firstTransit
        val live = journey.transitLegs.any { it.realTime }
        if (!now.isBefore(journey.end)) {
            return TrackingStatus("You've arrived", toName, "Arrived", 1f, live, true, null, now)
        }
        val currentIndex = journey.legs.indexOfFirst { !now.isAfter(it.end) }.takeIf { it >= 0 } ?: journey.legs.lastIndex
        val current = journey.legs[currentIndex]
        val progress = progressTowards(journey.end, journey.start, now)

        // Before boarding the first service.
        if (firstTransit != null && now.isBefore(firstTransit.start)) {
            val countdown = countdown(firstTransit.start, now)
            val stop = firstTransit.fromName.ifBlank { "your stop" }
            val detail = buildList {
                add(stop)
                firstTransit.platform?.let { add("Platform $it") }
                firstTransit.headsign?.let { add("towards $it") }
                TimeFormatter.punctuality(firstTransit.departureDelayMinutes)?.let { add(it) }
            }.joinToString(" · ")
            return TrackingStatus(
                title = if (countdown == "Now") "${firstTransit.label} departing now" else "${firstTransit.label} in $countdown",
                detail = detail,
                chip = countdown,
                progress = progress,
                isLive = firstTransit.realTime,
                finished = false,
                mode = firstTransit.mode,
                updatedAt = now,
            )
        }

        return if (current.kind == LegKind.TRANSIT) {
            TrackingStatus(
                title = "On ${current.label} · get off at ${current.toName}",
                detail = "Arrive ${TimeFormatter.clock(current.end)} · ${countdown(current.end, now)} to go",
                chip = countdown(current.end, now),
                progress = progress,
                isLive = current.realTime,
                finished = false,
                mode = current.mode,
                updatedAt = now,
            )
        } else {
            val next = journey.legs.drop(currentIndex + 1).firstOrNull { it.kind == LegKind.TRANSIT }
            if (next != null) {
                TrackingStatus(
                    title = "Walk to ${next.fromName} · ${next.label} in ${countdown(next.start, now)}",
                    detail = listOfNotNull(next.platform?.let { "Platform $it" }, next.headsign?.let { "towards $it" }).joinToString(" · ").ifEmpty { "Change here" },
                    chip = countdown(next.start, now),
                    progress = progress,
                    isLive = next.realTime,
                    finished = false,
                    mode = next.mode,
                    updatedAt = now,
                )
            } else {
                TrackingStatus(
                    title = "Walk to $toName",
                    detail = "Arrive ${TimeFormatter.clock(journey.end)}",
                    chip = countdown(journey.end, now),
                    progress = progress,
                    isLive = live,
                    finished = false,
                    mode = null,
                    updatedAt = now,
                )
            }
        }
    }
}
