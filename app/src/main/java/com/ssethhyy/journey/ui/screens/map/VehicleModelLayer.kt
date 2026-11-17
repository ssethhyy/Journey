/*
 * Feature Module: Map
 * File: VehicleModelLayer.kt
 * Description: Renders a static 3D vehicle model at a fixed geographic position using Mapbox ModelLayer.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
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

/**
 * Configuration for a single vehicle rendered as a 3D model on the map.
 */
private data class Vehicle(
    val longitude: Double,
    val latitude: Double,
    val altitudeAboveGround: Double,
    val headingDegrees: Double,
    val modelUri: String,
    val modelScale: Double,
)

/**
 * The vehicle to render. Edit these values to change what appears on the map.
 */
private val vehicle = Vehicle(
    longitude = 144.9631,
    latitude = -37.8136,
    altitudeAboveGround = 0.0,
    headingDegrees = 0.0,
    modelUri = "asset://model_bus_ptv_motor.glb",
    modelScale = 1.0,
)

/**
 * Renders the configured 3D vehicle model at its fixed geographic position.
 *
 * Call this inside the `MapboxMap { ... }` content block in `MapScreen`.
 */
@Composable
fun VehicleModelLayer() {

    val modelId = "vehicle-model"
    val layerId = "vehicle-model-layer"

    // Register the model in the style once (or when the URI changes).
    MapEffect(vehicle.modelUri) { mapView ->
        mapView.mapboxMap.addModel(
            model(modelId) {
                uri(vehicle.modelUri)
            }
        )
    }

    // GeoJSON source holding the vehicle's position.
    val sourceState = rememberGeoJsonSourceState(key = "${vehicle.longitude},${vehicle.latitude}") {
        data = GeoJSONData(Feature.fromGeometry(Point.fromLngLat(vehicle.longitude, vehicle.latitude))
        )
    }

    // The model layer, driven by the source above.
    ModelLayer(
        sourceState = sourceState,
        layerId = layerId,
    ) {
        this.modelId = ModelIdValue(modelId)

        // Scale: adjust to match your model's native units.
        this.modelScale = DoubleListValue(
            listOf(vehicle.modelScale, vehicle.modelScale, vehicle.modelScale)
        )

        // Rotation: [X, Y, Z] in degrees. Z controls heading for a ground vehicle.
        this.modelRotation = DoubleListValue(listOf(0.0, 0.0, vehicle.headingDegrees))

        // Translation: [X, Y, Z] in meters. Z is altitude above ground.
        this.modelTranslation = DoubleListValue(listOf(0.0, 0.0, vehicle.altitudeAboveGround))

        this.modelEmissiveStrength = DoubleValue(1.0)
    }
}
