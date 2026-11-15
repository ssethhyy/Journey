/*
 * Feature Module: Map
 * File: MapViewModel.kt
 * Description: Stop markers by zoom level, plus the selected stop preview.
 *
 * Each mode appears from its own zoom level: V/Line always, then trains, trams, and buses closest in. V/Line and train
 * stops are few enough to load once for the whole network; trams and buses load for the area on screen.
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

    /** Tram and bus stops loaded around the viewport. */
    private val stops = MutableStateFlow<Map<String, Stop>>(emptyMap())
    /** Every V/Line and train stop, loaded once. */
    private val networkStops = MutableStateFlow<Map<String, Stop>>(emptyMap())
    private val zoom = MutableStateFlow(DEFAULT_ZOOM)
    private var networkJob: Job? = null
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<Throwable?>(null)
    private val selected = MutableStateFlow<Stop?>(null)
    private val selectedBoard = MutableStateFlow<LoadState<DepartureBoard>?>(null)
    private val userLocation = MutableStateFlow<LatLng?>(null)
    private var lastViewport: Viewport? = null
    private var loadJob: Job? = null
    /** Areas already loaded for each set of modes, so panning back or zooming in inside them asks PTV nothing. */
    private val covered = mutableMapOf<Set<TransportMode>, MutableList<LoadedArea>>()
    private var boardJob: Job? = null

    val state: StateFlow<MapUiState> = combine(
        combine(stops, networkStops, zoom, loading, error) { s, n, z, l, e -> Loaded(n + s, z, l, e) },
        combine(selected, selectedBoard, userLocation) { s, b, u -> Triple(s, b, u) },
        container.favouritesRepository.favouriteKeys,
        container.settingsRepository.settings,
    ) { (all, zoom, loading, error), (sel, board, location), favKeys, settings ->
        val shown = visibleModes(zoom, settings.mapModes)
        MapUiState(
            // The selected stop stays on the map even when its mode isn't shown at this zoom.
            stops = all.values.filter { it.mode in shown || it.key == sel?.key },
            modes = settings.mapModes,
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
        zoom.value = viewport.zoom
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val settings = container.settingsRepository.current()
            val shown = visibleModes(viewport.zoom, settings.mapModes)
            loading.value = true
            val results = buildList {
                if (networkStops.value.isEmpty() || error.value != null) add(loadNetwork())
                // Trams and buses: only the stops in view, once zoomed in far enough for them.
                for (group in LOCAL_GROUPS) {
                    val modes = group.modes.filter { it in shown }.toSet()
                    if (modes.isEmpty()) continue
                    val radius = GeoUtils.viewportRadiusMeters(viewport.latitude, viewport.zoom, viewport.widthDp, viewport.heightDp)
                        .toInt()
                        .coerceIn(300, group.maxRadiusM)
                    if (isCovered(modes, viewport.latitude, viewport.longitude, radius)) continue
                    // Ask for a little more than the screen so small pans stay inside what's loaded.
                    val asked = (radius * 1.3).toInt().coerceAtMost(group.maxRadiusM)
                    val result = loadCatching { repo.nearbyStops(viewport.latitude, viewport.longitude, modes, asked, maxResults = group.maxResults) }
                    if (result is LoadState.Success) {
                        stops.update { current -> trim(current + result.data.associateBy { it.key }, viewport) }
                        // A full page of results may have left stops out at the edge, so it doesn't count as covered.
                        if (result.data.size < group.maxResults) {
                            covered.getOrPut(modes) { mutableListOf() }.add(LoadedArea(viewport.latitude, viewport.longitude, asked, System.currentTimeMillis()))
                        }
                    }
                    add(result)
                }
            }
            error.value = results.filterIsInstance<LoadState.Error>().firstOrNull()?.error
            loading.value = false
        }
    }

    /** Every V/Line and train stop, which are few enough to keep for the whole network. */
    private suspend fun loadNetwork(): LoadState<Unit> {
        val result = loadCatching {
            NETWORK_GROUPS.flatMap { group ->
                repo.nearbyStops(NETWORK_CENTRE.latitude, NETWORK_CENTRE.longitude, group.modes, group.maxRadiusM, maxResults = group.maxResults)
            }
        }
        if (result is LoadState.Success) networkStops.value = result.data.associateBy { it.key }
        return when (result) {
            is LoadState.Success -> LoadState.Success(Unit)
            is LoadState.Error -> result
            LoadState.Loading -> LoadState.Loading
        }
    }

    private fun isCovered(modes: Set<TransportMode>, latitude: Double, longitude: Double, radius: Int): Boolean {
        val now = System.currentTimeMillis()
        val areas = covered[modes] ?: return false
        areas.removeAll { now - it.loadedAt > COVERAGE_MS }
        return areas.any { GeoUtils.haversineMeters(latitude, longitude, it.latitude, it.longitude) + radius <= it.radiusM }
    }

    /** Keeps the marker set bounded by dropping the stops furthest from the viewport centre. */
    private fun trim(all: Map<String, Stop>, viewport: Viewport): Map<String, Stop> {
        if (all.size <= MAX_CACHED_STOPS) return all
        // Some loaded areas lose stops here, so they have to be fetched again next time.
        covered.clear()
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
        covered.clear()
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
        (stops.value[key] ?: networkStops.value[key])?.let(::select)
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

    fun toggleFavourite(stop: Stop) = viewModelScope.launch { container.toggleFavourite(stop) }

    private val _locating = MutableStateFlow(false)
    /** True while waiting for a location fix after the user asked for one. */
    val locating: StateFlow<Boolean> = _locating.asStateFlow()
    private var locateJob: Job? = null

    /**
     * Points the map at the user: straight away at the last known fix if there is one, then again at a fresh fix.
     * [onFailed] runs if no location could be found at all.
     */
    fun locateUser(onFailed: () -> Unit = {}, onLocated: (LatLng) -> Unit) {
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            _locating.value = true
            val quick = container.locationProvider.lastKnownLocation()
            quick?.let {
                userLocation.value = it
                onLocated(it)
            }
            val fresh = container.locationProvider.currentLocation()
            _locating.value = false
            when {
                fresh != null && (quick == null || GeoUtils.haversineMeters(quick.latitude, quick.longitude, fresh.latitude, fresh.longitude) > 30) -> {
                    userLocation.value = fresh
                    onLocated(fresh)
                }
                fresh == null && quick == null -> onFailed()
            }
        }
    }

    fun hasLocationPermission() = container.locationProvider.hasPermission()

    private data class LoadedArea(val latitude: Double, val longitude: Double, val radiusM: Int, val loadedAt: Long)

    private data class Loaded(val stops: Map<String, Stop>, val zoom: Double, val loading: Boolean, val error: Throwable?)

    /** Modes loaded together, how far from the centre and how many at most. */
    private data class StopGroup(val modes: Set<TransportMode>, val maxRadiusM: Int, val maxResults: Int)

    companion object {
        /**
         * The zoom each mode appears from. V/Line shows across the whole state, trains from a city-wide view, trams
         * from the inner suburbs and buses (day and night) once you're down to a few streets.
         */
        fun minZoom(mode: TransportMode): Double = when (mode) {
            TransportMode.VLINE -> 0.0
            TransportMode.TRAIN -> 9.5
            TransportMode.TRAM -> 12.0
            TransportMode.BUS, TransportMode.NIGHT_BUS -> 14.0
        }

        /** The modes to draw at [zoom], out of the ones turned on. */
        fun visibleModes(zoom: Double, enabled: Set<TransportMode>): Set<TransportMode> =
            enabled.filter { zoom >= minZoom(it) }.toSet()

        const val DEFAULT_ZOOM = 12.0
        const val MAX_CACHED_STOPS = 2_500
        /** How long a loaded area counts as up to date (stops rarely change). */
        private const val COVERAGE_MS = 15 * 60_000L
        private val NETWORK_CENTRE = LatLng(-37.8136, 144.9631)
        private val NETWORK_GROUPS = listOf(
            // Reaches Mildura, Albury and Mallacoota from Melbourne.
            StopGroup(setOf(TransportMode.VLINE), maxRadiusM = 700_000, maxResults = 2_000),
            StopGroup(setOf(TransportMode.TRAIN), maxRadiusM = 90_000, maxResults = 1_000),
        )
        private val LOCAL_GROUPS = listOf(
            StopGroup(setOf(TransportMode.TRAM), maxRadiusM = 15_000, maxResults = 1_500),
            StopGroup(setOf(TransportMode.BUS, TransportMode.NIGHT_BUS), maxRadiusM = 4_000, maxResults = 500),
        )
    }
}
