/*
 * Feature Module: Plan
 * File: PlanViewModel.kt
 * Description: Journey planning state: origin/destination, time options and results.
 */

package com.ssethhyy.journey.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.location.LocationProvider
import com.ssethhyy.journey.data.planner.JourneyResults
import com.ssethhyy.journey.data.planner.PlanLocation
import com.ssethhyy.journey.data.planner.TimeMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.RecentTrip
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.loadCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

val CurrentLocationPlaceholder = PlanLocation(
    name = "Current location",
    latitude = Double.NaN,
    longitude = Double.NaN,
    isCurrentLocation = true,
)

data class PlanUiState(
    val from: PlanLocation = CurrentLocationPlaceholder,
    val to: PlanLocation? = null,
    val timeMode: TimeMode = TimeMode.LEAVE_NOW,
    val time: Instant = Instant.now(),
    val results: LoadState<JourneyResults>? = null,
    val loadingMore: Boolean = false,
    val updatedAt: Instant? = null,
    val refreshing: Boolean = false,
    val places: List<SavedPlace> = emptyList(),
    val recents: List<RecentTrip> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val canPlan: Boolean get() = to != null
}

class PlanViewModel(private val container: AppContainer) : ViewModel() {
    private val local = MutableStateFlow(PlanUiState())
    private var planJob: Job? = null

    val state: StateFlow<PlanUiState> = combine(
        local,
        container.placesRepository.places,
        container.placesRepository.recentTrips,
        container.settingsRepository.settings,
    ) { s, places, recents, settings -> s.copy(places = places, recents = recents, settings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // Destinations requested from elsewhere (e.g. tapping Home on Favourites).
        viewModelScope.launch {
            container.planRequest.filterNotNull().collect { request ->
                local.update { it.copy(to = request.to, from = request.from ?: it.from, timeMode = TimeMode.LEAVE_NOW) }
                container.consumePlanRequest(request.id)
                plan()
            }
        }
    }

    fun setFrom(location: PlanLocation) {
        local.update { it.copy(from = location) }
        plan()
    }

    fun setTo(location: PlanLocation) {
        local.update { it.copy(to = location) }
        plan()
    }

    fun clearDestination() {
        planJob?.cancel()
        local.update { it.copy(to = null, results = null) }
    }

    fun swap() {
        val s = local.value
        val to = s.to ?: return
        local.update { it.copy(from = to, to = s.from) }
        plan()
    }

    fun setTime(mode: TimeMode, time: Instant = Instant.now()) {
        local.update { it.copy(timeMode = mode, time = time) }
        plan()
    }

    fun openRecent(trip: RecentTrip) {
        local.update {
            it.copy(
                from = if (trip.fromIsCurrent) CurrentLocationPlaceholder else PlanLocation(trip.fromName, trip.fromLat, trip.fromLon),
                to = PlanLocation(trip.toName, trip.toLat, trip.toLon, trip.toSubtitle),
            )
        }
        plan()
    }

    private suspend fun resolve(location: PlanLocation): PlanLocation {
        if (!location.isCurrentLocation) return location
        val here = container.locationProvider.currentLocation() ?: LocationProvider.MELBOURNE_CBD
        return location.copy(latitude = here.latitude, longitude = here.longitude)
    }

    /**
     * Plans the current trip. [silent] keeps showing the current results while refreshing in the
     * background (used for live updates), and keeps them if the refresh fails.
     */
    fun plan(silent: Boolean = false) {
        val s = local.value
        val to = s.to ?: return
        if (silent && s.results !is LoadState.Success) return
        planJob?.cancel()
        planJob = viewModelScope.launch {
            if (silent) local.update { it.copy(refreshing = true) } else local.update { it.copy(results = LoadState.Loading) }
            val result = loadCatching {
                val from = resolve(s.from)
                val destination = resolve(to)
                container.journeyRepository.plan(from, destination, s.timeMode, s.time).also {
                    container.placesRepository.addRecent(
                        RecentTrip(
                            fromName = s.from.name,
                            fromLat = from.latitude,
                            fromLon = from.longitude,
                            fromIsCurrent = s.from.isCurrentLocation,
                            toName = to.name,
                            toLat = destination.latitude,
                            toLon = destination.longitude,
                            toSubtitle = to.subtitle,
                        ),
                    )
                }
            }
            local.update { state ->
                when {
                    silent && result is LoadState.Error -> state.copy(refreshing = false)
                    else -> state.copy(
                        results = result,
                        refreshing = false,
                        updatedAt = if (result is LoadState.Success) Instant.now() else state.updatedAt,
                    )
                }
            }
        }
    }

    /** Live update tick: only re-plan "leave now" trips, whose options change minute to minute. */
    fun liveRefresh() {
        if (local.value.timeMode == TimeMode.LEAVE_NOW) plan(silent = true)
    }

    fun loadMore() {
        val s = local.value
        val current = (s.results as? LoadState.Success)?.data ?: return
        val cursor = current.nextPageCursor ?: return
        val to = s.to ?: return
        if (s.loadingMore) return
        viewModelScope.launch {
            local.update { it.copy(loadingMore = true) }
            val more = runCatching {
                container.journeyRepository.plan(resolve(s.from), resolve(to), s.timeMode, s.time, pageCursor = cursor)
            }.getOrNull()
            local.update { state ->
                val existing = (state.results as? LoadState.Success)?.data
                if (more == null || existing == null) {
                    state.copy(loadingMore = false)
                } else {
                    state.copy(
                        loadingMore = false,
                        results = LoadState.Success(
                            JourneyResults(
                                journeys = (existing.journeys + more.journeys).distinctBy { it.id }.sortedBy { it.start },
                                nextPageCursor = more.nextPageCursor,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun clearRecents() = viewModelScope.launch { container.placesRepository.clearRecents() }
}
