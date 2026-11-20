
/*
 * Feature Module: Map
 * File: TripMapLayer.kt
 * Description: Draws the route line, stops, and live vehicle marker for a selected trip.
 *
 * Geopath selection:
 *  - usePatternGeopath = true: Uses the geopath from the stopping pattern response.
 *  - usePatternGeopath = false: Uses routeDetails().geopaths and RouteLine.forStops().
 *
 * Rendering
 *  - Route line + stop dots are two style layers in the same "top" slot, both laid flat on the
 *    map (3D space), both the transport mode colour.
 *  - At close zoom, an optional 3D carriage consist follows the live route position.
 *  - Otherwise, the vehicle is an Android-view annotation (2D, screen space).
 *
 * Live tracking (see LiveTracker below)
 *  - The route geopath is turned into a 1D "distance along path" axis.
 *  - Every distinct GPS fix is snapped onto that axis.
 *  - A small kinematic model (cruise speed, acceleration, braking to each stop, platform dwell)
 *    runs every frame, in real time, on that axis. It is the thing that is displayed.
 *  - Each new fix is compared with where the model was at the fix's time. The difference does
 *    not move the train; it becomes a "debt" that is paid off by speeding up / slowing down.
 */

package com.ssethhyy.journey.ui.screens.map

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.CirclePitchAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.CirclePitchScale
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.viewannotation.geometry
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.RouteLine
import com.ssethhyy.journey.data.model.StoppingPattern
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.model.VehiclePosition
import com.ssethhyy.journey.ui.components.VehicleBadge
import com.ssethhyy.journey.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// =================================================================================================
// Tunables
// =================================================================================================

/** How often we ask the server for the vehicle position. */
private const val LIVE_REFRESH_MS = 10_000L
private const val POLL_INTERVAL_S = LIVE_REFRESH_MS / 1000.0

/** How often the server's position actually changes (roughly). */
private const val SERVER_UPDATE_PERIOD_S = 30.0

/** Assumed delay between GPS sampling and the data reaching us. */
private const val FEED_LATENCY_S = 8.0

/** Stop dot radius (px). */
private const val STOP_DOT_RADIUS = 6.0
private const val LINE_WIDTH = 6.0

private const val M_PER_DEG_LAT = 110_540.0

/** Extra distance the model path continues past the final point. */
private const val TAIL_EXTENSION_M = 150.0

// --- Matching GPS / stops to the path ---
private const val STOP_MATCH_TOL_M = 60.0
private const val MIN_STOP_SPACING_M = 30.0
private const val FIX_BACK_WINDOW_M = 400.0
private const val FIX_OFFROUTE_M = 150.0
private const val FIX_REJECT_M = 400.0

// --- Model behaviour ---
private const val STEP_S = 0.05
private const val MAX_CATCHUP_S = 120.0
private const val HISTORY_S = 150.0
private const val CORRECTION_GAIN = 0.8
private const val CATCHUP_TAU_S = 6.0
private const val RESYNC_AHEAD_M = 800.0
private const val RESYNC_BEHIND_M = 500.0
private const val RELEASE_AHEAD_M = 40.0
private const val DEPART_HOLD_M = 30.0
private const val PASSED_STOP_M = 20.0
private const val STATION_ZONE_M = 35.0
private const val ARRIVE_M = 0.3
private const val ARRIVE_SPEED_MS = 0.5
private const val CREEP_MS = 0.8
private const val LEARN_ALPHA = 0.4
private const val MIN_LEARN_SPEED_MS = 3.0

private fun nowSeconds(): Double = System.nanoTime() / 1_000_000_000.0

@Suppress("UnusedReceiverParameter")
private fun VehiclePosition.reportedAgeSeconds(): Double? = null

// =================================================================================================
// Composable
// =================================================================================================

