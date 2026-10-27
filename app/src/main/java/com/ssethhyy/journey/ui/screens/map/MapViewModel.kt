/*
 * Feature Module: Map
 * File: MapViewModel.kt
 * Description: Viewport-driven stop loading for every mode, plus the selected stop preview.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.location.LatLng
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.loadCatching
import com.ssethhyy.journey.utils.GeoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Viewport(val latitude: Double, val longitude: Double, val zoom: Double, val widthDp: Float, val heightDp: Float)

data class MapUiState(
    val stops: List<Stop> = emptyList(),
    val modes: Set<TransportMode> = TransportMode.entries.toSet(),
    val zoomedOutTooFar: Boolean = false,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val selected: Stop? = null,
    val selectedBoard: LoadState<DepartureBoard>? = null,
    val favouriteKeys: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val userLocation: LatLng? = null,
)

class MapViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.ptvRepository

    private val stops = MutableStateFlow<Map<String, Stop>>(emptyMap())
    private val zoomedOut = MutableStateFlow(false)
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<Throwable?>(null)
    private val selected = MutableStateFlow<Stop?>(null)
    private val selectedBoard = MutableStateFlow<LoadState<DepartureBoard>?>(null)
    private val userLocation = MutableStateFlow<LatLng?>(null)
    private var lastViewport: Viewport? = null
    private var loadJob: Job? = null
    private var boardJob: Job? = null

    val state: StateFlow<MapUiState> = combine(
        combine(stops, zoomedOut, loading, error) { s, z, l, e -> Quad(s, z, l, e) },
        combine(selected, selectedBoard, userLocation) { s, b, u -> Triple(s, b, u) },
        container.favouritesRepository.favouriteKeys,
        container.settingsRepository.settings,
    ) { (stops, zoomedOut, loading, error), (sel, board, location), favKeys, settings ->
        MapUiState(
            stops = stops.values.filter { it.mode in settings.mapModes },
            modes = settings.mapModes,
            zoomedOutTooFar = zoomedOut,
            loading = loading,
            error = error,
            selected = sel,
            selectedBoard = board,
            favouriteKeys = favKeys,
            settings = settings,
            userLocation = location,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun onViewportIdle(viewport: Viewport) {
        lastViewport = viewport
        if (viewport.zoom < MIN_STOP_ZOOM) {
            zoomedOut.value = true
            return
        }
        zoomedOut.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading.value = true
            val settings = container.settingsRepository.current()
            val radius = GeoUtils.viewportRadiusMeters(viewport.latitude, viewport.zoom, viewport.widthDp, viewport.heightDp)
                .toInt()
                .coerceIn(300, MAX_RADIUS_M)
            val result = loadCatching {
                repo.nearbyStops(viewport.latitude, viewport.longitude, settings.mapModes, radius, maxResults = MAX_RESULTS)
            }
            when (result) {
                is LoadState.Success -> {
                    error.value = null
                    stops.update { current -> trim(current + result.data.associateBy { it.key }, viewport) }
                }
                is LoadState.Error -> error.value = result.error
                LoadState.Loading -> Unit
            }
            loading.value = false
        }
    }

    /** Keeps the marker set bounded by dropping the stops furthest from the viewport centre. */
    private fun trim(all: Map<String, Stop>, viewport: Viewport): Map<String, Stop> {
        if (all.size <= MAX_CACHED_STOPS) return all
        return all.values
            .sortedBy { GeoUtils.haversineMeters(viewport.latitude, viewport.longitude, it.latitude, it.longitude) }
            .take(MAX_CACHED_STOPS)
            .associateBy { it.key }
    }

    fun toggleMode(mode: TransportMode) {
        viewModelScope.launch {
            val current = container.settingsRepository.current().mapModes
            val next = if (mode in current) current - mode else current + mode
            container.settingsRepository.setMapModes(next.ifEmpty { TransportMode.entries.toSet() })
            lastViewport?.let(::onViewportIdle)
        }
    }

    fun retry() {
        lastViewport?.let(::onViewportIdle)
    }

    fun select(stop: Stop?) {
        selected.value = stop
        boardJob?.cancel()
        if (stop == null) {
            selectedBoard.value = null
            return
        }
        boardJob = viewModelScope.launch {
            selectedBoard.value = LoadState.Loading
            selectedBoard.value = loadCatching { repo.departures(stop.mode, stop.id, maxResults = 4) }
        }
    }

    fun selectByKey(key: String) {
        stops.value[key]?.let(::select)
    }

    /** Adds a stop that may not be in the loaded set yet (e.g. from search) and selects it. */
    fun focusStop(stop: Stop) {
        stops.update { it + (stop.key to stop) }
        select(stop)
    }

    fun refreshSelected() {
        selected.value?.let { stop ->
            boardJob?.cancel()
            boardJob = viewModelScope.launch {
                selectedBoard.value = loadCatching { repo.departures(stop.mode, stop.id, maxResults = 4, force = true) }
            }
        }
    }

    fun toggleFavourite(stop: Stop) = viewModelScope.launch { container.favouritesRepository.toggle(stop) }

    fun locateUser(onLocated: (LatLng) -> Unit) {
        viewModelScope.launch {
            container.locationProvider.currentLocation()?.let {
                userLocation.value = it
                onLocated(it)
            }
        }
    }

    fun hasLocationPermission() = container.locationProvider.hasPermission()

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        const val MIN_STOP_ZOOM = 13.0
        const val MAX_RADIUS_M = 4_000
        const val MAX_RESULTS = 500
        const val MAX_CACHED_STOPS = 2_500
    }
}
