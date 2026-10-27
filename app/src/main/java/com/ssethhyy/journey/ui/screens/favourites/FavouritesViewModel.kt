/*
 * Feature Module: Favourites
 * File: FavouritesViewModel.kt
 * Description: Favourite stops with live departures and network status.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class FavouritesUiState(
    val isLoaded: Boolean = false,
    val favourites: List<FavouriteStop> = emptyList(),
    val boards: Map<String, LoadState<DepartureBoard>> = emptyMap(),
    val filter: Set<TransportMode> = emptySet(),
    val disruptions: List<Disruption> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val refreshing: Boolean = false,
    val places: List<SavedPlace> = emptyList(),
) {
    val availableModes: List<TransportMode> get() = favourites.map { it.mode }.distinct().sortedBy { it.ordinal }

    val visibleGroups: List<Pair<TransportMode, List<FavouriteStop>>>
        get() = favourites
            .filter { filter.isEmpty() || it.mode in filter }
            .groupBy { it.mode }
            .toList()
            .sortedBy { it.first.ordinal }

    /**
     * Disruptions on the routes serving the user's favourite stops: those referenced by their live
     * departures, plus network disruptions naming one of those routes.
     */
    val relevantDisruptions: List<Disruption>
        get() {
            val loaded = boards.values.mapNotNull { it.dataOrNull }
            val routeKeys = loaded.flatMap { board -> board.routes.map { it.key } + board.departures.mapNotNull { it.route?.key } }.toSet()
            val referenced = loaded.flatMap { Disruption.forStop(emptyList(), it) }
            val onRoutes = disruptions.filter { d -> d.isCurrent && d.routes.any { it.key in routeKeys } }
            return (referenced + onRoutes).distinctBy { it.id }.sortedBy { it.severity }
        }
}

class FavouritesViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.ptvRepository
    private val favouritesRepo = container.favouritesRepository

    private val boards = MutableStateFlow<Map<String, LoadState<DepartureBoard>>>(emptyMap())
    private val filter = MutableStateFlow<Set<TransportMode>>(emptySet())
    private val disruptions = MutableStateFlow<List<Disruption>>(emptyList())
    private val refreshing = MutableStateFlow(false)
    private val requestLimiter = Semaphore(4)
    private var refreshJob: Job? = null

    val state: StateFlow<FavouritesUiState> = combine(
        favouritesRepo.favourites,
        container.settingsRepository.settings,
        boards,
        filter,
        combine(disruptions, refreshing, container.placesRepository.places) { d, r, p -> Triple(d, r, p) },
    ) { favourites, settings, boards, filter, (disruptions, refreshing, places) ->
        FavouritesUiState(
            isLoaded = true,
            favourites = favourites,
            boards = boards,
            filter = filter.filterTo(mutableSetOf()) { mode -> favourites.any { it.mode == mode } },
            disruptions = disruptions,
            settings = settings,
            refreshing = refreshing,
            places = places,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouritesUiState())

    init {
        // Reload whenever the set of favourites or the departures-per-stop setting changes.
        viewModelScope.launch {
            combine(
                favouritesRepo.favourites.map { list -> list.map { it.key } }.distinctUntilChanged(),
                container.settingsRepository.settings.map { it.departuresPerStop }.distinctUntilChanged(),
            ) { keys, _ -> keys }.collect { refresh(force = false) }
        }
        loadDisruptions(force = false)
    }

    fun refresh(force: Boolean = true, userInitiated: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (userInitiated) refreshing.value = true
            val favourites = favouritesRepo.favourites.first()
            val perStop = container.settingsRepository.current().departuresPerStop
            // Drop boards for removed favourites.
            boards.update { current -> current.filterKeys { key -> favourites.any { it.key == key } } }
            val jobs = favourites.map { fav ->
                launch {
                    if (boards.value[fav.key] == null) boards.update { it + (fav.key to LoadState.Loading) }
                    val result = requestLimiter.withPermit {
                        loadCatching { repo.departures(fav.mode, fav.stopId, maxResults = perStop + 2, force = force) }
                    }
                    // Keep showing stale data if a background refresh fails.
                    boards.update { current ->
                        val previous = current[fav.key]
                        val next = if (result is LoadState.Error && previous is LoadState.Success) previous else result
                        current + (fav.key to next)
                    }
                }
            }
            if (userInitiated) loadDisruptions(force = true)
            jobs.forEach { it.join() }
            refreshing.value = false
        }
    }

    private fun loadDisruptions(force: Boolean) {
        viewModelScope.launch {
            runCatching { repo.disruptions(force = force) }.onSuccess { disruptions.value = it }
        }
    }

    fun toggleFilter(mode: TransportMode) = filter.update { if (mode in it) it - mode else it + mode }

    fun clearFilter() = filter.update { emptySet() }

    fun setUserName(name: String) = viewModelScope.launch { container.settingsRepository.setUserName(name) }

    fun remove(key: String) = viewModelScope.launch { favouritesRepo.remove(key) }

    fun rename(key: String, nickname: String?) = viewModelScope.launch { favouritesRepo.rename(key, nickname) }

    fun move(key: String, delta: Int) = viewModelScope.launch { favouritesRepo.move(key, delta) }

    fun restore(favourite: FavouriteStop) = viewModelScope.launch {
        favouritesRepo.add(favourite.toStop())
        if (favourite.nickname != null) favouritesRepo.rename(favourite.key, favourite.nickname)
    }
}