@Composable
fun TripMapLayer(
    runRef: String,
    mode: TransportMode,
    directionId: Int,
    onLoaded: (StoppingPattern) -> Unit = {},
    render3dVehicles: Boolean = false,
    usePatternGeopath: Boolean = false,
) {
    val container = LocalContext.current.appContainer

    var pattern: StoppingPattern? by remember(runRef, mode) {
        mutableStateOf(null)
    }

    var geopath: List<Point> by remember(runRef, mode) {
        mutableStateOf(emptyList())
    }

    var vehicle: VehiclePosition? by remember(runRef, mode) {
        mutableStateOf(null)
    }

    val tracker = remember(runRef, mode) { LiveTracker(motionProfileFor(mode)) }
    val markerPoint = remember(runRef, mode) { mutableStateOf<Point?>(null) }
    val markerVisible = remember(runRef, mode) { mutableStateOf(false) }
    val frontDistance = remember(runRef, mode) { mutableStateOf<Double?>(null) }

    var mapZoom by remember { mutableStateOf(0.0) }

    // ---- Initial load: pattern + selected geopath source ------------------------------

    LaunchedEffect(runRef, mode, directionId, usePatternGeopath) {
        pattern = null
        geopath = emptyList()
        vehicle = null

        val result: Result<StoppingPattern> = withContext(Dispatchers.IO) {
            runCatching {
                container.ptvRepository.stoppingPattern(
                    runRef,
                    mode,
                    withPath = true,
                )
            }
        }

        result.fold(
            onSuccess = { loaded ->
                Log.d(
                    "TripMapLayer",
                    "loaded stops=${loaded.stops.size} " +
                        "direction=${loaded.directionId} route=${loaded.route?.id}",
                )

                pattern = loaded
                vehicle = loaded.vehiclePosition

                loaded.vehiclePosition?.let { vp ->
                    tracker.onFix(
                        vp.latitude,
                        vp.longitude,
                        nowSeconds(),
                        vp.reportedAgeSeconds(),
                    )
                }

                onLoaded(loaded)

                if (usePatternGeopath) {
                    // ---- PATTERN GEOPATH --------------------------------------------

                    val patternPath = loaded.path

                    if (patternPath.size >= 2) {
                        geopath = patternPath.map { (lat, lon) ->
                            Point.fromLngLat(lon, lat)
                        }

                        Log.d(
                            "GeoPath",
                            "Using PATTERN geopath: points=${geopath.size} " +
                                "first=${patternPath.first()} " +
                                "last=${patternPath.last()}",
                        )
                    } else {
                        Log.w(
                            "GeoPath",
                            "Pattern geopath contains no usable points: " +
                                "points=${patternPath.size}",
                        )
                    }
                } else {
                    // ---- ROUTE DETAILS GEOPATH --------------------------------------

                    val routeId = loaded.route?.id

                    if (routeId == null) {
                        Log.w(
                            "GeoPath",
                            "No route ID on pattern; cannot load route-details geopath",
                        )
                    } else {
                        val geoResult: Result<List<Point>> =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val details =
                                        container.ptvRepository.routeDetails(routeId)

                                    Log.d("GeoPath", "=== ROUTE DETAILS GEOPATH ===")
                                    Log.d(
                                        "GeoPath",
                                        "geopaths map size=${details.geopaths.size}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "geopaths keys=${details.geopaths.keys}",
                                    )

                                    for ((dir, chunks) in details.geopaths) {
                                        val pts = chunks.sumOf { it.size }

                                        Log.d(
                                            "GeoPath",
                                            "dir=$dir chunks=${chunks.size} totalPts=$pts",
                                        )

                                        chunks.forEachIndexed { ci, chunk ->
                                            Log.d(
                                                "GeoPath",
                                                "dir=$dir chunk[$ci] size=${chunk.size} " +
                                                    "first=${chunk.firstOrNull()} " +
                                                    "last=${chunk.lastOrNull()}",
                                            )
                                        }
                                    }

                                    val routeShapes =
                                        details.geopaths[loaded.directionId].orEmpty()

                                    Log.d(
                                        "GeoPath",
                                        "Selected direction=${loaded.directionId} " +
                                            "chunks=${routeShapes.size} " +
                                            "sizes=${routeShapes.map { it.size }}",
                                    )

                                    val flatShapes = routeShapes.flatten()

                                    Log.d(
                                        "GeoPath",
                                        "flatShapes size=${flatShapes.size}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "flatShapes first5=${flatShapes.take(5)}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "flatShapes last5=${flatShapes.takeLast(5)}",
                                    )

                                    if (flatShapes.isEmpty()) {
                                        throw IllegalStateException(
                                            "No geopath points for direction ${loaded.directionId}",
                                        )
                                    }

                                    fun nearestOnPath(
                                        lat: Double,
                                        lon: Double,
                                    ): Pair<Double, Double> =
                                        flatShapes.minByOrNull { (slat, slon) ->
                                            val dLat = slat - lat
                                            val dLon = slon - lon
                                            dLat * dLat + dLon * dLon
                                        } ?: (lat to lon)

                                    val virtualStops =
                                        loaded.stops.map { ps ->
                                            nearestOnPath(
                                                ps.stop.latitude,
                                                ps.stop.longitude,
                                            )
                                        }

                                    Log.d(
                                        "GeoPath",
                                        "virtualStops size=${virtualStops.size}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "virtualStops first5=${virtualStops.take(5)}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "virtualStops last5=${virtualStops.takeLast(5)}",
                                    )

                                    val stitched =
                                        withContext(Dispatchers.Default) {
                                            RouteLine.forStops(
                                                virtualStops,
                                                routeShapes,
                                            )
                                        }

                                    Log.d(
                                        "GeoPath",
                                        "stitched size=${stitched.size}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "stitched first5=${stitched.take(5)}",
                                    )
                                    Log.d(
                                        "GeoPath",
                                        "stitched last5=${stitched.takeLast(5)}",
                                    )

                                    if (stitched.size <= virtualStops.size + 2) {
                                        Log.w(
                                            "GeoPath",
                                            "SUSPICIOUS: stitched output resembles " +
                                                "stop-to-stop fallback",
                                        )
                                    } else {
                                        Log.d(
                                            "GeoPath",
                                            "OK: real route shape points preserved",
                                        )
                                    }

                                    stitched.map { (lat, lon) ->
                                        Point.fromLngLat(lon, lat)
                                    }
                                }
                            }

                        geoResult.fold(
                            onSuccess = { points ->
                                geopath = points

                                Log.d(
                                    "GeoPath",
                                    "Using ROUTE DETAILS geopath: " +
                                        "points=${points.size} " +
                                        "first=${points.firstOrNull()} " +
                                        "last=${points.lastOrNull()}",
                                )
                            },
                            onFailure = { t ->
                                Log.e(
                                    "GeoPath",
                                    "Route-details geopath fetch failed",
                                    t,
                                )
                            },
                        )
                    }
                }
            },
            onFailure = { t ->
                Log.e(
                    "TripMapLayer",
                    "Pattern fetch failed",
                    t,
                )
            },
        )
    }

    // ---- Live position poll: fixed rate, feeds the tracker directly -----------------

    LaunchedEffect(runRef, mode, tracker) {
        var nextAt = SystemClock.elapsedRealtime() + LIVE_REFRESH_MS

        while (isActive) {
            delay((nextAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            nextAt += LIVE_REFRESH_MS

            val sentSec = nowSeconds()

            val fresh = withContext(Dispatchers.IO) {
                runCatching {
                    container.ptvRepository.stoppingPattern(
                        runRef,
                        mode,
                        withPath = false,
                    )
                }
            }.getOrNull()

            val recvSec = nowSeconds()

            val clockNow = SystemClock.elapsedRealtime()
            if (nextAt < clockNow) {
                nextAt = clockNow + LIVE_REFRESH_MS
            }

            val pos = fresh?.vehiclePosition ?: continue

            vehicle = pos

            tracker.onFix(
                pos.latitude,
                pos.longitude,
                (sentSec + recvSec) / 2.0,
                pos.reportedAgeSeconds(),
            )
        }
    }

    // ---- Per-frame animation: advance the model, publish the marker position ---------

    LaunchedEffect(tracker) {
        var lastPublishNs = 0L
        var lastFreshCheckNs = 0L

        while (isActive) {
            withFrameNanos { frameNs ->
                tracker.update(nowSeconds())

                if (frameNs - lastPublishNs >= 33_000_000L) {
                    lastPublishNs = frameNs

                    val p = tracker.position()
                    if (p != null && p != markerPoint.value) {
                        markerPoint.value = p
                    }

                    val distance = tracker.distanceAlongPath()
                    if (distance != frontDistance.value) {
                        frontDistance.value = distance
                    }
                }

                if (frameNs - lastFreshCheckNs >= 1_000_000_000L) {
                    lastFreshCheckNs = frameNs
                    markerVisible.value = vehicle?.isFresh(Instant.now()) == true
                }
            }
        }
    }

    val current: StoppingPattern = pattern ?: return

    // Use the selected geopath. If it is unavailable, fall back to the actual stops,
    // rather than silently switching to the other geopath source.
    val linePoints: List<Point> = remember(current, geopath) {
        when {
            geopath.size >= 2 -> geopath

            else -> current.stops.map { ps ->
                Point.fromLngLat(
                    ps.stop.longitude,
                    ps.stop.latitude,
                )
            }
        }
    }

    // Distance-along-path geometry shared by the stop dots and the tracker.

    val route: RouteGeometry? = remember(current, linePoints, mode) {
        buildRoute(
            linePoints = linePoints,
            stopLatLons = current.stops.map {
                it.stop.latitude to it.stop.longitude
            },
            profile = motionProfileFor(mode),
        )
    }

    LaunchedEffect(tracker, route) {
        tracker.setRoute(route)
    }

    // Track the MapView and observe camera zoom.

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    MapEffect(Unit) { mapView ->
        mapViewRef = mapView
        mapZoom = mapView.mapboxMap.cameraState.zoom

        val cameraSubscription = mapView.mapboxMap.subscribeCameraChanged {
            mapZoom = mapView.mapboxMap.cameraState.zoom
        }

        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            cameraSubscription.cancel()

            if (mapViewRef === mapView) {
                mapViewRef = null
            }
        }
    }

    // ---- Route line + stop dots -------------------------------------------------------

    MapEffect(route) { mapView ->
        mapViewRef = mapView

        val style = mapView.mapboxMap.style ?: return@MapEffect

        removeRouteLayers(style, runRef)

        if (route == null || linePoints.size < 2) {
            return@MapEffect
        }

        val argb = mode.color.toArgb()

        style.addSource(
            geoJsonSource(lineSourceId(runRef)) {
                geometry(LineString.fromLngLats(linePoints))
            },
        )

        style.addLayer(
            lineLayer(lineLayerId(runRef), lineSourceId(runRef)) {
                lineColor(argb)
                lineWidth(LINE_WIDTH)
                lineOpacity(1.0)
                lineEmissiveStrength(1.0)
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                slot("top")
            },
        )

        val dots: List<Feature> = route.stops.map { stop ->
            Feature.fromGeometry(route.path.pointAt(stop.s))
        }

        if (dots.isNotEmpty()) {
            style.addSource(
                geoJsonSource(stopsSourceId(runRef)) {
                    featureCollection(
                        FeatureCollection.fromFeatures(dots),
                    )
                },
            )

            style.addLayer(
                circleLayer(stopsLayerId(runRef), stopsSourceId(runRef)) {
                    circleColor(argb)
                    circleRadius(STOP_DOT_RADIUS)
                    circleOpacity(1.0)
                    circleEmissiveStrength(1.0)
                    circlePitchAlignment(CirclePitchAlignment.MAP)
                    circlePitchScale(CirclePitchScale.MAP)
                    slot("top")
                },
            )
        }
    }

    DisposableEffect(runRef) {
        onDispose {
            val mapView = mapViewRef ?: return@onDispose
            val style = mapView.mapboxMap.style ?: return@onDispose

            removeRouteLayers(style, runRef)
        }
    }

    // ---- Live vehicle rendering -------------------------------------------------------

    val trainDistance = frontDistance.value

    val renderTrain3d = render3dVehicles &&
        mapZoom >= TRAIN_3D_MIN_ZOOM &&
        markerVisible.value &&
        route != null &&
        trainDistance != null

    VehicleModelLayer(
        enabled = renderTrain3d,
        zoom = mapZoom,
        frontDistanceMeters = trainDistance ?: 0.0,
        pathLengthMeters = route?.path?.length ?: 0.0,
        pointAtDistance = { distance ->
            route?.path?.pointAt(distance)
                ?: markerPoint.value
                ?: Point.fromLngLat(0.0, 0.0)
        },
        headingAtDistance = { distance ->
            route?.path?.headingAt(distance) ?: 0.0
        },
        consist = listOf(
            "asset://model_train_xtrapolis_motor.glb",
            "asset://model_train_xtrapolis_motor.glb",
            "asset://model_train_xtrapolis_motor.glb",
            "asset://model_train_xtrapolis_motor.glb",
            "asset://model_train_xtrapolis_motor.glb",
            "asset://model_train_xtrapolis_motor.glb",
        ),
    )

    LiveVehicleAnnotation(
        mode = mode,
        point = markerPoint,
        visible = markerVisible,
        enabled = !renderTrain3d,
    )
}

