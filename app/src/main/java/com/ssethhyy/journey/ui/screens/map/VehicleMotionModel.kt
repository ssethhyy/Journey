package com.ssethhyy.journey.ui.screens.map

/*
 * Feature Module: Map
 * File: VehicleMotionModel.kt
 * Description:
 *   Standalone route-position prediction and vehicle motion model.
 *
 * Responsibilities:
 *   - Convert a route geopath into a distance-along-path coordinate system.
 *   - Project GPS fixes onto the route.
 *   - Distinguish new GPS samples from repeated/cached samples.
 *   - Predict vehicle movement continuously between GPS samples.
 *   - Model acceleration, braking and target speed.
 *   - Learn cruise speed from observed GPS progress.
 *   - Estimate station arrival and stopping position.
 *   - Hold the vehicle at predicted stops for an estimated dwell period.
 *   - Use GPS fixes to correct the predicted position (asymmetrically:
 *     being behind is corrected harder than being ahead).
 *
 * Polling:
 *   API polling is fixed at 10 seconds by the caller.
 *   This model does not recommend or adapt polling intervals.
 */

import android.util.Log
import com.mapbox.geojson.Point
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// =================================================================================================
// General configuration
// =================================================================================================

internal const val M_PER_DEG_LAT = 110_540.0

internal const val TAIL_EXTENSION_M = 150.0

internal const val STOP_MATCH_TOL_M = 60.0
internal const val MIN_STOP_SPACING_M = 30.0

internal const val FIX_BACK_WINDOW_M = 400.0
internal const val FIX_OFFROUTE_M = 150.0
internal const val FIX_REJECT_M = 400.0

internal const val STEP_S = 0.05
internal const val MAX_CATCHUP_S = 120.0
internal const val HISTORY_S = 150.0

internal const val FEED_LATENCY_S = 8.0
internal const val SERVER_UPDATE_PERIOD_S = 30.0

internal const val RESYNC_AHEAD_M = 800.0
internal const val RESYNC_BEHIND_M = 500.0

internal const val ARRIVE_M = 0.3
internal const val ARRIVE_SPEED_MS = 0.5

internal const val PASSED_STOP_M = 20.0

// Estimated dwell is deliberately fixed rather than learned.
internal const val ESTIMATED_DWELL_S = 30.0

// GPS movement classification.

private const val GPS_STATIONARY_RADIUS_M = 5.0
private const val GPS_DEPARTURE_STEP_M = 3.0
private const val GPS_DEPARTURE_TOTAL_M = 8.0

private const val GPS_STOP_CONFIRM_SAMPLES = 3
private const val GPS_DEPARTURE_CONFIRM_SAMPLES = 2

// Motion correction.

private const val CORRECTION_GAIN_BEHIND = 1.0   // model behind GPS: trust fully
private const val CORRECTION_GAIN_AHEAD = 0.5    // model ahead of GPS: ease off gently
private const val CATCHUP_TAU_S = 6.0

// Cruise learning.

private const val CRUISE_LEARN_ALPHA = 0.35

// Small visual lead applied to the displayed position. Set 0.0 to disable.
private const val DISPLAY_LEAD_S = 1.0

// Motion tuning.

private const val MIN_ACCEL = 0.15
private const val MAX_ACCEL = 2.0

private const val MIN_BRAKE = 0.15
private const val MAX_BRAKE = 2.5

private const val STOP_APPROACH_BUFFER_M = 3.0

private const val TAG = "VehicleMotionModel"

internal fun modelNowSeconds(): Double =
    System.nanoTime() / 1_000_000_000.0

// =================================================================================================
// Motion profiles
// =================================================================================================

internal class MotionProfile(
    val cruise: Double,
    val minCruise: Double,
    val maxCruise: Double,
    val accel: Double,
    val brake: Double,
    val platformOverrun: Double,
    val dwellS: Double,
)

