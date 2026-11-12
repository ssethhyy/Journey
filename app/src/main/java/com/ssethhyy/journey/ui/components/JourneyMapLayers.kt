/*
 * Feature Module: UI Components
 * File: JourneyMapLayers.kt
 * Description: Draws a planned journey on a Mapbox map: one line per leg in its mode's colour (walking white),
 * each joined to the next, a dot wherever you change, and start and end markers. Used by the journey page's
 * small map and the Map tab's full journey view.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMapComposable
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroupState
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.ui.theme.ModeColors

/** The colour a leg is drawn in: its PTV mode colour, white for walking. */
fun JourneyLeg.mapColor(): Color = when {
    kind == LegKind.WALK -> Color.White
    mode != null -> ModeColors.of(mode)
    else -> badgeColor()
}

object JourneyGeometry {
    /** Legs that have a drawable path. */
    fun drawable(journey: Journey): List<JourneyLeg> = journey.legs.filter { it.path.size > 1 }

    /** Each leg's points, starting where the previous leg ended so the route reads as one line. */
    fun segments(legs: List<JourneyLeg>): List<Pair<JourneyLeg, List<Point>>> = legs.mapIndexed { index, leg ->
        val points = leg.path.map { Point.fromLngLat(it.longitude, it.latitude) }
        val joined = legs.getOrNull(index - 1)?.path?.lastOrNull()?.let { listOf(Point.fromLngLat(it.longitude, it.latitude)) + points } ?: points
        leg to joined
    }

    /** Where one leg hands over to the next: a change of mode, a new service, or getting on after walking. */
    fun changePoints(legs: List<JourneyLeg>): List<Point> =
        legs.drop(1).mapNotNull { leg -> leg.path.firstOrNull()?.let { Point.fromLngLat(it.longitude, it.latitude) } }

    fun allPoints(legs: List<JourneyLeg>): List<Point> = legs.flatMap { it.path }.map { Point.fromLngLat(it.longitude, it.latitude) }

    fun points(leg: JourneyLeg): List<Point> = leg.path.map { Point.fromLngLat(it.longitude, it.latitude) }
}

/**
 * The journey's route, change dots and start/end markers. [highlight] dims every other leg so the chosen one
 * stands out. Everything is emissive so Mapbox Standard's night lighting doesn't darken the colours.
 */
@MapboxMapComposable
@Composable
fun JourneyRouteLayers(journey: Journey, darkMap: Boolean, highlight: JourneyLeg? = null) {
    val legs = remember(journey) { JourneyGeometry.drawable(journey) }
    if (legs.isEmpty()) return
    val segments = remember(legs) { JourneyGeometry.segments(legs) }
    val lineState = remember { PolylineAnnotationGroupState().apply { lineEmissiveStrength = 1.0; lineJoin = LineJoin.ROUND } }
    PolylineAnnotationGroup(
        annotations = segments.map { (leg, points) ->
            val dimmed = highlight != null && leg != highlight
            PolylineAnnotationOptions()
                .withPoints(points)
                .withLineColor(leg.mapColor().toArgb())
                .withLineWidth(if (leg.kind == LegKind.WALK) 4.0 else if (leg == highlight) 8.0 else 6.0)
                .withLineOpacity(if (dimmed) 0.35 else 1.0)
                .withLineBorderColor(Color.Black.copy(alpha = if (darkMap) 0.6f else 0.35f).toArgb())
                .withLineBorderWidth(1.5)
                .withLineSortKey(if (leg == highlight) 10.0 else 0.0)
        },
        polylineAnnotationGroupState = lineState,
    )
    val all = remember(legs) { JourneyGeometry.allPoints(legs) }
    val changes = remember(legs) { JourneyGeometry.changePoints(legs) }
    val circleState = remember { CircleAnnotationGroupState().apply { circleEmissiveStrength = 1.0 } }
    CircleAnnotationGroup(
        annotations = buildList {
            // A small ringed dot wherever you change between walking, trains, trams and buses.
            changes.forEach {
                add(
                    CircleAnnotationOptions().withPoint(it).withCircleRadius(5.5).withCircleColor(Color.White.toArgb())
                        .withCircleStrokeColor(Color(0xFF202124).toArgb()).withCircleStrokeWidth(2.5).withCircleSortKey(1.0),
                )
            }
            add(
                CircleAnnotationOptions().withPoint(all.first()).withCircleRadius(7.0).withCircleColor(Color.White.toArgb())
                    .withCircleStrokeColor(Color(0xFF1B6EF3).toArgb()).withCircleStrokeWidth(4.0).withCircleSortKey(2.0),
            )
            add(
                CircleAnnotationOptions().withPoint(all.last()).withCircleRadius(8.0).withCircleColor(Color(0xFFD93025).toArgb())
                    .withCircleStrokeColor(Color.White.toArgb()).withCircleStrokeWidth(3.0).withCircleSortKey(2.0),
            )
        },
        circleAnnotationGroupState = circleState,
    )
}