private fun lineSourceId(runRef: String) = "$runRef-line-source"
private fun lineLayerId(runRef: String) = "$runRef-route-line"
private fun stopsSourceId(runRef: String) = "$runRef-stops-source"
private fun stopsLayerId(runRef: String) = "$runRef-stops-layer"

private fun removeRouteLayers(style: Style, runRef: String) {
    for (layerId in listOf(stopsLayerId(runRef), lineLayerId(runRef))) {
        if (style.styleLayerExists(layerId)) {
            style.removeStyleLayer(layerId)
        }
    }

    for (sourceId in listOf(stopsSourceId(runRef), lineSourceId(runRef))) {
        if (style.styleSourceExists(sourceId)) {
            style.removeStyleSource(sourceId)
        }
    }
}

/**
 * Lives in its own composable so that the ~30 Hz position updates only recompose this tiny scope.
 */
@Composable
private fun LiveVehicleAnnotation(
    mode: TransportMode,
    point: State<Point?>,
    visible: State<Boolean>,
    enabled: Boolean,
) {
    val p = point.value ?: return

    ViewAnnotation(
        options = ViewAnnotationOptions.Builder()
            .geometry(p)
            .allowOverlap(true)
            .allowOverlapWithPuck(true)
            .ignoreCameraPadding(true)
            .visible(enabled && visible.value)
            .build(),
    ) {
        LiveVehicleMarker(mode)
    }
}

