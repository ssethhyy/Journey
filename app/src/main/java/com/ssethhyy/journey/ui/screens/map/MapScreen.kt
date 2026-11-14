/*
 * Feature Module: Map
 * File: MapScreen.kt
 * Description: Full-screen, edge-to-edge Mapbox map showing stops for every mode with a floating stop preview.
 */

package com.ssethhyy.journey.ui.screens.map

import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.EdgeInsets
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import com.ssethhyy.journey.ui.components.MapControlPill
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
    // How open the Nearby panel is while it moves, for the map's small zoom as it does.
    var nearbyProgress by remember { mutableFloatStateOf(if (nearbyExpanded) 1f else 0f) }
    var locationEnabled by remember { mutableStateOf(vm.hasLocationPermission()) }

    // The part of the map not covered by the filters at the top or the panel and toolbar at the bottom, kept up
    // to date below. Centring with this padding puts a point in the middle of what you can actually see.
    var visiblePadding by remember { mutableStateOf(EdgeInsets(0.0, 0.0, 0.0, 0.0)) }
    // True after centring on you, until you drag the map yourself: while true, panels opening or closing re-centre.
    var followingUser by remember { mutableStateOf(false) }

    fun flyTo(lat: Double, lon: Double, zoom: Double = 15.5) {
        viewportState.flyTo(
            CameraOptions.Builder().center(Point.fromLngLat(lon, lat)).zoom(zoom).padding(visiblePadding).build(),
            MapAnimationOptions.mapAnimationOptions { duration(1_200) },
        )
    }

    fun centreOnUser(location: com.ssethhyy.journey.data.location.LatLng) {
        followingUser = true
        flyTo(location.latitude, location.longitude)
    }

    val locating by vm.locating.collectAsStateWithLifecycle()
    // A planned journey opened full size from its page: drawn over the map with a panel of its legs.
    val journeyRequest by container.journeyMap.collectAsStateWithLifecycle()
    val journey = remember(journeyRequest) { journeyRequest?.let { container.journeyRepository.journey(it.journeyId) } }
    var selectedLeg by remember(journeyRequest) { mutableStateOf<Int?>(null) }
    var journeyPanelHeightPx by remember { mutableIntStateOf(0) }
    val openLegStop = com.ssethhyy.journey.ui.components.rememberLegStopOpener()
    LaunchedEffect(journeyRequest, journey) {
        // An old journey the planner no longer has can't be drawn.
        if (journeyRequest != null && journey == null) container.closeJourneyMap()
        if (journey != null) hasCenteredOnUser = true
    }
    androidx.activity.compose.BackHandler(enabled = isActive && journeyRequest != null) {
        val request = journeyRequest ?: return@BackHandler
        container.closeJourneyMap()
        navigator.openJourney(request.journeyId, request.fromName, request.toName)
    }
    val rouletteVm = appViewModel(key = "stop_roulette") { StopRouletteViewModel(it) }
    val roulette by rouletteVm.state.collectAsStateWithLifecycle()
    // Opening a journey clears whatever was on the map before (a stop preview, a dice roll) so its panel shows.
    LaunchedEffect(journeyRequest) {
        if (journeyRequest != null) {
            vm.select(null)
            rouletteVm.dismiss()
        }
    }
    val rollStop = {
        rouletteVm.roll { stop ->
            vm.focusStop(stop)
            flyTo(stop.latitude, stop.longitude, 14.5)
        }
    }
    // The Stop roulette shortcut on For You.
    val rouletteRequest by container.rouletteRequest.collectAsStateWithLifecycle()
    LaunchedEffect(rouletteRequest, isActive) {
        if (rouletteRequest != null && isActive) {
            container.clearRouletteRequest()
            rollStop()
        }
    }
    // Permission first, then the system Location switch, then find the user and the stops around them.
    val enableLocation = com.ssethhyy.journey.ui.components.rememberLocationEnabler(
        onUnavailable = {
            locationEnabled = vm.hasLocationPermission()
            nearbyVm.load(force = true)
        },
    ) {
        locationEnabled = true
        vm.locateUser(
            onFailed = { android.widget.Toast.makeText(context, "Couldn't find your location. Try again outside or near a window.", android.widget.Toast.LENGTH_LONG).show() },
        ) { centreOnUser(it) }
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
            vm.locateUser { centreOnUser(it) }
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
    val nearbyVisible = showNearby && state.selected == null && journey == null
    val journeyPanelVisible = journey != null && state.selected == null
    val previewClearance = when {
        state.selected != null -> with(density) { previewHeightPx.toDp() } + 4.dp
        journeyPanelVisible -> with(density) { journeyPanelHeightPx.toDp() } + 4.dp
        nearbyVisible -> with(density) { nearbyHeightPx.toDp() } + 4.dp
        else -> 0.dp
    }
    // The row of map control pills sits just above whichever panel is showing.
    val controlsVisible = roulette is RouletteState.Idle || state.selected == null
    val controlsClearance = if (controlsVisible) ControlRowHeight + 8.dp else 0.dp

    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val bottomCoveredPx = navBottomPx + with(density) { (ToolbarClearance + previewClearance + controlsClearance).toPx() }
    val topCoveredPx = statusTopPx + with(density) { 72.dp.toPx() }
    LaunchedEffect(bottomCoveredPx, topCoveredPx) {
        visiblePadding = EdgeInsets(topCoveredPx.toDouble(), 0.0, bottomCoveredPx.toDouble(), 0.0)
        // Wait for the panel to stop moving (each new height restarts this) so dragging it doesn't nudge the camera
        // every frame.
        kotlinx.coroutines.delay(160)
        // Opening, expanding or closing the Nearby panel keeps you in the middle of the uncovered map.
        val here = state.userLocation
        if (followingUser && here != null && journey == null && state.selected == null) {
            viewportState.easeTo(
                CameraOptions.Builder().center(Point.fromLngLat(here.longitude, here.latitude)).padding(visiblePadding).build(),
                MapAnimationOptions.mapAnimationOptions { duration(450) },
            )
        }
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
            val bottomOrnamentPadding = ToolbarClearance + previewClearance + controlsClearance
            // The middle of the part of the map you can see, so the zoom effect leans in on it.
            val pivotY = with(density) {
                val full = heightDp.dp.toPx()
                ((topCoveredPx + (full - bottomCoveredPx)) / 2f / full).coerceIn(0f, 1f)
            }
            MapboxMap(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // A subtle zoom while the Nearby panel moves; none once it settles.
                        val scale = if (nearbyVisible) nearbyZoomScale(nearbyProgress) else 1f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, pivotY)
                    },
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
                    MapboxStandardStyle {
                        lightPreset = preset
                        // Flat map: 3D buildings and satellite imagery use up far more of the Mapbox free tier.
                        show3dObjects = BooleanValue(false)
                        // Our own stop markers replace Mapbox's transit POIs.
                        showTransitLabels = BooleanValue(false)
                    }
                },
            ) {
                journey?.let { j ->
                    com.ssethhyy.journey.ui.components.JourneyRouteLayers(
                        j,
                        darkMap = state.settings.mapFollowsTheme && darkTheme,
                        highlight = selectedLeg?.let(j.legs::getOrNull),
                    )
                }
                MapEffect(state.settings.searchRegion) { mapView ->
                    // The map stays within the chosen area (Victoria by default), so you can't wander overseas.
                    val region = state.settings.searchRegion
                    mapView.mapboxMap.setBounds(
                        com.mapbox.maps.CameraBoundsOptions.Builder()
                            .bounds(
                                com.mapbox.maps.CoordinateBounds(
                                    Point.fromLngLat(region.west, region.south),
                                    Point.fromLngLat(region.east, region.north),
                                ),
                            )
                            .minZoom(if (region == com.ssethhyy.journey.data.planner.SearchRegion.VICTORIA) 5.5 else 3.0)
                            .build(),
                    )
                }
                MapEffect(Unit) { mapView ->
                    // Dragging the map yourself stops it following you when panels change size.
                    mapView.gestures.addOnMoveListener(object : com.mapbox.maps.plugin.gestures.OnMoveListener {
                        override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                            followingUser = false
                        }
                        override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector) = false
                        override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) = Unit
                    })
                }
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
                            // Bigger networks on top: V/Line, then trains, trams and buses.
                            .withCircleSortKey(if (isSelected) 10.0 else 5.0 - MapViewModel.minZoom(stop.mode) / 3.0)
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
            StopRouletteCard(
                state = roulette,
                onPlan = { stop -> navigator.planTo(com.ssethhyy.journey.data.planner.PlanLocation(stop.name, stop.latitude, stop.longitude, stop.suburb)) },
                onOpenStop = navigator::openStop,
                onRollAgain = rollStop,
                onClose = rouletteVm::dismiss,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            when {
                state.error != null -> StatusPill(
                    icon = { Icon(Sym.Refresh, null, Modifier.size(18.dp)) },
                    text = state.error?.message ?: "Couldn't load stops",
                    onClick = vm::retry,
                )
                state.loading -> StatusPill(icon = { LoadingIndicator(Modifier.size(20.dp)) }, text = "Loading stops")
            }
        }

        // Right-side map controls. With a roll result at the top and a stop preview at the bottom there is no room
        // for them in between, and the roulette card has its own actions, so they step aside.
        // Map controls as a row of pills resting on top of the panel below. If they don't all fit, the row scrolls
        // sideways and the rest are hidden at the edge.
        if (controlsVisible) BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = ToolbarClearance + previewClearance + 4.dp),
        ) {
            val rowWidth = maxWidth
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = rowWidth)
                    .height(ControlRowHeight)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!showNearby) {
                    MapControlPill(
                        icon = Sym.NearMe,
                        label = "Nearby",
                        onClick = {
                            showNearby = true
                            nearbyExpanded = true
                        },
                    )
                }
                if (state.settings.gameEnabled) {
                    MapControlPill(
                        icon = Sym.Casino,
                        label = "Roll a stop",
                        loading = roulette is RouletteState.Rolling,
                        onClick = rollStop,
                    )
                }
                MapControlPill(
                    icon = Sym.MyLocation,
                    label = "My location",
                    loading = locating,
                    onClick = enableLocation,
                )
            }
        }

        // Fit the whole journey, or the chosen leg, into the part of the map the panels leave visible.
        val statusTop = WindowInsets.statusBars.getTop(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        LaunchedEffect(journey, selectedLeg, journeyPanelHeightPx, isActive) {
            val j = journey ?: return@LaunchedEffect
            if (!isActive) return@LaunchedEffect
            val drawable = com.ssethhyy.journey.ui.components.JourneyGeometry.drawable(j)
            val points = selectedLeg?.let { j.legs.getOrNull(it) }?.let(com.ssethhyy.journey.ui.components.JourneyGeometry::points)?.takeIf { it.size > 1 }
                ?: com.ssethhyy.journey.ui.components.JourneyGeometry.allPoints(drawable)
            if (points.size < 2) return@LaunchedEffect
            val px = { dp: androidx.compose.ui.unit.Dp -> with(density) { dp.toPx() }.toDouble() }
            // Never let the panels leave less than a third of the screen for the route, or Mapbox can't fit it at all.
            val screenHeight = with(density) { heightDp.dp.toPx() }.toDouble()
            val top = statusTop + px(84.dp)
            val bottom = (navBottom + px(ToolbarClearance) + journeyPanelHeightPx + px(16.dp)).coerceAtMost(screenHeight * 0.66 - top)
            val padding = EdgeInsets(top, px(40.dp), bottom, px(40.dp))
            val camera = viewportState.cameraForCoordinates(points, CameraOptions.Builder().build(), padding, 17.0, null)
            viewportState.flyTo(camera, MapAnimationOptions.mapAnimationOptions { duration(900) })
        }

        // A planned journey's legs, while exploring it on the map (the stop preview takes over when a stop is tapped).
        AnimatedVisibility(
            visible = journeyPanelVisible,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = ToolbarClearance - 8.dp)
                .onSizeChanged { journeyPanelHeightPx = it.height },
        ) {
            val request = journeyRequest
            journey?.let { j ->
                JourneyMapPanel(
                    journey = j,
                    destination = request?.toName.orEmpty(),
                    selected = selectedLeg,
                    onSelect = { selectedLeg = it },
                    onOpenStop = openLegStop,
                    onClose = container::closeJourneyMap,
                )
            }
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
                onExpandedChange = { nearbyExpanded = it },
                onOpenAll = navigator::openNearby,
                onProgress = { nearbyProgress = it },
                onClose = { showNearby = false },
                onSelect = { stop ->
                    nearbyExpanded = false
                    vm.focusStop(stop)
                    flyTo(stop.latitude, stop.longitude, 16.5)
                },
                onAllowLocation = enableLocation,
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
                    onPlan = { navigator.planTo(com.ssethhyy.journey.data.planner.PlanLocation(stop.name, stop.latitude, stop.longitude, stop.suburb)) },
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
    onPlan: () -> Unit,
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
                HapticIconButton(Sym.Directions, "Plan a trip to ${stop.name}", onClick = onPlan)
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
            message = "Add a public Mapbox token (starts with pk.) in Settings → API access, or set MAPBOX_ACCESS_TOKEN in .env. Search still works without it.",
        )
    }
}

/** Height of the row of map control pills. */
private val ControlRowHeight = 48.dp
