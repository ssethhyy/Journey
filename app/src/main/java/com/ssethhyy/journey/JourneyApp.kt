/*
 * Feature Module: App
 * File: JourneyApp.kt
 * Description: Application class and manual dependency container.
 */

package com.ssethhyy.journey

import android.app.Application
import android.content.Context
import com.mapbox.common.MapboxOptions
import com.ssethhyy.journey.data.api.PtvApi
import com.ssethhyy.journey.data.api.PtvCredentials
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.planner.JourneyRepository
import com.ssethhyy.journey.data.planner.PlanLocation
import com.ssethhyy.journey.data.planner.PlannerApi
import com.ssethhyy.journey.data.repository.PlacesRepository
import com.ssethhyy.journey.tracking.TrackingManager
import com.ssethhyy.journey.data.location.LocationProvider
import com.ssethhyy.journey.data.repository.FavouritesRepository
import com.ssethhyy.journey.data.repository.PtvRepository
import com.ssethhyy.journey.data.repository.SettingsRepository
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce
import com.ssethhyy.journey.widget.WidgetRefreshWorker
import kotlinx.coroutines.launch

/** A request for the map tab to fly to a point. */
data class MapFocusRequest(val stop: Stop, val id: Long = System.nanoTime())

/** A request for the Plan tab to plan a trip to [to] (optionally from [from]). */
data class PlanRequest(val to: PlanLocation, val from: PlanLocation? = null, val id: Long = System.nanoTime())

class AppContainer(
    context: Context,
    ptvBaseUrl: String = PtvApi.DEFAULT_BASE_URL,
    credentialsOverride: PtvCredentials? = null,
    /** False where no GPU surface exists (Robolectric), so the map shows its placeholder. */
    val mapRenderingEnabled: Boolean = true,
    plannerBaseUrl: String = PlannerApi.DEFAULT_BASE_URL,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(context)
    val favouritesRepository = FavouritesRepository(context)
    val locationProvider = LocationProvider(context)
    val ptvApi = PtvApi(
        credentialsProvider = { credentialsOverride ?: settingsRepository.current().ptvCredentials },
        baseUrl = ptvBaseUrl,
    )
    val ptvRepository = PtvRepository(ptvApi)
    val placesRepository = PlacesRepository(context)
    val mykiRepository = com.ssethhyy.journey.data.myki.MykiRepository(context)
    val fareConfigRepository = com.ssethhyy.journey.data.fare.FareConfigRepository(context)
    val journeyRepository = JourneyRepository(PlannerApi(plannerBaseUrl))
    val trackingManager = TrackingManager(context.applicationContext)
    val mykiManager = com.ssethhyy.journey.data.myki.MykiManager(
        mykiRepository,
        fareConfigRepository,
        ptvRepository,
        com.ssethhyy.journey.data.myki.MykiNotifier(context.applicationContext),
    )

    private val _planRequest = MutableStateFlow<PlanRequest?>(null)

    /** A destination the Plan tab should pick up (e.g. a saved place tapped on Favourites). */
    val planRequest: StateFlow<PlanRequest?> = _planRequest.asStateFlow()

    fun requestPlan(request: PlanRequest) {
        _planRequest.value = request
    }

    fun consumePlanRequest(id: Long) {
        if (_planRequest.value?.id == id) _planRequest.value = null
    }

    private val _mapFocus = MutableStateFlow<MapFocusRequest?>(null)
    val mapFocus: StateFlow<MapFocusRequest?> = _mapFocus.asStateFlow()

    fun focusMap(request: MapFocusRequest) {
        _mapFocus.value = request
    }

    fun consumeMapFocus(id: Long) {
        if (_mapFocus.value?.id == id) _mapFocus.value = null
    }
}

open class JourneyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        HapticUtil.initialize(this)
        keepWidgetInSync()

        // Keep runtime-configurable values (haptics, Mapbox token override) in sync with settings.
        container.appScope.launch {
            container.settingsRepository.settings
                .map { it.hapticsEnabled to it.mapboxTokenOverride }
                .distinctUntilChanged()
                .collect { (haptics, token) ->
                    HapticUtil.isAppHapticsEnabled.value = haptics
                    if (token.isNotBlank()) MapboxOptions.accessToken = token
                }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun keepWidgetInSync() {
        // Refresh the widget whenever the set of favourites (or their nicknames/order) changes.
        container.appScope.launch {
            container.favouritesRepository.favourites
                .map { list -> list.map { it.key to it.displayName } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(1_500)
                .collect { runCatching { WidgetRefreshWorker.refreshNow(this@JourneyApp) } }
        }
    }

    /** Overridden in tests to point the app at a fake PTV server. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as JourneyApp).container