@Composable
private fun LiveVehicleMarker(mode: TransportMode) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(52.dp)
                .background(
                    mode.color.copy(alpha = 0.22f),
                    CircleShape,
                ),
        )

        VehicleBadge(
            mode,
            Modifier.shadow(4.dp, CircleShape),
            size = 38.dp,
        )
    }
}

// =================================================================================================
// Per-mode motion parameters
// =================================================================================================

private class MotionProfile(
    val cruise: Double,
    val minCruise: Double,
    val maxCruise: Double,
    val accel: Double,
    val brake: Double,
    val platformOverrun: Double,
    val dwellS: Double,
)

private fun motionProfileFor(mode: TransportMode): MotionProfile =
    when (mode.name.uppercase().replace("_", "")) {
        "TRAM" -> MotionProfile(9.0, 3.0, 16.0, 1.0, 1.1, 6.0, 15.0)
        "BUS", "NIGHTBUS" -> MotionProfile(12.0, 3.0, 25.0, 1.1, 1.3, 5.0, 12.0)
        "VLINE" -> MotionProfile(30.0, 8.0, 45.0, 0.6, 0.8, 90.0, 60.0)
        else -> MotionProfile(20.0, 6.0, 40.0, 0.8, 0.9, 50.0, 25.0)
    }

