/*
 * Feature Module: PTV API
 * File: PtvMappers.kt
 * Description: Pure mapping functions from API DTOs to app models.
 */

package com.ssethhyy.journey.data.api

import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.Facility
import com.ssethhyy.journey.data.model.FacilityKind
import com.ssethhyy.journey.data.model.PatternStop
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.StopDetails
import com.ssethhyy.journey.data.model.StoppingPattern
import com.ssethhyy.journey.data.model.TripProgress
import com.ssethhyy.journey.data.model.VehiclePosition
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.model.VehicleInfo
import java.time.Instant

object PtvMappers {
    fun parseInstant(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    }

    fun route(dto: RouteDto): Route = Route(
        id = dto.routeId,
        mode = TransportMode.fromRouteType(dto.routeType),
        name = dto.routeName.orEmpty().trim(),
        number = dto.routeNumber.orEmpty().trim(),
        gtfsId = dto.routeGtfsId,
        serviceStatus = dto.serviceStatus?.description,
    )

    fun stop(dto: StopGeoDto): Stop = Stop(
        id = dto.id,
        mode = TransportMode.fromRouteType(dto.routeType),
        name = dto.name.orEmpty().trim(),
        suburb = dto.suburb?.trim()?.takeIf { it.isNotEmpty() },
        latitude = dto.latitude ?: 0.0,
        longitude = dto.longitude ?: 0.0,
        distanceMeters = dto.distance?.takeIf { it > 0 },
        landmark = dto.landmark?.takeIf { it.isNotBlank() },
        routes = dto.routes.map(::route).distinctBy { it.id },
        sequence = dto.sequence ?: 0,
    )

    fun stop(dto: DepartureStopDto): Stop = Stop(
        id = dto.id,
        mode = TransportMode.fromRouteType(dto.routeType),
        name = dto.name.orEmpty().trim(),
        suburb = dto.suburb?.trim()?.takeIf { it.isNotEmpty() },
        latitude = dto.latitude ?: 0.0,
        longitude = dto.longitude ?: 0.0,
        sequence = dto.sequence ?: 0,
    )

    fun disruption(dto: DisruptionDto, modeKey: String? = null): Disruption = Disruption(
        id = dto.id,
        title = dto.title.orEmpty().trim(),
        description = dto.description.orEmpty().trim(),
        url = dto.url?.takeIf { it.isNotBlank() },
        status = dto.status,
        type = dto.type,
        publishedOn = parseInstant(dto.publishedOn),
        fromDate = parseInstant(dto.fromDate),
        toDate = parseInstant(dto.toDate),
        routes = dto.routes.map {
            Route(
                id = it.routeId,
                mode = TransportMode.fromRouteType(it.routeType),
                name = it.routeName.orEmpty(),
                number = it.routeNumber.orEmpty(),
            )
        },
        stopNames = dto.stops.mapNotNull { it.stopName },
        mode = modeKey?.let(TransportMode::fromDisruptionKey)
            ?: dto.routes.firstOrNull()?.let { TransportMode.fromRouteType(it.routeType) },
    )

    fun disruptions(response: DisruptionsResponse): List<Disruption> =
        response.disruptions
            .flatMap { (key, list) -> list.map { disruption(it, key) } }
            .distinctBy { it.id }
            .sortedWith(compareBy<Disruption> { it.severity.ordinal }.thenByDescending { it.publishedOn })

    private fun vehicle(run: RunDto?): VehicleInfo? = run?.vehicleDescriptor?.let {
        VehicleInfo(description = it.description?.takeIf { d -> d.isNotBlank() }, lowFloor = it.lowFloor, airConditioned = it.airConditioned, id = it.id?.takeIf { id -> id.isNotBlank() })
    }

