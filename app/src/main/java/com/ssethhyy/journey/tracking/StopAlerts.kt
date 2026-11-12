/*
 * Feature Module: Tracking
 * File: StopAlerts.kt
 * Description: When to show the full-screen "get off here" alert while a planned journey is tracked, and what it
 * says. It fires once per ride, as soon as either you are close to the stop (by the phone's location, which rides
 * with you) or the service is about to arrive there (by its live or timetabled time). Pure logic, unit tested.
 */

package com.ssethhyy.journey.tracking

import com.ssethhyy.journey.data.location.LatLng
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.utils.GeoUtils
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Duration
import java.time.Instant

/** What the full-screen alert shows. */
data class StopAlert(
    /** Identifies the ride, so each one alerts only once. */
    val key: String,
    val stopName: String,
    val mode: TransportMode?,
    /** "Press the stop button" or "Your stop is next, get ready to get off". */
    val instruction: String,
    /** "Arriving 6:50 pm · Platform 2". */
    val detail: String,
    val arrival: Instant,
    val routeLabel: String,
)

object StopAlertLogic {
    /** Trains and V/Line run further between stops, so they warn earlier and from further away. */
    fun leadSeconds(mode: TransportMode?): Long = when (mode) {
        TransportMode.TRAIN, TransportMode.VLINE -> 100
        else -> 70
    }

    fun nearMeters(mode: TransportMode?): Double = when (mode) {
        TransportMode.VLINE -> 1_500.0
        TransportMode.TRAIN -> 800.0
        else -> 350.0
    }

    /** Give the ride a minute to get going before distance counts, so boarding right next to the stop doesn't alert. */
    private const val SETTLE_SECONDS = 60L

    /** A ride that ended more than this long ago is over; no late alert. */
    private const val TOO_LATE_SECONDS = 120L

    fun key(journey: Journey, leg: JourneyLeg): String = "${journey.id}:${leg.label}@${(leg.scheduledStart ?: leg.start).epochSecond}"

    /**
     * The alert to show now, if any: for the ride in progress (or about to finish) that hasn't alerted yet, once
     * it is within [leadSeconds] of arriving or you are within [nearMeters] of the stop.
     */
    fun due(journey: Journey, here: LatLng?, now: Instant, alreadyShown: Set<String>): StopAlert? {
        for (leg in journey.legs) {
            if (leg.kind != LegKind.TRANSIT) continue
            val key = key(journey, leg)
            if (key in alreadyShown) continue
            if (now.isBefore(leg.start)) continue
            if (now.isAfter(leg.end.plusSeconds(TOO_LATE_SECONDS))) continue
            val secondsLeft = Duration.between(now, leg.end).seconds
            val stop = leg.path.lastOrNull()
            val near = here != null && stop != null && now.isAfter(leg.start.plusSeconds(SETTLE_SECONDS)) &&
                GeoUtils.haversineMeters(here.latitude, here.longitude, stop.latitude, stop.longitude) <= nearMeters(leg.mode)
            if (secondsLeft <= leadSeconds(leg.mode) || near) return alertFor(journey, leg)
        }
        return null
    }

    fun instruction(mode: TransportMode?): String = when (mode) {
        TransportMode.BUS, TransportMode.NIGHT_BUS, TransportMode.TRAM -> "Press the stop button"
        else -> "Get ready, your stop is next"
    }

    fun alertFor(journey: Journey, leg: JourneyLeg): StopAlert = StopAlert(
        key = key(journey, leg),
        stopName = leg.toName.ifBlank { "your stop" },
        mode = leg.mode,
        instruction = instruction(leg.mode),
        detail = listOfNotNull("Arriving ${TimeFormatter.clock(leg.end)}", leg.arrivalPlatform?.let { "Platform $it" }).joinToString(" · "),
        arrival = leg.end,
        routeLabel = leg.label,
    )
}

/** How the alert may vibrate, from the setting and the phone's ring mode. */
enum class RingMode { NORMAL, VIBRATE, SILENT }

object StopAlertVibration {
    /** Vibrate only if the setting allows it and the phone isn't on silent. It never makes a sound. */
    fun shouldVibrate(settingOn: Boolean, ringMode: RingMode): Boolean = settingOn && ringMode != RingMode.SILENT
}