internal fun motionProfileFor(mode: com.ssethhyy.journey.data.model.TransportMode): MotionProfile =
    when (mode.name.uppercase().replace("_", "")) {

        "TRAM" -> MotionProfile(
            cruise = 9.0,
            minCruise = 3.0,
            maxCruise = 16.0,
            accel = 1.0,
            brake = 1.1,
            platformOverrun = 6.0,
            dwellS = ESTIMATED_DWELL_S,
        )

        "BUS", "NIGHTBUS" -> MotionProfile(
            cruise = 12.0,
            minCruise = 3.0,
            maxCruise = 25.0,
            accel = 1.1,
            brake = 1.3,
            platformOverrun = 5.0,
            dwellS = ESTIMATED_DWELL_S,
        )

        "VLINE" -> MotionProfile(
            cruise = 30.0,
            minCruise = 8.0,
            maxCruise = 45.0,
            accel = 0.6,
            brake = 0.8,
            platformOverrun = 90.0,
            dwellS = ESTIMATED_DWELL_S,
        )

        else -> MotionProfile(
            cruise = 20.0,
            minCruise = 6.0,
            maxCruise = 40.0,
            accel = 0.8,
            brake = 0.9,
            platformOverrun = 50.0,
            dwellS = ESTIMATED_DWELL_S,
        )
    }

// =================================================================================================
// Route geometry
// =================================================================================================

internal class PathModel private constructor(
    private val lon: DoubleArray,
    private val lat: DoubleArray,
) {

    internal class Projection(
        val s: Double,
        val offsetM: Double,
    )

    private val mPerDegLon =
        M_PER_DEG_LAT * cos(Math.toRadians(lat[lat.size / 2]))

    private val x = DoubleArray(lon.size) {
        (lon[it] - lon[0]) * mPerDegLon
    }

    private val y = DoubleArray(lat.size) {
        (lat[it] - lat[0]) * M_PER_DEG_LAT
    }

    private val cum = DoubleArray(lon.size)

    private var segT = 0.0

    init {
        for (i in 1 until lon.size) {
            cum[i] = cum[i - 1] +
                hypot(
                    x[i] - x[i - 1],
                    y[i] - y[i - 1],
                )
        }
    }

    val length: Double
        get() = cum.last()

    private fun segmentIndexAt(s: Double): Int {
        var i = java.util.Arrays.binarySearch(cum, s)

        if (i < 0) {
            i = -i - 2
        }

        return i.coerceIn(0, cum.size - 2)
    }

    private fun segDistance(
        i: Int,
        px: Double,
        py: Double,
    ): Double {

        val ax = x[i]
        val ay = y[i]

        val dx = x[i + 1] - ax
        val dy = y[i + 1] - ay

        val l2 = dx * dx + dy * dy

        val t = if (l2 > 0.0) {
            ((px - ax) * dx + (py - ay) * dy) / l2
        } else {
            0.0
        }.coerceIn(0.0, 1.0)

        segT = t

        return hypot(
            px - (ax + dx * t),
            py - (ay + dy * t),
        )
    }

    private fun segS(i: Int): Double =
        cum[i] + (cum[i + 1] - cum[i]) * segT

    fun pointAt(s: Double): Point {
        val c = s.coerceIn(0.0, length)
        val i = segmentIndexAt(c)

        val segLen = cum[i + 1] - cum[i]

        val t = if (segLen > 0.0) {
            (c - cum[i]) / segLen
        } else {
            0.0
        }

        return Point.fromLngLat(
            lon[i] + (lon[i + 1] - lon[i]) * t,
            lat[i] + (lat[i + 1] - lat[i]) * t,
        )
    }

    fun headingAt(s: Double): Double {
        val c = s.coerceIn(0.0, length)
        val i = segmentIndexAt(c)

        val dx = x[i + 1] - x[i]
        val dy = y[i + 1] - y[i]

        return (
            Math.toDegrees(atan2(dx, dy)) + 360.0
            ) % 360.0
    }

    fun project(
        latP: Double,
        lonP: Double,
        fromS: Double = 0.0,
        toS: Double = Double.MAX_VALUE,
    ): Projection? {

        val px = (lonP - lon[0]) * mPerDegLon
        val py = (latP - lat[0]) * M_PER_DEG_LAT

        val first = segmentIndexAt(max(fromS, 0.0))
        val last = segmentIndexAt(min(toS, length))

        var bestD = Double.MAX_VALUE
        var bestS = 0.0

        for (i in first..last) {
            val d = segDistance(i, px, py)

            if (d < bestD) {
                bestD = d
                bestS = segS(i)
            }
        }

        return if (bestD == Double.MAX_VALUE) {
            null
        } else {
            Projection(bestS, bestD)
        }
    }

    fun projectForward(
        latP: Double,
        lonP: Double,
        fromS: Double,
        tolM: Double,
    ): Projection? {

        val px = (lonP - lon[0]) * mPerDegLon
        val py = (latP - lat[0]) * M_PER_DEG_LAT

        var best: Projection? = null
        var runBest: Projection? = null

        for (i in segmentIndexAt(max(fromS, 0.0)) until cum.size - 1) {
            val d = segDistance(i, px, py)
            val projectedS = segS(i)

            if (best == null || d < best.offsetM) {
                best = Projection(projectedS, d)
            }

            if (d <= tolM) {
                if (runBest == null || d < runBest.offsetM) {
                    runBest = Projection(projectedS, d)
                }
            } else if (runBest != null) {
                break
            }
        }

        return runBest ?: best
    }

    companion object {

        fun create(
            points: List<Point>,
            tailMeters: Double,
        ): PathModel? {

            val lons = ArrayList<Double>(points.size + 1)
            val lats = ArrayList<Double>(points.size + 1)

            for (p in points) {
                val lo = p.longitude()
                val la = p.latitude()

                if (
                    lons.isNotEmpty() &&
                    lo == lons.last() &&
                    la == lats.last()
                ) {
                    continue
                }

                lons.add(lo)
                lats.add(la)
            }

            if (lons.size < 2) {
                return null
            }

            if (tailMeters > 0.0) {
                val n = lons.size

                val mLon =
                    M_PER_DEG_LAT * cos(Math.toRadians(lats[n / 2]))

                var k = n - 2

                while (
                    k > 0 &&
                    hypot(
                        (lons[n - 1] - lons[k]) * mLon,
                        (lats[n - 1] - lats[k]) * M_PER_DEG_LAT,
                    ) < 10.0
                ) {
                    k--
                }

                val dx = (lons[n - 1] - lons[k]) * mLon
                val dy = (lats[n - 1] - lats[k]) * M_PER_DEG_LAT
                val len = hypot(dx, dy)

                if (len > 0.0) {
                    lons.add(
                        lons[n - 1] + dx / len * tailMeters / mLon,
                    )

                    lats.add(
                        lats[n - 1] + dy / len * tailMeters / M_PER_DEG_LAT,
                    )
                }
            }

            return PathModel(
                lons.toDoubleArray(),
                lats.toDoubleArray(),
            )
        }
    }
}

