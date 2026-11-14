/*
 * Feature Module: Map
 * File: NearbyPanel.kt
 * Description: Floating Nearby panel over the map that you drag between collapsed and open.
 *
 * Open, it shows about three stops (the list scrolls for more). Drag the handle (or the list) down to collapse it and
 * up to open it; let go and it springs to whichever step is closer, or the way you flicked. Keep pulling up past open
 * and it opens the full Nearby page instead.
 */

package com.ssethhyy.journey.ui.screens.map

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
/** How far past open you pull before letting go opens the full Nearby page. */
private val PullToOpenAll = 110.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearbyPanel(
    state: NearbyUiState,
    expanded: Boolean,
    now: Instant,
    onExpandedChange: (Boolean) -> Unit,
    onOpenAll: () -> Unit,
    onClose: () -> Unit,
    onSelect: (Stop) -> Unit,
    onAllowLocation: () -> Unit,
    modifier: Modifier = Modifier,
    /** How open the panel is, 0 (collapsed) to 1 (open), as it moves; for the map's zoom effect. */
    onProgress: (Float) -> Unit = {},
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val openPx = with(density) { ListHeight.toPx() }
    val maxPx = openPx + with(density) { PullToOpenAll.toPx() }
    val stops = state.stops.dataOrNull.orEmpty()
    val summary = when {
        state.stops is LoadState.Loading -> "Finding stops near you…"
        state.stops is LoadState.Error -> "Couldn't load nearby stops"
        stops.isEmpty() -> "No stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}"
        state.locationStatus == LocationStatus.NO_PERMISSION || state.locationStatus == LocationStatus.SERVICES_OFF || state.locationStatus == LocationStatus.FALLBACK ->
            "${stops.size} stops near the city"
        else -> "${stops.size} stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}"
    }

    // The list's visible height: 0 collapsed, ListHeight open, more while pulling towards the full page.
    var height by remember { mutableFloatStateOf(if (expanded) openPx else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val progress = (height / openPx).coerceIn(0f, 1f)
    val report by rememberUpdatedState(onProgress)
    LaunchedEffect(progress) { report(progress) }

    fun animateTo(target: Float, velocity: Float = 0f) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(height, target, initialVelocity = -velocity, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                height = value
            }
        }
    }

    // Expanded or collapsed from outside (picking a stop collapses it): follow along unless a finger is on it.
    LaunchedEffect(expanded) {
        val target = if (expanded) openPx else 0f
        if (!dragging && height != target) animateTo(target)
    }

    fun dragBy(delta: Float) {
        settleJob?.cancel()
        // Past open the pull gets heavier, so the full page doesn't open by accident.
        val resisted = if (delta < 0 && height >= openPx) delta * 0.5f else delta
        height = (height - resisted).coerceIn(0f, maxPx)
    }

    /** Lets go: open the full page if pulled far enough, otherwise spring to the nearer step or the flick's way. */
    fun settle(velocity: Float) {
        dragging = false
        val open = when {
            height >= maxPx * 0.92f || (height > openPx + 24f && velocity < -1_500f) -> {
                HapticUtil.performUIHaptic(view)
                onOpenAll()
                true
            }
            velocity < -800f -> true
            velocity > 800f -> false
            else -> height > openPx / 2
        }
        if (open != expanded) {
            HapticUtil.performUIHaptic(view)
            onExpandedChange(open)
        }
        animateTo(if (open) openPx else 0f, velocity)
    }

    val dragState = rememberDraggableState { dragBy(it) }
    // The list hands its drags to the panel: pulling down at its top collapses it, pushing up while it's partly open
    // opens it first, and pulling up at its end keeps going towards the full page.
    val listScroll = remember(openPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0 && height < openPx) {
                    dragging = true
                    dragBy(available.y)
                    return available
                }
                if (available.y > 0 && height > openPx) {
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column {
            // Header: drag it to open or collapse the panel, or tap it.
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragging = true },
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse nearby" else "Expand nearby") {
                        HapticUtil.performUIHaptic(view)
                        onExpandedChange(!expanded)
                    }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Icon(
                        Sym.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = 180f * progress },
                    )
                    HapticIconButton(Sym.Close, "Hide nearby", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (height > 0.5f) {
                val listHeight = with(density) { height.toDp() }
                Box(Modifier.fillMaxWidth().height(listHeight).clipToBounds()) {
                    LazyColumn(
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
                    // Pulling past open: say what letting go will do.
                    val pull = ((height - openPx) / (maxPx - openPx)).coerceIn(0f, 1f)
                    if (pull > 0.02f) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .graphicsLayer { alpha = (pull * 2f).coerceAtMost(1f) },
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Sym.KeyboardArrowUp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (height >= maxPx * 0.92f) "Let go for every stop within 1 km" else "Keep pulling for every stop nearby",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
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
