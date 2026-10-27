/*
 * Feature Module: Trip
 * File: TripProgress.kt
 * Description: Pure logic for the live trip screen: which line to draw, where along the stops a
 * vehicle is, and which stops to show when the list is shortened. No Android dependencies.
 */

package com.ssethhyy.journey.data.model

import com.ssethhyy.journey.utils.GeoUtils
import kotlin.math.cos
import kotlin.math.hypot

/** Where a vehicle sits along a stopping pattern. */
data class VehicleProgress(
    /** The vehicle is between stop [afterIndex] and the next one (or at [atIndex] when that is set). */
    val afterIndex: Int,
    /** 0..1 along the leg from [afterIndex] to the next stop. */
    val fraction: Double,
    /** Set when the vehicle is within [TripProgress.AT_STOP_METERS] of a stop. */
    val atIndex: Int?,
    /** How far the vehicle is from the stop-to-stop line, a sign it may not be on this run's route. */
    val offRouteMeters: Double,
) {
    /** The stop row the vehicle belongs next to when it is at a stop, else the one it last passed. */
    val anchorIndex: Int get() = atIndex ?: afterIndex
}

/** One row of the shortened stop list. */
sealed interface TripWindowItem {
    data class Stop(val index: Int) : TripWindowItem
    /** [count] stops that are hidden; tapping expands the list. */
    data class Collapsed(val firstIndex: Int, val count: Int) : TripWindowItem
}

object TripProgress {
    const val AT_STOP_METERS = 90.0
    private const val PATH_MATCH_MAX_MEAN_METERS = 250.0
    private const val OFF_ROUTE_METERS = 500.0

    private data class Local(val x: Double, val y: Double)

    /** Metres east/north of an origin; accurate enough over a single trip. */
    private fun local(lat: Double, lon: Double, originLat: Double, originLon: Double): Local {
        val metersPerDegree = 111_320.0
        return Local((lon - originLon) * metersPerDegree * cos(Math.toRadians(originLat)), (lat - originLat) * metersPerDegree)
    }

    /**
     * Picks the geopath that actually follows these stops (PTV sends every variant of a route), or
     * the straight stop-to-stop line when none matches or none was sent.
     */
    fun bestPath(stops: List<Pair<Double, Double>>, geopaths: List<List<Pair<Double, Double>>>): List<Pair<Double, Double>> {
        val usable = stops.filter { it.first != 0.0 || it.second != 0.0 }
        if (usable.size < 2) return usable
        val best = geopaths
            .map { path -> path to meanDistanceToVertices(usable, path) }
            .minByOrNull { it.second }
        return if (best != null && best.second <= PATH_MATCH_MAX_MEAN_METERS) best.first else usable
    }

    private fun meanDistanceToVertices(stops: List<Pair<Double, Double>>, path: List<Pair<Double, Double>>): Double {
        if (path.isEmpty()) return Double.MAX_VALUE
        return stops.sumOf { (lat, lon) ->
            path.minOf { (pLat, pLon) -> GeoUtils.haversineMeters(lat, lon, pLat, pLon) }
        } / stops.size
    }

    /** Projects the vehicle onto the line through [stops]; null when there are fewer than two stops. */
    fun locate(stops: List<Pair<Double, Double>>, lat: Double, lon: Double): VehicleProgress? {
        if (stops.size < 2) return null
        val (originLat, originLon) = stops.first()
        val point = local(lat, lon, originLat, originLon)
        var bestIndex = 0
        var bestFraction = 0.0
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until stops.lastIndex) {
            val a = local(stops[i].first, stops[i].second, originLat, originLon)
            val b = local(stops[i + 1].first, stops[i + 1].second, originLat, originLon)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val distance = hypot(point.x - (a.x + t * dx), point.y - (a.y + t * dy))
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
                bestFraction = t
            }
        }
        val nearestStop = stops.indices.minBy { GeoUtils.haversineMeters(lat, lon, stops[it].first, stops[it].second) }
        val atStop = nearestStop.takeIf { GeoUtils.haversineMeters(lat, lon, stops[it].first, stops[it].second) <= AT_STOP_METERS }
        return VehicleProgress(bestIndex, bestFraction, atStop, bestDistance)
    }

    fun isOnRoute(progress: VehicleProgress): Boolean = progress.offRouteMeters <= OFF_ROUTE_METERS

    /**
     * The stops to list: a few either side of where you are and where the vehicle is, with the
     * rest folded into "N stops" rows and the final stop kept. [expanded] shows everything.
     */
    fun window(stopCount: Int, fromIndex: Int, vehicleIndex: Int?, expanded: Boolean, ahead: Int = 4): List<TripWindowItem> {
        if (stopCount == 0) return emptyList()
        if (expanded || stopCount <= ahead + 4) return List(stopCount) { TripWindowItem.Stop(it) }
        val last = stopCount - 1
        val from = fromIndex.coerceIn(0, last)
        val low = minOf(from, vehicleIndex ?: from)
        val high = maxOf(from, vehicleIndex ?: from)
        val start = (low - 1).coerceAtLeast(0)
        val end = (high + ahead).coerceAtMost(last)
        return buildList {
            if (start > 0) {
                // Keep the first stop visible only when it is the only one hidden.
                if (start == 1) add(TripWindowItem.Stop(0)) else add(TripWindowItem.Collapsed(0, start))
            }
            for (i in start..end) add(TripWindowItem.Stop(i))
            if (end < last) {
                val hidden = last - end - 1
                when {
                    hidden == 1 -> add(TripWindowItem.Stop(end + 1))
                    hidden > 1 -> add(TripWindowItem.Collapsed(end + 1, hidden))
                }
                add(TripWindowItem.Stop(last))
            }
        }
    }
}
