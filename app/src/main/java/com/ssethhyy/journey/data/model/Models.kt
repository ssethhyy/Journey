/*
 * Feature Module: Domain Models
 * File: Models.kt
 * Description: App-level models mapped from PTV API responses.
 */

package com.ssethhyy.journey.data.model

import java.time.Duration
import java.time.Instant

data class Route(
    val id: Int,
    val mode: TransportMode,
    val name: String,
    val number: String,
    val gtfsId: String? = null,
    val serviceStatus: String? = null,
) {
    /** Short label for badges: the route number for trams/buses, the line name for trains. */
    val badgeLabel: String
        get() {
            if (number.isBlank()) return name
            // PTV suffixes some numbers with words ("250-251 combined"); keep the number itself.
            val first = number.trim().substringBefore(' ')
            return if (first.any(Char::isDigit)) first else number.trim()
        }

    /** Longer label for list rows. */
    val displayName: String get() = if (number.isNotBlank() && number != name) "$number $name" else name

    val key: String get() = "${mode.routeType}:$id"
}

data class Stop(
    val id: Int,
    val mode: TransportMode,
    val name: String,
    val suburb: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double? = null,
    val landmark: String? = null,
    val routes: List<Route> = emptyList(),
    val sequence: Int = 0,
) {
    val key: String get() = stopKey(mode, id)

    companion object {
        fun stopKey(mode: TransportMode, id: Int) = "${mode.routeType}:$id"
    }
}

data class Departure(
    val stopId: Int,
    val route: Route?,
    val runRef: String?,
    val directionId: Int,
    val directionName: String?,
    val destination: String?,
    val scheduled: Instant?,
    val estimated: Instant?,
    val platform: String?,
    val atPlatform: Boolean,
    val flags: String?,
    val disruptionIds: List<Long>,
    val isExpress: Boolean,
    val isCancelled: Boolean,
    val vehicle: VehicleInfo?,
) {
    val bestTime: Instant? get() = estimated ?: scheduled
    val isLive: Boolean get() = estimated != null

    /** Delay in whole minutes (positive = late). Null if no live data. */
    val delayMinutes: Long?
        get() = if (estimated != null && scheduled != null) Duration.between(scheduled, estimated).toMinutes() else null

    val headsign: String get() = destination ?: directionName ?: route?.name.orEmpty()
}

data class VehicleInfo(
    val description: String?,
    val lowFloor: Boolean?,
    val airConditioned: Boolean?,
    /** The physical vehicle: carriage numbers for a Metro train ("1380T-1433T-..."), the fleet number for a tram or bus. */
    val id: String? = null,
)

data class DepartureBoard(
    val departures: List<Departure>,
    val disruptions: Map<Long, Disruption>,
    val routes: List<Route>,
    val fetchedAt: Instant,
)

enum class DisruptionSeverity { SEVERE, MAJOR, MINOR, PLANNED, INFO }

data class Disruption(
    val id: Long,
    val title: String,
    val description: String,
    val url: String?,
    val status: String?,
    val type: String?,
    val publishedOn: Instant?,
    val fromDate: Instant?,
    val toDate: Instant?,
    val routes: List<Route>,
    val stopNames: List<String>,
    val mode: TransportMode?,
) {
    val severity: DisruptionSeverity get() = severityFor(type)

    val isCurrent: Boolean get() = status.isNullOrBlank() || status.equals("Current", ignoreCase = true)

    companion object {
        /**
         * Disruptions worth showing for a stop: those PTV links to the stop itself plus line-wide ones
         * referenced by its departures (PTV rarely links line works to individual stops).
         */
        fun forStop(stopLevel: List<Disruption>, board: DepartureBoard?): List<Disruption> {
            val referenced = board?.departures.orEmpty().flatMap { it.disruptionIds }.toSet()
            val fromBoard = board?.disruptions.orEmpty().filterKeys { it in referenced }.values
            return (stopLevel + fromBoard)
                .filter { it.isCurrent }
                .distinctBy { it.id }
                .sortedWith(compareBy<Disruption> { it.severity }.thenByDescending { it.publishedOn })
        }

        fun severityFor(type: String?): DisruptionSeverity {
            val t = type.orEmpty().lowercase()
            return when {
                "suspend" in t || "cancel" in t || ("closure" in t && "planned" !in t) -> DisruptionSeverity.SEVERE
                "major" in t -> DisruptionSeverity.MAJOR
                "minor" in t || "delay" in t -> DisruptionSeverity.MINOR
                "planned" in t || "works" in t -> DisruptionSeverity.PLANNED
                else -> DisruptionSeverity.INFO
            }
        }
    }
}

data class Facility(val kind: FacilityKind, val detail: String? = null)

enum class FacilityKind(val label: String) {
    TOILET("Toilets"),
    TAXI_RANK("Taxi rank"),
    CAR_PARKING("Car parking"),
    CCTV("CCTV"),
    WHEELCHAIR("Wheelchair accessible"),
    LIFT("Lift"),
    ESCALATOR("Escalator"),
    STAIRS("Stairs"),
    HEARING_LOOP("Hearing loop"),
    TACTILE("Tactile indicators"),
    WAITING_ROOM("Waiting room"),
    LIGHTING("Lighting"),
    AUDIO_INFO("Audio announcements"),
}

data class StopDetails(
    val stop: Stop,
    val facilities: List<Facility>,
    val stationDescription: String?,
)

data class RouteDirection(
    val id: Int,
    val name: String,
    val description: String?,
)

data class RouteDetails(
    val route: Route,
    val directions: List<RouteDirection>,
    /** Polylines per direction id (lat, lon pairs). */
    val geopaths: Map<Int, List<List<Pair<Double, Double>>>>,
)

data class PatternStop(
    val stop: Stop,
    val scheduled: Instant?,
    val estimated: Instant?,
    val platform: String?,
    val sequence: Int,
) {
    val bestTime: Instant? get() = estimated ?: scheduled

    /** Seconds behind schedule (negative = early), when both times are known. */
    val delaySeconds: Long? get() = if (scheduled != null && estimated != null) estimated.epochSecond - scheduled.epochSecond else null
}

/** Where a vehicle is right now, as reported by PTV. */
data class VehiclePosition(
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,
    /** When PTV recorded this position, if it says. */
    val recordedAt: Instant?,
    /** After this moment the position should no longer be trusted. */
    val expiresAt: Instant?,
) {
    /** PTV positions go stale when a vehicle drops off the feed; ignore ones that are clearly old. */
    fun isFresh(now: Instant): Boolean = when {
        expiresAt != null -> now.isBefore(expiresAt)
        recordedAt != null -> Duration.between(recordedAt, now).abs().toMinutes() < MAX_AGE_MINUTES
        else -> true
    }

    companion object {
        const val MAX_AGE_MINUTES = 5L
    }
}

data class StoppingPattern(
    val route: Route?,
    val destination: String?,
    val stops: List<PatternStop>,
    val vehicle: VehicleInfo?,
    val disruptions: List<Disruption>,
    val vehiclePosition: VehiclePosition?,
    /** The route's line on the map (best matching geopath), or empty when PTV didn't send one. */
    val path: List<Pair<Double, Double>> = emptyList(),
)

data class SearchResults(
    val stops: List<Stop>,
    val routes: List<Route>,
) {
    val isEmpty: Boolean get() = stops.isEmpty() && routes.isEmpty()
}
