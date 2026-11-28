/*
 * Feature Module: Trip
 * File: TripScreen.kt
 * Description: Stopping pattern for a single run with live times and vehicle info.
 */

package com.ssethhyy.journey.ui.screens.trip

import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.ui.components.hapticConfirm
import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.components.usesBottomBar
import com.ssethhyy.journey.ui.components.rememberTrackFab
import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ssethhyy.journey.data.crowding.AirConditioning
import com.ssethhyy.journey.data.crowding.AirConditioningCheck
import com.ssethhyy.journey.data.crowding.CrowdingResult
import com.ssethhyy.journey.data.model.PatternStop
import com.ssethhyy.journey.data.model.TripProgress
import com.ssethhyy.journey.data.model.TripWindowItem
import com.ssethhyy.journey.data.model.VehiclePosition
import com.ssethhyy.journey.data.model.VehicleProgress
import com.ssethhyy.journey.ui.components.TimelineCollapsedRow
import com.ssethhyy.journey.ui.components.TimelineVehicleRow
import com.ssethhyy.journey.ui.components.VehicleBadge
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.StoppingPattern
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.sharedAppViewModel
import com.ssethhyy.journey.ui.components.floatingBarBlur
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.DisruptionCard
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.RouteBadge
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.TimelineRow
import com.ssethhyy.journey.ui.components.TrackButton
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.loadCatching
import com.ssethhyy.journey.ui.navigation.TripKey
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripViewModel(private val container: AppContainer, private val runRef: String, private val mode: TransportMode) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<StoppingPattern>>(LoadState.Loading)
    val state: StateFlow<LoadState<StoppingPattern>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _crowding = MutableStateFlow<CrowdingUi>(CrowdingUi.Idle)
    val crowding: StateFlow<CrowdingUi> = _crowding.asStateFlow()

    init {
        load()
    }

    fun load(userInitiated: Boolean = false) = viewModelScope.launch {
        if (userInitiated) _refreshing.value = true
        val previous = _state.value.dataOrNull
        // The route line never changes mid-trip, so only the first load fetches it.

        val result = loadCatching {
            val fresh = container.ptvRepository.stoppingPattern(runRef, mode, withPath = previous == null || previous.path.isEmpty())
            if (previous != null && fresh.path.isEmpty()) fresh.copy(path = previous.path) else fresh
        }
        _state.value = if (result is LoadState.Error && _state.value is LoadState.Success) _state.value else result
        _refreshing.value = false
        (_state.value.dataOrNull)?.let { loadCrowding(it, force = userInitiated) }
    }

    /** Crowding is a bonus: any failure just changes the card, it never affects the stopping pattern. */
    private suspend fun loadCrowding(pattern: StoppingPattern, force: Boolean) {
        val position = pattern.vehiclePosition?.let { it.latitude to it.longitude }
        _crowding.value = when (val r = container.crowdingRepository.crowdingFor(mode, pattern.vehicle?.id, position, force)) {
            CrowdingResult.NotConfigured -> CrowdingUi.NotConfigured
            CrowdingResult.NotFound -> CrowdingUi.NotFound
            is CrowdingResult.Found -> CrowdingUi.Data(r.crowding)
            is CrowdingResult.Failed -> if (_crowding.value is CrowdingUi.Data && !r.badKey) _crowding.value else CrowdingUi.Unavailable(r.reason, r.badKey)
        }
    }
}

/** How often the live position and predicted times refresh while the screen is open. */
private const val LIVE_REFRESH_MS = 10_000L

/** What "Track this service" follows for this run, once its stopping pattern has loaded. */
internal fun tripTrackTarget(runRef: String, mode: TransportMode, fromStopId: Int, title: String, pattern: StoppingPattern?): TrackingTarget.Service? =
    pattern?.let {
        TrackingTarget.Service(
            runRef = runRef,
            mode = mode,
            stopId = fromStopId,
            stopName = it.stops.firstOrNull { s -> s.stop.id == fromStopId }?.stop?.name ?: "Your stop",
            routeLabel = it.route?.badgeLabel?.let { badge -> if (mode == TransportMode.TRAIN || mode == TransportMode.VLINE) "$badge line" else "${mode.label} $badge" } ?: mode.label,
            headsign = it.destination ?: title,
        )
    }

