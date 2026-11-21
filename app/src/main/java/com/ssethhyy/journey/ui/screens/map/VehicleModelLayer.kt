/*
 * Feature Module: Map
 * File: VehicleModelLayer.kt
 * Description: Renders a live, multi-carriage 3D vehicle along a route using Mapbox ModelLayer.
 *
 * Models are registered before carriage layers are composed.
 * Each carriage has a persistent GeoJSON source and ModelLayer.
 * Source geometry is updated as the vehicle moves.
 *
 * Positioning model:
 *  - Carriages are placed between "joint" points along the route.
 *  - Joints are spaced by chord distance (2D straight-line distance),
 *    not arc length, so couplers stay physically attached.
 *  - All carriage transforms are computed in one pass, then applied
 *    together so every carriage moves in the same frame.
 *
 * Carriage length is declared per-carriage in the consist.
 */

package com.ssethhyy.journey.ui.screens.map

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point

import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.style.DoubleListValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.layers.ModelIdValue
import com.mapbox.maps.extension.compose.style.layers.generated.ModelLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState

import com.mapbox.maps.extension.style.model.addModel
import com.mapbox.maps.extension.style.model.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

// =================================================================================================
// Configuration
// =================================================================================================

private const val TAG = "VehicleModelLayer"

private const val LAYER_PREFIX = "vehicle-carriage"
private const val SOURCE_PREFIX = "vehicle-carriage-source"

private const val CARRIAGE_GAP_METRES = 0.35
private const val VEHICLE_MODEL_SCALE = 1.0
private const val VEHICLE_ALTITUDE_METRES = 0.0

// Chord-distance search step / tolerance.
private const val JOINT_SEARCH_STEP_M = 0.05
private const val JOINT_SEARCH_EPS_M = 0.02

private const val EARTH_RADIUS_M = 6_371_000.0

// =================================================================================================
// Public data model
// =================================================================================================

/**
 * One carriage in the consist.
 *
 * @param modelUri GLB asset URI for this carriage.
 * @param lengthMetres Physical length along the model's Z axis, in metres.
 * @param direction 0 = forward facing (as authored), 1 = rotated 180° about Z.
 */
data class TrainCarriage(
    val modelUri: String,
    val lengthMetres: Double,
    val direction: Int = 0,
)

private data class CarriagePose(
    val index: Int,
    val modelUri: String,
    val direction: Int,
    val position: Point,
    val headingDegrees: Double,
)

private data class PivotHit(val point: Point, val arcDistance: Double)

// =================================================================================================
// Public composable
// =================================================================================================

@Composable
fun VehicleModelLayer(
    enabled: Boolean,
    zoom: Double,
    frontDistanceMeters: Double?,
    pathLengthMeters: Double,
    pointAtDistance: (Double) -> Point,
    headingAtDistance: (Double) -> Double,
    consist: List<TrainCarriage>,
) {
    if (
        !enabled ||
        !zoom.isFinite() ||
        zoom < VEHICLE_3D_MIN_ZOOM
    ) {
        return
    }

    val frontDistance = frontDistanceMeters ?: return

    if (
        !frontDistance.isFinite() ||
        !pathLengthMeters.isFinite() ||
        pathLengthMeters <= 0.0 ||
        consist.isEmpty()
    ) {
        return
    }

    // Register each unique model exactly once.
    val distinctModels = remember(consist) {
        consist.map { it.modelUri }.distinct()
    }

    val registrationKey = remember(distinctModels) {
        distinctModels.joinToString(separator = "\u0000")
    }

    var registeredModelKey by remember {
        mutableStateOf<String?>(null)
    }

    MapEffect(registrationKey) { mapView ->
        distinctModels.forEach { uri ->
            mapView.mapboxMap.addModel(
                model(modelIdFor(uri)) {
                    uri(uri)
                }
            )
        }
        registeredModelKey = registrationKey
    }

    if (registeredModelKey != registrationKey) {
        return
    }

    var poses by remember { mutableStateOf<Map<Int, CarriagePose>>(emptyMap()) }

    LaunchedEffect(
        frontDistance,
        pathLengthMeters,
        registrationKey,
        consist,
    ) {
        poses = computeAllPoses(
            frontDistance = frontDistance,
            pathLengthMeters = pathLengthMeters,
            pointAtDistance = pointAtDistance,
            headingAtDistance = headingAtDistance,
            consist = consist,
        )
    }

    poses.forEach { (index, pose) ->
        key(index, pose.modelUri, pose.direction) {
            CarriageLayer(
                index = index,
                modelUri = pose.modelUri,
                direction = pose.direction,
                position = pose.position,
                headingDegrees = pose.headingDegrees,
            )
        }
    }
}

// =================================================================================================
// Pose computation
// =================================================================================================

