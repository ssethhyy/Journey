/*
 * Feature Module: Data
 * File: RouteLine.kt
 * Description: Turns PTV's route shapes into the line every map draws.
 *
 * PTV's geopath is the route's real shape (the track or road it runs along): long strings of points, but sent as
 * separate pieces in no particular order (a line in two halves, the city loop, short depot runs and other variants).
 * So a service's line is built stop by stop: between each pair of consecutive stops it takes the piece that runs past
 * both and cuts it between them, joins two pieces where a stop pair spans them, and only falls back to a gentle curve
 * where PTV has no shape at all. The joined line is smoothed a touch so every map's corners look the same.
 *
 * Points are (latitude, longitude) pairs throughout.
 */

package com.ssethhyy.journey.data.model

import com.ssethhyy.journey.utils.GeoUtils
import kotlin.math.cos

object RouteLine {
    /** A stop counts as on a piece of shape when it's this close to it. */
    private const val ON_SHAPE_METERS = 200.0
    /** Two pieces are joined when their ends are this close. */
    private const val JOIN_METERS = 600.0

    /**
     * The line for a service calling at [stops] (in order), following the route's real shape from [shapes] wherever
     * PTV has it, and a smooth curve through the stops wherever it doesn't.
     */
    fun forStops(stops: List<Pair<Double, Double>>, shapes: List<List<Pair<Double, Double>>>): List<Pair<Double, Double>> =
        alongStops(stops, shapes)?.let(::smooth) ?: curveThrough(stops)

    /**
     * The route's real shape from the first stop to the last, stitched stop pair by stop pair; null when the shapes
     * cover less than half the journey (then they're for a different route and a curve looks better).
     */
    fun alongStops(stops: List<Pair<Double, Double>>, shapes: List<List<Pair<Double, Double>>>): List<Pair<Double, Double>>? {
        val usable = stops.filter { it.first != 0.0 || it.second != 0.0 }
        val pieces = shapes.filter { it.size > 1 }
        if (usable.size < 2 || pieces.isEmpty()) return null
        val out = ArrayList<Pair<Double, Double>>()
        var followed = 0
        for (i in 0 until usable.lastIndex) {
            val a = usable[i]
            val b = usable[i + 1]
            val leg = between(a, b, pieces)
            if (leg != null) followed++
            val part = leg ?: listOf(a, b)
            // Each leg starts where the last one ended.
            out += if (out.isEmpty()) part else part.drop(1)
        }
        return out.takeIf { followed * 2 >= usable.lastIndex }
    }

    /** The shape from stop [a] to stop [b]: within one piece, across two that meet, or null when there's none. */
    private fun between(a: Pair<Double, Double>, b: Pair<Double, Double>, pieces: List<List<Pair<Double, Double>>>): List<Pair<Double, Double>>? {
        val nearA = pieces.mapNotNull { piece -> nearest(piece, a).takeIf { it.second <= ON_SHAPE_METERS }?.let { piece to it.first } }
        val nearB = pieces.mapNotNull { piece -> nearest(piece, b).takeIf { it.second <= ON_SHAPE_METERS }?.let { piece to it.first } }
        // One piece runs past both stops: the shortest such run (pieces overlap, e.g. a line and its loop variant).
        val within = nearA.mapNotNull { (piece, ia) ->
            val ib = nearB.firstOrNull { it.first === piece }?.second ?: return@mapNotNull null
            cut(piece, ia, ib)
        }.minByOrNull(::length)
        if (within != null) return listOf(a) + within + listOf(b)
        // Two pieces that meet: follow the first to its end nearest the second, then the second from there.
        val joined = nearA.flatMap { (pa, ia) ->
            nearB.mapNotNull { (pb, ib) ->
                val endA = listOf(0, pa.lastIndex).minBy { metres(pa[it], pb[nearest(pb, pa[it]).first]) }
                val startB = nearest(pb, pa[endA]).first
                if (metres(pa[endA], pb[startB]) > JOIN_METERS) null else cut(pa, ia, endA) + cut(pb, startB, ib)
            }
        }.minByOrNull(::length)
        return joined?.let { listOf(a) + it + listOf(b) }
    }

    /**
     * Rounds off corners a little (Chaikin's method), keeping both ends where they are: one pass for detailed shapes,
     * two for sparse ones, so real track isn't blurred and coarse lines don't look jagged.
     */
    fun smooth(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (path.size < 3) return path
        var line = path
        repeat(if (path.size > 400) 1 else 2) { line = chaikin(line) }
        return line
    }

    /** A smooth curve through every stop (a Catmull-Rom spline), where PTV sent no shape. */
    fun curveThrough(stops: List<Pair<Double, Double>>, steps: Int = 8): List<Pair<Double, Double>> {
        val points = stops.filter { it.first != 0.0 || it.second != 0.0 }
        if (points.size < 3) return points
        val out = ArrayList<Pair<Double, Double>>(points.size * steps)
        for (i in 0 until points.lastIndex) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                out += catmullRom(p0.first, p1.first, p2.first, p3.first, t) to catmullRom(p0.second, p1.second, p2.second, p3.second, t)
            }
        }
        out += points.last()
        return out
    }

    /** [piece] from index [from] to [to], reversed when the service runs the other way along it. */
    private fun cut(piece: List<Pair<Double, Double>>, from: Int, to: Int): List<Pair<Double, Double>> =
        if (from <= to) piece.subList(from, to + 1) else piece.subList(to, from + 1).reversed()

    /** The index of [piece]'s point nearest [point], and how far it is in metres. */
    private fun nearest(piece: List<Pair<Double, Double>>, point: Pair<Double, Double>): Pair<Int, Double> {
        val scale = cos(Math.toRadians(point.first))
        var best = 0
        var bestDistance = Double.MAX_VALUE
        piece.forEachIndexed { i, (lat, lon) ->
            val dLat = lat - point.first
            val dLon = (lon - point.second) * scale
            val d = dLat * dLat + dLon * dLon
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best to metres(point, piece[best])
    }

    private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>) = GeoUtils.haversineMeters(a.first, a.second, b.first, b.second)

    private fun length(path: List<Pair<Double, Double>>): Double = path.zipWithNext { a, b -> metres(a, b) }.sum()

    private fun chaikin(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(path.size * 2)
        out += path.first()
        for (i in 0 until path.lastIndex) {
            val (aLat, aLon) = path[i]
            val (bLat, bLon) = path[i + 1]
            out += (0.75 * aLat + 0.25 * bLat) to (0.75 * aLon + 0.25 * bLon)
            out += (0.25 * aLat + 0.75 * bLat) to (0.25 * aLon + 0.75 * bLon)
        }
        out += path.last()
        return out
    }

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }
}
