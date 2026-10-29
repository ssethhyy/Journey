/*
 * Feature Module: Plan
 * File: JourneyDetailScreen.kt
 * Description: A single planned journey: route map preview and step-by-step legs.
 */

package com.ssethhyy.journey.ui.screens.plan

import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.components.ToolbarFab
import com.ssethhyy.journey.ui.components.usesBottomBar
import com.ssethhyy.journey.ui.components.rememberTrackFab
import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.ssethhyy.journey.R
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.ui.components.JourneyTimeline
import com.ssethhyy.journey.ui.components.TrackButton
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.badgeColor
import com.ssethhyy.journey.ui.components.formatDuration
import com.ssethhyy.journey.ui.navigation.JourneyDetailKey
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.theme.isAppInDarkTheme
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JourneyDetailScreen(key: JourneyDetailKey) {
    val container = LocalContext.current.appContainer
    val navigator = currentNavigator
    var journey by remember(key.journeyId) { mutableStateOf(container.journeyRepository.journey(key.journeyId)) }
    var updatedAt by remember { mutableStateOf<java.time.Instant?>(java.time.Instant.now()) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                runCatching { container.journeyRepository.refreshJourney(key.journeyId) }
                    .onSuccess { updated ->
                        if (updated != null) {
                            journey = updated
                            updatedAt = java.time.Instant.now()
                        }
                    }
                refreshing = false
            }
        }
    }
    // Live updates while the journey hasn't finished.
    AutoRefreshEffect(enabled = journey?.end?.isAfter(java.time.Instant.now()) == true, intervalMs = 30_000) { refresh() }
    var logging by remember { mutableStateOf(false) }
    if (logging) journey?.let { com.ssethhyy.journey.ui.screens.myki.LogJourneySheet(it, onDismiss = { logging = false }) }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "To ${key.toName.ifBlank { "destination" }}",
                subtitle = journey?.let { "${TimeFormatter.clock(it.start)} – ${TimeFormatter.clock(it.end)} · ${formatDuration(it.durationMinutes)}" },
                onBackClick = navigator::back,
                pageFab = if (journey?.isWalkOnly == false) rememberTrackFab(TrackingTarget.Trip(key.journeyId, key.fromName, key.toName), "Track journey") else null,
                pageActions = if (journey?.isWalkOnly == false) listOf(ToolbarFab(Sym.Contactless, "Log trip") { logging = true }) else emptyList(),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (!usesBottomBar() && journey?.isWalkOnly == false) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        onClick = { logging = true },
                        icon = { Icon(Sym.Contactless, null) },
                        text = { Text("Log trip") },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    TrackButton(TrackingTarget.Trip(key.journeyId, key.fromName, key.toName), label = "Track journey")
                }
            }
        },
    ) { padding ->
        @Suppress("NAME_SHADOWING")
        val journey = journey
        if (journey == null) {
            Box(Modifier.padding(padding)) {
                MessageState(Sym.Route, "Journey expired", "Plan the trip again to see up-to-date options.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = pageBottomInset(104.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "map") { JourneyMap(journey) }
            item(key = "live") { LiveStatusRow(updatedAt = updatedAt, refreshing = refreshing, onRefresh = refresh) }
            item(key = "chips") {
                val chipColors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(formatDuration(journey.durationMinutes)) }, leadingIcon = { Icon(Sym.Schedule, null, Modifier.size(18.dp)) }, shape = CircleShape, colors = chipColors, border = null)
                    AssistChip(
                        onClick = {},
                        label = { Text(if (journey.transfers == 0) "No changes" else "${journey.transfers} change${if (journey.transfers > 1) "s" else ""}") },
                        leadingIcon = { Icon(Sym.SyncAlt, null, Modifier.size(18.dp)) },
                        shape = CircleShape,
                        colors = chipColors,
                        border = null,
                    )
                    if (journey.walkMeters >= 50) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${DistanceFormatter.distance(journey.walkMeters)} walking") },
                            leadingIcon = { Icon(Sym.DirectionsWalk, null, Modifier.size(18.dp)) },
                            shape = CircleShape,
                            colors = chipColors,
                            border = null,
                        )
                    }
                }
            }
            item(key = "timeline") {
                RoundedCardContainer(spacing = 0.dp) {
                    Box(Modifier.padding(vertical = 12.dp)) {
                        JourneyTimeline(journey, destinationName = key.toName)
                    }
                }
            }
            item(key = "attribution") {
                Text(
                    "Times include live updates where available. Planning by Transitous using Transport Victoria open data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun JourneyMap(journey: Journey) {
    val container = LocalContext.current.appContainer
    val settings = container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null).value ?: return
    val token = settings.mapboxTokenOverride.ifBlank { stringResource(R.string.mapbox_access_token) }
    if (!token.startsWith("pk.") || !container.mapRenderingEnabled) return
    val dark = isAppInDarkTheme(settings.themeMode)
    val legs = journey.legs.filter { it.path.size > 1 }
    val all = legs.flatMap { it.path }.map { Point.fromLngLat(it.longitude, it.latitude) }
    if (all.size < 2) return
    val viewport = rememberMapViewportState()
    LaunchedEffect(journey.id) {
        val camera = viewport.cameraForCoordinates(all, CameraOptions.Builder().build(), EdgeInsets(56.0, 56.0, 56.0, 56.0), null, null)
        viewport.setCameraOptions(camera)
    }
    val walkColor = MaterialTheme.colorScheme.outline
    MapboxMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(28.dp)),
        mapViewportState = viewport,
        compass = {},
        scaleBar = {},
        style = {
            MapboxStandardStyle {
                lightPreset = if (dark && settings.mapFollowsTheme) LightPresetValue.NIGHT else LightPresetValue.DAY
                show3dObjects = BooleanValue(false)
            }
        },
    ) {
        legs.forEach { leg ->
            val points = leg.path.map { Point.fromLngLat(it.longitude, it.latitude) }
            PolylineAnnotation(points = points) {
                lineColor = if (leg.kind == LegKind.WALK) walkColor else leg.badgeColor()
                lineWidth = if (leg.kind == LegKind.WALK) 3.0 else 6.0
                lineOpacity = if (leg.kind == LegKind.WALK) 0.8 else 1.0
            }
        }
        val start = all.first()
        val end = all.last()
        CircleAnnotation(point = start) {
            circleRadius = 7.0
            circleColor = Color.White
            circleStrokeColor = Color(0xFF1B6EF3)
            circleStrokeWidth = 4.0
        }
        CircleAnnotation(point = end) {
            circleRadius = 8.0
            circleColor = Color(0xFFD93025)
            circleStrokeColor = Color.White
            circleStrokeWidth = 3.0
        }
    }
}
