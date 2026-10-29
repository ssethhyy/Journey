/*
 * Feature Module: Stop details
 * File: StopDetailScreen.kt
 * Description: Full timetable for a stop with route/direction filters, disruptions, facilities and routes.
 */

package com.ssethhyy.journey.ui.screens.stop

import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.components.ToolbarFab
import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.FloatingPullToRefreshBox
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.FacilityKind
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ActionItem
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.DepartureRow
import com.ssethhyy.journey.ui.components.DepartureSkeleton
import com.ssethhyy.journey.ui.components.DisruptionCard
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.FavouriteToggle
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.ListExpandToggleButton
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.icon
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.StopDetailKey
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StopDetailScreen(key: StopDetailKey) {
    val vm = appViewModel(key = "stop_${key.routeType}_${key.stopId}") { StopDetailViewModel(it, key.toStop()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val now by rememberNow()
    val stop = state.stop

    AutoRefreshEffect(enabled = state.settings.autoRefresh) { vm.loadBoard(force = true) }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = stop.name,
                subtitle = listOfNotNull(stop.suburb, stop.mode.label).joinToString(" · "),
                onBackClick = navigator::back,
                pageFab = ToolbarFab(
                    icon = if (state.isFavourite) Sym.StarFilled else Sym.Star,
                    contentDescription = if (state.isFavourite) "Remove from favourites" else "Add to favourites",
                    onClick = vm::toggleFavourite,
                ),
                pageActions = if (stop.latitude != 0.0) listOf(ToolbarFab(Sym.Map, "Show on map") { navigator.showOnMap(stop) }) else emptyList(),
                actions = {
                    if (stop.latitude != 0.0) {
                        HapticIconButton(Sym.Map, "Show on map", onClick = { navigator.showOnMap(stop) })
                    }
                    FavouriteToggle(state.isFavourite, vm::toggleFavourite)
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        FloatingPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refreshAll,
            topInset = innerPadding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            val board = state.board
            val routes: List<Route> = (board.dataOrNull?.routes.orEmpty() + state.details.dataOrNull?.stop?.routes.orEmpty())
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }))
            val departures = board.dataOrNull?.departures.orEmpty()
                .filter { it.bestTime?.isAfter(now.minusSeconds(60)) == true }
            val directions = departures.mapNotNull { d -> d.directionName?.let { d.directionId to it } }.distinct()
            val visibleDepartures = departures
                .filter { state.directionFilter == null || it.directionId == state.directionFilter }
                .take(if (state.showAll) 40 else 10)

            LazyColumn(
                modifier = Modifier.fillMaxSize().floatingBarBlur(innerPadding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = pageBottomInset(32.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") { StopHeader(state.stop.mode, routes.size, state.details.dataOrNull?.stationDescription) }

                if (state.disruptions.isNotEmpty()) {
                    item(key = "disruptions_label") { SectionLabel("Service updates") }
                    items(state.disruptions, key = { "d${it.id}" }) { DisruptionCard(it) }
                }

                item(key = "departures_label") {
                    SectionLabel("Departures") {
                        board.dataOrNull?.let {
                            Text(
                                "Updated ${TimeFormatter.clock(it.fetchedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                if (routes.size > 1) {
                    item(key = "route_filters") {
                        FilterRow {
                            FilterChip(selected = state.routeFilter == null, onClick = { vm.setRouteFilter(null) }, label = { Text("All routes") }, shape = CircleShape)
                            routes.forEach { route ->
                                FilterChip(
                                    selected = state.routeFilter == route.id,
                                    onClick = { vm.setRouteFilter(if (state.routeFilter == route.id) null else route.id) },
                                    label = { Text(route.badgeLabel) },
                                    leadingIcon = { Icon(route.mode.icon, null, Modifier.size(16.dp)) },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                }
                if (directions.size > 1) {
                    item(key = "direction_filters") {
                        FilterRow {
                            FilterChip(selected = state.directionFilter == null, onClick = { vm.setDirectionFilter(null) }, label = { Text("Both directions") }, shape = CircleShape)
                            directions.forEach { (id, name) ->
                                FilterChip(
                                    selected = state.directionFilter == id,
                                    onClick = { vm.setDirectionFilter(if (state.directionFilter == id) null else id) },
                                    label = { Text("To $name") },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                }

                item(key = "departures") {
                    when (board) {
                        LoadState.Loading -> RoundedCardContainer { DepartureSkeleton(rows = 5) }
                        is LoadState.Error -> ErrorState(board.error, onRetry = { vm.loadBoard() }, onOpenSettings = navigator::openSettings)
                        is LoadState.Success -> if (visibleDepartures.isEmpty()) {
                            MessageState(Sym.EventBusy, "No upcoming departures", "There are no services scheduled from this stop soon.")
                        } else {
                            RoundedCardContainer {
                                visibleDepartures.forEach { dep -> DepartureListRow(dep, now, state, onOpenTrip = {
                                    dep.runRef?.let { navigator.openTrip(it, stop.mode, stop.id, dep.headsign) }
                                }) }
                            }
                        }
                    }
                }
                if (board is LoadState.Success && departures.size > 10) {
                    item(key = "more") {
                        ListExpandToggleButton(
                            isExpanded = state.showAll,
                            onToggle = vm::toggleShowAll,
                            expandedText = "Show fewer departures",
                            collapsedText = "Show more departures",
                        )
                    }
                }

                val facilities = state.details.dataOrNull?.facilities.orEmpty()
                if (facilities.isNotEmpty()) {
                    item(key = "facilities") {
                        SectionLabel("Facilities & accessibility")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            facilities.forEach { facility ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(listOfNotNull(facility.kind.label, facility.detail).joinToString(" · ")) },
                                    leadingIcon = { Icon(facility.kind.icon(), null, Modifier.size(18.dp)) },
                                    shape = CircleShape,
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    border = null,
                                )
                            }
                        }
                    }
                }

                if (routes.isNotEmpty()) {
                    item(key = "routes") {
                        SectionLabel("Routes from this stop")
                        RoundedCardContainer {
                            routes.forEach { route ->
                                ActionItem(
                                    icon = route.mode.icon,
                                    title = route.displayName,
                                    description = route.serviceStatus ?: route.mode.label,
                                    onClick = { navigator.openRoute(route) },
                                )
                            }
                        }
                    }
                }

                item(key = "attribution") {
                    Text(
                        "Timetable data: Public Transport Victoria (CC BY 4.0)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StopHeader(mode: com.ssethhyy.journey.data.model.TransportMode, routeCount: Int, description: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModePictogram(mode, size = 56.dp)
        Spacer(Modifier.width(16.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                when (mode) {
                    com.ssethhyy.journey.data.model.TransportMode.TRAIN, com.ssethhyy.journey.data.model.TransportMode.VLINE -> "${mode.label} station"
                    else -> "${mode.label} stop"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    if (routeCount > 0) "$routeCount ${if (routeCount == 1) "route" else "routes"}" else null,
                    description,
                ).joinToString(" · ").ifEmpty { "Live departures" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun DepartureListRow(dep: Departure, now: java.time.Instant, state: StopDetailUiState, onOpenTrip: () -> Unit) {
    val view = LocalView.current
    DepartureRow(
        departure = dep,
        now = now,
        display = state.settings.timeDisplay,
        hasDisruption = dep.disruptionIds.isNotEmpty(),
        onClick = {
            HapticUtil.performUIHaptic(view)
            onOpenTrip()
        },
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceBright),
    )
}

@Composable
private fun FacilityKind.icon(): ImageVector = when (this) {
    FacilityKind.TOILET -> Sym.Wc
    FacilityKind.TAXI_RANK -> Sym.LocalTaxi
    FacilityKind.CAR_PARKING -> Sym.DirectionsCar
    FacilityKind.CCTV -> Sym.Videocam
    FacilityKind.WHEELCHAIR -> Sym.AccessibleForward
    FacilityKind.LIFT -> Sym.Elevator
    FacilityKind.ESCALATOR -> Sym.Escalator
    FacilityKind.STAIRS -> Sym.Stairs
    FacilityKind.HEARING_LOOP -> Sym.Hearing
    FacilityKind.TACTILE -> Sym.Grain
    FacilityKind.WAITING_ROOM -> Sym.Weekend
    FacilityKind.LIGHTING -> Sym.Lightbulb
    FacilityKind.AUDIO_INFO -> Sym.VolumeUp
}
