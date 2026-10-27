/*
 * Feature Module: Journey planner
 * File: PlannerApi.kt
 * Description: Client for the MOTIS journey-planning API (Transitous), which plans multi-leg trips
 * over Victoria's open GTFS timetables with real-time updates. No API key is required.
 */

package com.ssethhyy.journey.data.planner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
data class PlannerPlaceDto(
    val name: String? = null,
    val stopId: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val departure: String? = null,
    val arrival: String? = null,
    val scheduledDeparture: String? = null,
    val scheduledArrival: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val cancelled: Boolean = false,
)

@Serializable
data class LegGeometryDto(
    val points: String = "",
    val precision: Int = 6,
)

@Serializable
data class LegDto(
    val mode: String = "WALK",
    val from: PlannerPlaceDto = PlannerPlaceDto(),
    val to: PlannerPlaceDto = PlannerPlaceDto(),
    val duration: Long = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val scheduledStartTime: String? = null,
    val scheduledEndTime: String? = null,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val distance: Double? = null,
    val cancelled: Boolean = false,
    val intermediateStops: List<PlannerPlaceDto> = emptyList(),
    val legGeometry: LegGeometryDto? = null,
)

@Serializable
data class ItineraryDto(
    val duration: Long = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val transfers: Int = 0,
    val legs: List<LegDto> = emptyList(),
)

@Serializable
data class PlanResponseDto(
    val itineraries: List<ItineraryDto> = emptyList(),
    val direct: List<ItineraryDto> = emptyList(),
    val nextPageCursor: String? = null,
    val previousPageCursor: String? = null,
)

@Serializable
data class GeocodeAreaDto(
    val name: String = "",
    val adminLevel: Double = 0.0,
    val unique: Boolean = false,
    @SerialName("default") val isDefault: Boolean = false,
)

@Serializable
data class GeocodeMatchDto(
    val type: String = "PLACE",
    val name: String = "",
    val id: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val street: String? = null,
    val houseNumber: String? = null,
    val zip: String? = null,
    val areas: List<GeocodeAreaDto> = emptyList(),
)

class PlannerException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PlannerApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    suspend fun plan(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        time: Instant? = null,
        arriveBy: Boolean = false,
        pageCursor: String? = null,
        numItineraries: Int = 5,
    ): PlanResponseDto = get(
        "/api/v1/plan",
        buildList {
            add("fromPlace" to "$fromLat,$fromLon")
            add("toPlace" to "$toLat,$toLon")
            add("numItineraries" to numItineraries.toString())
            add("detailedTransfers" to "false")
            if (time != null) add("time" to time.toString())
            if (arriveBy) add("arriveBy" to "true")
            if (pageCursor != null) add("pageCursor" to pageCursor)
        },
        PlanResponseDto.serializer(),
    )

    suspend fun geocode(text: String, nearLat: Double?, nearLon: Double?): List<GeocodeMatchDto> = get(
        "/api/v1/geocode",
        buildList {
            add("text" to text.trim())
            add("language" to "en")
            if (nearLat != null && nearLon != null) add("place" to "$nearLat,$nearLon")
        },
        ListSerializer(GeocodeMatchDto.serializer()),
    )

    internal fun url(path: String, query: List<Pair<String, String>>): String =
        baseUrl.trimEnd('/') + path + "?" + query.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8").replace("+", "%20")}" }

    private suspend fun <T> get(path: String, query: List<Pair<String, String>>, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(path, query))
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw PlannerException("Couldn't reach the journey planner. Check your connection.", e)
        }
        response.use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw PlannerException("The journey planner returned an error (${res.code})")
            try {
                json.decodeFromString(serializer, body)
            } catch (e: Exception) {
                throw PlannerException("Unexpected response from the journey planner", e)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.transitous.org"
        const val USER_AGENT = "Journey-Android/2.0 (Victorian public transport app)"
    }
}
