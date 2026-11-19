/*
 * Feature Module: Map
 * File: MapScreen.kt
 * Description: Full-screen, edge-to-edge Mapbox map showing stops for every mode with
 *              a floating stop preview, a journey panel, and a trip-stops mode.
 *
 * Overlays
 * --------
 * At most one context overlay is active:
 *   NEARBY  – the floating nearby-stops drawer (default)
 *   JOURNEY – a planned journey's legs
 *   TRIP    – a single run's stop dots (+ vehicle when 3D is on)
 * A tapped stop preview takes over from all of them.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
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
import com.mapbox.maps.plugin.gestures.gestures
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
import com.ssethhyy.journey.ui.components.JourneyGeometry
import com.ssethhyy.journey.ui.components.JourneyRouteLayers
import com.ssethhyy.journey.ui.components.MapControlPill
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.TrackingBanner
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.home.ToolbarClearance
import com.ssethhyy.journey.ui.screens.nearby.NearbyViewModel
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.ui.theme.isAppInDarkTheme
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import java.time.Instant

private val MELBOURNE = Point.fromLngLat(144.9631, -37.8136)

/** Which context overlay the map is showing. At most one is active. */
private enum class MapOverlay { NEARBY, JOURNEY, TRIP }

/** Height of the row of map control pills. */
private val ControlRowHeight = 48.dp

