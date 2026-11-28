/*
 * Feature Module: Data
 * File: PtvRepository.kt
 * Description: Caching repository over the PTV Timetable API.
 */

package com.ssethhyy.journey.data.repository

import com.ssethhyy.journey.data.model.RouteLine
import com.ssethhyy.journey.data.api.PtvApi
import com.ssethhyy.journey.data.api.PtvMappers
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.RouteDetails
import com.ssethhyy.journey.data.model.RouteDirection
import com.ssethhyy.journey.data.model.SearchResults
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.StopDetails
import com.ssethhyy.journey.data.fare.StopFare
import com.ssethhyy.journey.data.model.StoppingPattern
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.model.VehiclePosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal TTL cache that also saves API calls: two callers asking for the same key at once share one request, and a
 * forced reload within [minForceAgeMs] of the last one reuses it (pull to refresh straight after opening a page, or
 * two screens refreshing the same stop together).
 */
class TtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxEntries: Int = 200,
    private val minForceAgeMs: Long = 0,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val storedAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        return if (clock() - entry.storedAt <= ttlMs) entry.value else null.also { map.remove(key) }
    }

    fun put(key: K, value: V) {
        if (map.size >= maxEntries) {
            map.entries.minByOrNull { it.value.storedAt }?.key?.let(map::remove)
        }
        map[key] = Entry(value, clock())
    }

    fun clear() = map.clear()

    suspend fun getOrLoad(key: K, force: Boolean = false, loader: suspend () -> V): V {
        val cached = map[key]
        if (cached != null) {
            val age = clock() - cached.storedAt
            if ((!force && age <= ttlMs) || (force && age < minForceAgeMs)) return cached.value
        }
        // Someone else is already fetching this: wait for their answer instead of asking again.
        inFlight[key]?.let { return it.await() }
        val mine = CompletableDeferred<V>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) return existing.await()
        return try {
            loader().also {
                put(key, it)
                mine.complete(it)
            }
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, mine)
        }
    }
}

/**
 * [liveVehicle] looks a service's vehicle up in Transport Victoria's GTFS Realtime feed (see [stoppingPattern]); null
 * keeps to PTV's positions only.
 */
