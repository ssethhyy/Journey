/*
 * Feature Module: Map
 * File: MapScreen.kt
 * Description: Full-screen, edge-to-edge Mapbox map showing stops for every mode with a floating stop preview.
 */

package com.ssethhyy.journey.ui.screens.map

import com.ssethhyy.journey.ui.components.Sym
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupInteractionsState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroupState
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardSatelliteStyle
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.ssethhyy.journey.R
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.DepartureRow
import com.ssethhyy.journey.ui.components.DepartureSkeleton
import com.ssethhyy.journey.ui.components.FavouriteToggle
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.InlineError
import com.ssethhyy.journey.ui.components.MapOverlayButton
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.components.TrackingBanner
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.home.ToolbarClearance
import com.ssethhyy.journey.ui.screens.nearby.NearbyViewModel
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.ui.theme.isAppInDarkTheme
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Instant

private val MELBOURNE = Point.fromLngLat(144.9631, -37.8136)

@OptIn(FlowPreview::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(isActive: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm = appViewModel { MapViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val view = LocalView.current
    val darkTheme = isAppInDarkTheme(state.settings.themeMode)

    val resourceToken = stringResource(R.string.mapbox_access_token)
    val token = state.settings.mapboxTokenOverride.ifBlank { resourceToken }
    val hasValidToken = token.startsWith("pk.") && container.mapRenderingEnabled

    val viewportState = rememberMapViewportState {
        setCameraOptions {
            center(MELBOURNE)
            zoom(14.0)
            pitch(0.0)
        }
    }
    var hasCenteredOnUser by rememberSaveable { mutableStateOf(false) }
    val nearbyVm = appViewModel(key = "map_nearby") { NearbyViewModel(it) }
    val nearbyState by nearbyVm.state.collectAsStateWithLifecycle()
    // The Nearby panel opens expanded the first time the map is shown.
    var showNearby by rememberSaveable { mutableStateOf(true) }
    var nearbyExpanded by rememberSaveable { mutableStateOf(true) }
    var nearbyLoaded by rememberSaveable { mutableStateOf(false) }
    var locationEnabled by remember { mutableStateOf(vm.hasLocationPermission()) }

    fun flyTo(lat: Double, lon: Double, zoom: Double = 15.5) {
        viewportState.flyTo(
            CameraOptions.Builder().center(Point.fromLngLat(lon, lat)).zoom(zoom).build(),
            MapAnimationOptions.mapAnimationOptions { duration(1_200) },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        locationEnabled = grants.values.any { it }
        if (locationEnabled) vm.locateUser { flyTo(it.latitude, it.longitude) }
        nearbyVm.load(force = true)
    }

    // First time the tab is shown, centre on the user if we already have permission.
    LaunchedEffect(isActive) {
        if (isActive && !nearbyLoaded) {
            nearbyLoaded = true
            nearbyVm.load()
        }
        if (isActive && !hasCenteredOnUser && locationEnabled) {
            hasCenteredOnUser = true
            vm.locateUser { flyTo(it.latitude, it.longitude) }
        }
    }

    // Requests from search / stop details to show a stop on the map.
    LaunchedEffect(Unit) {
        container.mapFocus.filterNotNull().collect { request ->
            hasCenteredOnUser = true
            vm.focusStop(request.stop)
            flyTo(request.stop.latitude, request.stop.longitude, 16.5)
            container.consumeMapFocus(request.id)
        }
    }

    // Measured height of the floating stop preview, so map controls and ornaments sit above it.
    var previewHeightPx by remember { mutableIntStateOf(0) }
    var nearbyHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val nearbyVisible = showNearby && state.selected == null
    val previewClearance = when {
        state.selected != null -> with(density) { previewHeightPx.toDp() } + 4.dp
        nearbyVisible -> with(density) { nearbyHeightPx.toDp() } + 4.dp
        else -> 0.dp
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value

        // Load stops whenever the camera settles.
        LaunchedEffect(viewportState, widthDp, heightDp) {
            snapshotFlow { viewportState.cameraState }
                .filterNotNull()
                .debounce(450)
                .collect { camera ->
                    vm.onViewportIdle(Viewport(camera.center.latitude(), camera.center.longitude(), camera.zoom, widthDp, heightDp))
                }
        }

        if (hasValidToken) {
            val bottomOrnamentPadding = ToolbarClearance + previewClearance
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                // TextureView (instead of SurfaceView) so the navigation bar blur can sample the map.
                composeMapInitOptions = remember(density) { ComposeMapInitOptions(density.density, textureView = true) },
                mapViewportState = viewportState,
                compass = {
                    Compass(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 64.dp),
                        alignment = Alignment.TopEnd,
                    )
                },
                scaleBar = {},
                logo = {
                    Logo(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = bottomOrnamentPadding),
                    )
                },
                attribution = {
                    Attribution(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = bottomOrnamentPadding),
                        iconColor = MaterialTheme.colorScheme.primary,
                    )
                },
                onMapClickListener = OnMapClickListener {
                    if (state.selected != null) vm.select(null)
                    false
                },
                style = {
                    val preset = if (state.settings.mapFollowsTheme && darkTheme) LightPresetValue.NIGHT else LightPresetValue.DAY
                    if (state.settings.mapSatellite) {
                        MapboxStandardSatelliteStyle {
                            lightPreset = preset
                            showTransitLabels = BooleanValue(false)
                        }
                    } else {
                        MapboxStandardStyle {
                            lightPreset = preset
                            show3dObjects = BooleanValue(state.settings.map3dObjects)
                            // Our own stop markers replace Mapbox's transit POIs.
                            showTransitLabels = BooleanValue(false)
                        }
                    }
                },
            ) {
                MapEffect(locationEnabled) { mapView ->
                    mapView.location.updateSettings {
                        enabled = locationEnabled
                        locationPuck = createDefault2DPuck(withBearing = true)
                        puckBearingEnabled = true
                        puckBearing = PuckBearing.HEADING
                        pulsingEnabled = true
                    }
                }

                val selectedKey = state.selected?.key
                val annotations = remember(state.stops, selectedKey) {
                    state.stops.map { stop ->
                        val isSelected = stop.key == selectedKey
                        CircleAnnotationOptions()
                            .withPoint(Point.fromLngLat(stop.longitude, stop.latitude))
                            .withCircleColor(stop.mode.color.toArgb())
                            .withCircleRadius(if (isSelected) 11.0 else 6.5)
                            .withCircleStrokeColor(Color.White.toArgb())
                            .withCircleStrokeWidth(if (isSelected) 3.5 else 1.75)
                            .withCircleSortKey(if (isSelected) 10.0 else stop.mode.ordinal.toDouble())
                            .withData(JsonPrimitive(stop.key))
                    }
                }
                val groupState = remember {
                    CircleAnnotationGroupState().apply {
                        // Standard style's night lighting would otherwise darken markers to near-invisible.
                        circleEmissiveStrength = 1.0
                    }
                }
                groupState.interactionsState = remember(vm, view) {
                    CircleAnnotationGroupInteractionsState().onClicked { annotation ->
                        annotation.getData()?.asString?.let { key ->
                            HapticUtil.performUIHaptic(view)
                            vm.selectByKey(key)
                        }
                        true
                    }
                }
                CircleAnnotationGroup(annotations = annotations, circleAnnotationGroupState = groupState)
            }
        } else {
            MissingTokenCard()
        }

        // Top overlay: mode filters, status chips.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeFilterRow(
                selected = state.modes,
                onToggle = vm::toggleMode,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Spacer(Modifier.height(8.dp))
            TrackingBanner(
                onOpen = { navigator.openTracked(it) },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            when {
                state.zoomedOutTooFar -> StatusPill(icon = { Icon(Sym.ZoomIn, null, Modifier.size(18.dp)) }, text = "Zoom in to see stops")
                state.error != null -> StatusPill(
                    icon = { Icon(Sym.Refresh, null, Modifier.size(18.dp)) },
                    text = state.error?.message ?: "Couldn't load stops",
                    onClick = vm::retry,
                )
                state.loading -> StatusPill(icon = { LoadingIndicator(Modifier.size(20.dp)) }, text = "Loading stops")
            }
        }

        // Right-side map controls.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 16.dp, bottom = ToolbarClearance + previewClearance + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasValidToken) {
                MapOverlayButton(
                    icon = if (state.settings.mapSatellite) Sym.Map else Sym.Layers,
                    contentDescription = if (state.settings.mapSatellite) "Show standard map" else "Show satellite map",
                    onClick = {
                        val next = !state.settings.mapSatellite
                        container.appScope.launch { container.settingsRepository.setMapSatellite(next) }
                    },
                )
            }
            if (!showNearby) {
                MapOverlayButton(
                    icon = Sym.NearMe,
                    contentDescription = "Show nearby stops",
                    onClick = {
                        showNearby = true
                        nearbyExpanded = true
                    },
                )
            }
            MapOverlayButton(
                icon = Sym.MyLocation,
                contentDescription = "Centre on my location",
                onClick = {
                    if (vm.hasLocationPermission()) {
                        locationEnabled = true
                        vm.locateUser { flyTo(it.latitude, it.longitude) }
                        nearbyVm.load(force = true)
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
            )
        }

        // Floating Nearby panel above the toolbar (hidden while a stop preview is showing).
        AnimatedVisibility(
            visible = nearbyVisible,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = ToolbarClearance - 8.dp)
                .onSizeChanged { nearbyHeightPx = it.height },
        ) {
            val now by rememberNow()
            NearbyPanel(
                state = nearbyState,
                expanded = nearbyExpanded,
                now = now,
                onToggleExpanded = { nearbyExpanded = it },
                onClose = { showNearby = false },
                onSelect = { stop ->
                    nearbyExpanded = false
                    vm.focusStop(stop)
                    flyTo(stop.latitude, stop.longitude, 16.5)
                },
                onAllowLocation = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
            )
        }

        // Floating stop preview above the toolbar.
        AnimatedVisibility(
            visible = state.selected != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = ToolbarClearance - 8.dp)
                .onSizeChanged { previewHeightPx = it.height },
        ) {
            val now by rememberNow()
            val stop = state.selected
            if (stop != null) {
                StopPreviewCard(
                    stop = stop,
                    board = state.selectedBoard,
                    now = now,
                    isFavourite = stop.key in state.favouriteKeys,
                    timeDisplay = state.settings.timeDisplay,
                    onToggleFavourite = { vm.toggleFavourite(stop) },
                    onClose = { vm.select(null) },
                    onOpen = { navigator.openStop(stop) },
                    onRetry = vm::refreshSelected,
                    onOpenTrip = { dep -> dep.runRef?.let { navigator.openTrip(it, stop.mode, stop.id, dep.headsign) } },
                )
            }
        }
    }
}

