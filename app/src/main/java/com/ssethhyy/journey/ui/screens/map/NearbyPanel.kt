/*
 * Feature Module: Map
 * File: NearbyPanel.kt
 * Description: Floating, collapsible panel listing nearby stops over the map.
 */

package com.ssethhyy.journey.ui.screens.map

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.repository.TimeDisplay
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RouteBadge
import com.ssethhyy.journey.ui.screens.nearby.LocationStatus
import com.ssethhyy.journey.ui.screens.nearby.NearbyUiState
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearbyPanel(
    state: NearbyUiState,
    expanded: Boolean,
    now: Instant,
    onToggleExpanded: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSelect: (Stop) -> Unit,
    onAllowLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "nearby_chevron")
    val stops = state.stops.dataOrNull.orEmpty()
    val summary = when {
        state.stops is LoadState.Loading -> "Finding stops near you…"
        state.stops is LoadState.Error -> "Couldn't load nearby stops"
        stops.isEmpty() -> "No stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}"
        state.locationStatus == LocationStatus.NO_PERMISSION || state.locationStatus == LocationStatus.FALLBACK ->
            "${stops.size} stops near the city"
        else -> "${stops.size} stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column {
            // Header: tap or drag to expand/collapse.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse nearby" else "Expand nearby") {
                        HapticUtil.performUIHaptic(view)
                        onToggleExpanded(!expanded)
                    }
                    .pointerInput(expanded) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -40f && !expanded) onToggleExpanded(true)
                                if (total > 40f && expanded) onToggleExpanded(false)
                            },
                        ) { _, dragAmount -> total += dragAmount }
                    }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = if (expanded) 4.dp else 8.dp),
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
                        modifier = Modifier.graphicsLayer { rotationZ = chevron },
                    )
                    HapticIconButton(Sym.Close, "Hide nearby", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    if (state.locationStatus == LocationStatus.NO_PERMISSION) {
                        Row(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Sym.LocationOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Location is off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onAllowLocation) { Text("Allow") }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
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

@Composable
private fun NearbyStopRow(stop: Stop, board: LoadState<DepartureBoard>?, now: Instant, display: TimeDisplay, onClick: () -> Unit) {
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
