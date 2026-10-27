/*
 * Feature Module: PTV API
 * File: PtvDtos.kt
 * Description: kotlinx.serialization models for PTV Timetable API v3 responses.
 */

package com.ssethhyy.journey.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class StatusDto(
    val version: String? = null,
    val health: Int? = null,
)

@Serializable
data class RouteServiceStatusDto(
    val description: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class GeopathDto(
    @SerialName("direction_id") val directionId: Int = 0,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_to") val validTo: String? = null,
    val paths: List<String> = emptyList(),
)

@Serializable
data class RouteDto(
    @SerialName("route_type") val routeType: Int = 0,
    @SerialName("route_id") val routeId: Int = 0,
    @SerialName("route_name") val routeName: String? = null,
    @SerialName("route_number") val routeNumber: String? = null,
    @SerialName("route_gtfs_id") val routeGtfsId: String? = null,
    @SerialName("route_service_status") val serviceStatus: RouteServiceStatusDto? = null,
    val geopath: List<GeopathDto> = emptyList(),
)

@Serializable
data class StopGeoDto(
    @SerialName("stop_distance") val distance: Double? = null,
    @SerialName("stop_suburb") val suburb: String? = null,
    @SerialName("stop_name") val name: String? = null,
    @SerialName("stop_id") val id: Int = 0,
    @SerialName("route_type") val routeType: Int = 0,
    @SerialName("stop_latitude") val latitude: Double? = null,
    @SerialName("stop_longitude") val longitude: Double? = null,
    @SerialName("stop_landmark") val landmark: String? = null,
    @SerialName("stop_sequence") val sequence: Int? = null,
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
data class StopsByDistanceResponse(
    val stops: List<StopGeoDto> = emptyList(),
    val status: StatusDto? = null,
)

@Serializable
data class StopsOnRouteResponse(
    val stops: List<StopGeoDto> = emptyList(),
    val geopath: List<GeopathDto> = emptyList(),
)

@Serializable
data class SearchResponse(
    val stops: List<StopGeoDto> = emptyList(),
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
data class DepartureDto(
    @SerialName("stop_id") val stopId: Int = 0,
    @SerialName("route_id") val routeId: Int = 0,
    @SerialName("run_id") val runId: Int? = null,
    @SerialName("run_ref") val runRef: String? = null,
    @SerialName("direction_id") val directionId: Int = 0,
    @SerialName("disruption_ids") val disruptionIds: List<Long> = emptyList(),
    @SerialName("scheduled_departure_utc") val scheduledUtc: String? = null,
    @SerialName("estimated_departure_utc") val estimatedUtc: String? = null,
    @SerialName("at_platform") val atPlatform: Boolean = false,
    @SerialName("platform_number") val platformNumber: String? = null,
    val flags: String? = null,
    @SerialName("departure_sequence") val sequence: Int = 0,
)

@Serializable
data class VehicleDescriptorDto(
    val operator: String? = null,
    val id: String? = null,
    @SerialName("low_floor") val lowFloor: Boolean? = null,
    @SerialName("air_conditioned") val airConditioned: Boolean? = null,
    val description: String? = null,
)

@Serializable
data class VehiclePositionDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double? = null,
    @SerialName("datetime_utc") val datetimeUtc: String? = null,
    @SerialName("expiry_time") val expiryTime: String? = null,
)

@Serializable
data class RunDto(
    @SerialName("run_id") val runId: Int? = null,
    @SerialName("run_ref") val runRef: String? = null,
    @SerialName("route_id") val routeId: Int = 0,
    @SerialName("route_type") val routeType: Int = 0,
    @SerialName("final_stop_id") val finalStopId: Int? = null,
    @SerialName("destination_name") val destinationName: String? = null,
    val status: String? = null,
    @SerialName("direction_id") val directionId: Int = 0,
    @SerialName("express_stop_count") val expressStopCount: Int = 0,
    @SerialName("vehicle_descriptor") val vehicleDescriptor: VehicleDescriptorDto? = null,
    @SerialName("vehicle_position") val vehiclePosition: VehiclePositionDto? = null,
)

@Serializable
data class DirectionDto(
    @SerialName("direction_id") val directionId: Int = 0,
    @SerialName("direction_name") val directionName: String? = null,
    @SerialName("route_direction_description") val description: String? = null,
    @SerialName("route_id") val routeId: Int = 0,
    @SerialName("route_type") val routeType: Int = 0,
)

@Serializable
data class DirectionsResponse(
    val directions: List<DirectionDto> = emptyList(),
)

@Serializable
data class DisruptionRouteDto(
    @SerialName("route_type") val routeType: Int = 0,
    @SerialName("route_id") val routeId: Int = 0,
    @SerialName("route_name") val routeName: String? = null,
    @SerialName("route_number") val routeNumber: String? = null,
)

@Serializable
data class DisruptionStopDto(
    @SerialName("stop_id") val stopId: Int = 0,
    @SerialName("stop_name") val stopName: String? = null,
)

@Serializable
data class DisruptionDto(
    @SerialName("disruption_id") val id: Long = 0,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    @SerialName("disruption_status") val status: String? = null,
    @SerialName("disruption_type") val type: String? = null,
    @SerialName("published_on") val publishedOn: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("from_date") val fromDate: String? = null,
    @SerialName("to_date") val toDate: String? = null,
    val routes: List<DisruptionRouteDto> = emptyList(),
    val stops: List<DisruptionStopDto> = emptyList(),
    val colour: String? = null,
)

@Serializable
data class DisruptionsResponse(
    val disruptions: Map<String, List<DisruptionDto>> = emptyMap(),
)

@Serializable
data class DepartureStopDto(
    @SerialName("stop_id") val id: Int = 0,
    @SerialName("stop_name") val name: String? = null,
    @SerialName("stop_suburb") val suburb: String? = null,
    @SerialName("route_type") val routeType: Int = 0,
    @SerialName("stop_latitude") val latitude: Double? = null,
    @SerialName("stop_longitude") val longitude: Double? = null,
    @SerialName("stop_sequence") val sequence: Int? = null,
)

@Serializable
data class DeparturesResponse(
    val departures: List<DepartureDto> = emptyList(),
    val stops: Map<String, DepartureStopDto> = emptyMap(),
    val routes: Map<String, RouteDto> = emptyMap(),
    val runs: Map<String, RunDto> = emptyMap(),
    val directions: Map<String, DirectionDto> = emptyMap(),
    /** Departures return a map keyed by id; stopping patterns return a plain list. Both are accepted. */
    @Serializable(with = DisruptionMapSerializer::class)
    val disruptions: Map<String, DisruptionDto> = emptyMap(),
)

/** Normalises PTV's two disruption encodings (object keyed by id, or array) into a map. */
object DisruptionMapSerializer : JsonTransformingSerializer<Map<String, DisruptionDto>>(
    MapSerializer(String.serializer(), DisruptionDto.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonObject(
            element.mapIndexedNotNull { index, item ->
                val obj = item as? JsonObject ?: return@mapIndexedNotNull null
                val id = (obj["disruption_id"] as? JsonPrimitive)?.content ?: index.toString()
                id to obj
            }.toMap(),
        )
        is JsonObject -> element
        else -> JsonObject(emptyMap())
    }
}

@Serializable
data class GpsDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class StopLocationDto(
    val gps: GpsDto? = null,
    val suburb: String? = null,
)

@Serializable
data class StopAmenitiesDto(
    val toilet: Boolean? = null,
    @SerialName("taxi_rank") val taxiRank: Boolean? = null,
    @SerialName("car_parking") val carParking: String? = null,
    val cctv: Boolean? = null,
)

@Serializable
data class WheelchairDto(
    @SerialName("accessible_ramp") val accessibleRamp: Boolean? = null,
    val parking: Boolean? = null,
    val telephone: Boolean? = null,
    val toilet: Boolean? = null,
)

@Serializable
data class StopAccessibilityDto(
    val lighting: Boolean? = null,
    @SerialName("audio_customer_information") val audioInfo: Boolean? = null,
    val escalator: Boolean? = null,
    @SerialName("hearing_loop") val hearingLoop: Boolean? = null,
    val lift: Boolean? = null,
    val stairs: Boolean? = null,
    @SerialName("stop_accessible") val stopAccessible: Boolean? = null,
    @SerialName("tactile_ground_surface_indicator") val tactile: Boolean? = null,
    @SerialName("waiting_room") val waitingRoom: Boolean? = null,
    val wheelchair: WheelchairDto? = null,
)

@Serializable
data class StopTicketDto(
    val zone: String? = null,
    @SerialName("is_free_fare_zone") val isFreeFareZone: Boolean = false,
    @SerialName("ticket_zones") val ticketZones: List<Int> = emptyList(),
)

@Serializable
data class StopDetailsDto(
    @SerialName("stop_id") val id: Int = 0,
    @SerialName("stop_name") val name: String? = null,
    @SerialName("route_type") val routeType: Int = 0,
    @SerialName("station_description") val stationDescription: String? = null,
    @SerialName("stop_location") val location: StopLocationDto? = null,
    @SerialName("stop_amenities") val amenities: StopAmenitiesDto? = null,
    @SerialName("stop_accessibility") val accessibility: StopAccessibilityDto? = null,
    @SerialName("stop_ticket") val ticket: StopTicketDto? = null,
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
data class StopDetailsResponse(
    val stop: StopDetailsDto? = null,
)

@Serializable
data class RouteResponse(
    val route: RouteDto? = null,
)

@Serializable
data class ErrorDto(
    val message: String? = null,
)