class PtvRepository(
    private val api: PtvApi,
    private val liveVehicle: (suspend (mode: TransportMode, vehicleId: String?, near: Pair<Double, Double>?, radiusMeters: Double) -> com.ssethhyy.journey.data.crowding.RtVehicle?)? = null,
) {
    private val departuresCache = TtlCache<String, DepartureBoard>(ttlMs = 20_000, minForceAgeMs = 8_000)
    // Stops don't move, so a nearby list stays good for a while.
    private val nearbyCache = TtlCache<String, List<Stop>>(ttlMs = 10 * 60_000L, minForceAgeMs = 30_000)
    private val stopDetailsCache = TtlCache<String, StopDetails>(ttlMs = 6 * 60 * 60_000L)
    private val routeCache = TtlCache<Int, RouteDetails>(ttlMs = 6 * 60 * 60_000L)
    private val routeStopsCache = TtlCache<String, List<Stop>>(ttlMs = 6 * 60 * 60_000L)
    private val disruptionsCache = TtlCache<String, List<Disruption>>(ttlMs = 5 * 60_000L, minForceAgeMs = 30_000)
    private val searchCache = TtlCache<String, SearchResults>(ttlMs = 5 * 60_000L)

    suspend fun nearbyStops(
        latitude: Double,
        longitude: Double,
        modes: Set<TransportMode>,
        radiusMeters: Int,
        maxResults: Int = 60,
        force: Boolean = false,
    ): List<Stop> {
        if (modes.isEmpty()) return emptyList()
        // Round coordinates (~11 m) so small GPS jitter still hits the cache.
        val key = "%.4f,%.4f|%s|%d|%d".format(latitude, longitude, modes.sorted().joinToString(), radiusMeters, maxResults)
        return nearbyCache.getOrLoad(key, force) {
            api.stopsNearby(latitude, longitude, modes.map { it.routeType }, maxResults, radiusMeters)
                .stops
                .map(PtvMappers::stop)
                .distinctBy { it.key }
                .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
        }
    }

    suspend fun departures(
        mode: TransportMode,
        stopId: Int,
        maxResults: Int = 5,
        routeId: Int? = null,
        force: Boolean = false,
    ): DepartureBoard {
        val key = "${mode.routeType}:$stopId:$maxResults:${routeId ?: ""}"
        return departuresCache.getOrLoad(key, force) {
            PtvMappers.departureBoard(api.departures(mode.routeType, stopId, maxResults, routeId))
        }
    }

    suspend fun search(term: String, modes: Set<TransportMode> = TransportMode.entries.toSet()): SearchResults {
        val clean = term.trim()
        if (clean.length < 2) return SearchResults(emptyList(), emptyList())
        val key = "${clean.lowercase()}|${modes.sorted().joinToString()}"
        return searchCache.getOrLoad(key) {
            val response = api.search(clean, modes.map { it.routeType })
            SearchRanking.rank(
                query = clean,
                results = SearchResults(
                    stops = response.stops.map(PtvMappers::stop).distinctBy { it.key },
                    routes = response.routes.map(PtvMappers::route).distinctBy { it.key },
                ),
            )
        }
    }

    suspend fun stopDetails(mode: TransportMode, stopId: Int, fallback: Stop? = null): StopDetails {
        val key = "${mode.routeType}:$stopId"
        return stopDetailsCache.getOrLoad(key) {
            val dto = api.stopDetails(stopId, mode.routeType).stop
            if (dto == null) {
                StopDetails(fallback ?: Stop(stopId, mode, "", null, 0.0, 0.0), emptyList(), null)
            } else {
                PtvMappers.stopDetails(dto, fallback)
            }
        }
    }

    suspend fun routeDetails(routeId: Int): RouteDetails = routeCache.getOrLoad(routeId) {
        coroutineScope {
            val routeDeferred = async { api.route(routeId) }
            val directionsDeferred = async { api.directions(routeId) }
            val routeDto = routeDeferred.await().route ?: throw IllegalStateException("Route $routeId not found")
            val directions = directionsDeferred.await().directions
                .filter { it.routeId == routeId || it.routeId == 0 }
                .map { RouteDirection(it.directionId, it.directionName.orEmpty(), it.description) }
                .distinctBy { it.id }
            RouteDetails(
                route = PtvMappers.route(routeDto),
                directions = directions,
                geopaths = routeDto.geopath.groupBy { it.directionId }.mapValues { (_, paths) ->
                    paths.flatMap { it.paths }.map(PtvMappers::parseGeopath).filter { it.size > 1 }
                },
            )
        }
    }

    suspend fun stopsOnRoute(routeId: Int, mode: TransportMode, directionId: Int?): List<Stop> {
        val key = "$routeId:${mode.routeType}:${directionId ?: ""}"
        return routeStopsCache.getOrLoad(key) {
            api.stopsOnRoute(routeId, mode.routeType, directionId).stops
                .map(PtvMappers::stop)
                .sortedBy { it.sequence }
        }
    }

    suspend fun disruptions(modes: Set<TransportMode> = TransportMode.entries.toSet(), force: Boolean = false): List<Disruption> {
        val key = "all|${modes.sorted().joinToString()}"
        return disruptionsCache.getOrLoad(key, force) {
            PtvMappers.disruptions(api.disruptions(modes.map { it.routeType })).filter { it.isCurrent }
        }
    }

    suspend fun disruptionsForRoute(routeId: Int): List<Disruption> = disruptionsCache.getOrLoad("route|$routeId") {
        PtvMappers.disruptions(api.disruptionsForRoute(routeId)).filter { it.isCurrent }
    }

    suspend fun disruptionsForStop(stopId: Int): List<Disruption> = disruptionsCache.getOrLoad("stop|$stopId") {
        PtvMappers.disruptions(api.disruptionsForStop(stopId)).filter { it.isCurrent }
    }

    /** [withPath] also fetches the route line for the map; refreshes can skip it and reuse the first one. */
    private val stopFareCache = TtlCache<String, StopFare>(ttlMs = 24 * 60 * 60_000L)

    /** The fare zones PTV lists for a stop; null when they can't be fetched (failures are not cached). */
    suspend fun stopFare(mode: TransportMode, stopId: Int): StopFare? {
        val key = "${mode.routeType}:$stopId"
        stopFareCache.get(key)?.let { return it }
        val ticket = runCatching { api.stopDetails(stopId, mode.routeType).stop?.ticket }.getOrNull() ?: return null
        return StopFare(ticket.ticketZones.toSet(), ticket.isFreeFareZone).also { stopFareCache.put(key, it) }
    }

    /** Fare zones of the stop nearest a point, for journeys that only give coordinates. */
    suspend fun stopFareNear(mode: TransportMode, latitude: Double, longitude: Double): StopFare? {
        val stop = runCatching { nearbyStops(latitude, longitude, setOf(mode), radiusMeters = 700, maxResults = 1).firstOrNull() }.getOrNull() ?: return null
        return stopFare(mode, stop.id)
    }

    /**
     * A service's stops and, with [withPath], its line: the route's real shape from PTV between the first and last
     * stop. The pattern's own shapes come first; when none fits, the route's shapes (cached for hours) are tried;
     * only if PTV has no shape at all is a smooth curve drawn through the stops.
     */
    suspend fun stoppingPattern(runRef: String, mode: TransportMode, withPath: Boolean = true): StoppingPattern {
        val pattern = withLivePosition(
            PtvMappers.stoppingPattern(api.pattern(runRef, mode.routeType, stopId = null, includeGeopath = withPath), withPath),
            mode,
        )
        if (!withPath || pattern.path.isNotEmpty()) return pattern
        val stops = pattern.stops.map { it.stop.latitude to it.stop.longitude }
        val routeShapes = pattern.route?.id?.let { id ->
            runCatching { routeDetails(id).geopaths.values.flatten() }.getOrNull()
        }.orEmpty()
        // Stitching thousands of shape points is real work: keep it off the main thread.
        val line = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { RouteLine.forStops(stops, routeShapes) }
        return pattern.copy(path = line)
    }

    /**
     * Prefers Transport Victoria's GTFS Realtime position for the service's vehicle: it's sent more reliably than PTV's
     * and says when it was recorded, so the vehicle can be placed between updates. The vehicle is found by its numbers,
     * near PTV's position when there is one, or near where the timetable says it should be. PTV's position stays when
     * it's newer, or when the feed has nothing.
     */
    private suspend fun withLivePosition(pattern: StoppingPattern, mode: TransportMode): StoppingPattern {
        val lookup = liveVehicle ?: return pattern
        val ptv = pattern.vehiclePosition
        val (near, radius) = when {
            ptv != null -> (ptv.latitude to ptv.longitude) to 250.0
            else -> (expectedPosition(pattern, java.time.Instant.now()) ?: return if (pattern.vehicle?.id != null) {
                lookupOrSame(pattern, lookup, mode, null, 0.0)
            } else pattern) to 600.0
        }
        return lookupOrSame(pattern, lookup, mode, near, radius)
    }

    private suspend fun lookupOrSame(
        pattern: StoppingPattern,
        lookup: suspend (TransportMode, String?, Pair<Double, Double>?, Double) -> com.ssethhyy.journey.data.crowding.RtVehicle?,
        mode: TransportMode,
        near: Pair<Double, Double>?,
        radius: Double,
    ): StoppingPattern {
        val rt = runCatching { lookup(mode, pattern.vehicle?.id, near, radius) }.getOrNull() ?: return pattern
        val lat = rt.latitude ?: return pattern
        val lon = rt.longitude ?: return pattern
        val recorded = rt.timestampSec?.let { java.time.Instant.ofEpochSecond(it) }
        val ptv = pattern.vehiclePosition
        // Keep PTV's when it's the newer of the two.
        if (ptv?.recordedAt != null && recorded != null && ptv.recordedAt.isAfter(recorded)) return pattern
        // A position more than a few minutes old isn't live any more.
        if (recorded != null && recorded.isBefore(java.time.Instant.now().minusSeconds(300))) return pattern
        return pattern.copy(
            vehiclePosition = VehiclePosition(
                latitude = lat,
                longitude = lon,
                bearing = rt.bearing?.toDouble() ?: ptv?.bearing,
                recordedAt = recorded,
                expiresAt = recorded?.plusSeconds(180),
            ),
        )
    }

    /** Where the timetable puts the service now: between the stop it last left and the next, by time. */
    private fun expectedPosition(pattern: StoppingPattern, now: java.time.Instant): Pair<Double, Double>? {
        val stops = pattern.stops.filter { it.bestTime != null && (it.stop.latitude != 0.0 || it.stop.longitude != 0.0) }
        if (stops.size < 2) return null
        val nextIndex = stops.indexOfFirst { it.bestTime!!.isAfter(now) }
        if (nextIndex <= 0) return null // Not left the first stop yet, or already finished.
        val a = stops[nextIndex - 1]
        val b = stops[nextIndex]
        val span = java.time.Duration.between(a.bestTime, b.bestTime).seconds.coerceAtLeast(1)
        val f = (java.time.Duration.between(a.bestTime, now).seconds.toDouble() / span).coerceIn(0.0, 1.0)
        return (a.stop.latitude + (b.stop.latitude - a.stop.latitude) * f) to (a.stop.longitude + (b.stop.longitude - a.stop.longitude) * f)
    }

    fun clearCaches() {
        departuresCache.clear()
        nearbyCache.clear()
        disruptionsCache.clear()
        searchCache.clear()
    }
}