/** Enough stops for the Nearby panel at full screen (everything within the search radius in the city). */
const val NEARBY_MAX_STOPS = 100

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

    // ---- External requests -----------------------------------------------------------
    val journeyRequest by container.journeyMap.collectAsStateWithLifecycle()
    val journey = remember(journeyRequest) {
        journeyRequest?.let { container.journeyRepository.journey(it.journeyId) }
    }
    val tripRequest by container.tripMap.collectAsStateWithLifecycle()
    val rouletteRequest by container.rouletteRequest.collectAsStateWithLifecycle()

    // ---- Which overlay is active? ----------------------------------------------------
    val overlay: MapOverlay? = when {
        state.selected != null -> null
        tripRequest != null -> MapOverlay.TRIP
        journey != null -> MapOverlay.JOURNEY
        else -> MapOverlay.NEARBY
    }
    val tripMode = overlay == MapOverlay.TRIP
    val journeyMode = overlay == MapOverlay.JOURNEY

    // ---- Nearby ----------------------------------------------------------------------
    val nearbyVm = appViewModel(key = "map_nearby") { NearbyViewModel(it, maxResults = NEARBY_MAX_STOPS) }
    val nearbyState by nearbyVm.state.collectAsStateWithLifecycle()
    var showNearby by rememberSaveable { mutableStateOf(true) }
    var nearbyExpanded by rememberSaveable { mutableStateOf(true) }
    var nearbyLoaded by rememberSaveable { mutableStateOf(false) }
    var nearbyProgress by remember { mutableFloatStateOf(if (nearbyExpanded) 1f else 0f) }
    var nearbyFull by rememberSaveable { mutableStateOf(false) }
    var nearbyFullProgress by remember { mutableFloatStateOf(0f) }
    var nearbyHeightPx by remember { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler(enabled = isActive && nearbyFull) { nearbyFull = false }
    val nearbyVisible = overlay == MapOverlay.NEARBY && showNearby

    // ---- Location --------------------------------------------------------------------
    var locationEnabled by remember { mutableStateOf(vm.hasLocationPermission()) }
    val locating by vm.locating.collectAsStateWithLifecycle()
    var hasCenteredOnUser by rememberSaveable { mutableStateOf(false) }
    var followingUser by remember { mutableStateOf(false) }

    // ---- Layout measurements ---------------------------------------------------------
    val density = LocalDensity.current
    var previewHeightPx by remember { mutableIntStateOf(0) }
    var journeyPanelHeightPx by remember { mutableIntStateOf(0) }
    var visiblePadding by remember { mutableStateOf(EdgeInsets(0.0, 0.0, 0.0, 0.0)) }

    val journeyPanelVisible = journeyMode
    val previewClearance: androidx.compose.ui.unit.Dp = when {
        state.selected != null -> with(density) { previewHeightPx.toDp() } + 4.dp
        journeyPanelVisible -> with(density) { journeyPanelHeightPx.toDp() } + 4.dp
        nearbyVisible -> with(density) { nearbyHeightPx.toDp() } + 4.dp
        else -> 0.dp
    }

    // ---- Roulette --------------------------------------------------------------------
    val rouletteVm = appViewModel(key = "stop_roulette") { StopRouletteViewModel(it) }
    val roulette by rouletteVm.state.collectAsStateWithLifecycle()
    val controlsVisible = (roulette is RouletteState.Idle || state.selected == null) &&
        !(nearbyVisible && nearbyFullProgress > 0.6f)
    val controlsClearance: androidx.compose.ui.unit.Dp = if (controlsVisible) ControlRowHeight + 8.dp else 0.dp

    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val bottomCoveredPx = navBottomPx + with(density) {
        (ToolbarClearance + previewClearance + controlsClearance).toPx()
    }
    val topCoveredPx = statusTopPx + with(density) { 72.dp.toPx() }

    // ---- Camera helpers --------------------------------------------------------------
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

    val rollStop = {
        rouletteVm.roll { stop ->
            vm.focusStop(stop)
            flyTo(stop.latitude, stop.longitude, 14.5)
        }
    }

    // ---- Effects ---------------------------------------------------------------------

    // Opening a journey or a trip clears whatever was on the map so its panel shows.
    LaunchedEffect(journeyRequest, tripRequest) {
        if (journeyRequest != null || tripRequest != null) {
            vm.select(null)
            rouletteVm.dismiss()
        }
    }

    // An old journey the planner no longer has can't be drawn.
    LaunchedEffect(journeyRequest, journey) {
        if (journeyRequest != null && journey == null) container.closeJourneyMap()
        if (journey != null) hasCenteredOnUser = true
    }

    // Leaving the tab clears any active trip overlay.
    LaunchedEffect(isActive) {
        if (!isActive && tripRequest != null) container.closeTripMap()
    }

    androidx.activity.compose.BackHandler(enabled = isActive && journeyRequest != null) {
        val request = journeyRequest ?: return@BackHandler
        container.closeJourneyMap()
        navigator.openJourney(request.journeyId, request.fromName, request.toName)
    }
    androidx.activity.compose.BackHandler(enabled = isActive && tripRequest != null) {
        container.closeTripMap()
    }

    // Permission → system switch → find the user and the stops around them.
    val enableLocation = com.ssethhyy.journey.ui.components.rememberLocationEnabler(
        onUnavailable = {
            locationEnabled = vm.hasLocationPermission()
            nearbyVm.load(force = true)
        },
    ) {
        locationEnabled = true
        vm.locateUser(
            onFailed = {
                android.widget.Toast.makeText(
                    context,
                    "Couldn't find your location. Try again outside or near a window.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            },
        ) { centreOnUser(it) }
        nearbyVm.load(force = true)
    }

    // First time the tab is shown: load nearby, and centre on the user if permitted.
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

    // The Stop roulette shortcut on For You.
    LaunchedEffect(rouletteRequest, isActive) {
        if (rouletteRequest != null && isActive) {
            container.clearRouletteRequest()
            rollStop()
        }
    }

    // Keep visible padding up to date as panels open, close or resize.
    LaunchedEffect(bottomCoveredPx, topCoveredPx) {
        if (nearbyVisible && nearbyFullProgress > 0f) return@LaunchedEffect
        visiblePadding = EdgeInsets(topCoveredPx.toDouble(), 0.0, bottomCoveredPx.toDouble(), 0.0)
        kotlinx.coroutines.delay(160)
        val here = state.userLocation
        if (followingUser && here != null && journey == null && state.selected == null && tripRequest == null) {
            viewportState.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(here.longitude, here.latitude))
                    .padding(visiblePadding)
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(450) },
            )
        }
    }

    // ---- Layout ----------------------------------------------------------------------

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value

        // Load stops whenever the camera settles. Suppressed in trip mode: we don't want
        // the whole city's stop dots loading behind a single run.
        LaunchedEffect(viewportState, widthDp, heightDp, overlay) {
            if (overlay == MapOverlay.TRIP) return@LaunchedEffect
            snapshotFlow { viewportState.cameraState }
                .filterNotNull()
                .debounce(450)
                .collect { camera ->
                    vm.onViewportIdle(
                        Viewport(camera.center.latitude(), camera.center.longitude(), camera.zoom, widthDp, heightDp),
                    )
                }
        }

        if (hasValidToken) {
            val bottomOrnamentPadding = ToolbarClearance + previewClearance + controlsClearance
            val pivotY = with(density) {
                val full = heightDp.dp.toPx()
                ((topCoveredPx + (full - bottomCoveredPx)) / 2f / full).coerceIn(0f, 1f)
            }

            MapboxMap(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = if (nearbyVisible) nearbyZoomScale(nearbyProgress) else 1f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, pivotY)
                    },
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
                        show3dObjects = BooleanValue(false)
                        showTransitLabels = BooleanValue(false)
                    }
                },
            ) {
                // ---- Journey route lines (only when a journey is active) -------------
                if (overlay == MapOverlay.JOURNEY) {
                    journey?.let { j ->
                        JourneyRouteLayers(
                            j,
                            darkMap = state.settings.mapFollowsTheme && darkTheme,
                            highlight = null,
                        )
                    }
                }

                // ---- Trip mode: stops (+ optional vehicle), nothing else -------------
                val currentTrip = tripRequest
                if (overlay == MapOverlay.TRIP && currentTrip != null) {
                    TripMapLayer(
                        runRef = currentTrip.runRef,
                        mode = currentTrip.mode,
                        directionId = currentTrip.directionId,
                    )
                    if (state.settings.mapRender3dVehicles) {
                        VehicleModelLayer()
                    }
                }

                // ---- Search region bounds --------------------------------------------
                MapEffect(state.settings.searchRegion) { mapView ->
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

                // ---- Dragging the map stops auto-follow ------------------------------
                MapEffect(Unit) { mapView ->
                    mapView.gestures.addOnMoveListener(object : com.mapbox.maps.plugin.gestures.OnMoveListener {
                        override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                            followingUser = false
                        }
                        override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector) = false
                        override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) = Unit
                    })
                }

                // ---- Location puck ---------------------------------------------------
                MapEffect(locationEnabled) { mapView ->
                    mapView.location.updateSettings {
                        enabled = locationEnabled
                        locationPuck = createDefault2DPuck(withBearing = true)
                        puckBearingEnabled = true
                        puckBearing = PuckBearing.HEADING
                        pulsingEnabled = true
                    }
                }

                // ---- Stop dots (never in trip mode) ---------------------------------
                if (overlay != MapOverlay.TRIP) {
                    val selectedKey = state.selected?.key
                    val annotations = remember(state.stops, selectedKey, journey) {
                        val visible = if (journey != null) {
                            state.stops.filter { it.key == selectedKey }
                        } else {
                            state.stops
                        }
                        visible.map { stop ->
                            val isSelected = stop.key == selectedKey
                            CircleAnnotationOptions()
                                .withPoint(Point.fromLngLat(stop.longitude, stop.latitude))
                                .withCircleColor(stop.mode.color.toArgb())
                                .withCircleRadius(if (isSelected) 11.0 else 6.5)
                                .withCircleStrokeColor(Color.White.toArgb())
                                .withCircleStrokeWidth(if (isSelected) 3.5 else 1.75)
                                .withCircleSortKey(if (isSelected) 10.0 else 5.0 - MapViewModel.minZoom(stop.mode) / 3.0)
                                .withData(JsonPrimitive(stop.key))
                        }
                    }
                    val groupState = remember {
                        CircleAnnotationGroupState().apply { circleEmissiveStrength = 1.0 }
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
            }
        } else {
            MissingTokenCard()
        }

        // ---- Top overlay: mode filters, banner, roulette, status ---------------------
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
                onPlan = { stop ->
                    navigator.planTo(
                        com.ssethhyy.journey.data.planner.PlanLocation(
                            stop.name, stop.latitude, stop.longitude, stop.suburb,
                        ),
                    )
                },
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

        // ---- Map control pills -------------------------------------------------------
        if (controlsVisible) {
            BoxWithConstraints(
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = if (nearbyVisible) (1f - nearbyFullProgress / 0.6f).coerceIn(0f, 1f) else 1f
                    }
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
        }

        // ---- Fit the journey into the uncovered part of the map ----------------------
        val statusTop = WindowInsets.statusBars.getTop(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        LaunchedEffect(journey, journeyPanelHeightPx, isActive) {
            val j = journey ?: return@LaunchedEffect
            if (!isActive) return@LaunchedEffect
            val drawable = JourneyGeometry.drawable(j)
            val points = JourneyGeometry.allPoints(drawable)
            if (points.size < 2) return@LaunchedEffect
            val px = { dp: androidx.compose.ui.unit.Dp -> with(density) { dp.toPx() }.toDouble() }
            val screenHeight = with(density) { heightDp.dp.toPx() }.toDouble()
            val top = statusTop + px(84.dp)
            val bottom = (navBottom + px(ToolbarClearance) + journeyPanelHeightPx + px(ControlRowHeight + 24.dp))
                .coerceAtMost(screenHeight * 0.66 - top)
            val padding = EdgeInsets(top, px(40.dp), bottom, px(40.dp))
            val camera = viewportState.cameraForCoordinates(points, CameraOptions.Builder().build(), padding, 17.0, null)
            viewportState.flyTo(camera, MapAnimationOptions.mapAnimationOptions { duration(900) })
        }

        // ---- Journey panel -----------------------------------------------------------
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
                    selected = null,
                    onSelect = {},
                    onOpenStop = com.ssethhyy.journey.ui.components.rememberLegStopOpener(),
                    onClose = container::closeJourneyMap,
                )
            }
        }

        // ---- Trip panel (placeholder; expand with stop list next) --------------------
        AnimatedVisibility(
            visible = tripMode,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = ToolbarClearance - 8.dp),
        ) {
            val request = tripRequest
            if (request != null) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "To ${request.title}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Showing this run's stops",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HapticIconButton(
                            icon = Sym.Close,
                            contentDescription = "Close trip",
                            onClick = { container.closeTripMap() },
                        )
                    }
                }
            }
        }

        // ---- Nearby panel ------------------------------------------------------------
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
                full = nearbyFull,
                now = now,
                onExpandedChange = { nearbyExpanded = it },
                onFullChange = { nearbyFull = it },
                onProgress = { nearbyProgress = it },
                onFullProgress = { nearbyFullProgress = it },
                onVisibleStops = { nearbyVm.loadBoards(it) },
                maxHeight = heightDp.dp - with(density) { (statusTopPx + navBottomPx).toDp() } - ToolbarClearance,
                onClose = {
                    nearbyFull = false
                    showNearby = false
                },
                onSelect = { stop ->
                    nearbyFull = false
                    nearbyExpanded = false
                    vm.focusStop(stop)
                    flyTo(stop.latitude, stop.longitude, 16.5)
                },
                onAllowLocation = enableLocation,
            )
        }

        // ---- Stop preview ------------------------------------------------------------
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
                    onOpenTrip = { dep ->
                        dep.runRef?.let { navigator.openTrip(it, stop.mode, stop.id, dep.headsign) }
                    },
                    onPlan = {
                        navigator.planTo(
                            com.ssethhyy.journey.data.planner.PlanLocation(
                                stop.name, stop.latitude, stop.longitude, stop.suburb,
                            ),
                        )
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small composables
// ---------------------------------------------------------------------------------------------

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
                    .fillMaxWidth(),
            ) {
                when (board) {
                    null, LoadState.Loading -> DepartureSkeleton(rows = 2)
                    is LoadState.Error -> InlineError(board.error.message ?: "Couldn't load departures", onRetry)
                    is LoadState.Success -> {
                        val upcoming = board.data.departures
                            .filter { it.bestTime?.isAfter(now.minusSeconds(60)) == true }
                            .take(3)
                        if (upcoming.isEmpty()) {
                            Text(
                                "No upcoming departures",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        upcoming.forEachIndexed { i, dep ->
                            if (i > 0) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            DepartureRow(
                                dep,
                                now,
                                timeDisplay,
                                hasDisruption = dep.disruptionIds.isNotEmpty(),
                                onClick = { onOpenTrip(dep) },
                            )
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
