/*
 * Feature Module: Trip
 * File: TripMap.kt
 * Description: The map at the top of the trip screen: the route line, its stops, and the live
 * vehicle (the mode's pictogram) gliding to each new position. Falls back to a plain card when the
 * map can't render (no Mapbox token).
 */

package com.ssethhyy.journey.ui.screens.trip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ViewAnnotationOptions
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroupState
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.ssethhyy.journey.R
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.StoppingPattern
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.model.VehiclePosition
import com.ssethhyy.journey.ui.components.MapOverlayButton
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.VehicleBadge
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.ui.theme.isAppInDarkTheme

private val MapHeight = 188.dp

@Composable
fun TripMap(
    pattern: StoppingPattern,
    vehicle: VehiclePosition?,
    mode: TransportMode,
    fromStopId: Int,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val settings = container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null).value
    val token = settings?.mapboxTokenOverride?.ifBlank { null } ?: stringResource(R.string.mapbox_access_token)
    val shape = RoundedCornerShape(28.dp)
    if (settings == null || !token.startsWith("pk.") || !container.mapRenderingEnabled) {
        MapPlaceholder(mode, vehicle != null, modifier.clip(shape))
        return
    }

    val dark = isAppInDarkTheme(settings.themeMode)
    val line = pattern.path.map { (lat, lon) -> Point.fromLngLat(lon, lat) }
    val stops = pattern.stops.filter { it.stop.latitude != 0.0 || it.stop.longitude != 0.0 }
    val viewport = rememberMapViewportState()
    val animated = rememberAnimatedPosition(vehicle)
    var followVehicle by remember { mutableStateOf(false) }

    // Fit the whole route once it is known; "follow" then re-centres on the vehicle on demand.
    LaunchedEffect(line.size, stops.size) {
        val all = line.ifEmpty { stops.map { Point.fromLngLat(it.stop.longitude, it.stop.latitude) } }
        if (all.size > 1) {
            viewport.setCameraOptions(
                viewport.cameraForCoordinates(all, CameraOptions.Builder().build(), EdgeInsets(40.0, 40.0, 40.0, 40.0), null, null),
            )
        }
    }
    LaunchedEffect(followVehicle) {
        val position = animated ?: return@LaunchedEffect
        if (followVehicle) {
            viewport.easeTo(
                CameraOptions.Builder().center(Point.fromLngLat(position.second, position.first)).zoom(14.5).build(),
                MapAnimationOptions.mapAnimationOptions { duration(900) },
            )
        } else {
            val all = line.ifEmpty { stops.map { Point.fromLngLat(it.stop.longitude, it.stop.latitude) } }
            if (all.size > 1) {
                viewport.easeTo(
                    viewport.cameraForCoordinates(all, CameraOptions.Builder().build(), EdgeInsets(40.0, 40.0, 40.0, 40.0), null, null),
                    MapAnimationOptions.mapAnimationOptions { duration(900) },
                )
            }
        }
    }

    val routeColor = mode.color
    Box(modifier.fillMaxWidth().height(MapHeight).clip(shape)) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = viewport,
            compass = {},
            scaleBar = {},
            style = {
                MapboxStandardStyle {
                    lightPreset = if (dark && settings.mapFollowsTheme) LightPresetValue.NIGHT else LightPresetValue.DAY
                    show3dObjects = BooleanValue(false)
                }
            },
        ) {
            if (line.size > 1) {
                // Night lighting would otherwise dim the line to near-invisible, so draw it self-lit,
                // with a white casing under the coloured line to keep it readable on any style.
                val lineState = remember { PolylineAnnotationGroupState().apply { lineEmissiveStrength = 1.0 } }
                val casing = remember(line) {
                    listOf(PolylineAnnotationOptions().withPoints(line).withLineColor(Color.White.toArgbInt()).withLineWidth(9.0).withLineOpacity(0.95))
                }
                val route = remember(line, routeColor) {
                    listOf(PolylineAnnotationOptions().withPoints(line).withLineColor(routeColor.toArgbInt()).withLineWidth(5.5))
                }
                PolylineAnnotationGroup(annotations = casing, polylineAnnotationGroupState = lineState)
                PolylineAnnotationGroup(annotations = route, polylineAnnotationGroupState = lineState)
            }
            val groupState = remember { CircleAnnotationGroupState().apply { circleEmissiveStrength = 1.0 } }
            val dots = remember(stops, fromStopId, routeColor) {
                stops.map { ps ->
                    val mine = ps.stop.id == fromStopId
                    CircleAnnotationOptions()
                        .withPoint(Point.fromLngLat(ps.stop.longitude, ps.stop.latitude))
                        .withCircleRadius(if (mine) 8.0 else 4.5)
                        .withCircleColor(if (mine) routeColor.toArgbInt() else Color.White.toArgbInt())
                        .withCircleStrokeColor(if (mine) Color.White.toArgbInt() else routeColor.toArgbInt())
                        .withCircleStrokeWidth(if (mine) 3.0 else 2.0)
                }
            }
            CircleAnnotationGroup(annotations = dots, circleAnnotationGroupState = groupState)
            animated?.let { (lat, lon) ->
                ViewAnnotation(
                    options = ViewAnnotationOptions.Builder()
                        .geometry(Point.fromLngLat(lon, lat))
                        .allowOverlap(true)
                        .build(),
                ) {
                    LiveVehicleMarker(mode)
                }
            }
        }
        if (animated != null) {
            MapOverlayButton(
                icon = if (followVehicle) Sym.Route else Sym.MyLocation,
                contentDescription = if (followVehicle) "Show whole route" else "Follow the vehicle",
                onClick = { followVehicle = !followVehicle },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp),
            )
        }
    }
}

private fun Color.toArgbInt(): Int = this.toArgb()

@Composable
private fun LiveVehicleMarker(mode: TransportMode) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(52.dp).background(mode.color.copy(alpha = 0.22f), CircleShape))
        VehicleBadge(mode, Modifier.shadow(4.dp, CircleShape), size = 38.dp)
    }
}

/** Eases the vehicle between PTV's positions instead of jumping, so movement reads as live. */
@Composable
private fun rememberAnimatedPosition(target: VehiclePosition?): Pair<Double, Double>? {
    var shown by remember { mutableStateOf(target?.let { it.latitude to it.longitude }) }
    LaunchedEffect(target?.latitude, target?.longitude) {
        val to = target?.let { it.latitude to it.longitude }
        val from = shown
        if (to == null || from == null || from == to) {
            shown = to
            return@LaunchedEffect
        }
        Animatable(0f).animateTo(1f, tween(durationMillis = 1_800, easing = LinearEasing)) {
            shown = (from.first + (to.first - from.first) * value) to (from.second + (to.second - from.second) * value)
        }
    }
    return shown
}

@Composable
private fun MapPlaceholder(mode: TransportMode, live: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().height(MapHeight), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym.Map, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Add a Mapbox token to see this ${mode.label.lowercase()} on the map",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
