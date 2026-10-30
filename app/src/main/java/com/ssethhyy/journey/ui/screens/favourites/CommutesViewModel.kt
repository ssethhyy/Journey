/*
 * Feature Module: Commutes
 * File: CommutesViewModel.kt
 * Description: Plans each commute for "leave now" so For You can show when to go and a backup if it is missed.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.commute.Commute
import com.ssethhyy.journey.data.commute.CommuteLogic
import com.ssethhyy.journey.data.commute.CommuteRepository
import com.ssethhyy.journey.data.location.LocationProvider
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.TimeMode
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
import java.time.Instant
import java.time.ZoneId

/** The trip to leave for now, and the one to take if that is missed. */
data class CommuteTrip(val primary: Journey, val backup: Journey?)

data class CommuteCardState(
    val commute: Commute,
    /** Null until the first plan finishes. */
    val trip: LoadState<CommuteTrip?>?,
)

data class CommutesUiState(
    val loaded: Boolean = false,
    val cards: List<CommuteCardState> = emptyList(),
)

class CommutesViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.commuteRepository
    private val trips = MutableStateFlow<Map<String, LoadState<CommuteTrip?>>>(emptyMap())
    private val limiter = Semaphore(3)
    private var refreshJob: Job? = null

    private val commutes = repo.data
        .map { CommuteLogic.visible(it, Instant.now(), ZoneId.systemDefault()) }
        .distinctUntilChanged()

    val state: StateFlow<CommutesUiState> = combine(commutes, trips) { list, trips ->
        CommutesUiState(loaded = true, cards = list.map { CommuteCardState(it, trips[it.key]) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommutesUiState())

    init {
        viewModelScope.launch { commutes.map { list -> list.map { it.key } }.distinctUntilChanged().collect { refresh() } }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val list = commutes.first()
            trips.update { current -> current.filterKeys { key -> list.any { it.key == key } } }
            list.map { commute -> launch { load(commute) } }.forEach { it.join() }
        }
    }

    private suspend fun load(commute: Commute) {
        if (trips.value[commute.key] == null) trips.update { it + (commute.key to LoadState.Loading) }
        val result = limiter.withPermit {
            loadCatching {
                val here = if (commute.from.isCurrent || commute.to.isCurrent) {
                    container.locationProvider.currentLocation() ?: LocationProvider.MELBOURNE_CBD
                } else {
                    null
                }
                fun resolve(end: com.ssethhyy.journey.data.commute.CommuteEnd) =
                    end.toPlan().let { if (end.isCurrent && here != null) it.copy(latitude = here.latitude, longitude = here.longitude) else it }
                val now = Instant.now()
                val results = container.journeyRepository.plan(resolve(commute.from), resolve(commute.to), TimeMode.LEAVE_NOW, null)
                CommuteLogic.pick(results.journeys, now)?.let { CommuteTrip(it.primary, it.backup) }
            }
        }
        // Keep the last good answer if a background refresh fails.
        trips.update { current ->
            val previous = current[commute.key]
            current + (commute.key to if (result is LoadState.Error && previous is LoadState.Success) previous else result)
        }
    }

    fun saveManual(commute: Commute) = viewModelScope.launch { repo.save(commute) }
    fun remove(commute: Commute) = viewModelScope.launch {
        if (commute.manual) repo.removeManual(commute.id) else repo.hide(commute.key)
    }

    fun newId(): String = CommuteRepository.newId()
}
