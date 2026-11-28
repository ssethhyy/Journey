/*
 * Feature Module: Map
 * File: NearbyPanel.kt
 * Description: Floating Nearby panel over the map that you drag between collapsed and open.
 *
 * Open, it shows about three stops (the list scrolls for more). Drag the handle (or the list) down to collapse it and
 * up to open it; keep dragging up and it grows with your finger until it fills the screen, listing every stop
 * nearby. Let go and it springs to whichever step is closer (collapsed, open or full screen), or the way you flicked.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.repository.TimeDisplay
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RouteBadge
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.screens.nearby.LocationStatus
import com.ssethhyy.journey.ui.screens.nearby.NearbyUiState
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

/** Room for about three stops when open. */
private val ListHeight = 204.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearbyPanel(
    state: NearbyUiState,
    expanded: Boolean,
    /** Pulled all the way up: the panel fills the screen and lists every stop nearby. */
    full: Boolean,
    now: Instant,
    onExpandedChange: (Boolean) -> Unit,
    onFullChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSelect: (Stop) -> Unit,
    onAllowLocation: () -> Unit,
    /** The tallest the whole panel can be, header included (the screen above the toolbar). */
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    /** How open the panel is, 0 (collapsed) to 1 (open), as it moves; for the map's zoom effect. */
    onProgress: (Float) -> Unit = {},
    /** How far from open to full screen it is, 0 to 1, so the map can tuck its controls away. */
    onFullProgress: (Float) -> Unit = {},
    /** The stops scrolled into view, so their departures can be loaded. */
    onVisibleStops: (List<Stop>) -> Unit = {},
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val openPx = with(density) { ListHeight.toPx() }
    var headerPx by remember { mutableFloatStateOf(with(density) { 84.dp.toPx() }) }
    // Full screen: the whole height the panel may take, less its header.
    val fullPx = (with(density) { maxHeight.toPx() } - headerPx).coerceAtLeast(openPx + 1f)
    val stops = state.stops.dataOrNull.orEmpty()
    val summary = when {
        state.stops is LoadState.Loading -> "Finding stops near you…"
        state.stops is LoadState.Error -> "Couldn't load nearby stops"
        stops.isEmpty() -> "No stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}"
        state.locationStatus == LocationStatus.NO_PERMISSION || state.locationStatus == LocationStatus.SERVICES_OFF || state.locationStatus == LocationStatus.FALLBACK ->
            "${stops.size} stops near the city"
        else -> "${stops.size} stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}"
    }

    fun targetFor(expanded: Boolean, full: Boolean) = when {
        full -> fullPx
        expanded -> openPx
        else -> 0f
    }

    // The list's visible height: 0 collapsed, ListHeight open, up to fullPx at full screen. Dragging moves it
    // directly, so the panel follows your finger all the way to full screen.
    var height by remember { mutableFloatStateOf(targetFor(expanded, full)) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val progress = (height / openPx).coerceIn(0f, 1f)
    val fullProgress = ((height - openPx) / (fullPx - openPx)).coerceIn(0f, 1f)
    val report by rememberUpdatedState(onProgress)
    val reportFull by rememberUpdatedState(onFullProgress)
    LaunchedEffect(progress) { report(progress) }
    LaunchedEffect(fullProgress) { reportFull(fullProgress) }

    fun animateTo(target: Float, velocity: Float = 0f) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(height, target, initialVelocity = -velocity, animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                height = value
            }
        }
    }

    // Changed from outside (picking a stop collapses it, Back leaves full screen): follow along unless a finger is on it.
    LaunchedEffect(expanded, full, fullPx) {
        val target = targetFor(expanded, full)
        if (!dragging && height != target) animateTo(target)
    }

    fun dragBy(delta: Float) {
        settleJob?.cancel()
        height = (height - delta).coerceIn(0f, fullPx)
    }

    /** Lets go: spring to the nearest step (collapsed, open or full screen), or on in the direction of a flick. */
    fun settle(velocity: Float) {
        dragging = false
        val anchors = listOf(0f, openPx, fullPx)
        val target = when {
            velocity < -800f -> anchors.firstOrNull { it > height + 1f } ?: fullPx
            velocity > 800f -> anchors.lastOrNull { it < height - 1f } ?: 0f
            else -> anchors.minBy { kotlin.math.abs(it - height) }
        }
        val nowFull = target == fullPx
        val nowExpanded = target != 0f
        if (nowFull != full || nowExpanded != expanded) HapticUtil.performUIHaptic(view)
        if (nowFull != full) onFullChange(nowFull)
        if (nowExpanded != expanded) onExpandedChange(nowExpanded)
        animateTo(target, velocity)
    }

    fun atAnchor() = listOf(0f, openPx, fullPx).any { kotlin.math.abs(it - height) < 0.5f }

    val dragState = rememberDraggableState { dragBy(it) }
    // The list hands drags to the panel: once the panel is moving it keeps the whole gesture, pulling down at the top
    // of the list shrinks it, and pulling up at the end of the list grows it towards full screen.
    val listScroll = remember(openPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (dragging && !atAnchor()) {
                    dragBy(available.y)
                    return available
                }
                if (available.y < 0 && height < openPx - 0.5f) {
                    dragging = true
                    dragBy(available.y)
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                dragging = true
                dragBy(available.y)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!dragging) return Velocity.Zero
                settle(available.y)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragging) settle(available.y)
                return Velocity.Zero
            }
        }
    }

    // Morphs from a floating panel into a page as it's pulled to full screen.
    val (morphShape, morphColor) = rememberSheetMorph(fullProgress)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = morphShape,
        color = morphColor,
        shadowElevation = 6.dp,
    ) {
        Column {
            // Header: swipe it up or down to open, collapse or fill the screen with the panel. The handle sits on the
            // title's line rather than above it, so the panel starts with its title.
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerPx = it.height.toFloat() }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragging = true },
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    .semantics {
                        stateDescription = if (full) "Full screen" else if (expanded) "Expanded" else "Collapsed"
                        customActions = listOf(
                            androidx.compose.ui.semantics.CustomAccessibilityAction(if (expanded) "Collapse nearby" else "Expand nearby") {
                                if (full) onFullChange(false) else onExpandedChange(!expanded)
                                true
                            },
                        )
                    },
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.stops is LoadState.Loading) {
                            LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            Icon(Sym.NearMe, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nearby", style = MaterialTheme.typography.titleMedium)
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HapticIconButton(Sym.Close, "Hide nearby", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // In line with "Nearby" (its line's centre), in the middle of the panel.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }

            if (height > 0.5f) {
                val listHeight = with(density) { height.toDp() }
                Box(Modifier.fillMaxWidth().height(listHeight).clipToBounds()) {
                    val listState = rememberLazyListState()
                    val visible by rememberUpdatedState(onVisibleStops)
                    LaunchedEffect(listState, stops) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { info -> stops.firstOrNull { it.key == info.key } } }
                            .distinctUntilChanged()
                            .collect { if (it.isNotEmpty()) visible(it) }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxOf(listHeight, ListHeight))
                            .nestedScroll(listScroll)
                            .graphicsLayer { alpha = (progress * 1.6f).coerceAtMost(1f) },
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val blocked = state.locationStatus == LocationStatus.NO_PERMISSION || state.locationStatus == LocationStatus.SERVICES_OFF
                        if (blocked) {
                            item(key = "location") {
                                Row(
                                    Modifier
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Sym.LocationOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (state.locationStatus == LocationStatus.SERVICES_OFF) "Location is switched off on this phone" else "Location isn't allowed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = onAllowLocation, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) { Text(if (state.locationStatus == LocationStatus.SERVICES_OFF) "Turn on" else "Allow") }
                                }
                            }
                        }
                        items(stops, key = { it.key }) { stop ->
                            NearbyStopRow(stop, state.boards[stop.key], now, state.settings.timeDisplay, onClick = {
                                HapticUtil.performUIHaptic(view)
                                onSelect(stop)
                            })
                        }
                    }
                }
            }
        }
    }
}

/** How much the map zooms while the Nearby panel moves: nothing at rest, a touch mid-way. */
internal fun nearbyZoomScale(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    // Peaks half-way between collapsed and open, like the map leaning in as the panel moves.
    return 1f + 0.035f * (4f * p * (1f - p))
}

@Composable
internal fun NearbyStopRow(stop: Stop, board: LoadState<DepartureBoard>?, now: Instant, display: TimeDisplay, onClick: () -> Unit) {
    val next = board?.dataOrNull?.departures?.firstOrNull { it.bestTime?.isAfter(now.minusSeconds(60)) == true && !it.isCancelled }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModePictogram(stop.mode, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(stop.distanceMeters?.let(DistanceFormatter::distanceAndWalk), stop.suburb).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (next != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                next.route?.let { RouteBadge(it, compact = true) }
                val time = next.bestTime
                if (time != null) {
                    Text(
                        if (display == TimeDisplay.RELATIVE) TimeFormatter.countdown(time, now) else TimeFormatter.clock(time),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (next.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
