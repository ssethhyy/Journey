/*
 * Feature Module: Stop details
 * File: StopDetailViewModel.kt
 * Description: Departures, disruptions and facilities for a single stop.
 */

package com.ssethhyy.journey.ui.screens.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.StopDetails
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StopDetailUiState(
    val stop: Stop,
    val details: LoadState<StopDetails> = LoadState.Loading,
    val board: LoadState<DepartureBoard> = LoadState.Loading,
    val disruptions: List<Disruption> = emptyList(),
    val routeFilter: Int? = null,
    val directionFilter: Int? = null,
    val showAll: Boolean = false,
    val isFavourite: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val refreshing: Boolean = false,
)

class StopDetailViewModel(private val container: AppContainer, initialStop: Stop) : ViewModel() {
    private val repo = container.ptvRepository
    private val local = MutableStateFlow(StopDetailUiState(stop = initialStop))
    private var boardJob: Job? = null

    val state: StateFlow<StopDetailUiState> = combine(
        local,
        container.favouritesRepository.favouriteKeys.map { initialStop.key in it },
        container.settingsRepository.settings,
    ) { s, fav, settings ->
        s.copy(
            isFavourite = fav,
            settings = settings,
            disruptions = Disruption.forStop(s.disruptions, s.board.dataOrNull),
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        loadDetails()
        loadBoard(force = false)
        loadDisruptions()
    }

    private fun loadDetails() = viewModelScope.launch {
        val s = local.value.stop
        val result = loadCatching { repo.stopDetails(s.mode, s.id, fallback = s) }
        local.update { current ->
            val details = result.dataOrNull
            current.copy(
                details = result,
                // Fill in coordinates/suburb that may be missing when opened from a departure.
                stop = if (details != null && current.stop.latitude == 0.0) {
                    current.stop.copy(latitude = details.stop.latitude, longitude = details.stop.longitude, suburb = current.stop.suburb ?: details.stop.suburb)
                } else {
                    current.stop
                },
            )
        }
    }

    fun loadBoard(force: Boolean = true, userInitiated: Boolean = false) {
        boardJob?.cancel()
        boardJob = viewModelScope.launch {
            if (userInitiated) local.update { it.copy(refreshing = true) }
            val s = local.value.stop
            val result = loadCatching {
                repo.departures(s.mode, s.id, maxResults = if (local.value.showAll) 12 else 6, routeId = local.value.routeFilter, force = force)
            }
            local.update { current ->
                val keepOld = result is LoadState.Error && current.board is LoadState.Success
                current.copy(board = if (keepOld) current.board else result, refreshing = false)
            }
        }
    }

    private fun loadDisruptions() = viewModelScope.launch {
        runCatching { repo.disruptionsForStop(local.value.stop.id) }
            .onSuccess { list -> local.update { it.copy(disruptions = list) } }
    }

    fun refreshAll() {
        loadBoard(force = true, userInitiated = true)
        loadDisruptions()
    }

    fun setRouteFilter(routeId: Int?) {
        local.update { it.copy(routeFilter = routeId, directionFilter = null) }
        loadBoard(force = false)
    }

    fun setDirectionFilter(directionId: Int?) = local.update { it.copy(directionFilter = directionId) }

    fun toggleShowAll() {
        local.update { it.copy(showAll = !it.showAll) }
        loadBoard(force = false)
    }

    fun toggleFavourite() = viewModelScope.launch { container.favouritesRepository.toggle(local.value.stop) }
}
