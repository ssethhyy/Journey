/*
 * Feature Module: Plan
 * File: JourneyDetailScreen.kt
 * Description: A single planned journey: route map preview and step-by-step legs.
 */

package com.ssethhyy.journey.ui.screens.plan

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.extension.compose.MapEffect
import com.ssethhyy.journey.ui.components.hapticClick
import com.ssethhyy.journey.ui.components.JourneyRouteLayers
import com.ssethhyy.journey.ui.components.JourneyGeometry
import androidx.compose.ui.graphics.toArgb
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroupState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationGroup
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
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
    val navigator = currentNavigator
    // The same one the map's journey panel uses, so the page opens showing exactly what the panel did.
    val live = journeyLiveViewModel(key.journeyId)
    val journey by live.journey.collectAsStateWithLifecycle()
    val updatedAt by live.updatedAt.collectAsStateWithLifecycle()
    val refreshing by live.refreshing.collectAsStateWithLifecycle()
    val now by com.ssethhyy.journey.ui.components.rememberNow()
    val openLegStop = com.ssethhyy.journey.ui.components.rememberLegStopOpener()
    var selected by androidx.compose.runtime.saveable.rememberSaveable(key.journeyId) { mutableStateOf(key.selectedLeg.takeIf { it >= 0 }) }
    // Live updates while the journey hasn't finished.
    AutoRefreshEffect(enabled = journey?.end?.isAfter(now) == true, intervalMs = 30_000) { live.refresh() }
    var logging by remember { mutableStateOf(false) }
    if (logging) journey?.let { com.ssethhyy.journey.ui.screens.myki.LogJourneySheet(it, onDismiss = { logging = false }) }
    // Pulled up out of the map's journey panel: drag it back down to return to the map.
    val pullDown = com.ssethhyy.journey.ui.components.rememberPullDownToClose(key.fromMap, navigator::back)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(key.scrollIndex, key.scrollOffset)

    Scaffold(
        modifier = pullDown.pageModifier,
        topBar = {
            ReusableTopAppBar(
                title = "To ${key.toName.ifBlank { "destination" }}",
                subtitle = "From ${key.fromName.ifBlank { "your start" }}",
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .floatingBarBlur(padding.calculateTopPadding())
                .nestedScroll(pullDown.connection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = pageBottomInset(104.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Pulled up from the map, the map is right behind it; otherwise a preview opens it there.
            if (!key.fromMap) {
                item(key = "map") { JourneyMap(journey, onOpen = { navigator.showJourneyOnMap(key.journeyId, key.fromName, key.toName) }) }
            }
            journeyItems(
                journey = journey,
                destination = key.toName,
                now = now,
                updatedAt = updatedAt,
                refreshing = refreshing,
                onRefresh = live::refresh,
                selected = selected,
                onSelect = { selected = it },
                onOpenStop = openLegStop,
            )
        }
    }
}

/**
 * A fixed preview of the route: it can't be dragged or zoomed, so it never fights the page's scrolling. Tap it to
 * explore the journey full size on the Map tab.
 */
@Composable
private fun JourneyMap(journey: Journey, onOpen: () -> Unit) {
    val container = LocalContext.current.appContainer
    val settings = container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null).value ?: return
    val token = settings.mapboxTokenOverride.ifBlank { stringResource(R.string.mapbox_access_token) }
    if (!token.startsWith("pk.") || !container.mapRenderingEnabled) return
    val dark = isAppInDarkTheme(settings.themeMode) && settings.mapFollowsTheme
    val all = remember(journey) { JourneyGeometry.allPoints(JourneyGeometry.drawable(journey)) }
    if (all.size < 2) return
    val viewport = rememberMapViewportState()
    val fitDensity = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(journey.id) {
        // Padding is in pixels: keep the whole route clear of the rounded corners and the Explore chip at the top.
        val px = { dp: Float -> with(fitDensity) { dp.dp.toPx() }.toDouble() }
        val camera = viewport.cameraForCoordinates(all, CameraOptions.Builder().build(), EdgeInsets(px(64f), px(32f), px(36f), px(32f)), null, null)
        viewport.setCameraOptions(camera)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        val mapDensity = androidx.compose.ui.platform.LocalDensity.current
        MapboxMap(
            // TextureView, like the Map tab: a SurfaceView can't be drawn through the app-wide edge blur and shows blank.
            composeMapInitOptions = androidx.compose.runtime.remember(mapDensity) { com.mapbox.maps.extension.compose.ComposeMapInitOptions(mapDensity.density, textureView = true) },
            modifier = Modifier.fillMaxSize(),
            mapViewportState = viewport,
            compass = {},
            scaleBar = {},
            style = {
                MapboxStandardStyle {
                    lightPreset = if (dark) LightPresetValue.NIGHT else LightPresetValue.DAY
                    show3dObjects = BooleanValue(false)
                }
            },
        ) {
            MapEffect(Unit) { mapView ->
                mapView.gestures.updateSettings {
                    scrollEnabled = false
                    pinchToZoomEnabled = false
                    rotateEnabled = false
                    pitchEnabled = false
                    doubleTapToZoomInEnabled = false
                    doubleTouchToZoomOutEnabled = false
                    quickZoomEnabled = false
                }
            }
            JourneyRouteLayers(journey, darkMap = dark)
        }
        // The map's own touch handling would swallow taps, so a clear layer on top takes them instead.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = "Open the journey on the map", onClick = hapticClick(onOpen)),
        )
        // Tappable too: a plain surface on top would swallow the tap meant for the layer beneath.
        Surface(
            onClick = hapticClick(onOpen),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Map, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Explore", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