@Composable
private fun StatusPill(icon: @Composable () -> Unit, text: String, onClick: (() -> Unit)? = null) {
    val view = LocalView.current
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick?.invoke()
        },
        enabled = onClick != null,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StopPreviewCard(
    stop: Stop,
    board: LoadState<DepartureBoard>?,
    now: Instant,
    isFavourite: Boolean,
    timeDisplay: com.ssethhyy.journey.data.repository.TimeDisplay,
    onToggleFavourite: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onOpenTrip: (com.ssethhyy.journey.data.model.Departure) -> Unit,
) {
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModePictogram(stop.mode, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stop.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(stop.suburb, stop.mode.label).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FavouriteToggle(isFavourite, onToggleFavourite)
                HapticIconButton(Sym.Close, "Close", onClick = onClose)
            }
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .then(Modifier),
            ) {
                when (board) {
                    null, LoadState.Loading -> DepartureSkeleton(rows = 2)
                    is LoadState.Error -> InlineError(board.error.message ?: "Couldn't load departures", onRetry)
                    is LoadState.Success -> {
                        val upcoming = board.data.departures.filter { it.bestTime?.isAfter(now.minusSeconds(60)) == true }.take(3)
                        if (upcoming.isEmpty()) {
                            Text(
                                "No upcoming departures",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        upcoming.forEachIndexed { i, dep ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DepartureRow(dep, now, timeDisplay, hasDisruption = dep.disruptionIds.isNotEmpty(), onClick = { onOpenTrip(dep) })
                        }
                    }
                }
            }
            Button(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    onOpen()
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text("Timetable, disruptions & facilities")
                Spacer(Modifier.width(8.dp))
                Icon(Sym.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MissingTokenCard() {
    Box(Modifier.fillMaxSize().padding(bottom = ToolbarClearance), contentAlignment = Alignment.Center) {
        MessageState(
            icon = Sym.Map,
            title = "Map needs a Mapbox token",
            message = "Add a public Mapbox token (starts with pk.) in Settings → API access, or set mapbox.accessToken in local.properties. Search still works without it.",
        )
    }
}
