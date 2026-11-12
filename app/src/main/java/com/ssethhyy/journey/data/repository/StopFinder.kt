/*
 * Feature Module: Data
 * File: StopFinder.kt
 * Description: Finds the PTV stop behind a planned leg. The journey planner only gives a stop's name and
 * position, so the nearby PTV stops are matched against both to open the right stop page.
 */

package com.ssethhyy.journey.data.repository

import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.utils.GeoUtils

object StopMatch {
    private val NOISE = Regex("\\b(railway|station|stn|stop|platform|bay)\\b")
    private val SYMBOLS = Regex("[^a-z0-9/ ]")

    fun normalise(name: String): String =
        name.lowercase().replace(SYMBOLS, " ").replace(NOISE, " ").replace(Regex("\\s+"), " ").trim()

    /** 2 for the same name, 1 when one contains the other, 0 otherwise. */
    fun nameScore(a: String, b: String): Int {
        val x = normalise(a)
        val y = normalise(b)
        return when {
            x.isEmpty() || y.isEmpty() -> 0
            x == y -> 2
            x.contains(y) || y.contains(x) -> 1
            else -> 0
        }
    }

    const val NEAREST_ONLY_METERS = 150.0

    /** The best candidate: the closest name match, or failing that the nearest stop if it is really close. */
    fun best(candidates: List<Stop>, name: String, latitude: Double, longitude: Double): Stop? {
        val scored = candidates.map { it to GeoUtils.haversineMeters(latitude, longitude, it.latitude, it.longitude) }
        val named = scored.filter { nameScore(it.first.name, name) > 0 }
            .sortedWith(compareByDescending<Pair<Stop, Double>> { nameScore(it.first.name, name) }.thenBy { it.second })
        return named.firstOrNull()?.first ?: scored.minByOrNull { it.second }?.takeIf { it.second <= NEAREST_ONLY_METERS }?.first
    }
}

/** The PTV stop called [name] near this point, or null if none can be matched. */
suspend fun PtvRepository.findStop(name: String, latitude: Double, longitude: Double, mode: TransportMode? = null): Stop? {
    val modes = mode?.let { setOf(it) } ?: TransportMode.entries.toSet()
    val nearby = runCatching { nearbyStops(latitude, longitude, modes, radiusMeters = 400, maxResults = 20) }.getOrNull().orEmpty()
    return StopMatch.best(nearby, name, latitude, longitude)
}

/** The stop a leg starts from ([boarding]) or ends at. */
suspend fun PtvRepository.findStop(leg: JourneyLeg, boarding: Boolean): Stop? {
    val point = (if (boarding) leg.path.firstOrNull() else leg.path.lastOrNull()) ?: return null
    val name = if (boarding) leg.fromName else leg.toName
    return findStop(name, point.latitude, point.longitude, leg.mode)
}
