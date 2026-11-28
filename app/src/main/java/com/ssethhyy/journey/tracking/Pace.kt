/*
 * Feature Module: Tracking
 * File: Pace.kt
 * Description: How fast you need to go to make the service you're tracking, and when to set off.
 *
 * Both come from how far you are from the stop you board at (straight-line distance, stretched a little for real
 * streets) and how long is left before the service leaves. They're deliberately rough: a pace to aim for, not a speed.
 */

package com.ssethhyy.journey.tracking

import java.time.Instant

/** A pace to aim for, from a stroll to a run. */
enum class Pace(val label: String) {
    LEISURELY("Leisurely walk"),
    WALK("Walk"),
    BRISK("Brisk walk"),
    JOG("Jog"),
    RUN("Run"),
    /** Not even running would make it. */
    TOO_LATE("You may miss it"),
}

object PaceLogic {
    /** Streets aren't straight lines: walking distance is about this much longer. */
    private const val ROUTE_FACTOR = 1.3
    /** An ordinary walking pace, in metres a second, for "when to leave". */
    const val WALKING_SPEED = 1.35
    /** Time to spare at the stop (finding the platform, tapping on). */
    const val SPARE_SECONDS = 60L
    /** Closer than this you're at the stop: no pace or leave alert needed. */
    const val AT_STOP_METERS = 80.0

    /** How far you'll actually walk to cover [straightMeters]. */
    fun walkingMeters(straightMeters: Double) = straightMeters * ROUTE_FACTOR

    /** The pace needed to cover [straightMeters] with time to spare before [departure]; null when you're at the stop. */
    fun pace(straightMeters: Double, departure: Instant, now: Instant): Pace? {
        if (straightMeters < AT_STOP_METERS) return null
        val seconds = departure.epochSecond - now.epochSecond - SPARE_SECONDS / 2
        if (seconds <= 0) return Pace.TOO_LATE
        val speed = walkingMeters(straightMeters) / seconds
        return when {
            speed < 0.9 -> Pace.LEISURELY
            speed < 1.4 -> Pace.WALK
            speed < 1.9 -> Pace.BRISK
            speed < 2.8 -> Pace.JOG
            speed < 4.5 -> Pace.RUN
            else -> Pace.TOO_LATE
        }
    }

    /** When to set off at an ordinary walking pace to reach the stop with time to spare before [departure]. */
    fun leaveAt(straightMeters: Double, departure: Instant): Instant =
        departure.minusSeconds((walkingMeters(straightMeters) / WALKING_SPEED).toLong() + SPARE_SECONDS)

    /** Minutes of walking for [straightMeters], to the nearest minute and at least 1. */
    fun walkMinutes(straightMeters: Double): Long = Math.round((walkingMeters(straightMeters) / WALKING_SPEED) / 60.0).coerceAtLeast(1)
}
