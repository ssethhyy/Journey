/*
 * Feature Module: PTV API
 * File: PtvApi.kt
 * Description: Signed HTTP client for the PTV Timetable API v3.
 */

package com.ssethhyy.journey.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PtvCredentials(val devId: String, val apiKey: String) {
    val isValid: Boolean get() = devId.isNotBlank() && apiKey.isNotBlank()
}

sealed class PtvException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingCredentials : PtvException("PTV API credentials are not configured")
    class InvalidCredentials : PtvException("PTV rejected the developer ID or API key")
    class Network(cause: Throwable) : PtvException("Couldn't reach PTV. Check your connection.", cause)
    class Http(val code: Int, detail: String?) : PtvException("PTV returned an error ($code)${detail?.let { ": $it" } ?: ""}")
    class Parse(cause: Throwable) : PtvException("Unexpected response from PTV", cause)
}

/**
 * Request signing per the PTV Timetable API v3 spec:
 * signature = uppercase hex(HMAC-SHA1(apiKey, path + query including devid)).
 */
object PtvSigner {
    fun hmacSha1Hex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    /** Returns the signed path+query (without host). */
    fun signedPath(pathAndQuery: String, credentials: PtvCredentials): String {
        val separator = if ('?' in pathAndQuery) '&' else '?'
        val withDevId = "$pathAndQuery${separator}devid=${credentials.devId}"
        return "$withDevId&signature=${hmacSha1Hex(credentials.apiKey, withDevId)}"
    }
}

class PtvApi(
    private val credentialsProvider: suspend () -> PtvCredentials?,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) {
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun stopsNearby(
        latitude: Double,
        longitude: Double,
        routeTypes: Collection<Int>,
        maxResults: Int,
        maxDistanceMeters: Int,
    ): StopsByDistanceResponse = get(
        "/v3/stops/location/$latitude,$longitude",
        listOf("max_results" to maxResults.toString(), "max_distance" to maxDistanceMeters.toString()) +
            routeTypes.map { "route_types" to it.toString() },
        StopsByDistanceResponse.serializer(),
    )

    suspend fun departures(
        routeType: Int,
        stopId: Int,
        maxResults: Int,
        routeId: Int? = null,
        directionId: Int? = null,
    ): DeparturesResponse {
        val path = if (routeId != null) {
            "/v3/departures/route_type/$routeType/stop/$stopId/route/$routeId"
        } else {
            "/v3/departures/route_type/$routeType/stop/$stopId"
        }
        return get(
            path,
            buildList {
                add("max_results" to maxResults.toString())
                add("expand" to "All")
                add("include_cancelled" to "true")
                add("look_backwards" to "false")
                if (directionId != null) add("direction_id" to directionId.toString())
            },
            DeparturesResponse.serializer(),
        )
    }

    suspend fun search(term: String, routeTypes: Collection<Int>): SearchResponse = get(
        "/v3/search/${encodePath(term)}",
        listOf("include_outlets" to "false", "match_stop_by_suburb" to "true", "match_route_by_suburb" to "false") +
            routeTypes.map { "route_types" to it.toString() },
        SearchResponse.serializer(),
    )

    suspend fun stopDetails(stopId: Int, routeType: Int): StopDetailsResponse = get(
        "/v3/stops/$stopId/route_type/$routeType",
        listOf(
            "stop_location" to "true",
            "stop_amenities" to "true",
            "stop_accessibility" to "true",
            "stop_ticket" to "true",
            "stop_disruptions" to "false",
        ),
        StopDetailsResponse.serializer(),
    )

    suspend fun stopsOnRoute(routeId: Int, routeType: Int, directionId: Int?): StopsOnRouteResponse = get(
        "/v3/stops/route/$routeId/route_type/$routeType",
        buildList {
            if (directionId != null) add("direction_id" to directionId.toString())
            add("include_geopath" to "false")
        },
        StopsOnRouteResponse.serializer(),
    )

    suspend fun route(routeId: Int): RouteResponse = get(
        "/v3/routes/$routeId",
        listOf("include_geopath" to "true"),
        RouteResponse.serializer(),
    )

    suspend fun directions(routeId: Int): DirectionsResponse = get(
        "/v3/directions/route/$routeId",
        emptyList(),
        DirectionsResponse.serializer(),
    )

    suspend fun disruptions(routeTypes: Collection<Int>): DisruptionsResponse = get(
        "/v3/disruptions",
        listOf("disruption_status" to "current") + routeTypes.map { "route_types" to it.toString() },
        DisruptionsResponse.serializer(),
    )

    suspend fun disruptionsForRoute(routeId: Int): DisruptionsResponse = get(
        "/v3/disruptions/route/$routeId",
        listOf("disruption_status" to "current"),
        DisruptionsResponse.serializer(),
    )

    suspend fun disruptionsForStop(stopId: Int): DisruptionsResponse = get(
        "/v3/disruptions/stop/$stopId",
        listOf("disruption_status" to "current"),
        DisruptionsResponse.serializer(),
    )

    suspend fun pattern(runRef: String, routeType: Int, stopId: Int?, includeGeopath: Boolean = true): DeparturesResponse = get(
        "/v3/pattern/run/${encodePath(runRef)}/route_type/$routeType",
        buildList {
            add("expand" to "All")
            add("include_skipped_stops" to "false")
            add("include_geopath" to includeGeopath.toString())
            if (stopId != null) add("stop_id" to stopId.toString())
        },
        DeparturesResponse.serializer(),
    )

    /** Builds the path+query for a request (exposed for tests). */
    internal fun buildPathAndQuery(path: String, query: List<Pair<String, String>>): String {
        if (query.isEmpty()) return path
        return path + "?" + query.joinToString("&") { (k, v) -> "$k=${encodeQuery(v)}" }
    }

    private suspend fun <T> get(path: String, query: List<Pair<String, String>>, serializer: KSerializer<T>): T {
        val credentials = credentialsProvider()?.takeIf { it.isValid } ?: throw PtvException.MissingCredentials()
        val signed = PtvSigner.signedPath(buildPathAndQuery(path, query), credentials)
        val request = Request.Builder().url(baseUrl.trimEnd('/') + signed).get().build()

        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw PtvException.Network(e)
        }

        return withContext(Dispatchers.Default) {
            response.use { res ->
                val body = res.body?.string().orEmpty()
                when {
                    res.code == 401 || res.code == 403 -> throw PtvException.InvalidCredentials()
                    !res.isSuccessful -> {
                        val detail = runCatching { json.decodeFromString(ErrorDto.serializer(), body).message }.getOrNull()
                        throw PtvException.Http(res.code, detail)
                    }
                    else -> try {
                        json.decodeFromString(serializer, body)
                    } catch (e: Exception) {
                        throw PtvException.Parse(e)
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://timetableapi.ptv.vic.gov.au"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        internal fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        internal fun encodePath(value: String): String = encodeQuery(value.trim())
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