    fun departureBoard(response: DeparturesResponse, now: Instant = Instant.now()): DepartureBoard {
        val routes = response.routes.mapValues { route(it.value) }
        val disruptions = response.disruptions.values.associate { it.id to disruption(it) }
        val departures = response.departures.map { dto ->
            val run = dto.runRef?.let { response.runs[it] } ?: dto.runId?.let { response.runs[it.toString()] }
            val direction = response.directions[dto.directionId.toString()]
            Departure(
                stopId = dto.stopId,
                route = routes[dto.routeId.toString()],
                runRef = dto.runRef ?: dto.runId?.toString(),
                directionId = dto.directionId,
                directionName = direction?.directionName,
                destination = run?.destinationName?.trim()?.takeIf { it.isNotEmpty() },
                scheduled = parseInstant(dto.scheduledUtc),
                estimated = parseInstant(dto.estimatedUtc),
                platform = dto.platformNumber?.takeIf { it.isNotBlank() },
                atPlatform = dto.atPlatform,
                flags = dto.flags,
                disruptionIds = dto.disruptionIds,
                isExpress = (run?.expressStopCount ?: 0) > 0,
                isCancelled = run?.status.equals("cancelled", ignoreCase = true) ||
                    dto.flags.orEmpty().split("-").any { it.equals("CAN", ignoreCase = true) },
                vehicle = vehicle(run),
            )
        }.sortedBy { it.bestTime ?: Instant.MAX }

        return DepartureBoard(
            departures = departures,
            disruptions = disruptions,
            routes = routes.values.sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.name })),
            fetchedAt = now,
        )
    }

    fun stoppingPattern(response: DeparturesResponse, withPath: Boolean = true): StoppingPattern {
        val stops = response.stops
        val firstRun = response.runs.values.firstOrNull()
        val patternStops = response.departures.mapNotNull { dto ->
            val stopDto = stops[dto.stopId.toString()] ?: return@mapNotNull null
            PatternStop(
                stop = stop(stopDto),
                scheduled = parseInstant(dto.scheduledUtc),
                estimated = parseInstant(dto.estimatedUtc),
                platform = dto.platformNumber?.takeIf { it.isNotBlank() },
                sequence = dto.sequence,
            )
        }.sortedWith(compareBy({ it.sequence }, { it.scheduled }))
        val position = firstRun?.vehiclePosition?.let { p ->
            if (p.latitude != null && p.longitude != null && (p.latitude != 0.0 || p.longitude != 0.0)) {
                VehiclePosition(p.latitude, p.longitude, p.bearing, parseInstant(p.datetimeUtc), parseInstant(p.expiryTime))
            } else {
                null
            }
        }
        val routeDto = response.routes.values.firstOrNull()
        val geopaths = routeDto?.geopath.orEmpty().flatMap { it.paths }.map(::parseGeopath).filter { it.size > 1 }
        return StoppingPattern(
            route = routeDto?.let(::route),
            destination = firstRun?.destinationName,
            stops = patternStops,
            vehicle = vehicle(firstRun),
            disruptions = response.disruptions.values.map { disruption(it) },
            vehiclePosition = position,
            path = if (withPath) TripProgress.bestPath(patternStops.map { it.stop.latitude to it.stop.longitude }, geopaths) else emptyList(),
        )
    }

    fun stopDetails(dto: StopDetailsDto, fallback: Stop?): StopDetails {
        val facilities = buildList {
            dto.amenities?.let { a ->
                if (a.toilet == true) add(Facility(FacilityKind.TOILET))
                if (a.taxiRank == true) add(Facility(FacilityKind.TAXI_RANK))
                a.carParking?.takeIf { it.isNotBlank() && it != "0" }?.let { add(Facility(FacilityKind.CAR_PARKING, "$it spaces")) }
                if (a.cctv == true) add(Facility(FacilityKind.CCTV))
            }
            dto.accessibility?.let { a ->
                if (a.stopAccessible == true || a.wheelchair?.accessibleRamp == true) add(Facility(FacilityKind.WHEELCHAIR))
                if (a.lift == true) add(Facility(FacilityKind.LIFT))
                if (a.escalator == true) add(Facility(FacilityKind.ESCALATOR))
                if (a.stairs == true) add(Facility(FacilityKind.STAIRS))
                if (a.hearingLoop == true) add(Facility(FacilityKind.HEARING_LOOP))
                if (a.tactile == true) add(Facility(FacilityKind.TACTILE))
                if (a.waitingRoom == true) add(Facility(FacilityKind.WAITING_ROOM))
                if (a.lighting == true) add(Facility(FacilityKind.LIGHTING))
                if (a.audioInfo == true) add(Facility(FacilityKind.AUDIO_INFO))
            }
        }
        val stop = Stop(
            id = dto.id,
            mode = TransportMode.fromRouteType(dto.routeType),
            name = dto.name?.trim().orEmpty().ifEmpty { fallback?.name.orEmpty() },
            suburb = dto.location?.suburb ?: fallback?.suburb,
            latitude = dto.location?.gps?.latitude ?: fallback?.latitude ?: 0.0,
            longitude = dto.location?.gps?.longitude ?: fallback?.longitude ?: 0.0,
            routes = dto.routes.map(::route).ifEmpty { fallback?.routes.orEmpty() },
        )
        return StopDetails(stop = stop, facilities = facilities, stationDescription = dto.stationDescription?.takeIf { it.isNotBlank() })
    }

    /**
     * PTV geopaths are encoded as whitespace separated "lat,lon" pairs.
     * Some responses separate pairs with commas only, so both forms are handled.
     */
    fun parseGeopath(path: String): List<Pair<Double, Double>> {
        val tokens = path.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val numbers = tokens.mapNotNull { it.toDoubleOrNull() }
        if (numbers.size != tokens.size || numbers.size % 2 != 0) return emptyList()
        return numbers.chunked(2).map { (lat, lon) -> lat to lon }
    }
}
