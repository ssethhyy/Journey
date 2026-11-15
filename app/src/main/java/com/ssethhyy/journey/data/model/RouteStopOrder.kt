/*
 * Feature Module: Data
 * File: RouteStopOrder.kt
 * Description: Puts a route's stops in travel order when PTV doesn't say what it is.
 *
 * Asked for one direction, PTV numbers the stops that direction serves and still lists the other direction's stops
 * with stop_sequence 0, which put them first in alphabetical order. Those are left out. If nothing is numbered at all,
 * the route's own path line runs through the stops in order, so each stop is placed by the nearest point along it.
 */

package com.ssethhyy.journey.data.model

import kotlin.math.cos

object RouteStopOrder {
    /**
     * [stops] in travel order for [directionId]. When PTV numbered some, those are the direction's stops, in its order;
     * otherwise they're ordered along [geopaths] (the route's path lines by direction), using the chosen direction's
     * path if there is one.
     */
    fun order(
        stops: List<Stop>,
        geopaths: Map<Int, List<List<Pair<Double, Double>>>>,
        directionId: Int?,
        directionName: String?,
    ): List<Stop> {
        if (stops.any { it.sequence != 0 }) return stops.filter { it.sequence != 0 }.sortedBy { it.sequence }
        if (stops.size < 2) return stops
        val own = directionId?.let { geopaths[it] }?.flatten().orEmpty()
        val path = own.ifEmpty { geopaths.values.firstOrNull { it.isNotEmpty() }?.flatten().orEmpty() }
        if (path.size < 2) return stops
        val ordered = stops.sortedBy { nearestIndex(path, it.latitude, it.longitude) }
        if (own.isNotEmpty()) return ordered
        // A path that isn't this direction's may run the other way: the stop that names where we're going goes last.
        return if (endsAtStart(ordered, directionName)) ordered.reversed() else ordered
    }

    private fun nearestIndex(path: List<Pair<Double, Double>>, latitude: Double, longitude: Double): Int {
        val scale = cos(Math.toRadians(latitude))
        var best = 0
        var bestDistance = Double.MAX_VALUE
        path.forEachIndexed { i, (lat, lon) ->
            val dLat = lat - latitude
            val dLon = (lon - longitude) * scale
            val d = dLat * dLat + dLon * dLon
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    private fun endsAtStart(ordered: List<Stop>, directionName: String?): Boolean {
        val words = directionName.orEmpty().lowercase().split(Regex("[^a-z]+")).filter { it.length > 2 }.toSet()
        if (words.isEmpty()) return false
        fun score(stop: Stop) = "${stop.name} ${stop.suburb.orEmpty()}".lowercase().split(Regex("[^a-z]+")).count { it in words }
        return score(ordered.first()) > score(ordered.last())
    }
}
