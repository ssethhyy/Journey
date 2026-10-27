/*
 * Feature Module: Data
 * File: PtvRepository.kt
 * Description: Caching repository over the PTV Timetable API.
 */

package com.ssethhyy.journey.data.repository

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/** Minimal TTL cache. */
class TtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxEntries: Int = 200,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val storedAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()

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
        if (!force) get(key)?.let { return it }
        return loader().also { put(key, it) }
    }
}

class PtvRepository(private val api: PtvApi) {
    private val departuresCache = TtlCache<String, DepartureBoard>(ttlMs = 20_000)
    private val nearbyCache = TtlCache<String, List<Stop>>(ttlMs = 60_000)
    private val stopDetailsCache = TtlCache<String, StopDetails>(ttlMs = 6 * 60 * 60_000L)
    private val routeCache = TtlCache<Int, RouteDetails>(ttlMs = 6 * 60 * 60_000L)
    private val routeStopsCache = TtlCache<String, List<Stop>>(ttlMs = 6 * 60 * 60_000L)
    private val disruptionsCache = TtlCache<String, List<Disruption>>(ttlMs = 2 * 60_000L)
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

    suspend fun stoppingPattern(runRef: String, mode: TransportMode, withPath: Boolean = true): StoppingPattern =
        PtvMappers.stoppingPattern(api.pattern(runRef, mode.routeType, stopId = null, includeGeopath = withPath), withPath)

    fun clearCaches() {
        departuresCache.clear()
        nearbyCache.clear()
        disruptionsCache.clear()
        searchCache.clear()
    }
}

/** Client-side ranking so the most likely matches surface first. */
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

    fun rank(query: String, results: SearchResults): SearchResults = SearchResults(
        stops = results.stops.sortedWith(
            compareBy<Stop>({ score(query, it.name) }, { it.mode.ordinal }, { it.distanceMeters ?: Double.MAX_VALUE }, { it.name }),
        ),
        routes = results.routes.sortedWith(
            compareBy(
                { minOf(score(query, it.number), score(query, it.name)) },
                { it.mode.ordinal },
                { it.number.toIntOrNull() ?: Int.MAX_VALUE },
                { it.name },
            ),
        ),
    )
}