/** The trip's view model, shared by the map's trip panel and the trip page so they show the same thing. */
@Composable
internal fun tripViewModel(runRef: String, mode: TransportMode): TripViewModel =
    sharedAppViewModel(key = "trip_$runRef") { TripViewModel(it, runRef, mode) }

/** How often the trip panel and page refresh the live position and times. */
internal const val TRIP_REFRESH_MS = LIVE_REFRESH_MS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(key: TripKey) {
    val vm = tripViewModel(key.runRef, key.mode)
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val crowding by vm.crowding.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val view = androidx.compose.ui.platform.LocalView.current
    val now by rememberNow()
    var showAllStops by rememberSaveable { mutableStateOf(false) }
    AutoRefreshEffect(enabled = true, intervalMs = LIVE_REFRESH_MS) { vm.load() }
    // Pulled up out of the map's trip panel: it starts where the panel was scrolled, and dragging down goes back.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(key.scrollIndex, key.scrollOffset)
    val pullDown = com.ssethhyy.journey.ui.components.rememberPullDownToClose(key.fromMap, navigator::back)

    val pattern = state.dataOrNull
    val trackTarget = tripTrackTarget(key.runRef, key.mode, key.fromStopId, key.title, pattern)
    Scaffold(
        modifier = pullDown.pageModifier,
        topBar = {
            ReusableTopAppBar(
                title = "To ${pattern?.destination ?: key.title}",
                subtitle = listOfNotNull(pattern?.route?.displayName, key.mode.label).joinToString(" · "),
                onBackClick = navigator::back,
                pageFab = trackTarget?.let { rememberTrackFab(it, "Track this service") },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (!usesBottomBar() && trackTarget != null) TrackButton(target = trackTarget, label = "Track this service")
        },
    ) { padding ->
        if (key.fromMap) {
            // Laid out like the panel at full height, so the panel turns into it without a jump.
            when (val s = state) {
                LoadState.Loading -> Box(Modifier.padding(padding)) { LoadingState(label = "Loading stopping pattern") }
                is LoadState.Error -> Box(Modifier.padding(padding)) { ErrorState(s.error, onRetry = { vm.load() }, onOpenSettings = navigator::openSettings) }
                is LoadState.Success -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .floatingBarBlur(padding.calculateTopPadding())
                        .nestedScroll(pullDown.connection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = pageBottomInset(104.dp)),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tripItems(
                        data = s.data,
                        mode = key.mode,
                        fromStopId = key.fromStopId,
                        now = now,
                        crowding = crowding,
                        showAllStops = showAllStops,
                        onShowAllStops = { showAllStops = it },
                        onOpenStop = navigator::openStop,
                    )
                }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val s = state) {
                LoadState.Loading -> LoadingState(label = "Loading stopping pattern")
                is LoadState.Error -> ErrorState(s.error, onRetry = { vm.load() }, onOpenSettings = navigator::openSettings)
                is LoadState.Success -> {
                    val data = s.data
                    val liveVehicle = data.vehiclePosition?.takeIf { it.isFresh(now) }
                    val context = LocalContext.current

                    // Pinned above the list; the stop list below is shortened to make room for it.
                    TripMap(
                        pattern = data,
                        vehicle = liveVehicle,
                        mode = key.mode,
                        fromStopId = key.fromStopId,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                    )

                    // Live tracking in the Map View currently only available for Trains.
                    //if(key.mode == TransportMode.TRAIN) {
                        TextButton(
                            onClick = {
                                val target = pattern ?: return@TextButton
                                context.appContainer.openTripMap(
                                    AppContainer.TripMapRequest(
                                        runRef = key.runRef,
                                        mode = key.mode,
                                        directionId = target.directionId,
                                        title = target.destination ?: key.title,
                                        fromStopId = key.fromStopId.toString(),
                                    )
                                )
                                navigator.goToTab(com.ssethhyy.journey.ui.navigation.HomeTab.MAP)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text("Show on map")
                        }
                    //}

                    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                    com.ssethhyy.journey.ui.components.PullRefreshHaptics(pullState, refreshing)
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        state = pullState,
                        onRefresh = {
                            HapticUtil.performConfirmHaptic(view)
                            vm.load(userInitiated = true)
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pageBottomInset(104.dp)),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tripItems(
                                data = data,
                                mode = key.mode,
                                fromStopId = key.fromStopId,
                                now = now,
                                crowding = crowding,
                                showAllStops = showAllStops,
                                onShowAllStops = { showAllStops = it },
                                onOpenStop = navigator::openStop,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The trip's live status, crowding, stopping pattern and service updates, as list items: shared by the map's trip
 * panel and the page it pulls up into, so the two look the same.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.tripItems(
    data: StoppingPattern,
    mode: TransportMode,
    fromStopId: Int,
    now: Instant,
    crowding: CrowdingUi,
    showAllStops: Boolean,
    onShowAllStops: (Boolean) -> Unit,
    onOpenStop: (com.ssethhyy.journey.data.model.Stop) -> Unit,
) {
    val stops = data.stops
    val fromIndex = stops.indexOfFirst { it.stop.id == fromStopId }.coerceAtLeast(0)
    val liveVehicle = data.vehiclePosition?.takeIf { it.isFresh(now) }
    val progress = liveVehicle
        ?.let { TripProgress.locate(stops.map { s -> s.stop.latitude to s.stop.longitude }, it.latitude, it.longitude) }
        ?.takeIf(TripProgress::isOnRoute)
    item(key = "live") {
        LiveStatusCard(
            mode = mode,
            stops = stops,
            fromIndex = fromIndex,
            vehicle = liveVehicle,
            progress = progress,
            now = now,
        )
    }
    item(key = "crowding") {
        CrowdingCard(crowding, AirConditioningCheck.check(data.vehicle, data.disruptions))
    }
    if (stops.isEmpty()) {
        item(key = "chips") { TripChips(data) }
        item { MessageState(Sym.Route, "No stops available", "PTV hasn't published a stopping pattern for this service.") }
    } else {
        item(key = "label") { SectionLabel("Stopping pattern") }
        item(key = "timeline") {
            val window = remember(stops.size, fromIndex, progress?.anchorIndex, showAllStops) {
                TripProgress.window(stops.size, fromIndex, progress?.anchorIndex, expanded = showAllStops)
            }
            RoundedCardContainer(spacing = 0.dp) {
                window.forEach { entry ->
                    when (entry) {
                        is TripWindowItem.Collapsed -> TimelineCollapsedRow(entry.count, mode.color, onClick = { onShowAllStops(true) })
                        is TripWindowItem.Stop -> {
                            val index = entry.index
                            val ps = stops[index]
                            StopRow(
                                ps = ps,
                                index = index,
                                lastIndex = stops.lastIndex,
                                mode = mode,
                                highlighted = index == fromIndex,
                                passed = index <= passedUpTo(progress, fromIndex),
                                hasVehicle = progress?.atIndex == index,
                                now = now,
                                onClick = { onOpenStop(ps.stop) },
                            )
                            if (progress != null && progress.atIndex == null && progress.afterIndex == index && index < stops.lastIndex) {
                                TimelineVehicleRow(
                                    mode = mode,
                                    lineColor = mode.color,
                                    text = "Between ${ps.stop.name} and ${stops[index + 1].stop.name}",
                                    detail = liveVehicle?.let { updatedText(it, now) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "chips") { TripChips(data) }
        if (data.disruptions.isNotEmpty()) {
            item(key = "disruptions_label") { SectionLabel("Service updates") }
            items(data.disruptions, key = { "d${it.id}" }) { DisruptionCard(it) }
        }
        if (showAllStops && stops.size > 8) {
            item(key = "fewer") {
                TextButton(onClick = hapticConfirm { onShowAllStops(false) }, modifier = Modifier.fillMaxWidth()) { Text("Show fewer stops") }
            }
        }
    }
}

@Composable
fun TripMapRequest(runRef: String, mode: TransportMode, title: String, fromStopId: Int) {
    TODO("Not yet implemented")
}

/** Highest stop index the vehicle (or, without live location, the user's stop) has already passed. */
private fun passedUpTo(progress: VehicleProgress?, fromIndex: Int): Int = when {
    progress == null -> fromIndex - 1
    progress.atIndex != null -> progress.atIndex - 1
    else -> progress.afterIndex
}

private fun updatedText(position: VehiclePosition, now: Instant): String? {
    val recorded = position.recordedAt ?: return null
    val seconds = Duration.between(recorded, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 15 -> "Updated just now"
        seconds < 90 -> "Updated ${seconds}s ago"
        else -> "Updated ${seconds / 60} min ago"
    }
}

@Composable
private fun delayColor(delayMinutes: Long?): Color? = when {
    delayMinutes == null || delayMinutes == 0L -> null
    delayMinutes >= 2 -> MaterialTheme.colorScheme.error
    delayMinutes > 0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun StopRow(
    ps: PatternStop,
    index: Int,
    lastIndex: Int,
    mode: TransportMode,
    highlighted: Boolean,
    passed: Boolean,
    hasVehicle: Boolean,
    now: Instant,
    onClick: () -> Unit,
) {
    val time = ps.bestTime
    val delayMinutes = ps.delaySeconds?.let { Math.round(it / 60.0) }
    val differs = ps.scheduled != null && ps.estimated != null && delayMinutes != null && delayMinutes != 0L
    TimelineRow(
        title = ps.stop.name,
        subtitle = ps.platform?.let { "Platform $it" } ?: ps.stop.suburb,
        lineColor = mode.color,
        isFirst = index == 0,
        isLast = index == lastIndex,
        highlighted = highlighted,
        dimmed = passed,
        vehicleMode = if (hasVehicle) mode else null,
        // The predicted time leads; the timetable time sits underneath when the two differ.
        trailing = time?.let { TimeFormatter.clock(it) },
        trailingColor = delayColor(delayMinutes),
        trailingSubtitle = when {
            differs -> TimeFormatter.punctuality(delayMinutes)
            time != null && time.isAfter(now) -> TimeFormatter.countdown(time, now)
            ps.estimated != null -> "Live"
            else -> null
        },
        trailingDetail = when {
            differs -> "Scheduled ${TimeFormatter.clock(ps.scheduled!!)}"
            ps.estimated != null && time?.isAfter(now) == true -> "On time"
            else -> null
        },
        onClick = onClick,
    )
}

/**
 * Live location, plus the timetable and predicted times for the stop you are watching:
 * the "is it running late?" answer at a glance.
 */
@Composable
private fun LiveStatusCard(
    mode: TransportMode,
    stops: List<PatternStop>,
    fromIndex: Int,
    vehicle: VehiclePosition?,
    progress: VehicleProgress?,
    now: Instant,
) {
    val watched = stops.getOrNull(fromIndex)
    val delayMinutes = watched?.delaySeconds?.let { Math.round(it / 60.0) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VehicleBadge(mode, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vehicle != null) {
                            Box(Modifier.size(8.dp).background(Color(0xFF3DBE6A), CircleShape))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (vehicle != null) "Live location" else "No live location",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        when {
                            vehicle != null && progress != null -> {
                                val at = progress.atIndex
                                if (at != null) {
                                    "At ${stops[at].stop.name}"
                                } else {
                                    "Between ${stops[progress.afterIndex].stop.name} and ${stops[progress.afterIndex + 1].stop.name}"
                                }
                            }
                            vehicle != null -> "Shown on the map"
                            else -> "Not sharing its position, so times come from the timetable and live predictions"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (watched != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeColumn("Scheduled", watched.scheduled?.let { TimeFormatter.clock(it) } ?: "-", Modifier.weight(1f))
                    TimeColumn(
                        "Predicted",
                        watched.estimated?.let { TimeFormatter.clock(it) } ?: "-",
                        Modifier.weight(1f),
                        emphasis = watched.estimated != null,
                        valueColor = delayColor(delayMinutes),
                    )
                    TimeColumn("Status", TimeFormatter.punctuality(delayMinutes) ?: "Timetable", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false, valueColor: Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun TripChips(pattern: StoppingPattern) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pattern.route?.let { Box(Modifier.padding(top = 8.dp)) { RouteBadge(it) } }
        val chipColors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        pattern.vehicle?.description?.let {
            AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Sym.DirectionsTransit, null, Modifier.size(18.dp)) }, shape = CircleShape, colors = chipColors, border = null)
        }
        if (pattern.vehicle?.lowFloor == true) {
            AssistChip(onClick = {}, label = { Text("Low floor") }, leadingIcon = { Icon(Sym.Accessible, null, Modifier.size(18.dp)) }, shape = CircleShape, colors = chipColors, border = null)
        }
        if (pattern.vehicle?.airConditioned == true) {
            AssistChip(onClick = {}, label = { Text("Air conditioned") }, leadingIcon = { Icon(Sym.AcUnit, null, Modifier.size(18.dp)) }, shape = CircleShape, colors = chipColors, border = null)
        }
    }
}