// =================================================================================================
// Route and stops
// =================================================================================================

internal class StopPoint(
    val s: Double,
    var target: Double,
)

internal class RouteGeometry(
    val path: PathModel,
    val stops: List<StopPoint>,
)

internal fun buildRoute(
    linePoints: List<Point>,
    stopLatLons: List<Pair<Double, Double>>,
    profile: MotionProfile,
): RouteGeometry? {

    var path = PathModel.create(
        linePoints,
        TAIL_EXTENSION_M,
    ) ?: return null

    if (stopLatLons.size >= 2) {
        val a = path.project(
            stopLatLons.first().first,
            stopLatLons.first().second,
        )

        val b = path.project(
            stopLatLons.last().first,
            stopLatLons.last().second,
        )

        if (a != null && b != null && a.s > b.s) {
            path = PathModel.create(
                linePoints.asReversed(),
                TAIL_EXTENSION_M,
            ) ?: return null
        }
    }

    val stops = ArrayList<StopPoint>(stopLatLons.size)

    var from = 0.0

    for ((lat, lon) in stopLatLons) {
        val projection = path.projectForward(
            lat,
            lon,
            from - 30.0,
            STOP_MATCH_TOL_M,
        ) ?: continue

        val target = min(
            projection.s + profile.platformOverrun,
            path.length,
        )

        if (
            stops.isNotEmpty() &&
            target - stops.last().target < MIN_STOP_SPACING_M
        ) {
            continue
        }

        stops += StopPoint(
            s = projection.s,
            target = target,
        )

        from = projection.s
    }

    return RouteGeometry(
        path = path,
        stops = stops,
    )
}

