/*
 * Feature Module: Crowding
 * File: CrowdingRepository.kt
 * Description: Fetches Victoria's free GTFS Realtime vehicle position feeds (Transport Victoria Open Data) and
 * finds the vehicle a PTV run is using, so Journey can say how busy a service is and which carriages are fullest.
 *
 * The feeds need a free API key, sent in the KeyID header (the older Ocp-Apim-Subscription-Key is sent too, so
 * either style of key works). They allow about 20 requests a minute and refresh every 30 seconds or so, so results
 * are cached per feed for 30 seconds.
 *
 * Matching: PTV's run lists the physical vehicle (for a Metro train, the carriage numbers, like
 * "1380T-1433T-159M-160M-265M-266M"; for a tram or bus, its fleet number). The feed's vehicle id or carriage ids
 * are matched against those. If PTV gives no usable id but does give a position, the nearest feed vehicle within
 * a few hundred metres is used instead.
 */

package com.ssethhyy.journey.data.crowding

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.TtlCache
import com.ssethhyy.journey.utils.GeoUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Which feed carries a mode. */
enum class CrowdingFeed(val path: String) {
    METRO("metro"),
    TRAM("tram"),
    BUS("bus"),
    VLINE("vline"),
    ;

    companion object {
        fun forMode(mode: TransportMode): CrowdingFeed = when (mode) {
            TransportMode.TRAIN -> METRO
            TransportMode.TRAM -> TRAM
            TransportMode.BUS, TransportMode.NIGHT_BUS -> BUS
            TransportMode.VLINE -> VLINE
        }
    }
}

sealed class CrowdingException(message: String) : Exception(message) {
    class MissingKey : CrowdingException("No Transport Victoria Open Data key")
    class Unauthorized : CrowdingException("The Open Data key was rejected")
    class RateLimited : CrowdingException("The crowding feed is busy, try again shortly")
    class Network(message: String) : CrowdingException(message)
}

class CrowdingApi(
    private val keyProvider: suspend () -> String?,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun hasKey(): Boolean = !keyProvider().isNullOrBlank()

    suspend fun vehicles(feed: CrowdingFeed): List<RtVehicle> {
        val key = keyProvider()?.trim()?.takeIf { it.isNotEmpty() } ?: throw CrowdingException.MissingKey()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/opendata/public-transport/gtfs/realtime/v1/${feed.path}/vehicle-positions")
            .header("KeyID", key)
            .header("Ocp-Apim-Subscription-Key", key)
            .header("Accept", "application/x-protobuf")
            .get()
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw CrowdingException.Network(e.message ?: "Network error")
        }
        response.use {
            when {
                it.code == 401 || it.code == 403 -> throw CrowdingException.Unauthorized()
                it.code == 429 -> throw CrowdingException.RateLimited()
                !it.isSuccessful -> throw CrowdingException.Network("HTTP ${it.code}")
            }
            val bytes = it.body?.bytes() ?: ByteArray(0)
            return GtfsRealtime.parseVehicles(bytes)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.opendata.transport.vic.gov.au"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
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

/** What happened when we looked for a service's crowding. */
sealed interface CrowdingResult {
    /** No Open Data key has been added. */
    data object NotConfigured : CrowdingResult

    /** The feed worked but this service isn't in it (or can't be told apart). */
    data object NotFound : CrowdingResult

    data class Found(val crowding: VehicleCrowding) : CrowdingResult

    data class Failed(val reason: String, val badKey: Boolean = false) : CrowdingResult
}

object CrowdingMatcher {
    private val SEPARATORS = Regex("[\\s,;/|_-]+")

    /** Upper-case id parts: "1380T-1433T-159M" becomes {1380T, 1433T, 159M}. Parts shorter than 3 characters are ignored. */
    fun tokens(value: String?): Set<String> =
        value?.trim()?.uppercase()?.split(SEPARATORS)?.filter { it.length >= 3 }?.toSet().orEmpty()

    const val NEAREST_METERS = 250.0

    fun match(vehicles: List<RtVehicle>, runVehicleId: String?, position: Pair<Double, Double>?): RtVehicle? {
        val wanted = tokens(runVehicleId)
        if (wanted.isNotEmpty()) {
            vehicles.firstOrNull { v -> v.allIds.any { it in wanted } }?.let { return it }
        }
        if (position != null) {
            return vehicles
                .filter { it.latitude != null && it.longitude != null }
                .map { it to GeoUtils.haversineMeters(position.first, position.second, it.latitude!!, it.longitude!!) }
                .filter { it.second <= NEAREST_METERS }
                .minByOrNull { it.second }
                ?.first
        }
        return null
    }

    fun toCrowding(vehicle: RtVehicle): VehicleCrowding {
        val ordered = vehicle.carriages.sortedBy { it.sequence ?: Int.MAX_VALUE }
        val carriages = ordered.mapIndexed { index, c ->
            CarriageCrowding(
                label = (c.label ?: c.id)?.takeIf { it.isNotBlank() } ?: "Car ${index + 1}",
                occupancy = if (c.occupancy.hasData) c.occupancy else c.percentage?.let(Occupancy::fromPercentage) ?: Occupancy.UNKNOWN,
                percentage = c.percentage,
            )
        }
        return VehicleCrowding.of(vehicle.occupancy, vehicle.percentage, carriages, vehicle.timestampSec)
    }
}

class CrowdingRepository(private val api: CrowdingApi) {
    private val cache = TtlCache<CrowdingFeed, List<RtVehicle>>(ttlMs = 30_000L, maxEntries = 8)

    suspend fun isConfigured(): Boolean = api.hasKey()

    suspend fun crowdingFor(mode: TransportMode, runVehicleId: String?, position: Pair<Double, Double>?, force: Boolean = false): CrowdingResult {
        if (!api.hasKey()) return CrowdingResult.NotConfigured
        val feed = CrowdingFeed.forMode(mode)
        val vehicles = try {
            cache.getOrLoad(feed, force) { api.vehicles(feed) }
        } catch (e: CrowdingException.MissingKey) {
            return CrowdingResult.NotConfigured
        } catch (e: CrowdingException.Unauthorized) {
            return CrowdingResult.Failed(e.message.orEmpty(), badKey = true)
        } catch (e: CrowdingException) {
            return CrowdingResult.Failed(e.message.orEmpty())
        }
        val match = CrowdingMatcher.match(vehicles, runVehicleId, position) ?: return CrowdingResult.NotFound
        return CrowdingResult.Found(CrowdingMatcher.toCrowding(match))
    }

    fun clearCache() = cache.clear()
}
