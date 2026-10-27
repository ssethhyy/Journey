/*
 * Feature Module: Navigation
 * File: Navigation.kt
 * Description: Navigation 3 destination keys and a small navigator facade for screens.
 */

package com.ssethhyy.journey.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.MapFocusRequest
import com.ssethhyy.journey.PlanRequest
import com.ssethhyy.journey.data.planner.PlanLocation
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class StopDetailKey(
    val stopId: Int,
    val routeType: Int,
    val name: String,
    val suburb: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
) : NavKey {
    val mode: TransportMode get() = TransportMode.fromRouteType(routeType)

    fun toStop() = Stop(stopId, mode, name, suburb, latitude, longitude)
}

@Serializable
data class RouteDetailKey(val routeId: Int, val routeType: Int, val title: String, val number: String = "") : NavKey {
    val mode: TransportMode get() = TransportMode.fromRouteType(routeType)
}

@Serializable
data class TripKey(
    val runRef: String,
    val routeType: Int,
    val fromStopId: Int,
    val title: String,
) : NavKey {
    val mode: TransportMode get() = TransportMode.fromRouteType(routeType)
}

@Serializable
data object SearchKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object DisruptionsKey : NavKey

@Serializable
data object IslandSettingsKey : NavKey

@Serializable
data object MykiWalletKey : NavKey

@Serializable
data class MykiCardKey(val cardId: String) : NavKey

@Serializable
data object FaresKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data class JourneyDetailKey(val journeyId: String, val fromName: String, val toName: String) : NavKey

enum class HomeTab { FOR_YOU, PLAN, MAP }

@Stable
class Navigator(
    private val backStack: NavBackStack<NavKey>,
    private val container: AppContainer,
    private val selectTab: (HomeTab) -> Unit,
) {
    fun open(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openStop(stop: Stop) = open(
        StopDetailKey(stop.id, stop.mode.routeType, stop.name, stop.suburb, stop.latitude, stop.longitude),
    )

    fun openRoute(route: Route) = open(RouteDetailKey(route.id, route.mode.routeType, route.name, route.number))

    fun openTrip(runRef: String, mode: TransportMode, fromStopId: Int, title: String) =
        open(TripKey(runRef, mode.routeType, fromStopId, title))

    fun openSettings() = open(SettingsKey)
    fun openSearch() = open(SearchKey)
    fun openDisruptions() = open(DisruptionsKey)
    fun openIslandSettings() = open(IslandSettingsKey)
    fun openMyki() = open(MykiWalletKey)
    fun openMykiCard(cardId: String) = open(MykiCardKey(cardId))
    fun openFares() = open(FaresKey)
    fun openProfile() = open(ProfileKey)

    fun openJourney(journeyId: String, fromName: String, toName: String) = open(JourneyDetailKey(journeyId, fromName, toName))

    fun openTracked(target: TrackingTarget) = when (target) {
        is TrackingTarget.Service -> openTrip(target.runRef, target.mode, target.stopId, target.headsign)
        is TrackingTarget.Trip -> openJourney(target.journeyId, target.fromName, target.toName)
    }

    /** Returns to the home screen, switches to the Plan tab and plans a trip to [to]. */
    fun planTo(to: PlanLocation, from: PlanLocation? = null) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        selectTab(HomeTab.PLAN)
        container.requestPlan(PlanRequest(to, from))
    }

    /** Returns to the home screen, switches to the map tab and flies to the stop. */
    fun showOnMap(stop: Stop) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        selectTab(HomeTab.MAP)
        container.focusMap(MapFocusRequest(stop))
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }

val currentNavigator: Navigator
    @Composable get() = LocalNavigator.current
