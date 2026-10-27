/*
 * Feature Module: UI Utilities
 * File: Formatters.kt
 * Description: Pure formatting and geometry helpers for departures, distances and the map.
 */

package com.ssethhyy.journey.utils

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object TimeFormatter {
    val MELBOURNE: ZoneId = ZoneId.of("Australia/Melbourne")

    fun clock(time: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = MELBOURNE): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(time)

    /** Whole minutes until [time], rounding down; negative when in the past. */
    fun minutesUntil(time: Instant, now: Instant): Long = Math.floorDiv(Duration.between(now, time).seconds, 60L)

    /**
     * Short countdown label: "Now", "5 min", or a clock time when it is more than an hour away.
     */
    fun countdown(time: Instant, now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = MELBOURNE): String {
        val minutes = minutesUntil(time, now)
        return when {
            minutes <= 0 -> "Now"
            minutes < 60 -> "$minutes min"
            else -> clock(time, locale, zone)
        }
    }

    /** "On time", "2 min late", "1 min early" or null when there is no live estimate. */
    fun punctuality(delayMinutes: Long?): String? = when {
        delayMinutes == null -> null
        delayMinutes >= 1 -> "$delayMinutes min late"
        delayMinutes <= -1 -> "${-delayMinutes} min early"
        else -> "On time"
    }

    fun dateRange(from: Instant?, to: Instant?, locale: Locale = Locale.getDefault(), zone: ZoneId = MELBOURNE): String? {
        val formatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", locale).withZone(zone)
        return when {
            from != null && to != null -> "${formatter.format(from)} – ${formatter.format(to)}"
            from != null -> "From ${formatter.format(from)}"
            to != null -> "Until ${formatter.format(to)}"
            else -> null
        }
    }

    fun greeting(now: Instant, zone: ZoneId = MELBOURNE): String = when (now.atZone(zone).hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    /** "Good morning, Alex!" or "Good morning!" when no name is set. */
    fun personalGreeting(name: String?, now: Instant, zone: ZoneId = MELBOURNE): String {
        val clean = name?.trim().orEmpty()
        return if (clean.isEmpty()) "${greeting(now, zone)}!" else "${greeting(now, zone)}, $clean!"
    }

    fun ordinal(day: Int): String {
        val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** "Tuesday 12th August · 5:40 pm", short enough for the top bar pill. */
    fun shortToday(now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = MELBOURNE): String {
        val date = now.atZone(zone)
        val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
        val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        return "$weekday ${ordinal(date.dayOfMonth)} $month · ${clock(now, locale, zone)}"
    }

    /** "Today is Tuesday 12th August at 5:40 pm". */
    fun todayLine(now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = MELBOURNE): String {
        val date = now.atZone(zone)
        val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
        val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        return "Today is $weekday ${ordinal(date.dayOfMonth)} $month at ${clock(now, locale, zone)}"
    }
}

object DistanceFormatter {
    private const val WALKING_METERS_PER_MINUTE = 80.0

    fun distance(meters: Double): String = when {
        meters < 1000 -> "${(meters / 10).roundToInt() * 10} m"
        else -> String.format(Locale.getDefault(), "%.1f km", meters / 1000)
    }

    fun walkingMinutes(meters: Double): Int = (meters / WALKING_METERS_PER_MINUTE).roundToInt().coerceAtLeast(1)

    fun distanceAndWalk(meters: Double): String = "${distance(meters)} · ${walkingMinutes(meters)} min walk"
}

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Ground resolution of Mapbox's 512px tiles in metres per density-independent pixel. */
    fun metersPerDp(latitude: Double, zoom: Double): Double =
        (2 * PI * EARTH_RADIUS_M * cos(Math.toRadians(latitude))) / (512.0 * 2.0.pow(zoom))

    /** Radius (metres) of the circle that covers a viewport of the given size. */
    fun viewportRadiusMeters(latitude: Double, zoom: Double, widthDp: Float, heightDp: Float): Double =
        metersPerDp(latitude, zoom) * hypot(widthDp.toDouble(), heightDp.toDouble()) / 2.0
}
