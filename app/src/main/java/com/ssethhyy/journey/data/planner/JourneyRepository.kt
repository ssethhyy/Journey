/*
 * Feature Module: Journey planner
 * File: JourneyRepository.kt
 * Description: Plans journeys and searches locations, caching results briefly.
 */

package com.ssethhyy.journey.data.planner

import com.ssethhyy.journey.data.repository.TtlCache
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class TimeMode { LEAVE_NOW, DEPART_AT, ARRIVE_BY }

class JourneyRepository(private val api: PlannerApi) {
    private val geocodeCache = TtlCache<String, List<GeocodeResult>>(ttlMs = 10 * 60_000L)

    /** Journeys the user opened, so the detail screen can find them by id. */
    private val journeys = ConcurrentHashMap<String, Journey>()

    /** The request each journey came from, so it can be re-planned for live updates. */
    private val requests = ConcurrentHashMap<String, PlanRequestParams>()

    data class PlanRequestParams(val from: PlanLocation, val to: PlanLocation, val timeMode: TimeMode, val time: Instant?)

    suspend fun plan(
        from: PlanLocation,
        to: PlanLocation,
        timeMode: TimeMode,
        time: Instant?,
        pageCursor: String? = null,
    ): JourneyResults {
        val response = api.plan(
            fromLat = from.latitude,
            fromLon = from.longitude,
            toLat = to.latitude,
            toLon = to.longitude,
            time = if (timeMode == TimeMode.LEAVE_NOW) null else time,
            arriveBy = timeMode == TimeMode.ARRIVE_BY,
            pageCursor = pageCursor,
        )
        val params = PlanRequestParams(from, to, timeMode, time)
        return JourneyMapper.results(response).also { results ->
            results.journeys.forEach {
                journeys[it.id] = it
                requests[it.id] = params
            }
        }
    }

    fun journey(id: String): Journey? = journeys[id]

    /**
     * Re-plans the request a journey came from and returns the up-to-date version of the same
     * journey (matched by services and scheduled times), or null if it no longer appears.
     * The refreshed journey is stored under the original id so screens keep working.
     */
    suspend fun refreshJourney(id: String): Journey? {
        val old = journeys[id] ?: return null
        val params = requests[id] ?: return old
        // Plan from the original departure time so the journey is still in the result window.
        val firstTransit = old.firstTransit?.scheduledStart ?: old.start
        val response = api.plan(
            fromLat = params.from.latitude,
            fromLon = params.from.longitude,
            toLat = params.to.latitude,
            toLon = params.to.longitude,
            time = minOf(old.start, firstTransit).minusSeconds(120),
            arriveBy = false,
            numItineraries = 8,
        )
        val match = JourneyMapper.results(response).journeys.firstOrNull { it.matchKey == old.matchKey } ?: return null
        val updated = match.copy(id = id)
        journeys[id] = updated
        return updated
    }

    fun requestFor(id: String): PlanRequestParams? = requests[id]

    suspend fun searchLocations(text: String, near: GeoPoint?): List<GeocodeResult> {
        val clean = text.trim()
        if (clean.length < 2) return emptyList()
        val key = "${clean.lowercase()}|${near?.let { "%.2f,%.2f".format(it.latitude, it.longitude) }}"
        return geocodeCache.getOrLoad(key) {
            api.geocode(clean, near?.latitude, near?.longitude)
                .map(JourneyMapper::geocode)
                .distinctBy { "${it.name}|${"%.4f,%.4f".format(it.latitude, it.longitude)}" }
        }
    }
}
