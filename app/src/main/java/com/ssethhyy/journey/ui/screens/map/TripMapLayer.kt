
/*
 * Feature Module: Map
 * File: TripMapLayer.kt
 * Description:
 *   Draws the route line, stops, and live vehicle marker for a selected trip.
 *
 * Geopath selection:
 *   - usePatternGeopath = true: Uses the geopath from the stopping pattern response.
 *   - usePatternGeopath = false: Uses routeDetails().geopaths and RouteLine.forStops().
 *
 * Rendering:
 *   - Route line + stop dots are Mapbox style layers.
 *   - At close zoom, an optional 3D carriage consist follows the predicted route position.
 *   - Otherwise, the vehicle is an Android-view annotation.
 *
 * Tracking:
 *   - VehicleMotionModel handles all prediction and mathematical operations.
 *   - This file handles API requests, adaptive polling and rendering.
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

// =================================================================================================
// Rendering configuration
// =================================================================================================

private const val STOP_DOT_RADIUS = 6.0
private const val LINE_WIDTH = 6.0

const val VEHICLE_3D_MIN_ZOOM = 15.0

private const val LIVE_REFRESH_MS = 10_000L

// =================================================================================================
// Vehicle consist
// =================================================================================================

/*
 * These are the same carriage definitions used by the existing 3D renderer.
 *
 * Total:
 *   24.9 + 1.0 + 23.3 + 1.0 + 24.9
 * + 24.9 + 1.0 + 23.3 + 1.0 + 24.9
 * = 150.2 metres
 *
 * This is the length of the complete consist, not an individual carriage.
 */

private const val TRAIN_LENGTH_METERS = 150.2

private fun createTrainConsist() = listOf(
    TrainCarriage("asset://model_train_xtrapolis_motor.glb", 24.9, 1),
    TrainCarriage("asset://model_train_xtrapolis_car_1.glb", 1.0, 1),
    TrainCarriage("asset://model_train_xtrapolis_car_2.glb", 23.3, 1),
    TrainCarriage("asset://model_train_xtrapolis_car_1.glb", 1.0, 0),
    TrainCarriage("asset://model_train_xtrapolis_motor.glb", 24.9, 0),

    TrainCarriage("asset://model_train_xtrapolis_motor.glb", 24.9, 1),
    TrainCarriage("asset://model_train_xtrapolis_car_1.glb", 1.0, 1),
    TrainCarriage("asset://model_train_xtrapolis_car_2.glb", 23.3, 1),
    TrainCarriage("asset://model_train_xtrapolis_car_1.glb", 1.0, 0),
    TrainCarriage("asset://model_train_xtrapolis_motor.glb", 24.9, 0),
)