/**
 * Client-side ranking so the most likely matches surface first. Good matches (the name, or a word in it, starts with
 * what you typed) come before loose ones; within each, V/Line comes first, then trains, trams, buses and night buses.
 */
object SearchRanking {
    fun score(query: String, name: String): Int {
        val q = query.trim().lowercase()
        val n = name.lowercase()
        return when {
            n == q -> 0
            n.startsWith(q) -> 1
            n.split(' ', '/', '-', '(').any { it.startsWith(q) } -> 2
            q in n -> 3
            else -> 4
        }
    }

    /** 0 for a good match, 1 for a loose one. */
    private fun tier(score: Int) = if (score <= 2) 0 else 1

    fun rank(query: String, results: SearchResults): SearchResults = SearchResults(
        stops = results.stops.sortedWith(
            compareBy<Stop>(
                { tier(score(query, it.name)) },
                { it.mode.searchPriority },
                { score(query, it.name) },
                { it.distanceMeters ?: Double.MAX_VALUE },
                { it.name },
            ),
        ),
        routes = results.routes.sortedWith(
            compareBy(
                { tier(minOf(score(query, it.number), score(query, it.name))) },
                { it.mode.searchPriority },
                { minOf(score(query, it.number), score(query, it.name)) },
                { it.number.toIntOrNull() ?: Int.MAX_VALUE },
                { it.name },
            ),
        ),
    )
}