// =================================================================================================
// Path geometry: a polyline with a distance-along-path axis
// =================================================================================================

private class PathModel private constructor(
    private val lon: DoubleArray,
    private val lat: DoubleArray,
) {
    class Projection(val s: Double, val offsetM: Double)

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
        get() = cum[cum.size - 1]

    private fun segmentIndexAt(s: Double): Int {
        var i = java.util.Arrays.binarySearch(cum, s)

        if (i < 0) {
            i = -i - 2
        }

        return i.coerceIn(0, cum.size - 2)
    }

    /** Distance from (px,py) to segment i; leaves the clamped parameter in [segT]. */
    private fun segDistance(i: Int, px: Double, py: Double): Double {
        val ax = x[i]
        val ay = y[i]
        val dx = x[i + 1] - ax
        val dy = y[i + 1] - ay
        val l2 = dx * dx + dy * dy

        var t = if (l2 > 0.0) {
            ((px - ax) * dx + (py - ay) * dy) / l2
        } else {
            0.0
        }

        t = t.coerceIn(0.0, 1.0)
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

    /** Bearing clockwise from north for the path tangent at distance [s]. */
    fun headingAt(s: Double): Double {
        val c = s.coerceIn(0.0, length)
        val i = segmentIndexAt(c)
        val dx = x[i + 1] - x[i]
        val dy = y[i + 1] - y[i]

        return (
            Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360.0
            ) % 360.0
    }

    /** Nearest point on the path, searching only segments between distances [fromS, toS]. */
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

    /**
     * For stops: scan forward from [fromS] and take the FIRST stretch of path that passes within
     * [tolM] of the stop (best point within that stretch).
     */
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
            val s = segS(i)

            if (best == null || d < best.offsetM) {
                best = Projection(s, d)
            }

            if (d <= tolM) {
                if (runBest == null || d < runBest.offsetM) {
                    runBest = Projection(s, d)
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

            // Continue the final heading a little further so a train can overrun the last stop.
            if (tailMeters > 0.0) {
                val n = lons.size
                val mLon = M_PER_DEG_LAT * cos(Math.toRadians(lats[n / 2]))

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

/** A stop located on the path. [s] is the platform-centre; [target] is where the FRONT stops. */
private class StopPoint(
    val s: Double,
    val target: Double,
)

private class RouteGeometry(
    val path: PathModel,
    val stops: List<StopPoint>,
)

private fun buildRoute(
    linePoints: List<Point>,
    stopLatLons: List<Pair<Double, Double>>,
    profile: MotionProfile,
): RouteGeometry? {
    var path = PathModel.create(linePoints, TAIL_EXTENSION_M) ?: return null

    // Defensive: the model assumes the path runs in the direction of travel.
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
        val pr = path.projectForward(
            lat,
            lon,
            from - 30.0,
            STOP_MATCH_TOL_M,
        ) ?: continue

        val target = min(
            pr.s + profile.platformOverrun,
            path.length,
        )

        if (
            stops.isNotEmpty() &&
            target - stops.last().target < MIN_STOP_SPACING_M
        ) {
            continue
        }

        stops += StopPoint(pr.s, target)
        from = pr.s
    }

    return RouteGeometry(path, stops)
}

// =================================================================================================
// Live tracker
// =================================================================================================

private class LiveTracker(
    private val profile: MotionProfile,
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
    private var clock = 0.0
    private var s = 0.0
    private var v = 0.0
    private var cruise = profile.cruise
    private var nextStop = 0
    private var dwelling = false
    private var dwellElapsed = 0.0
    private var debt = 0.0

    private val history = ArrayDeque<Sample>()
    private var lastRecorded = -1e9

    // ---- public API ------------------------------------------------------------------

    fun setRoute(newRoute: RouteGeometry?) {
        route = newRoute
        ready = false
        lastFixPoint = null
        history.clear()

        latestFix?.let {
            ingest(it, nowSeconds())
        }
    }

    fun onFix(
        lat: Double,
        lon: Double,
        servedSec: Double,
        reportedAgeSec: Double?,
    ) {
        if (
            abs(lat - lastRawLat) < 1e-6 &&
            abs(lon - lastRawLon) < 1e-6
        ) {
            return
        }

        lastRawLat = lat
        lastRawLon = lon

        val fix = Fix(
            lat,
            lon,
            servedSec,
            reportedAgeSec,
            isFirstEver = !sawFirst,
        )

        sawFirst = true
        latestFix = fix

        ingest(fix, nowSeconds())
    }

    fun update(now: Double) {
        if (ready) {
            advanceTo(now)
        }
    }

    fun position(): Point? {
        val r = route ?: return null

        if (!ready) {
            return null
        }

        return r.path.pointAt(s)
    }

    fun distanceAlongPath(): Double? {
        if (!ready) {
            return null
        }

        return s
    }

    // ---- fix ingestion ----------------------------------------------------------------

    private fun ingest(
        fix: Fix,
        now: Double,
    ) {
        val r = route ?: return

        val age = fix.reportedAgeSec
            ?: if (fix.isFirstEver) {
                SERVER_UPDATE_PERIOD_S / 2.0
            } else {
                POLL_INTERVAL_S / 2.0
            }

        var fixTime = fix.servedSec - age - FEED_LATENCY_S

        lastFixPoint?.let {
            if (fixTime < it.t + 1.0) {
                fixTime = it.t + 1.0
            }
        }

        fixTime = min(fixTime, now)

        var proj: PathModel.Projection? = null

        val hint = if (ready) {
            sAt(fixTime)
        } else {
            null
        }

        if (hint != null) {
            proj = r.path.project(
                fix.lat,
                fix.lon,
                fromS = hint - FIX_BACK_WINDOW_M,
                toS = hint + profile.maxCruise * 60.0,
            )

            if (proj != null && proj.offsetM > FIX_OFFROUTE_M) {
                proj = null
            }
        }

        if (proj == null) {
            proj = r.path.project(fix.lat, fix.lon)
        }

        if (proj == null || proj.offsetM > FIX_REJECT_M) {
            Log.w(
                "TripMapLayer",
                "fix rejected: off path by ${proj?.offsetM}",
            )
            return
        }

        val sFix = proj.s

        if (!ready) {
            initialise(r, sFix, fixTime, now)
        } else {
            val predicted = sAt(fixTime) ?: s
            val error = sFix - predicted

            if (
                error > RESYNC_AHEAD_M ||
                error < -RESYNC_BEHIND_M
            ) {
                Log.w(
                    "TripMapLayer",
                    "re-sync: error=$error m",
                )

                initialise(r, sFix, fixTime, now)
            } else {
                lastFixPoint?.let {
                    learnSpeed(r, it, sFix, fixTime)
                }

                debt = error * CORRECTION_GAIN
            }
        }

        lastFixPoint = FixPoint(sFix, fixTime)
    }

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
        debt = 0.0
        dwelling = false
        dwellElapsed = 0.0

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
            abs(stop.target - sFix) < STATION_ZONE_M
        ) {
            v = 0.0
            dwelling = true
        } else {
            val d = if (stop != null) {
                max(stop.target - sFix, 0.0)
            } else {
                Double.MAX_VALUE
            }

            v = min(
                cruise,
                sqrt(2.0 * profile.brake * d),
            )
        }

        record()
        advanceTo(now)
    }

    private fun learnSpeed(
        r: RouteGeometry,
        prev: FixPoint,
        sFix: Double,
        fixTime: Double,
    ) {
        val dt = fixTime - prev.t

        if (dt < 8.0) {
            return
        }

        val vObs = (sFix - prev.s) / dt

        if (
            vObs < MIN_LEARN_SPEED_MS ||
            vObs > profile.maxCruise * 1.3
        ) {
            return
        }

        val zone = cruise * cruise /
            (2.0 * min(profile.accel, profile.brake))

        val contaminated = r.stops.any {
            it.target > prev.s - zone &&
                it.target < sFix + zone
        }

        if (contaminated) {
            return
        }

        cruise = (
            cruise + LEARN_ALPHA * (vObs - cruise)
            ).coerceIn(
                profile.minCruise,
                profile.maxCruise,
            )
    }

    // ---- time stepping ----------------------------------------------------------------

    private fun advanceTo(now: Double) {
        if (now - clock > MAX_CATCHUP_S) {
            clock = now - MAX_CATCHUP_S
        }

        while (clock < now) {
            val dt = min(STEP_S, now - clock)

            if (dt < 1e-6) {
                break
            }

            step(dt)
            clock += dt
            record()
        }
    }

    private fun record() {
        if (clock - lastRecorded < 0.1) {
            return
        }

        history.addLast(
            Sample(clock, s),
        )

        lastRecorded = clock

        while (
            history.size > 1 &&
            history.first().t < clock - HISTORY_S
        ) {
            history.removeFirst()
        }
    }

    /** Where the model believed the front of the vehicle was at time [t]. */
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

        var lo = 0
        var hi = history.size - 1

        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1

            if (history[mid].t <= t) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }

        val a = history[lo]

        val bt: Double
        val bs: Double

        if (lo + 1 < history.size) {
            bt = history[lo + 1].t
            bs = history[lo + 1].s
        } else {
            bt = clock
            bs = s
        }

        val f = if (bt > a.t) {
            (t - a.t) / (bt - a.t)
        } else {
            0.0
        }

        return a.s + (bs - a.s) * f
    }

    private fun step(dt: Double) {
        val r = route ?: return
        val stops = r.stops

        // Skip stops we are clearly past.
        while (
            !dwelling &&
            nextStop < stops.size &&
            s > stops[nextStop].target + PASSED_STOP_M
        ) {
            nextStop++
        }

        val stop = stops.getOrNull(nextStop)

        // ---- dwelling at a platform ---------------------------------------------------

        if (dwelling) {
            v = 0.0

            if (debt >= -DEPART_HOLD_M) {
                dwellElapsed += dt
            }

            val terminal = nextStop >= stops.lastIndex

            if (
                !terminal &&
                (
                    dwellElapsed >= profile.dwellS ||
                        debt > RELEASE_AHEAD_M
                    )
            ) {
                dwelling = false
                nextStop++
            }

            return
        }

        // ---- moving: choose the speed we'd like to be doing ---------------------------

        val remaining: Double? = stop?.let {
            it.target - s
        }

        var vTarget = cruise
        var brakeCap = Double.MAX_VALUE

        if (remaining != null) {
            if (
                remaining <= ARRIVE_M &&
                v <= ARRIVE_SPEED_MS
            ) {
                dwelling = true
                dwellElapsed = 0.0
                v = 0.0

                if (debt in 0.0..RELEASE_AHEAD_M) {
                    debt = 0.0
                }

                return
            }

            if (remaining <= ARRIVE_M) {
                vTarget = 0.0
                brakeCap = 0.0
            } else {
                brakeCap = max(
                    sqrt(2.0 * profile.brake * remaining),
                    CREEP_MS,
                )

                vTarget = min(cruise, brakeCap)
            }
        }

        v = if (v < vTarget) {
            min(
                vTarget,
                v + profile.accel * dt,
            )
        } else {
            max(
                vTarget,
                v - 1.5 * profile.brake * dt,
            )
        }

        // ---- pay off the debt by changing speed --------------------------------------

        var vMove = v

        if (abs(debt) > 0.5) {
            val extra = if (debt > 0.0) {
                min(
                    debt / CATCHUP_TAU_S,
                    0.5 * cruise,
                )
            } else {
                max(
                    debt / CATCHUP_TAU_S,
                    -0.6 * v,
                )
            }

            vMove = if (extra >= 0.0) {
                min(
                    v + extra,
                    max(brakeCap, v),
                )
            } else {
                v + extra
            }

            vMove = max(vMove, 0.0)

            debt -= (vMove - v) * dt
            debt *= exp(-dt / 60.0)
        }

        var ds = vMove * dt

        if (remaining != null && remaining >= 0.0) {
            ds = min(ds, remaining)
        }

        s = min(
            s + ds,
            r.path.length,
        )
    }
}
