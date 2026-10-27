/*
 * Feature Module: Journey planner
 * File: JourneyModels.kt
 * Description: Journey/leg/location models plus pure mapping and polyline decoding.
 */

package com.ssethhyy.journey.data.planner

import com.ssethhyy.journey.data.model.TransportMode
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Something a journey can start or end at. */
data class PlanLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String? = null,
    val isCurrentLocation: Boolean = false,
    val placeId: String? = null,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)
}

enum class LegKind { WALK, TRANSIT, OTHER }

data class JourneyLeg(
    val kind: LegKind,
    val mode: TransportMode?,
    val rawMode: String,
    val fromName: String,
    val toName: String,
    val start: Instant,
    val end: Instant,
    val scheduledStart: Instant?,
    val scheduledEnd: Instant?,
    val realTime: Boolean,
    val headsign: String?,
    val routeShortName: String?,
    val routeLongName: String?,
    val routeColor: Long?,
    val routeTextColor: Long?,
    val distanceMeters: Double?,
    val platform: String?,
    val arrivalPlatform: String?,
    val cancelled: Boolean,
    val intermediateStops: List<String>,
    val path: List<GeoPoint>,
) {
    val durationMinutes: Long get() = Duration.between(start, end).toMinutes().coerceAtLeast(1)

    /** Delay of the departure in minutes when real-time data is available. */
    val departureDelayMinutes: Long?
        get() = if (realTime && scheduledStart != null) Duration.between(scheduledStart, start).toMinutes() else null

    val label: String get() = routeShortName?.takeIf { it.isNotBlank() } ?: mode?.label ?: rawMode.lowercase().replaceFirstChar { it.uppercase() }
}

data class Journey(
    val id: String,
    val start: Instant,
    val end: Instant,
    val transfers: Int,
    val legs: List<JourneyLeg>,
) {
    val durationMinutes: Long get() = Duration.between(start, end).toMinutes()
    val transitLegs: List<JourneyLeg> get() = legs.filter { it.kind == LegKind.TRANSIT }
    val firstTransit: JourneyLeg? get() = transitLegs.firstOrNull()
    val walkMeters: Double get() = legs.filter { it.kind == LegKind.WALK }.sumOf { it.distanceMeters ?: 0.0 }
    val isWalkOnly: Boolean get() = transitLegs.isEmpty()
    val hasCancellation: Boolean get() = legs.any { it.cancelled }

    /**
     * Identifies "the same" journey across re-plans: the transit services used and their
     * scheduled departure times (real-time estimates change, the schedule does not).
     */
    val matchKey: String
        get() = transitLegs.joinToString("|") { "${it.label}@${(it.scheduledStart ?: it.start).epochSecond}" }
            .ifEmpty { "walk@${start.epochSecond / 300}" }
}

data class JourneyResults(
    val journeys: List<Journey>,
    val nextPageCursor: String?,
)

data class GeocodeResult(
    val name: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double,
    val type: String,
)

object JourneyMapper {
    private fun instant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Maps MOTIS / GTFS-extended modes to Victorian transport modes. */
    fun transportMode(mode: String): TransportMode? = when (mode.uppercase()) {
        "SUBWAY", "METRO", "RAIL", "SUBURBAN", "HIGHSPEED_RAIL" -> TransportMode.TRAIN
        "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "COACH" -> TransportMode.VLINE
        "TRAM", "CABLE_CAR" -> TransportMode.TRAM
        "BUS" -> TransportMode.BUS
        else -> null
    }

    private fun legKind(mode: String): LegKind = when (mode.uppercase()) {
        "WALK", "FOOT" -> LegKind.WALK
        "BIKE", "CAR", "RENTAL", "ODM", "FLEX" -> LegKind.OTHER
        else -> LegKind.TRANSIT
    }

