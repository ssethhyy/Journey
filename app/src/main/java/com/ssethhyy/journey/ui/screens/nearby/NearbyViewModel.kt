/*
 * Feature Module: Nearby
 * File: NearbyViewModel.kt
 * Description: Stops around the user with next departures for the closest ones.
 */

package com.ssethhyy.journey.ui.screens.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.location.LatLng
import com.ssethhyy.journey.data.location.LocationProvider
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class LocationStatus { UNKNOWN, NO_PERMISSION, LOCATING, FOUND, FALLBACK }

data class NearbyUiState(
    val stops: LoadState<List<Stop>> = LoadState.Loading,
    val boards: Map<String, LoadState<DepartureBoard>> = emptyMap(),
    val filter: Set<TransportMode> = emptySet(),
    val favouriteKeys: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val locationStatus: LocationStatus = LocationStatus.UNKNOWN,
    val refreshing: Boolean = false,
) {
    val visibleStops: List<Stop>
        get() = stops.dataOrNull.orEmpty().filter { filter.isEmpty() || it.mode in filter }

    val availableModes: List<TransportMode>
        get() = stops.dataOrNull.orEmpty().map { it.mode }.distinct().sortedBy { it.ordinal }
}

class NearbyViewModel(private val container: AppContainer) : ViewModel() {
    private val stops = MutableStateFlow<LoadState<List<Stop>>>(LoadState.Loading)
    private val boards = MutableStateFlow<Map<String, LoadState<DepartureBoard>>>(emptyMap())
    private val filter = MutableStateFlow<Set<TransportMode>>(emptySet())
    private val locationStatus = MutableStateFlow(LocationStatus.UNKNOWN)
    private val refreshing = MutableStateFlow(false)
    private val limiter = Semaphore(4)
    private var loadJob: Job? = null
    private var lastLocation: LatLng? = null

    val state: StateFlow<NearbyUiState> = combine(
        combine(stops, boards, filter) { s, b, f -> Triple(s, b, f) },
        container.favouritesRepository.favouriteKeys,
        container.settingsRepository.settings,
        locationStatus,
        refreshing,
    ) { (s, b, f), favs, settings, status, refreshing ->
        NearbyUiState(s, b, f, favs, settings, status, refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUiState())

    init {
        // Reload when radius or modes change in Settings.
        viewModelScope.launch {
            container.settingsRepository.settings
                .map { it.nearbyRadiusMeters to it.nearbyModes }
                .distinctUntilChanged()
                .drop(1)
                .collect { load(force = true) }
        }
    }

    fun hasPermission() = container.locationProvider.hasPermission()

    fun load(force: Boolean = false, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (userInitiated) refreshing.value = true
            if (stops.value !is LoadState.Success) stops.value = LoadState.Loading
            val location = if (!container.locationProvider.hasPermission()) {
                locationStatus.value = LocationStatus.NO_PERMISSION
                LocationProvider.MELBOURNE_CBD
            } else {
                if (lastLocation == null) locationStatus.value = LocationStatus.LOCATING
                val found = container.locationProvider.currentLocation()
                locationStatus.value = if (found != null) LocationStatus.FOUND else LocationStatus.FALLBACK
                found ?: lastLocation ?: LocationProvider.MELBOURNE_CBD
            }
            lastLocation = location
            val settings = container.settingsRepository.current()
            val result = loadCatching {
                container.ptvRepository.nearbyStops(
                    location.latitude,
                    location.longitude,
                    settings.nearbyModes,
                    settings.nearbyRadiusMeters,
                    maxResults = 50,
                    force = force,
                )
            }
            stops.value = if (result is LoadState.Error && stops.value is LoadState.Success) stops.value else result
            refreshing.value = false
            result.dataOrNull?.let { loadBoards(it.take(BOARD_PRELOAD), force) }
        }
    }

    /** Fetches departures for the given stops (the closest ones are preloaded). */
    fun loadBoards(stops: List<Stop>, force: Boolean = false) {
        viewModelScope.launch {
            val perStop = container.settingsRepository.current().departuresPerStop
            stops.forEach { stop ->
                launch {
                    if (!force && boards.value[stop.key] is LoadState.Success) return@launch
                    if (boards.value[stop.key] == null) boards.update { it + (stop.key to LoadState.Loading) }
                    val result = limiter.withPermit {
                        loadCatching { container.ptvRepository.departures(stop.mode, stop.id, maxResults = perStop + 1, force = force) }
                    }
                    boards.update { current ->
                        val previous = current[stop.key]
                        current + (stop.key to if (result is LoadState.Error && previous is LoadState.Success) previous else result)
                    }
                }
            }
        }
    }

    fun refreshBoards() {
        val visible = state.value.visibleStops.filter { boards.value.containsKey(it.key) }
        loadBoards(visible, force = true)
    }

    fun toggleFilter(mode: TransportMode) = filter.update { if (mode in it) it - mode else it + mode }

    fun toggleFavourite(stop: Stop) = viewModelScope.launch { container.favouritesRepository.toggle(stop) }

    companion object {
        const val BOARD_PRELOAD = 8
    }
}
