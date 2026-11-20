
/*
 * Feature Module: Map
 * File: VehicleModelLayer.kt
 * Description: Renders a live, multi-carriage 3D train along a route using Mapbox ModelLayer.
 *
 * Models are registered before carriage layers are composed.
 * Each carriage has a persistent GeoJSON source and ModelLayer.
 * Source geometry is updated as the train moves.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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

// =================================================================================================
// Configuration
// =================================================================================================

private const val TRAIN_MODEL_ID = "xtrapolis-train-model"
private const val TRAIN_MODEL_URI = "asset://model_train_xtrapolis_motor.glb"

private const val TRAIN_LAYER_PREFIX = "xtrapolis-carriage"
private const val TRAIN_SOURCE_PREFIX = "xtrapolis-carriage-source"

private const val CARRIAGE_LENGTH_METRES = 24.5
private const val CARRIAGE_GAP_METRES = 0.35

private const val TRAIN_MODEL_SCALE = 1.0
private const val TRAIN_ALTITUDE_METRES = 0.0

const val TRAIN_3D_MIN_ZOOM = 15.5

/**
 * Ordered train consist.
 *
 * The first entry is the leading carriage.
 * Repeated URIs intentionally share one registered model.
 */
val xtrapolisTrainConsist: List<String> = listOf(
    TRAIN_MODEL_URI,
    TRAIN_MODEL_URI,
    TRAIN_MODEL_URI,
    TRAIN_MODEL_URI,
    TRAIN_MODEL_URI,
    TRAIN_MODEL_URI,
)

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
    consist: List<String> = xtrapolisTrainConsist,
) {
    if (
        !enabled ||
        !zoom.isFinite() ||
        zoom < TRAIN_3D_MIN_ZOOM
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

    // Register each unique model exactly once for this model set.
    val distinctModels = remember(consist) {
        consist.distinct()
    }

    // A stable key representing the models that must be registered.
    val registrationKey = remember(distinctModels) {
        distinctModels.joinToString(separator = "\u0000")
    }

    // Only compose ModelLayers after all required models have been registered.
    var registeredModelKey by remember {
        mutableStateOf<String?>(null)
    }

    MapEffect(registrationKey) { mapView ->
        distinctModels.forEach { uri ->
            val modelId = modelIdFor(uri)

            mapView.mapboxMap.addModel(
                model(modelId) {
                    uri(uri)
                }
            )
        }

        // Trigger recomposition after registration has completed.
        registeredModelKey = registrationKey
    }

    if (registeredModelKey != registrationKey) {
        return
    }

    // Calculate the position of every carriage along the route.
    var distanceBehindFront = 0.0

    consist.forEachIndexed { index, modelUri ->

        val carriageCentreOffset =
            distanceBehindFront + CARRIAGE_LENGTH_METRES / 2.0

        val carriageDistance =
            frontDistance - carriageCentreOffset

        if (
            carriageDistance >= 0.0 &&
            carriageDistance <= pathLengthMeters
        ) {
            val position = pointAtDistance(carriageDistance)
            val heading = headingAtDistance(carriageDistance)

            if (
                position.longitude().isFinite() &&
                position.latitude().isFinite() &&
                heading.isFinite()
            ) {
                key(index, modelUri) {
                    TrainCarriageLayer(
                        index = index,
                        modelUri = modelUri,
                        position = position,
                        headingDegrees = heading,
                    )
                }
            }
        }

        distanceBehindFront +=
            CARRIAGE_LENGTH_METRES + CARRIAGE_GAP_METRES
    }
}

// =================================================================================================
// Individual carriage
// =================================================================================================

@Composable
private fun TrainCarriageLayer(
    index: Int,
    modelUri: String,
    position: Point,
    headingDegrees: Double,
) {
    val modelId = modelIdFor(modelUri)

    val sourceId = "$TRAIN_SOURCE_PREFIX-$index"
    val layerId = "$TRAIN_LAYER_PREFIX-$index"

    /*
     * Stable source identity.
     *
     * Unlike the previous implementation, the geographic position
     * is NOT part of the remember key or source ID.
     */
    val sourceState = rememberGeoJsonSourceState(
        key = sourceId,
        sourceId = sourceId,
    ) {
        data = GeoJSONData(
            Feature.fromGeometry(position)
        )
    }

    /*
     * Update the existing source with the latest carriage position.
     * This preserves the source and ModelLayer identities.
     */
    sourceState.data = GeoJSONData(
        Feature.fromGeometry(position)
    )

    ModelLayer(
        sourceState = sourceState,
        layerId = layerId,
    ) {
        // Reference the model registered by the parent MapEffect.
        this.modelId = ModelIdValue(modelId)

        // Match the working static bus implementation.
        this.modelScale = DoubleListValue(
            listOf(
                TRAIN_MODEL_SCALE,
                TRAIN_MODEL_SCALE,
                TRAIN_MODEL_SCALE,
            )
        )

        // Rotation: [X, Y, Z]. Z controls ground-plane heading.
        this.modelRotation = DoubleListValue(
            listOf(
                0.0,
                0.0,
                headingDegrees,
            )
        )

        // Translation: [X, Y, Z].
        this.modelTranslation = DoubleListValue(
            listOf(
                0.0,
                0.0,
                TRAIN_ALTITUDE_METRES,
            )
        )

        this.modelEmissiveStrength = DoubleValue(1.0)
    }
}

// =================================================================================================
// Helpers
// =================================================================================================

private fun modelIdFor(uri: String): String {
    return when (uri) {
        TRAIN_MODEL_URI -> TRAIN_MODEL_ID
        else -> "train-model-${uri.hashCode()}"
    }
}