// =================================================================================================
// Live prediction engine
// =================================================================================================

internal class LiveTracker(
    private val profile: MotionProfile,
    private val trainLengthMeters: Double,
) {

    private class Fix(
        val lat: Double,
        val lon: Double,
        val servedSec: Double,
        val reportedAgeSec: Double?,
        val isFirstEver: Boolean,
    )

    private class Sample(
        val t: Double,
        val s: Double,
    )

    private class FixPoint(
        val s: Double,
        val t: Double,
    )

    private var route: RouteGeometry? = null

    private var latestFix: Fix? = null
    private var sawFirst = false

    private var lastRawLat = Double.NaN
    private var lastRawLon = Double.NaN

    private var lastFixPoint: FixPoint? = null

    private var ready = false

    // Simulated vehicle state.

    private var clock = 0.0
    private var s = 0.0
    private var v = 0.0

    // Learned from GPS progress; starts at the profile default.
    private var cruise = profile.cruise
    private val acceleration = profile.accel.coerceIn(MIN_ACCEL, MAX_ACCEL)
    private val braking = profile.brake.coerceIn(MIN_BRAKE, MAX_BRAKE)

    private var targetSpeed = profile.cruise

    private var nextStop = 0
    private var dwelling = false
    private var dwellElapsed = 0.0

    private var debt = 0.0

    // Stationary detection.

    private var stationary = false
    private var stationarySince = 0.0
    private var stationaryAnchorS = 0.0
    private var stationarySamples = 0

    // Departure confirmation.

    private var departureSamples = 0
    private var departureDistance = 0.0
    private var lastDepartureFixS: Double? = null

    // Historical model positions.

    private val history = ArrayDeque<Sample>()
    private var lastRecorded = -1e9

    // =====================================================================
    // Public API
    // =====================================================================

    fun setRoute(newRoute: RouteGeometry?) {
        route = newRoute

        ready = false
        lastFixPoint = null

        history.clear()

        latestFix?.let {
            ingest(it, modelNowSeconds())
        }
    }

    /**
     * Accepts a GPS sample.
     *
     * Exact coordinate equality is intentional.
     * Identical coordinates are treated as potentially cached API responses.
     */
    fun onFix(
        lat: Double,
        lon: Double,
        servedSec: Double,
        reportedAgeSec: Double?,
    ): Boolean {

        if (
            lat == lastRawLat &&
            lon == lastRawLon
        ) {
            return false
        }

        lastRawLat = lat
        lastRawLon = lon

        val fix = Fix(
            lat = lat,
            lon = lon,
            servedSec = servedSec,
            reportedAgeSec = reportedAgeSec,
            isFirstEver = !sawFirst,
        )

        sawFirst = true
        latestFix = fix

        ingest(
            fix,
            modelNowSeconds(),
        )

        return true
    }

    private fun advanceTo(now: Double) {
        if (now - clock > MAX_CATCHUP_S) {
            clock = now - MAX_CATCHUP_S
        }

        while (clock < now) {
            val dt = min(
                STEP_S,
                now - clock,
            )

            if (dt < 1e-6) {
                break
            }

            step(dt)

            clock += dt
            record()
        }
    }

    fun update(now: Double) {
        if (ready) {
            advanceTo(now)
        }
    }

    /**
     * Displayed position. Includes a small visual lead while moving,
     * never leading past the next stop's stopping position.
     */
    fun position(): Point? {
        val r = route ?: return null

        if (!ready) {
            return null
        }

        val lead = if (isMoving()) v * DISPLAY_LEAD_S else 0.0
        val cap = max(
            r.stops.getOrNull(nextStop)?.target ?: r.path.length,
            s,
        )

        return r.path.pointAt(min(s + lead, cap))
    }

    fun distanceAlongPath(): Double? {
        if (!ready) {
            return null
        }

        return s
    }

    fun speedMetersPerSecond(): Double = v

    fun isDwelling(): Boolean = dwelling

    fun isStationary(): Boolean = stationary

    fun isMoving(): Boolean =
        ready && !stationary && !dwelling && v > 0.5

    fun stationaryDurationSeconds(now: Double): Double {
        if (!stationary) {
            return 0.0
        }

        return max(now - stationarySince, 0.0)
    }

    // =====================================================================
    // GPS ingestion
    // =====================================================================

    private fun ingest(
        fix: Fix,
        now: Double,
    ) {

        val r = route ?: return

        val age = fix.reportedAgeSec
            ?: if (fix.isFirstEver) {
                SERVER_UPDATE_PERIOD_S / 2.0
            } else {
                SERVER_UPDATE_PERIOD_S / 2.0
            }

        var fixTime =
            fix.servedSec - age - FEED_LATENCY_S

        lastFixPoint?.let {
            if (fixTime < it.t + 1.0) {
                fixTime = it.t + 1.0
            }
        }

        fixTime = min(fixTime, now)

        var projection: PathModel.Projection? = null

        val hint = if (ready) {
            sAt(fixTime)
        } else {
            null
        }

        if (hint != null) {
            projection = r.path.project(
                fix.lat,
                fix.lon,
                fromS = hint - FIX_BACK_WINDOW_M,
                toS = hint + profile.maxCruise * 60.0,
            )

            if (
                projection != null &&
                projection.offsetM > FIX_OFFROUTE_M
            ) {
                projection = null
            }
        }

        if (projection == null) {
            projection = r.path.project(
                fix.lat,
                fix.lon,
            )
        }

        if (
            projection == null ||
            projection.offsetM > FIX_REJECT_M
        ) {
            Log.w(
                TAG,
                "GPS fix rejected: off route by ${projection?.offsetM}m",
            )

            return
        }

        val sFix = projection.s

        if (!ready) {
            initialise(
                r,
                sFix,
                fixTime,
                now,
            )
        } else {
            val predicted = sAt(fixTime) ?: s
            val error = sFix - predicted     // + means model is behind

            Log.d(
                TAG,
                "fix error=%.1fm v=%.1f cruise=%.1f age=%s".format(
                    error,
                    v,
                    cruise,
                    fix.reportedAgeSec,
                ),
            )

            learnCruise(r, sFix, fixTime)

            val wasDwelling = dwelling
            val stoppedNow = updateStationaryDetection(sFix, fixTime)
            val departed = updateDepartureDetection(sFix)

            // Model thinks it's at the platform, but the vehicle clearly
            // hasn't arrived yet.
            val prematureDwell =
                dwelling && error < -max(40.0, profile.platformOverrun + 30.0)

            when {
                error > RESYNC_AHEAD_M ||
                    error < -RESYNC_BEHIND_M ||
                    prematureDwell ->
                    initialise(r, sFix, fixTime, now)

                departed ->
                    reanchorAfterDeparture(r, sFix, fixTime, now, wasDwelling)

                stoppedNow ->
                    replayFrom(sFix, fixTime, now, 0.0)

                else ->
                    debt = error *
                        if (error > 0) CORRECTION_GAIN_BEHIND else CORRECTION_GAIN_AHEAD
            }
        }

        lastFixPoint = FixPoint(
            s = sFix,
            t = fixTime,
        )
    }

    // =====================================================================
    // Cruise learning
    // =====================================================================

    /**
     * Average speed between fixes is a lower bound on cruise speed
     * (it includes dwell and braking), so it can always raise cruise,
     * but can only lower it on stop-free intervals.
     */
    private fun learnCruise(
        r: RouteGeometry,
        sFix: Double,
        fixTime: Double,
    ) {
        val prev = lastFixPoint ?: return

        val dt = fixTime - prev.t
        val ds = sFix - prev.s

        if (dt < 10.0 || ds < 50.0) {
            return
        }

        val obs = ds / dt

        val stopFree = r.stops.none {
            it.s > prev.s - 25.0 && it.s < sFix + 25.0
        }

        val target = when {
            obs > cruise -> obs      // faster than cruise even including any stops
            stopFree -> obs          // free-running interval, fair estimate
            else -> return
        }

        cruise += CRUISE_LEARN_ALPHA *
            (target.coerceIn(profile.minCruise, profile.maxCruise) - cruise)
    }

    // =====================================================================
    // Initialisation and replay
    // =====================================================================

    private fun initialise(
        r: RouteGeometry,
        sFix: Double,
        fixTime: Double,
        now: Double,
    ) {

        ready = true

        history.clear()
        lastRecorded = -1e9

        clock = fixTime
        s = sFix

        v = 0.0
        debt = 0.0

        dwelling = false
        dwellElapsed = 0.0

        stationary = false
        stationarySince = 0.0
        stationarySamples = 0
        stationaryAnchorS = sFix

        departureSamples = 0
        departureDistance = 0.0
        lastDepartureFixS = null

        val idx = r.stops.indexOfFirst {
            it.target > sFix - PASSED_STOP_M
        }

        nextStop = if (idx < 0) {
            r.stops.size
        } else {
            idx
        }

        val stop = r.stops.getOrNull(nextStop)

        if (
            stop != null &&
            sFix >= stop.s - STOP_APPROACH_BUFFER_M &&
            sFix <= stop.target + trainLengthMeters
        ) {
            v = 0.0
            dwelling = true
            stationary = true
            stationarySince = fixTime
            stationaryAnchorS = sFix
            dwellElapsed = 0.0
        } else {
            val distance = stop?.let {
                max(it.target - sFix, 0.0)
            } ?: Double.MAX_VALUE

            // Realistic speed just after leaving a stop.
            val prevStop = r.stops.lastOrNull { it.target <= sFix }

            val accelLimit = prevStop
                ?.takeIf { sFix - it.target < 100.0 }
                ?.let {
                    sqrt(2.0 * acceleration * max(sFix - it.target, 0.0))
                }
                ?: Double.MAX_VALUE

            v = min(
                cruise,
                min(
                    sqrt(2.0 * braking * distance),
                    accelLimit,
                ),
            )
        }

        targetSpeed = v

        record()
        advanceTo(now)
    }

    /**
     * Restarts the simulation from the fix's position and time, then
     * fast-forwards to now. Used instead of nudging via [debt] when the
     * fix tells us something definitive (departure, stop).
     */
    private fun replayFrom(
        sFix: Double,
        fixTime: Double,
        now: Double,
        v0: Double,
    ) {
        history.clear()
        lastRecorded = -1e9

        clock = fixTime
        s = sFix
        v = v0
        debt = 0.0
        targetSpeed = max(v0, profile.minCruise)

        record()
        advanceTo(now)
    }

    private fun reanchorAfterDeparture(
        r: RouteGeometry,
        sFix: Double,
        fixTime: Double,
        now: Double,
        fromStop: Boolean,
    ) {
        val prev = r.stops.lastOrNull { it.target <= sFix }

        // Speed the vehicle could have reached accelerating from the platform.
        val v0 = if (fromStop && prev != null) {
            min(
                cruise,
                sqrt(2.0 * acceleration * max(sFix - prev.target, 0.0)),
            )
        } else {
            min(cruise, 2.0)
        }

        replayFrom(sFix, fixTime, now, v0)
    }

    // =====================================================================
    // Stationary detection
    // =====================================================================

    /**
     * Returns true if the vehicle was newly confirmed stationary.
     */
    private fun updateStationaryDetection(
        sFix: Double,
        fixTime: Double,
    ): Boolean {

        if (lastFixPoint == null) {
            return false
        }

        var confirmedNow = false

        val displacement = abs(sFix - stationaryAnchorS)

        if (displacement <= GPS_STATIONARY_RADIUS_M) {
            stationarySamples++

            if (
                !stationary &&
                stationarySamples >= GPS_STOP_CONFIRM_SAMPLES
            ) {
                stationary = true
                stationarySince = fixTime
                stationaryAnchorS = sFix
                confirmedNow = true

                Log.d(
                    TAG,
                    "Stationary vehicle confirmed at $sFix",
                )

                val stop = route?.stops?.getOrNull(nextStop)

                if (
                    stop != null &&
                    abs(sFix - stop.s) <= STOP_MATCH_TOL_M
                ) {
                    dwelling = true
                    dwellElapsed = 0.0
                }
            }
        } else {
            stationarySamples = 0

            if (
                stationary &&
                abs(sFix - stationaryAnchorS) > GPS_DEPARTURE_TOTAL_M
            ) {
                stationary = false
                stationarySince = 0.0
            }

            stationaryAnchorS = sFix
        }

        return confirmedNow
    }

    // =====================================================================
    // Departure confirmation
    // =====================================================================

    /**
     * GPS can confirm departure early.
     *
     * Otherwise the model automatically resumes after the estimated
     * dwell period has elapsed.
     *
     * Returns true if a departure was confirmed.
     */
    private fun updateDepartureDetection(
        sFix: Double,
    ): Boolean {

        if (!dwelling && !stationary) {
            return false
        }

        val stop = route?.stops?.getOrNull(nextStop)

        if (stop != null) {
            val ahead = sFix - stop.target

            if (
                ahead > max(
                    12.0,
                    trainLengthMeters * 0.65,
                )
            ) {
                confirmDeparture()
                return true
            }
        }

        val previous = lastDepartureFixS

        if (previous != null) {
            val delta = sFix - previous

            if (delta >= GPS_DEPARTURE_STEP_M) {
                departureSamples++
                departureDistance += delta
            } else if (delta < -GPS_STATIONARY_RADIUS_M) {
                departureSamples = 0
                departureDistance = 0.0
            }
        }

        lastDepartureFixS = sFix

        if (
            departureSamples >= GPS_DEPARTURE_CONFIRM_SAMPLES &&
            departureDistance >= GPS_DEPARTURE_TOTAL_M
        ) {
            confirmDeparture()
            return true
        }

        return false
    }

    private fun confirmDeparture() {

        if (!dwelling && !stationary) {
            return
        }

        Log.d(
            TAG,
            "Departure confirmed by GPS",
        )

        // Only advance the stop index if the vehicle was actually at a stop,
        // not merely held at a light.
        if (dwelling) {
            nextStop++
        }

        dwelling = false
        stationary = false

        stationarySince = 0.0
        stationarySamples = 0

        dwellElapsed = 0.0

        departureSamples = 0
        departureDistance = 0.0
        lastDepartureFixS = null
    }

    // =====================================================================
    // History
    // =====================================================================

    private fun record() {

        if (clock - lastRecorded < 0.1) {
            return
        }

        history.addLast(
            Sample(
                clock,
                s,
            ),
        )

        lastRecorded = clock

        while (
            history.size > 1 &&
            history.first().t < clock - HISTORY_S
        ) {
            history.removeFirst()
        }
    }

    /**
     * Returns where the model believed the front of the vehicle was
     * at a particular time.
     */
    private fun sAt(t: Double): Double? {

        if (history.isEmpty()) {
            return null
        }

        if (t >= clock) {
            return s
        }

        if (t <= history.first().t) {
            return history.first().s
        }

        val samples = history.toList()

        var lo = 0
        var hi = samples.lastIndex

        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1

            if (samples[mid].t <= t) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }

        val a = samples[lo]
        val b = samples.getOrNull(lo + 1)

        val bt = b?.t ?: clock
        val bs = b?.s ?: s

        val fraction = if (bt > a.t) {
            (t - a.t) / (bt - a.t)
        } else {
            0.0
        }

        return a.s + (bs - a.s) * fraction
    }

    // =====================================================================
    // Acceleration and braking
    // =====================================================================

    private fun approachSpeed(
        current: Double,
        target: Double,
        acceleration: Double,
        dt: Double,
    ): Double {

        val difference = target - current
        val maximumChange = acceleration * dt

        return when {
            difference > maximumChange ->
                current + maximumChange

            difference < -maximumChange ->
                current - maximumChange

            else ->
                target
        }.coerceAtLeast(0.0)
    }

    /**
     * Maximum speed that still allows stopping at the target:
     *
     *   v = sqrt(2 * brake * (distance - buffer))
     */
    private fun brakingLimit(remaining: Double?): Double {

        if (remaining == null) {
            return Double.MAX_VALUE
        }

        if (remaining <= ARRIVE_M) {
            return 0.0
        }

        return sqrt(
            2.0 * braking * max(
                remaining - STOP_APPROACH_BUFFER_M,
                0.0,
            ),
        )
    }

    private fun calculateTargetSpeed(
        remaining: Double?,
    ): Double = min(cruise, brakingLimit(remaining))

    // =====================================================================
    // Main simulation step
    // =====================================================================

    private fun step(dt: Double) {

        val r = route ?: return
        val stops = r.stops

        while (
            !dwelling &&
            nextStop < stops.size &&
            s > stops[nextStop].target + PASSED_STOP_M
        ) {
            nextStop++
        }

        val stop = stops.getOrNull(nextStop)

        // -----------------------------------------------------------------
        // Estimated dwell
        // -----------------------------------------------------------------

        if (dwelling) {
            targetSpeed = 0.0
            v = 0.0

            dwellElapsed += dt

            if (dwellElapsed >= profile.dwellS) {
                confirmEstimatedDeparture()
            }

            return
        }

        // -----------------------------------------------------------------
        // Unscheduled stationary stop
        // -----------------------------------------------------------------

        if (stationary) {
            targetSpeed = 0.0

            v = approachSpeed(
                current = v,
                target = 0.0,
                acceleration = braking,
                dt = dt,
            )

            val ds = v * dt

            s = min(
                s + ds,
                r.path.length,
            )

            return
        }

        // -----------------------------------------------------------------
        // Moving
        // -----------------------------------------------------------------

        val remaining = stop?.let {
            it.target - s
        }

        targetSpeed = calculateTargetSpeed(remaining)

        val accelerationToUse = if (targetSpeed > v) {
            acceleration
        } else {
            braking
        }

        v = approachSpeed(
            current = v,
            target = targetSpeed,
            acceleration = accelerationToUse,
            dt = dt,
        )

        // -----------------------------------------------------------------
        // GPS correction debt
        // -----------------------------------------------------------------

        var movementSpeed = v

        if (abs(debt) > 0.5) {
            if (debt > 0.0) {
                // Behind GPS: may exceed cruise up to the profile max,
                // but never beyond the braking envelope for the next stop.
                val ceiling = max(
                    v,
                    min(profile.maxCruise, brakingLimit(remaining)),
                )

                val boost = min(
                    debt / CATCHUP_TAU_S,
                    0.5 * cruise,
                )

                movementSpeed = min(v + boost, ceiling)
            } else {
                // Ahead of GPS: ease off gently.
                movementSpeed = v + max(
                    debt / CATCHUP_TAU_S,
                    -0.4 * v,
                )
            }

            movementSpeed = max(movementSpeed, 0.0)

            debt -= (movementSpeed - v) * dt

            // Only forgive being ahead. Being behind is never leaked away.
            if (debt < 0.0) {
                debt *= exp(-dt / 20.0)
            }
        }

        // -----------------------------------------------------------------
        // Integrate position
        // -----------------------------------------------------------------

        var ds = movementSpeed * dt

        if (remaining != null && remaining >= 0.0) {
            ds = min(
                ds,
                remaining,
            )
        }

        s = min(
            s + ds,
            r.path.length,
        )

        // -----------------------------------------------------------------
        // Arrival
        // -----------------------------------------------------------------

        if (
            stop != null &&
            remaining != null &&
            remaining <= STOP_APPROACH_BUFFER_M + ARRIVE_M &&
            v <= ARRIVE_SPEED_MS
        ) {
            s = stop.target

            v = 0.0
            targetSpeed = 0.0

            dwelling = true
            dwellElapsed = 0.0

            stationary = true
            stationarySince = clock
            stationaryAnchorS = s
            stationarySamples = 0

            departureSamples = 0
            departureDistance = 0.0
            lastDepartureFixS = null

            Log.d(
                TAG,
                "Predicted station arrival at ${stop.target}; estimated dwell ${profile.dwellS}s",
            )
        }
    }

    /**
     * Resume movement after the estimated dwell period.
     *
     * Unlike GPS-confirmed departure, this does not depend on another
     * API response arriving. The vehicle accelerates from rest using
     * the normal motion profile.
     */
    private fun confirmEstimatedDeparture() {

        if (!dwelling) {
            return
        }

        Log.d(
            TAG,
            "Estimated dwell complete; resuming movement",
        )

        dwelling = false
        stationary = false

        stationarySince = 0.0
        stationarySamples = 0

        dwellElapsed = 0.0

        departureSamples = 0
        departureDistance = 0.0
        lastDepartureFixS = null

        nextStop++

        v = 0.0
        targetSpeed = profile.minCruise
    }
}