    private fun color(hex: String?): Long? = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    fun leg(dto: LegDto): JourneyLeg {
        val start = instant(dto.startTime) ?: instant(dto.from.departure) ?: Instant.EPOCH
        val end = instant(dto.endTime) ?: instant(dto.to.arrival) ?: start.plusSeconds(dto.duration)
        return JourneyLeg(
            kind = legKind(dto.mode),
            mode = transportMode(dto.mode),
            rawMode = dto.mode,
            fromName = cleanName(dto.from.name),
            toName = cleanName(dto.to.name),
            start = start,
            end = end,
            scheduledStart = instant(dto.scheduledStartTime),
            scheduledEnd = instant(dto.scheduledEndTime),
            realTime = dto.realTime,
            headsign = dto.headsign?.takeIf { it.isNotBlank() },
            routeShortName = dto.routeShortName?.takeIf { it.isNotBlank() },
            routeLongName = dto.routeLongName?.takeIf { it.isNotBlank() },
            routeColor = color(dto.routeColor),
            routeTextColor = color(dto.routeTextColor),
            distanceMeters = dto.distance,
            platform = platformOrNull(dto.from.track ?: dto.from.scheduledTrack),
            arrivalPlatform = platformOrNull(dto.to.track ?: dto.to.scheduledTrack),
            cancelled = dto.cancelled || dto.from.cancelled,
            intermediateStops = dto.intermediateStops.map { cleanName(it.name) },
            path = dto.legGeometry?.let { decodePolyline(it.points, it.precision) }.orEmpty(),
        )
    }

    fun journey(dto: ItineraryDto, index: Int): Journey {
        val legs = dto.legs.map(::leg)
        val start = instant(dto.startTime) ?: legs.firstOrNull()?.start ?: Instant.EPOCH
        val end = instant(dto.endTime) ?: legs.lastOrNull()?.end ?: start
        return Journey(
            id = "${start.epochSecond}-${end.epochSecond}-${legs.joinToString("|") { it.label }}-$index",
            start = start,
            end = end,
            transfers = dto.transfers,
            legs = legs,
        )
    }

    fun results(dto: PlanResponseDto): JourneyResults {
        val transit = dto.itineraries.mapIndexed { i, it -> journey(it, i) }
        // Offer a walk-only option when the planner suggests one and it is reasonably short.
        val direct = dto.direct.mapIndexed { i, it -> journey(it, 100 + i) }
            .filter { it.isWalkOnly && it.durationMinutes <= 40 }
            .take(1)
        return JourneyResults(
            journeys = (transit + direct).distinctBy { it.id }.sortedBy { it.end },
            nextPageCursor = dto.nextPageCursor,
        )
    }

    fun geocode(dto: GeocodeMatchDto): GeocodeResult {
        val locality = dto.areas.firstOrNull { it.isDefault }?.name
            ?: dto.areas.filter { it.adminLevel >= 7 }.maxByOrNull { it.adminLevel }?.name
        val name = if (dto.type.equals("ADDRESS", true) && dto.street != null) {
            listOfNotNull(dto.houseNumber, dto.street).joinToString(" ")
        } else {
            dto.name
        }
        return GeocodeResult(
            name = name,
            subtitle = locality,
            latitude = dto.lat,
            longitude = dto.lon,
            type = dto.type,
        )
    }

    /** GTFS "tracks" are sometimes descriptions (e.g. "Replacement bus"); only keep real platform numbers. */
    fun platformOrNull(track: String?): String? =
        track?.trim()?.takeIf { it.isNotEmpty() && it.length <= 4 && it.any(Char::isDigit) }

    /** Planner stop names include words like "Railway Station"; trim to what signage shows. */
    fun cleanName(name: String?): String = name.orEmpty()
        .replace(" Railway Station", " Station")
        .let { if (it == "START" || it == "END") "" else it }

    /** Decodes a Google encoded polyline with the given precision (MOTIS uses 7). */
    fun decodePolyline(encoded: String, precision: Int = 6): List<GeoPoint> {
        val factor = 10.0.pow(precision)
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            for (axis in 0..1) {
                var result = 0L
                var shift = 0
                var b: Int
                do {
                    if (index >= encoded.length) return points
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                } while (b >= 0x20)
                val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                if (axis == 0) lat += delta else lon += delta
            }
            points += GeoPoint(lat / factor, lon / factor)
        }
        return points
    }
}
