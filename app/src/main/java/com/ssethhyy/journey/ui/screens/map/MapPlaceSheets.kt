/*
 * Feature Module: Map
 * File: MapPlaceSheets.kt
 * Description: A stop or a line opened on the map, in a floating panel, instead of on a page of its own.
 *
 * The panel shows what the stop and line pages show (they share their list items) and can be pulled up into a full
 * page and back down to the map. A line is also drawn on the map behind its panel.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.FavouriteToggle
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.route.RouteDetailViewModel
import com.ssethhyy.journey.ui.screens.route.routeDetailItems
import com.ssethhyy.journey.ui.screens.stop.StopDetailViewModel
import com.ssethhyy.journey.ui.screens.stop.stopDetailItems
import com.ssethhyy.journey.ui.theme.color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The line the map is showing in its panel, if any; set by the navigator, cleared when the panel closes. */
object MapLineRequests {
    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    fun show(route: Route) {
        _route.value = route
    }

    fun clear() {
        _route.value = null
    }
}

/** The header every place panel shares: a picture, a title and subtitle, then its buttons. */
@Composable
private fun PlaceHeader(
    picture: @Composable () -> Unit,
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit,
) {
    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        picture()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** A stop on the map: its departures, filters, facilities and lines, the same as its page. */
@Composable
fun MapStopSheet(stop: Stop, onClose: () -> Unit, modifier: Modifier = Modifier, onFullProgress: (Float) -> Unit = {}) {
    val vm = appViewModel(key = "stop_${stop.mode.routeType}_${stop.id}") { StopDetailViewModel(it, stop) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val now by rememberNow()
    var step by rememberSaveable(stop.key) { mutableStateOf(SheetStep.OPEN) }
    var updatesCollapsed by rememberSaveable(stop.key) { mutableStateOf<Boolean?>(null) }
    AutoRefreshEffect(enabled = state.settings.autoRefresh) { vm.loadBoard(force = true) }
    BackHandler(onBack = onClose)

    MapSheet(
        step = step,
        onStepChange = { step = it },
        name = stop.name,
        onFullProgress = onFullProgress,
        onFullPage = { navigator.openStopPage(stop, fromMap = true) },
        modifier = modifier,
        header = {
            PlaceHeader(
                picture = { ModePictogram(stop.mode, size = 40.dp) },
                title = stop.name,
                subtitle = listOfNotNull(stop.suburb, stop.mode.label).joinToString(" · "),
            ) {
                if (stop.latitude != 0.0) {
                    HapticIconButton(Sym.Directions, "Plan a trip to ${stop.name}", onClick = {
                        navigator.planTo(com.ssethhyy.journey.data.planner.PlanLocation(stop.name, stop.latitude, stop.longitude, stop.suburb))
                    })
                }
                FavouriteToggle(state.isFavourite, vm::toggleFavourite)
                HapticIconButton(Sym.Close, "Close ${stop.name}", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        stopDetailItems(
            state = state,
            vm = vm,
            now = now,
            navigator = navigator,
            updatesCollapsed = updatesCollapsed ?: state.settings.stopDisruptionsCollapsed,
            onToggleUpdates = { updatesCollapsed = !(updatesCollapsed ?: state.settings.stopDisruptionsCollapsed) },
            showHeader = false,
        )
    }
}

/** A line on the map: its directions, service updates and stops; the line itself is drawn by [MapLineLayer]. */
@Composable
fun MapLineSheet(
    route: Route,
    onClose: () -> Unit,
    /** Fits the map to the line's points, in the part of the map the panel leaves uncovered. */
    onFit: suspend (List<Point>) -> Unit,
    modifier: Modifier = Modifier,
    onFullProgress: (Float) -> Unit = {},
) {
    val vm = appViewModel(key = "route_${route.id}") { RouteDetailViewModel(it, route.id, route.mode) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    var step by rememberSaveable(route.id) { mutableStateOf(SheetStep.OPEN) }
    var updatesCollapsed by rememberSaveable(route.id) { mutableStateOf<Boolean?>(null) }
    val color = route.mode.color
    BackHandler(onBack = onClose)

    val points = remember(state.details, state.directionId) { MapLineGeometry.points(state.details.dataOrNull, state.directionId) }
    LaunchedEffect(points) { if (points.size > 1) onFit(points) }

    MapSheet(
        step = step,
        onStepChange = { step = it },
        name = route.displayName,
        onFullProgress = onFullProgress,
        onFullPage = { navigator.openRoutePage(route, fromMap = true) },
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
        header = {
            PlaceHeader(
                picture = { ModePictogram(route.mode, size = 40.dp) },
                title = route.displayName,
                subtitle = state.details.dataOrNull?.route?.serviceStatus ?: route.mode.label,
            ) {
                HapticIconButton(Sym.Close, "Close ${route.displayName}", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        when (val d = state.details) {
            LoadState.Loading -> item(key = "loading") { LoadingState() }
            is LoadState.Error -> item(key = "error") { ErrorState(d.error, onRetry = { vm.load() }, onOpenSettings = navigator::openSettings) }
            is LoadState.Success -> routeDetailItems(
                data = d.data,
                state = state,
                color = color,
                onSelectDirection = vm::selectDirection,
                onRetry = { vm.load() },
                onOpenStop = navigator::openStop,
                updatesCollapsed = updatesCollapsed ?: state.settings.stopDisruptionsCollapsed,
                onToggleUpdates = { updatesCollapsed = !(updatesCollapsed ?: state.settings.stopDisruptionsCollapsed) },
                showMap = false,
            )
        }
    }
}

/** The shown line's points for the map, smoothed like every other line in the app. */
object MapLineGeometry {
    fun points(details: com.ssethhyy.journey.data.model.RouteDetails?, directionId: Int?): List<Point> =
        paths(details, directionId).flatten()

    fun paths(details: com.ssethhyy.journey.data.model.RouteDetails?, directionId: Int?): List<List<Point>> {
        val geopaths = details?.geopaths ?: return emptyList()
        val chosen = geopaths[directionId] ?: geopaths.values.firstOrNull().orEmpty()
        return chosen.map(com.ssethhyy.journey.data.model.RouteLine::smooth)
            .map { path -> path.map { (lat, lon) -> Point.fromLngLat(lon, lat) } }
            .filter { it.size > 1 }
    }
}

/** Draws the shown line on the map (inside the map's content). */
@Composable
fun MapLineLayer(route: Route) {
    val vm = appViewModel(key = "route_${route.id}") { RouteDetailViewModel(it, route.id, route.mode) }
    val state by vm.state.collectAsStateWithLifecycle()
    val paths = remember(state.details, state.directionId) { MapLineGeometry.paths(state.details.dataOrNull, state.directionId) }
    val color = route.mode.color.toArgb()
    // Emissive, so Mapbox Standard's night lighting doesn't dim the line colour.
    val lineState = remember { com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroupState().apply { lineEmissiveStrength = 1.0 } }
    com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroup(
        annotations = paths.map { line ->
            com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions().withPoints(line).withLineColor(color).withLineWidth(5.0)
        },
        polylineAnnotationGroupState = lineState,
    )
}
