/*
 * Feature Module: Tracking
 * File: JourneySteps.kt
 * Description: Turns a planned journey into turn-by-turn style steps ("Walk 350 m to Flinders Street",
 * "Frankston line to Frankston", "Arrive at Work") and works out which one you are on. Pure logic, unit tested;
 * used by the Essentials island to show your next steps while a journey is tracked.
 */

package com.ssethhyy.journey.tracking

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Duration
import java.time.Instant

enum class StepKind { WALK, RIDE, ARRIVE }

data class JourneyStep(
    val kind: StepKind,
    val mode: TransportMode?,
    /** What to do: "Walk 350 m to Flinders Street", "Frankston line to Frankston". */
    val title: String,
    /** Where and when: "Platform 6 · get off at Richmond". */
    val detail: String,
    /** When this step starts (or, for arriving, when you get there). */
    val time: Instant,
    /** True for the step happening now. */
    val current: Boolean,
)

object JourneySteps {
    /** The steps still ahead (including the one in progress), in order, ending with arriving. */
    fun upcoming(journey: Journey, destination: String, now: Instant): List<JourneyStep> {
        val legs = journey.legs
        val steps = legs.mapIndexedNotNull { index, leg ->
            if (!leg.end.isAfter(now)) return@mapIndexedNotNull null
            val current = !leg.start.isAfter(now)
            val nextName = leg.toName.ifBlank { if (index == legs.lastIndex) destination else "your next stop" }
            when (leg.kind) {
                LegKind.TRANSIT -> JourneyStep(
                    kind = StepKind.RIDE,
                    mode = leg.mode,
                    title = listOfNotNull(leg.label, leg.headsign?.takeIf { it.isNotBlank() }?.let { "to $it" }).joinToString(" "),
                    detail = listOfNotNull(
                        if (current) null else leg.fromName.takeIf { it.isNotBlank() }?.let { "From $it" },
                        if (current) null else leg.platform?.let { "Platform $it" },
                        "get off at $nextName ${TimeFormatter.clock(leg.end)}",
                    ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                    time = leg.start,
                    current = current,
                )
                else -> {
                    val distance = leg.distanceMeters?.takeIf { it >= 20 }?.let { DistanceFormatter.distance(it) + " " }.orEmpty()
                    val minutes = Duration.between(leg.start, leg.end).toMinutes().coerceAtLeast(1)
                    JourneyStep(
                        kind = StepKind.WALK,
                        mode = null,
                        title = "Walk ${distance}to $nextName",
                        detail = "About $minutes min",
                        time = leg.start,
                        current = current,
                    )
                }
            }
        }
        val arrive = JourneyStep(
            kind = StepKind.ARRIVE,
            mode = null,
            title = "Arrive at $destination",
            detail = "",
            time = journey.end,
            current = false,
        )
        return if (journey.end.isAfter(now)) steps + arrive else emptyList()
    }
}