// =================================================================================================
// Vehicle freshness
// =================================================================================================

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
    /** Where the vehicle is drawn, as it moves (null while it isn't shown); the map follows it. */
    onVehicleMoved: (Point?) -> Unit = {},
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

    val profile = remember(mode) {
        motionProfileFor(mode)
    }

    val tracker = remember(runRef, mode) {
        LiveTracker(
            profile = profile,
            trainLengthMeters = TRAIN_LENGTH_METERS,
        )
    }

    val markerPoint = remember(runRef, mode) {
        mutableStateOf<Point?>(null)
    }

    val markerVisible = remember(runRef, mode) {
        mutableStateOf(false)
    }

    // Reports the drawn position to the map, so the camera can follow the vehicle.
    val reportVehicle = androidx.compose.runtime.rememberUpdatedState(onVehicleMoved)
    LaunchedEffect(markerPoint, markerVisible) {
        androidx.compose.runtime.snapshotFlow { markerPoint.value?.takeIf { markerVisible.value } }
            .collect { reportVehicle.value(it) }
    }

    val frontDistance = remember(runRef, mode) {
        mutableStateOf<Double?>(null)
    }

    var mapZoom by remember {
        mutableStateOf(0.0)
    }

    // =====================================================================
    // Initial pattern and geopath loading
    // =====================================================================

    LaunchedEffect(
        runRef,
        mode,
        directionId,
        usePatternGeopath,
    ) {

        pattern = null
        geopath = emptyList()
        vehicle = null

        val result: Result<StoppingPattern> =
            withContext(Dispatchers.IO) {
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
                    "Loaded stops=${loaded.stops.size} " +
                        "direction=${loaded.directionId} " +
                        "route=${loaded.route?.id}",
                )

                pattern = loaded
                vehicle = loaded.vehiclePosition

                loaded.vehiclePosition?.let { vp ->

                    tracker.onFix(
                        vp.latitude,
                        vp.longitude,
                        modelNowSeconds(),
                        vp.reportedAgeSeconds(),
                    )
                }

                onLoaded(loaded)

                // ---------------------------------------------------------
                // Pattern geopath
                // ---------------------------------------------------------

                if (usePatternGeopath) {

                    val patternPath = loaded.path

                    if (patternPath.size >= 2) {

                        geopath = patternPath.map { (lat, lon) ->
                            Point.fromLngLat(
                                lon,
                                lat,
                            )
                        }

                        Log.d(
                            "GeoPath",
                            "Using PATTERN geopath: " +
                                "points=${geopath.size} " +
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

                    // -----------------------------------------------------
                    // Route details geopath
                    // -----------------------------------------------------

                    val routeId = loaded.route?.id

                    if (routeId == null) {

                        Log.w(
                            "GeoPath",
                            "No route ID on pattern; " +
                                "cannot load route-details geopath",
                        )

                    } else {

                        val geoResult: Result<List<Point>> =
                            withContext(Dispatchers.IO) {

                                runCatching {

                                    val details =
                                        container.ptvRepository.routeDetails(routeId)

                                    Log.d(
                                        "GeoPath",
                                        "=== ROUTE DETAILS GEOPATH ===",
                                    )

                                    Log.d(
                                        "GeoPath",
                                        "geopaths map size=${details.geopaths.size}",
                                    )

                                    Log.d(
                                        "GeoPath",
                                        "geopaths keys=${details.geopaths.keys}",
                                    )

                                    for ((dir, chunks) in details.geopaths) {

                                        val pts = chunks.sumOf {
                                            it.size
                                        }

                                        Log.d(
                                            "GeoPath",
                                            "dir=$dir chunks=${chunks.size} " +
                                                "totalPts=$pts",
                                        )

                                        chunks.forEachIndexed { ci, chunk ->

                                            Log.d(
                                                "GeoPath",
                                                "dir=$dir chunk[$ci] " +
                                                    "size=${chunk.size} " +
                                                    "first=${chunk.firstOrNull()} " +
                                                    "last=${chunk.lastOrNull()}",
                                            )
                                        }
                                    }

                                    val routeShapes =
                                        details.geopaths[loaded.directionId]
                                            .orEmpty()

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
                                            "No geopath points for direction " +
                                                loaded.directionId,
                                        )
                                    }

                                    fun nearestOnPath(
                                        lat: Double,
                                        lon: Double,
                                    ): Pair<Double, Double> =

                                        flatShapes.minByOrNull {
                                                (slat, slon) ->

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

                                    if (
                                        stitched.size <= virtualStops.size + 2
                                    ) {

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
                                        Point.fromLngLat(
                                            lon,
                                            lat,
                                        )
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

    // =====================================================================
    // Adaptive live position polling
    // =====================================================================

    // =====================================================================
    // Fixed live position polling
    // =====================================================================

    LaunchedEffect(
        runRef,
        mode,
        tracker,
    ) {

        while (isActive) {

            // Always poll at a fixed 10-second interval.
            // Prediction and animation are handled separately by the tracker.

            delay(LIVE_REFRESH_MS)

            val sentSec = modelNowSeconds()

            val result = withContext(Dispatchers.IO) {

                runCatching {

                    container.ptvRepository.stoppingPattern(
                        runRef,
                        mode,
                        withPath = false,
                    )
                }
            }

            val recvSec = modelNowSeconds()

            val fresh = result.getOrElse { error ->

                Log.w(
                    "TripMapLayer",
                    "Live position refresh failed",
                    error,
                )

                continue
            }

            val pos = fresh.vehiclePosition

            if (pos == null) {

                Log.d(
                    "TripMapLayer",
                    "Live response contained no vehicle position",
                )

                continue
            }

            vehicle = pos

            val accepted = tracker.onFix(
                lat = pos.latitude,
                lon = pos.longitude,
                servedSec = (sentSec + recvSec) / 2.0,
                reportedAgeSec = pos.reportedAgeSeconds(),
            )

            if (accepted) {

                Log.d(
                    "TripMapLayer",
                    "Accepted GPS sample: " +
                        "lat=${pos.latitude} " +
                        "lon=${pos.longitude}",
                )

            } else {

                Log.d(
                    "TripMapLayer",
                    "GPS response unchanged; tracker retained prediction",
                )
            }
        }
    }

    // =====================================================================
    // Per-frame prediction and marker updates
    // =====================================================================

    LaunchedEffect(tracker) {

        var lastPublishNs = 0L
        var lastFreshCheckNs = 0L

        while (isActive) {

            withFrameNanos { frameNs ->

                tracker.update(
                    modelNowSeconds(),
                )

                // Publish the position at approximately 30 Hz.

                if (
                    frameNs - lastPublishNs >= 33_000_000L
                ) {

                    lastPublishNs = frameNs

                    val p = tracker.position()

                    if (
                        p != null &&
                        p != markerPoint.value
                    ) {
                        markerPoint.value = p
                    }

                    val distance =
                        tracker.distanceAlongPath()

                    if (distance != frontDistance.value) {
                        frontDistance.value = distance
                    }
                }

                // Check feed freshness independently of the animation.

                if (
                    frameNs - lastFreshCheckNs >= 1_000_000_000L
                ) {

                    lastFreshCheckNs = frameNs

                    markerVisible.value =
                        vehicle?.isFresh(Instant.now()) == true
                }
            }
        }
    }

    // =====================================================================
    // Wait for pattern
    // =====================================================================

    val current: StoppingPattern = pattern ?: return

    // =====================================================================
    // Route geometry
    // =====================================================================

    val linePoints: List<Point> = remember(
        current,
        geopath,
    ) {

        when {
            geopath.size >= 2 ->
                geopath

            else ->
                current.stops.map { ps ->

                    Point.fromLngLat(
                        ps.stop.longitude,
                        ps.stop.latitude,
                    )
                }
        }
    }

    val route: RouteGeometry? = remember(
        current,
        linePoints,
        mode,
    ) {

        buildRoute(
            linePoints = linePoints,

            stopLatLons = current.stops.map {
                it.stop.latitude to it.stop.longitude
            },

            profile = profile,
        )
    }

    // =====================================================================
    // Supply route geometry to the prediction model
    // =====================================================================

    LaunchedEffect(
        tracker,
        route,
    ) {

        tracker.setRoute(route)
    }

    // =====================================================================
    // MapView and camera
    // =====================================================================

    var mapViewRef by remember {
        mutableStateOf<MapView?>(null)
    }

    MapEffect(Unit) { mapView ->

        mapViewRef = mapView

        mapZoom = mapView.mapboxMap.cameraState.zoom

        val cameraSubscription =
            mapView.mapboxMap.subscribeCameraChanged {

                mapZoom =
                    mapView.mapboxMap.cameraState.zoom
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

    // =====================================================================
    // Route line and stop dots
    // =====================================================================

    MapEffect(route) { mapView ->

        mapViewRef = mapView

        val style = mapView.mapboxMap.style
            ?: return@MapEffect

        removeRouteLayers(
            style,
            runRef,
        )

        if (
            route == null ||
            linePoints.size < 2
        ) {
            return@MapEffect
        }

        val argb = mode.color.toArgb()

        style.addSource(
            geoJsonSource(lineSourceId(runRef)) {

                geometry(
                    LineString.fromLngLats(linePoints),
                )
            },
        )

        style.addLayer(
            lineLayer(
                lineLayerId(runRef),
                lineSourceId(runRef),
            ) {

                lineColor(argb)
                lineWidth(LINE_WIDTH)
                lineOpacity(1.0)
                lineEmissiveStrength(1.0)

                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)

                slot("top")
            },
        )

        val dots: List<Feature> =
            route.stops.map { stop ->

                Feature.fromGeometry(
                    route.path.pointAt(stop.s),
                )
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
                circleLayer(
                    stopsLayerId(runRef),
                    stopsSourceId(runRef),
                ) {

                    circleColor(argb)
                    circleRadius(STOP_DOT_RADIUS)
                    circleOpacity(1.0)
                    circleEmissiveStrength(1.0)

                    circlePitchAlignment(
                        CirclePitchAlignment.MAP,
                    )

                    circlePitchScale(
                        CirclePitchScale.MAP,
                    )

                    slot("top")
                },
            )
        }
    }

    // =====================================================================
    // Cleanup
    // =====================================================================

    DisposableEffect(runRef) {

        onDispose {

            val mapView = mapViewRef
                ?: return@onDispose

            val style = mapView.mapboxMap.style
                ?: return@onDispose

            removeRouteLayers(
                style,
                runRef,
            )
        }
    }

    // =====================================================================
    // Live vehicle rendering
    // =====================================================================

    val trainDistance = frontDistance.value

    val renderTrain3d =

        render3dVehicles &&

            mapZoom >= VEHICLE_3D_MIN_ZOOM &&

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
                ?: Point.fromLngLat(
                    0.0,
                    0.0,
                )
        },

        headingAtDistance = { distance ->

            route?.path?.headingAt(distance)
                ?: 0.0
        },

        consist = createTrainConsist(),
    )

    LiveVehicleAnnotation(

        mode = mode,

        point = markerPoint,

        visible = markerVisible,

        enabled = !renderTrain3d,
    )
}

// =================================================================================================
// Mapbox layer IDs
// =================================================================================================

private fun lineSourceId(runRef: String) =
    "$runRef-line-source"

private fun lineLayerId(runRef: String) =
    "$runRef-route-line"

private fun stopsSourceId(runRef: String) =
    "$runRef-stops-source"

private fun stopsLayerId(runRef: String) =
    "$runRef-stops-layer"

// =================================================================================================
// Layer cleanup
// =================================================================================================

private fun removeRouteLayers(
    style: Style,
    runRef: String,
) {

    for (
    layerId in listOf(
        stopsLayerId(runRef),
        lineLayerId(runRef),
    )
    ) {

        if (style.styleLayerExists(layerId)) {
            style.removeStyleLayer(layerId)
        }
    }

    for (
    sourceId in listOf(
        stopsSourceId(runRef),
        lineSourceId(runRef),
    )
    ) {

        if (style.styleSourceExists(sourceId)) {
            style.removeStyleSource(sourceId)
        }
    }
}

// =================================================================================================
// Live vehicle annotation
// =================================================================================================

/**
 * Lives in its own composable so that the approximately 30 Hz
 * position updates only recompose this small rendering scope.
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

            .visible(
                enabled && visible.value,
            )

            .build(),

        ) {

        LiveVehicleMarker(mode)
    }
}

// =================================================================================================
// Vehicle marker
// =================================================================================================

@Composable
private fun LiveVehicleMarker(
    mode: TransportMode,
) {

    Box(
        contentAlignment = Alignment.Center,
    ) {

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

            Modifier.shadow(
                4.dp,
                CircleShape,
            ),

            size = 38.dp,
        )
    }
}
