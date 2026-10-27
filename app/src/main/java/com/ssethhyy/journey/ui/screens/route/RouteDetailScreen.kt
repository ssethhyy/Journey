/*
 * Feature Module: Route details
 * File: RouteDetailScreen.kt
 * Description: Line diagram for a route with direction picker, map preview and disruptions.
 */

package com.ssethhyy.journey.ui.screens.route

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.R
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.RouteDetails
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.DisruptionCard
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.SegmentedPicker
import com.ssethhyy.journey.ui.components.TimelineRow
import com.ssethhyy.journey.ui.loadCatching
import com.ssethhyy.journey.ui.navigation.RouteDetailKey
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteUiState(
    val details: LoadState<RouteDetails> = LoadState.Loading,
    val directionId: Int? = null,
    val stops: LoadState<List<Stop>> = LoadState.Loading,
    val disruptions: List<Disruption> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

class RouteDetailViewModel(private val container: AppContainer, private val routeId: Int, private val mode: TransportMode) : ViewModel() {
    private val local = MutableStateFlow(RouteUiState())

    val state: StateFlow<RouteUiState> = combine(local, container.settingsRepository.settings) { s, settings -> s.copy(settings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteUiState())

    init {
        load()
        viewModelScope.launch {
            runCatching { container.ptvRepository.disruptionsForRoute(routeId) }.onSuccess { d -> local.update { it.copy(disruptions = d) } }
        }
    }

    fun load() = viewModelScope.launch {
        local.update { it.copy(details = LoadState.Loading) }
        val details = loadCatching { container.ptvRepository.routeDetails(routeId) }
        val firstDirection = details.dataOrNull?.directions?.firstOrNull()?.id
        local.update { it.copy(details = details, directionId = it.directionId ?: firstDirection) }
        loadStops()
    }

    fun selectDirection(id: Int) {
        local.update { it.copy(directionId = id) }
        loadStops()
    }

    private fun loadStops() = viewModelScope.launch {
        local.update { it.copy(stops = LoadState.Loading) }
        val dir = local.value.directionId
        local.update { it.copy(stops = loadCatching { container.ptvRepository.stopsOnRoute(routeId, mode, dir) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(key: RouteDetailKey) {
    val vm = appViewModel(key = "route_${key.routeId}") { RouteDetailViewModel(it, key.routeId, key.mode) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val color = key.mode.color
    val details = state.details.dataOrNull

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = if (key.number.isNotBlank() && key.number != key.title) "${key.number} · ${key.title}" else key.title,
                subtitle = details?.route?.serviceStatus ?: key.mode.label,
                onBackClick = navigator::back,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (val d = state.details) {
            LoadState.Loading -> Box(Modifier.padding(padding)) { LoadingState() }
            is LoadState.Error -> Box(Modifier.padding(padding)) { ErrorState(d.error, onRetry = { vm.load() }, onOpenSettings = navigator::openSettings) }
            is LoadState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .floatingBarBlur(padding.calculateTopPadding()),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val paths = d.data.geopaths[state.directionId] ?: d.data.geopaths.values.firstOrNull().orEmpty()
                if (paths.isNotEmpty()) {
                    item(key = "map") { RouteMapPreview(paths, color, state.settings) }
                }
                if (d.data.directions.size > 1) {
                    item(key = "directions") {
                        SegmentedPicker(
                            items = d.data.directions,
                            selectedItem = d.data.directions.firstOrNull { it.id == state.directionId } ?: d.data.directions.first(),
                            onItemSelected = { vm.selectDirection(it.id) },
                            labelProvider = { "To ${it.name}" },
                        )
                    }
                }
                if (state.disruptions.isNotEmpty()) {
                    item(key = "disruptions_label") { SectionLabel("Service updates") }
                    items(state.disruptions, key = { "d${it.id}" }) { DisruptionCard(it) }
                }
                item(key = "stops_label") { SectionLabel("Stops") }
                when (val stops = state.stops) {
                    LoadState.Loading -> item(key = "stops_loading") { LoadingState() }
                    is LoadState.Error -> item(key = "stops_error") { ErrorState(stops.error, onRetry = { vm.load() }) }
                    is LoadState.Success -> if (stops.data.isEmpty()) {
                        item(key = "stops_empty") { MessageState(Sym.Route, "No stops listed", "PTV didn't return any stops for this direction.") }
                    } else {
                        item(key = "stops") {
                            RoundedCardContainer(spacing = 0.dp) {
                                stops.data.forEachIndexed { index, stop ->
                                    TimelineRow(
                                        title = stop.name,
                                        subtitle = stop.suburb,
                                        lineColor = color,
                                        isFirst = index == 0,
                                        isLast = index == stops.data.lastIndex,
                                        onClick = { navigator.openStop(stop) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteMapPreview(paths: List<List<Pair<Double, Double>>>, color: androidx.compose.ui.graphics.Color, settings: AppSettings) {
    val token = settings.mapboxTokenOverride.ifBlank { stringResource(R.string.mapbox_access_token) }
    if (!token.startsWith("pk.")) return
    val dark = isAppInDarkTheme(settings.themeMode)
    val points = paths.map { path -> path.map { (lat, lon) -> Point.fromLngLat(lon, lat) } }
    val viewport = rememberMapViewportState()
    LaunchedEffect(points) {
        val all = points.flatten()
        if (all.size > 1) {
            val camera = viewport.cameraForCoordinates(all, com.mapbox.maps.CameraOptions.Builder().build(), EdgeInsets(48.0, 48.0, 48.0, 48.0), null, null)
            viewport.setCameraOptions(camera)
        }
    }
    MapboxMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp)),
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
        points.forEach { line ->
            PolylineAnnotation(points = line) {
                lineColor = color
                lineWidth = 5.0
            }
        }
    }
}
