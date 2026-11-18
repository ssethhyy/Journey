/*
 * Feature Module: Map
 * File: TripMapLayer.kt
 * Description: Draws the route line and stops of a selected trip on the map.
 *              Fetches the stopping pattern itself; the parent only supplies runRef + mode.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupState
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.StoppingPattern
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TripMapLayer(
    runRef: String,
    mode: TransportMode,
    onLoaded: (StoppingPattern) -> Unit = {},
) {
    val container = LocalContext.current.appContainer

    var pattern by remember(runRef, mode) { mutableStateOf<StoppingPattern?>(null) }
    var loadFailed by remember(runRef, mode) { mutableStateOf(false) }

    LaunchedEffect(runRef, mode) {
        pattern = null
        loadFailed = false
        val result = withContext(Dispatchers.IO) {
            runCatching { container.ptvRepository.stoppingPattern(runRef, mode, withPath = true) }
        }
        result.fold(
            onSuccess = {
                pattern = it
                onLoaded(it)
            },
            onFailure = { loadFailed = true },
        )
    }

    val current = pattern ?: return

    // ---- Route line ---------------------------------------------------------------
    // pattern.path is a List<Pair<Double, Double>> — (latitude, longitude) per point, already the route's real,
    // smoothed shape (see RouteLine).
    val linePoints = remember(current) {
        current.path.map { (lat, lon) -> Point.fromLngLat(lon, lat) }
    }

    if (linePoints.size >= 2) {
        val lineSourceState = rememberGeoJsonSourceState(key = "$runRef-line") {
            data = GeoJSONData(LineString.fromLngLats(linePoints))
        }

        LineLayer(
            sourceState = lineSourceState,
            layerId = "$runRef-route-line",
        ) {
            lineColor = ColorValue(mode.color)
            lineWidth = DoubleValue(4.0)
            lineOpacity = DoubleValue(0.9)
            lineCap = com.mapbox.maps.extension.compose.style.layers.generated.LineCapValue.ROUND
            lineJoin = com.mapbox.maps.extension.compose.style.layers.generated.LineJoinValue.ROUND
        }
    }

    // ---- Stop dots ----------------------------------------------------------------
    if (current.stops.isEmpty()) return

    val annotations = remember(current.stops, mode) {
        current.stops.mapIndexed { index, ps ->
            CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(ps.stop.longitude, ps.stop.latitude))
                .withCircleColor(mode.color.toArgb())
                .withCircleRadius(7.0)
                .withCircleStrokeColor(Color.White.toArgb())
                .withCircleStrokeWidth(2.0)
                .withCircleSortKey(index.toDouble())
                .withData(JsonPrimitive(ps.stop.key))
        }
    }

    val groupState = remember(mode) {
        CircleAnnotationGroupState().apply {
            circleEmissiveStrength = 1.0
        }
    }

    CircleAnnotationGroup(annotations = annotations, circleAnnotationGroupState = groupState)
}