private fun computeAllPoses(
    frontDistance: Double,
    pathLengthMeters: Double,
    pointAtDistance: (Double) -> Point,
    headingAtDistance: (Double) -> Double,
    consist: List<TrainCarriage>,
): Map<Int, CarriagePose> {

    if (consist.isEmpty() || pathLengthMeters <= 0.0) return emptyMap()

    val poses = LinkedHashMap<Int, CarriagePose>(consist.size)

    var frontPivot: Point = pointAtDistance(frontDistance)
    var frontPivotArc: Double = frontDistance

    if (!isValidPoint(frontPivot)) return emptyMap()

    consist.forEachIndexed { index, carriage ->

        val lengthM = carriage.lengthMetres
        if (!lengthM.isFinite() || lengthM <= 0.0) {
            Log.w(TAG, "Invalid length $lengthM for ${carriage.modelUri} at index $index")
            return@forEachIndexed
        }

        val rearHit = findRearPivotByChord(
            frontPivot = frontPivot,
            frontArc = frontPivotArc,
            maxBackDistance = lengthM + CARRIAGE_GAP_METRES,
            pointAtDistance = pointAtDistance,
            targetChord = lengthM,
        ) ?: return@forEachIndexed

        val rearPivot = rearHit.point
        val centre = midpoint(frontPivot, rearPivot)

        val heading = bearingDegrees(rearPivot, centre, frontPivot)
            .let { if (carriage.direction == 1) it + 180.0 else it }
            .let { normaliseDegrees(it) }

        if (isValidPoint(centre) && heading.isFinite()) {
            poses[index] = CarriagePose(
                index = index,
                modelUri = carriage.modelUri,
                direction = carriage.direction,
                position = centre,
                headingDegrees = heading,
            )
        }

        frontPivot = rearPivot
        frontPivotArc = rearHit.arcDistance
    }

    return poses
}

private fun findRearPivotByChord(
    frontPivot: Point,
    frontArc: Double,
    maxBackDistance: Double,
    pointAtDistance: (Double) -> Point,
    targetChord: Double,
): PivotHit? {
    var back = 0.0
    var lastGood: PivotHit? = null

    while (back <= maxBackDistance + JOINT_SEARCH_EPS_M) {
        val d = frontArc - back
        if (d < 0.0) return lastGood

        val candidate = pointAtDistance(d)
        if (!isValidPoint(candidate)) return lastGood

        val chord = chordMeters(frontPivot, candidate)

        if (chord >= targetChord - JOINT_SEARCH_EPS_M) {
            return refineChord(
                frontPivot = frontPivot,
                lo = d,
                hi = d + JOINT_SEARCH_STEP_M,
                targetChord = targetChord,
                pointAtDistance = pointAtDistance,
            ) ?: PivotHit(candidate, d)
        }

        lastGood = PivotHit(candidate, d)
        back += JOINT_SEARCH_STEP_M
    }

    return lastGood
}

private fun refineChord(
    frontPivot: Point,
    lo: Double,
    hi: Double,
    targetChord: Double,
    pointAtDistance: (Double) -> Point,
): PivotHit? {
    if (hi < 0.0) return null
    var a = lo
    var b = hi
    var midPoint: Point? = null
    var midArc: Double = lo

    repeat(12) {
        val m = (a + b) / 2.0
        val p = pointAtDistance(m)
        if (!isValidPoint(p)) return@repeat
        midPoint = p
        midArc = m
        val c = chordMeters(frontPivot, p)
        if (c < targetChord) a = m else b = m
    }
    val p = midPoint ?: return null
    return PivotHit(p, midArc)
}

// =================================================================================================
// Individual carriage
// =================================================================================================

@Composable
private fun CarriageLayer(
    index: Int,
    modelUri: String,
    direction: Int,
    position: Point,
    headingDegrees: Double,
) {
    val modelId = modelIdFor(modelUri)

    val sourceId = "$SOURCE_PREFIX-$index"
    val layerId = "$LAYER_PREFIX-$index"

    val sourceState = rememberGeoJsonSourceState(
        key = sourceId,
        sourceId = sourceId,
    ) {
        data = GeoJSONData(Feature.fromGeometry(position))
    }

    sourceState.data = GeoJSONData(Feature.fromGeometry(position))

    ModelLayer(
        sourceState = sourceState,
        layerId = layerId,
    ) {
        this.modelId = ModelIdValue(modelId)

        this.modelScale = DoubleListValue(
            listOf(VEHICLE_MODEL_SCALE, VEHICLE_MODEL_SCALE, VEHICLE_MODEL_SCALE)
        )

        this.modelRotation = DoubleListValue(
            listOf(0.0, 0.0, headingDegrees)
        )

        this.modelTranslation = DoubleListValue(
            listOf(0.0, 0.0, VEHICLE_ALTITUDE_METRES)
        )

        this.modelEmissiveStrength = DoubleValue(1.0)
    }
}

// =================================================================================================
// Geometry helpers
// =================================================================================================

private fun chordMeters(a: Point, b: Point): Double {
    val latRad = Math.toRadians((a.latitude() + b.latitude()) / 2.0)
    val dx = Math.toRadians(b.longitude() - a.longitude()) * cos(latRad) * EARTH_RADIUS_M
    val dy = Math.toRadians(b.latitude() - a.latitude()) * EARTH_RADIUS_M
    return hypot(dx, dy)
}

private fun bearingDegrees(a: Point, centre: Point, b: Point): Double {
    val latRad = Math.toRadians(centre.latitude())
    val dx = Math.toRadians(b.longitude() - a.longitude()) * cos(latRad)
    val dy = Math.toRadians(b.latitude() - a.latitude())
    return normaliseDegrees(Math.toDegrees(atan2(dx, dy)))
}

private fun midpoint(a: Point, b: Point): Point = Point.fromLngLat(
    (a.longitude() + b.longitude()) / 2.0,
    (a.latitude() + b.latitude()) / 2.0,
)

private fun normaliseDegrees(deg: Double): Double {
    var d = deg % 360.0
    if (d < 0.0) d += 360.0
    return d
}

private fun isValidPoint(p: Point): Boolean =
    p.longitude().isFinite() && p.latitude().isFinite()

/**
 * Deterministic model ID for a given asset URI.
 * Uses the URI hash so the same asset always maps to the same model ID
 * across recompositions and configuration changes.
 */
private fun modelIdFor(uri: String): String = "vehicle-model-${uri.hashCode()}"
