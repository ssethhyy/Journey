/*
 * Feature Module: Favourites
 * File: FavouritesScreen.kt
 * Description: The For You tab: a column of widgets (greeting and weather, places, favourites, disruptions,
 * commutes, nearby stops and more) that the user orders, adds, removes and sets up in edit mode.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import com.ssethhyy.journey.ui.components.FloatingPullToRefreshBox
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.data.foryou.DateStyle
import com.ssethhyy.journey.data.foryou.ForYouWidget
import com.ssethhyy.journey.data.foryou.Shortcut
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.PlaceIcon
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.ui.components.PlaceEditorSheet
import com.ssethhyy.journey.ui.components.PlacesRow
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.DepartureRow
import com.ssethhyy.journey.ui.components.DepartureSkeleton
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.InlineError
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.HomeTab
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.home.ToolbarClearance
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(onAddFromNearby: () -> Unit, onAddFromSearch: () -> Unit) {
    val vm = appViewModel { FavouritesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val container = LocalContext.current.appContainer
    val now by rememberNow()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val layout = state.layout
    val headerGone by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > with(density) { 72.dp.roundToPx() }
        }
    }
    var renaming by remember { mutableStateOf<FavouriteStop?>(null) }
    var askingName by rememberSaveable { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<Pair<PlaceIcon, String>?>(null) }
    var editingExistingPlace by remember { mutableStateOf<SavedPlace?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var settingsFor by rememberSaveable { mutableStateOf<ForYouWidget?>(null) }
    var addingWidget by rememberSaveable { mutableStateOf(false) }
    // With the Greeting first, its header is pinned above the list and is the page's top bar: it shrinks to a slim
    // bar as you scroll down and grows back as you pull down at the top, following your finger, then settles to
    // whichever is closer (or the way you flicked) when you let go. One value drives all of it.
    val pinnedHeader = layout.widgets.firstOrNull() == ForYouWidget.GREETING
    // Bounded to 0..1: a settling spring stops at the ends instead of overshooting past them.
    val header = remember { androidx.compose.animation.core.Animatable(1f).apply { updateBounds(0f, 1f) } }
    // How far the list scrolls to fully collapse it: the big header's height less the slim bar's.
    var headerRangePx by remember { mutableFloatStateOf(with(density) { 180.dp.toPx() }) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var fingerOnList by remember { mutableStateOf(false) }
    fun settleHeader(target: Float, velocity: Float = 0f) {
        settleJob?.cancel()
        settleJob = scope.launch {
            header.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f), initialVelocity = velocity / headerRangePx)
        }
    }
    val headerScroll = remember(pinnedHeader) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            fun follow(dy: Float): Float {
                val before = header.value
                val after = (before + dy / headerRangePx).coerceIn(0f, 1f)
                if (after == before) return 0f
                settleJob?.cancel()
                fingerOnList = true
                scope.launch { header.snapTo(after) }
                return (after - before) * headerRangePx
            }

            // Scrolling down: the header shrinks first, then the list moves.
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (!pinnedHeader || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || available.y >= 0f) return androidx.compose.ui.geometry.Offset.Zero
                return androidx.compose.ui.geometry.Offset(0f, follow(available.y))
            }

            // Pulling down at the top of the list: the header grows back.
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (!pinnedHeader || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
                return androidx.compose.ui.geometry.Offset(0f, follow(available.y))
            }

            // Let go: settle to the nearer state, or the way you flicked.
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (!pinnedHeader || !fingerOnList) return androidx.compose.ui.unit.Velocity.Zero
                fingerOnList = false
                val v = available.y
                val target = when {
                    v < -600f -> 0f
                    v > 600f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 -> 1f
                    else -> if (header.value > 0.5f) 1f else 0f
                }
                if (header.value != target) settleHeader(target, v)
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    // It opens big each time For You is shown, then collapses after a few seconds if set to.
    LaunchedEffect(header.targetValue, layout.greeting.collapses, layout.greeting.collapseAfterSeconds, editing, pinnedHeader) {
        if (pinnedHeader && header.targetValue == 1f && layout.greeting.collapses && !editing) {
            delay(layout.greeting.collapseAfterSeconds * 1000L)
            if (!fingerOnList && header.value == 1f) settleHeader(0f)
        }
    }
    // A greeting further down the page stays a normal widget you can tap to collapse.
    var inlineHeaderExpanded by rememberSaveable { mutableStateOf(true) }
    val inlineHeader by androidx.compose.animation.core.animateFloatAsState(
        if (inlineHeaderExpanded) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "inline_header",
    )
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }
    val editRequest by container.forYouEditRequest.collectAsStateWithLifecycle()
    LaunchedEffect(editRequest) {
        if (editRequest) {
            container.clearForYouEditRequest()
            editing = true
        }
    }

    // Shared with the Map tab's Nearby panel (same key), so both show the same stops.
    val nearbyVm = appViewModel(key = "map_nearby") { com.ssethhyy.journey.ui.screens.nearby.NearbyViewModel(it, maxResults = com.ssethhyy.journey.ui.screens.map.NEARBY_MAX_STOPS) }
    val nearby by nearbyVm.state.collectAsStateWithLifecycle()
    val showNearby = state.isLoaded && ForYouWidget.NEAR_YOU in layout.widgets
    LaunchedEffect(showNearby) {
        if (showNearby && nearby.stops !is LoadState.Success) nearbyVm.load()
    }
    val enableLocation = com.ssethhyy.journey.ui.components.rememberLocationEnabler(onUnavailable = { nearbyVm.load(force = true) }) {
        nearbyVm.load(force = true)
    }
    val commutesVm = appViewModel(key = "commutes") { CommutesViewModel(it) }
    val commutes by commutesVm.state.collectAsStateWithLifecycle()
    val showCommutes = ForYouWidget.COMMUTES in layout.widgets
    // null = closed, Pair(null, _) = new commute, Pair(commute, _) = editing
    var commuteEditor by remember { mutableStateOf<Pair<com.ssethhyy.journey.data.commute.Commute?, Boolean>?>(null) }
    val commuteActions = remember(commutesVm) {
        CommuteActions(
            onPlan = { c -> navigator.planTo(c.to.toPlan(), c.from.toPlan().takeIf { !c.from.isCurrent }) },
            onOpen = { c, journey -> navigator.openJourney(journey.id, if (c.from.isCurrent) "Your location" else c.from.name, c.to.name) },
            onAdd = { commuteEditor = null to true },
            onEdit = { c -> commuteEditor = c to true },
            onRemove = { c -> commutesVm.remove(c) },
        )
    }

    AutoRefreshEffect(enabled = state.settings.autoRefresh && state.favourites.isNotEmpty()) { vm.refresh(force = true) }
    AutoRefreshEffect(enabled = showCommutes && commutes.cards.isNotEmpty(), intervalMs = 60_000) { commutesVm.refresh() }
    AutoRefreshEffect(enabled = showNearby && state.settings.autoRefresh) {
        nearbyVm.loadBoards(nearby.stops.dataOrNull.orEmpty().take(state.settings.forYouNearbyCount), force = true)
    }
    // The weather changes slowly; a reading every 15 minutes is plenty.
    AutoRefreshEffect(enabled = state.weather != null, intervalMs = 15 * 60_000L) { vm.loadWeather(force = false) }

    val onRemove: (FavouriteStop) -> Unit = { fav ->
        vm.remove(fav.key)
        scope.launch {
            val result = snackbarHostState.showSnackbar("Removed ${fav.displayName}", actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) vm.restore(fav)
        }
    }
    val removeWidget: (ForYouWidget) -> Unit = { widget ->
        val index = layout.widgets.indexOf(widget)
        vm.updateLayout { it.remove(widget) }
        settingsFor = null
        scope.launch {
            val result = snackbarHostState.showSnackbar("Removed ${widget.title}", actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) vm.updateLayout { it.add(widget, index) }
        }
    }
    val toggle: (ForYouWidget) -> Unit = { widget -> vm.updateLayout { it.toggleCollapsed(widget) } }
    val onShortcut: (Shortcut) -> Unit = { shortcut ->
        when (shortcut) {
            Shortcut.PLAN -> navigator.goToTab(HomeTab.PLAN)
            Shortcut.MAP -> navigator.goToTab(HomeTab.MAP)
            Shortcut.DISRUPTIONS -> navigator.openDisruptions()
            Shortcut.SEARCH -> navigator.openSearch()
            Shortcut.ROULETTE -> {
                container.requestRoulette()
                navigator.goToTab(HomeTab.MAP)
            }
        }
    }

    Scaffold(
        topBar = {
            // The big greeting is the first list item; once it has scrolled away this pill takes over.
            ReusableTopAppBar(
                isTabRoot = true,
                title = "For You",
                subtitle = TimeFormatter.shortToday(now),
                visible = (headerGone || !state.isLoaded) && !editing && !pinnedHeader,
                actions = { HapticIconButton(Sym.Edit, "Edit For You", onClick = { editing = true }) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = ToolbarClearance - 24.dp)) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        if (editing && state.isLoaded) {
            ForYouEditor(
                layout = layout,
                summary = { widgetSummary(it, state, commutes) },
                topPadding = innerPadding.calculateTopPadding(),
                bottomPadding = ToolbarClearance + innerPadding.calculateBottomPadding(),
                onReorder = { order ->
                    // Applied to the layout as it is now: widgets added or removed meanwhile are kept or left out,
                    // never lost or doubled by an out-of-date order.
                    vm.updateLayout { current ->
                        current.copy(widgets = order.filter { it in current.widgets }.distinct() + current.widgets.filter { it !in order })
                    }
                },
                onRemove = removeWidget,
                onSettings = { settingsFor = it },
                onAdd = { addingWidget = true },
                onShowTitles = { on -> vm.updateLayout { it.copy(showTitles = on) } },
                onReset = { vm.resetLayout() },
                onDone = { editing = false },
            )
        } else {
            FloatingPullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = {
                    vm.refresh(force = true, userInitiated = true)
                    if (showCommutes) commutesVm.refresh()
                    if (showNearby) nearbyVm.load(force = true)
                },
                topInset = innerPadding.calculateTopPadding(),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.isLoaded -> LoadingState()
                    else -> Column(Modifier.fillMaxSize()) {
                    if (pinnedHeader) {
                        val g = layout.greeting
                        val collapsedPx = with(density) { 48.dp.toPx() }
                        ForYouHeader(
                            greeting = if (g.showGreeting) ForYouDates.greeting(state.settings.userName, now) else null,
                            date = if (g.showDate) ForYouDates.format(g.dateStyle, now) else null,
                            weather = if (g.showWeather) state.weather ?: LoadState.Loading else null,
                            unit = g.unit,
                            showDetails = g.showWeatherDetails,
                            progress = header.value,
                            onToggle = { settleHeader(if (header.targetValue > 0.5f) 0f else 1f) },
                            onEdit = { editing = true },
                            onSetUpWeather = {
                                editing = true
                                settingsFor = ForYouWidget.GREETING
                            },
                            onRetryWeather = { vm.loadWeather(force = true) },
                            barTime = if (g.barTime) TimeFormatter.clock(now) else null,
                            barDate = if (g.barDate) ForYouDates.format(DateStyle.NUMERIC, now) else null,
                            barWeather = g.barWeather,
                            temperatureSp = g.weatherSize.temperatureSp,
                            onAddName = if (state.settings.userName.isBlank()) {
                                { askingName = true }
                            } else null,
                            modifier = Modifier
                                .padding(top = innerPadding.calculateTopPadding() + 4.dp, start = 16.dp, end = 16.dp)
                                .onSizeChanged { size ->
                                    // Measured while fully open: that height, less the slim bar, is the scroll it takes.
                                    if (header.value == 1f) headerRangePx = (size.height - collapsedPx).coerceAtLeast(collapsedPx)
                                },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(headerScroll)
                            .then(if (pinnedHeader) Modifier.headerBlend() else Modifier.floatingBarBlur(innerPadding.calculateTopPadding())),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (pinnedHeader) 12.dp else innerPadding.calculateTopPadding() + 4.dp,
                            bottom = ToolbarClearance + innerPadding.calculateBottomPadding(),
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The edit button lives in the greeting; without that at the top, a slim row carries it.
                        if (layout.widgets.firstOrNull() != ForYouWidget.GREETING) {
                            item(key = "top_row") { ForYouTopRow(onEdit = { editing = true }, modifier = Modifier.animateItem(placementSpec = null)) }
                        }
                        if (layout.widgets.isEmpty()) {
                            item(key = "nothing") {
                                MessageState(icon = Sym.DashboardCustomize, title = "Nothing on For You", message = "Add the widgets you want to see here.") {
                                    Button(onClick = {
                                        editing = true
                                        addingWidget = true
                                    }) {
                                        Icon(Sym.Add, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add a widget")
                                    }
                                }
                            }
                        }
                        layout.widgets.forEach { widget ->
                            when (widget) {
                                ForYouWidget.GREETING -> if (!pinnedHeader) {
                                    val g = layout.greeting
                                    item(key = "w_greeting") {
                                        ForYouHeader(
                                            greeting = if (g.showGreeting) ForYouDates.greeting(state.settings.userName, now) else null,
                                            date = if (g.showDate) ForYouDates.format(g.dateStyle, now) else null,
                                            weather = if (g.showWeather) state.weather ?: LoadState.Loading else null,
                                            unit = g.unit,
                                            showDetails = g.showWeatherDetails,
                                            progress = inlineHeader,
                                            onToggle = { inlineHeaderExpanded = !inlineHeaderExpanded },
                                            // The top row above already carries the Edit pill.
                                            showEdit = false,
                                            onEdit = { editing = true },
                                            onSetUpWeather = {
                                                editing = true
                                                settingsFor = ForYouWidget.GREETING
                                            },
                                            onRetryWeather = { vm.loadWeather(force = true) },
                                            barTime = if (g.barTime) TimeFormatter.clock(now) else null,
                                            barDate = if (g.barDate) ForYouDates.format(DateStyle.NUMERIC, now) else null,
                                            barWeather = g.barWeather,
                                            temperatureSp = g.weatherSize.temperatureSp,
                                            // No name yet: tapping the greeting asks for one.
                                            onAddName = if (state.settings.userName.isBlank()) {
                                                { askingName = true }
                                            } else null,
                                            modifier = Modifier.animateItem(placementSpec = null),
                                        )
                                    }
                                }
                                ForYouWidget.PLACES -> item(key = "w_places") {
                                    CollapsibleWidget("Places", layout.showTitles, layout.isCollapsed(widget), { toggle(widget) }, Modifier.animateItem(placementSpec = null)) {
                                        PlacesRow(
                                            places = state.places,
                                            onOpen = { navigator.planTo(it.toPlanLocation()) },
                                            onEdit = {
                                                editingExistingPlace = it
                                                editingPlace = null
                                            },
                                            onCreate = { icon, name ->
                                                editingExistingPlace = null
                                                editingPlace = icon to name
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            showAddTile = layout.places.showAddTile,
                                        )
                                    }
                                }
                                ForYouWidget.FAVOURITES -> favouritesWidget(
                                    state = state,
                                    now = now,
                                    collapsed = layout.isCollapsed(widget),
                                    onToggle = { toggle(widget) },
                                    onAdd = onAddFromNearby,
                                    onAddFromSearch = onAddFromSearch,
                                    onToggleFilter = vm::toggleFilter,
                                ) { fav, canMoveUp, canMoveDown, modifier ->
                                    FavouriteStopCard(
                                        favourite = fav,
                                        board = state.boards[fav.key],
                                        now = now,
                                        departuresToShow = state.settings.departuresPerStop,
                                        timeDisplay = state.settings.timeDisplay,
                                        canMoveUp = canMoveUp,
                                        canMoveDown = canMoveDown,
                                        onOpen = { navigator.openStop(fav.toStop()) },
                                        onOpenTrip = { dep ->
                                            dep.runRef?.let { navigator.openTrip(it, fav.mode, fav.stopId, dep.headsign) }
                                        },
                                        onRetry = { vm.refresh(force = true) },
                                        onRename = { renaming = fav },
                                        onMove = { delta -> vm.move(fav.key, delta) },
                                        onRemove = { onRemove(fav) },
                                        modifier = modifier,
                                    )
                                }
                                ForYouWidget.DISRUPTIONS -> {
                                    val list = state.widgetDisruptions
                                    if (list.isNotEmpty() || !layout.disruptions.hideWhenClear) {
                                        item(key = "w_disruptions") {
                                            DisruptionsWidget(
                                                disruptions = list,
                                                level = layout.disruptions.level,
                                                maxShown = layout.disruptions.maxShown,
                                                collapsed = layout.isCollapsed(widget),
                                                onToggle = { toggle(widget) },
                                                onOpenAll = navigator::openDisruptions,
                                                modifier = Modifier.animateItem(placementSpec = null),
                                            )
                                        }
                                    }
                                }
                                ForYouWidget.COMMUTES -> commutesSection(
                                    state = commutes,
                                    now = now,
                                    actions = commuteActions,
                                    config = layout.commutes,
                                    showTitle = layout.showTitles,
                                    collapsed = layout.isCollapsed(widget),
                                    onToggle = { toggle(widget) },
                                )
                                ForYouWidget.NEAR_YOU -> nearbySection(
                                    state = nearby,
                                    count = state.settings.forYouNearbyCount,
                                    now = now,
                                    onOpenStop = navigator::openStop,
                                    onOpenMap = { navigator.goToTab(HomeTab.MAP) },
                                    onEnableLocation = enableLocation,
                                    showTitle = layout.showTitles,
                                    collapsed = layout.isCollapsed(widget),
                                    onToggle = { toggle(widget) },
                                    showMapButton = layout.nearYou.showMapButton,
                                )
                                ForYouWidget.NETWORK_STATUS -> item(key = "w_network") {
                                    CollapsibleWidget("Network status", layout.showTitles, layout.isCollapsed(widget), { toggle(widget) }, Modifier.animateItem(placementSpec = null)) {
                                        NetworkStatusWidget(state.disruptions, layout.networkStatus.modes, navigator::openDisruptions)
                                    }
                                }
                                ForYouWidget.SHORTCUTS -> item(key = "w_shortcuts") {
                                    CollapsibleWidget("Shortcuts", layout.showTitles, layout.isCollapsed(widget), { toggle(widget) }, Modifier.animateItem(placementSpec = null)) {
                                        ShortcutsWidget(layout.shortcuts.shortcuts, onShortcut)
                                    }
                                }
                            }
                        }
                    }
                    }
                    }
                }
            }
        }
    }

    settingsFor?.let { widget ->
        WidgetSettingsSheet(
            widget = widget,
            state = state,
            weatherReady = container.weatherRepository::isReady,
            onLayout = { transform -> vm.updateLayout(transform) },
            onSettings = { block -> vm.updateSettings(block) },
            onReorderFavourites = { keys -> vm.reorderFavourites(keys) },
            onRetryWeather = { vm.loadWeather(force = true) },
            onRemove = { removeWidget(widget) },
            onDismiss = { settingsFor = null },
        )
    }

    if (addingWidget) {
        AddWidgetSheet(
            available = layout.available,
            onAdd = { widget ->
                vm.updateLayout { it.add(widget) }
                addingWidget = false
            },
            onDismiss = { addingWidget = false },
        )
    }

    commuteEditor?.let { (existing, _) ->
        CommuteEditorSheet(existing = existing, newId = commutesVm::newId, onSave = { commutesVm.saveManual(it) }, onDismiss = { commuteEditor = null })
    }

    if (editingPlace != null || editingExistingPlace != null) {
        PlaceEditorSheet(
            existing = editingExistingPlace,
            initialIcon = editingPlace?.first ?: PlaceIcon.STAR,
            initialName = editingPlace?.second.orEmpty(),
            onDismiss = {
                editingPlace = null
                editingExistingPlace = null
            }
        )
    }

    if (askingName) {
        NameDialog(
            onDismiss = { askingName = false },
            onConfirm = { name ->
                vm.setUserName(name)
                askingName = false
            },
        )
    }

    renaming?.let { fav ->
        RenameDialog(
            favourite = fav,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                vm.rename(fav.key, name)
                renaming = null
            },
        )
    }
}

/** The line under each widget's name in edit mode. */
private fun widgetSummary(widget: ForYouWidget, state: FavouritesUiState, commutes: CommutesUiState): String {
    val l = state.layout
    return when (widget) {
        ForYouWidget.GREETING -> {
            val parts = listOfNotNull(
                "weather".takeIf { l.greeting.showWeather },
                "greeting".takeIf { l.greeting.showGreeting },
                "date".takeIf { l.greeting.showDate },
            )
            val list = when (parts.size) {
                0 -> "Nothing shown"
                1 -> parts[0]
                else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
            }.replaceFirstChar { it.uppercase() }
            if (l.greeting.showWeather) "$list · ${l.greeting.provider.label}" else list
        }
        ForYouWidget.PLACES -> when (state.places.size) {
            0 -> "No places saved yet"
            1 -> "1 place"
            else -> "${state.places.size} places"
        }
        ForYouWidget.FAVOURITES -> "${state.favourites.size} stop${if (state.favourites.size == 1) "" else "s"} · ${state.settings.departuresPerStop} departures each"
        ForYouWidget.DISRUPTIONS -> "${l.disruptions.level.label} · ${l.disruptions.scope.label}"
        ForYouWidget.COMMUTES -> if (commutes.cards.isEmpty()) "No commutes yet" else "Up to ${l.commutes.maxShown} of ${commutes.cards.size}"
        ForYouWidget.NEAR_YOU -> "The ${state.settings.forYouNearbyCount} closest stops"
        ForYouWidget.NETWORK_STATUS -> TransportMode.entries.filter { it in l.networkStatus.modes }.joinToString(", ") { it.pluralLabel }
        ForYouWidget.SHORTCUTS -> l.shortcuts.shortcuts.joinToString(", ") { it.label }
    }
}

/**
 * The Favourites widget: its title, the mode filters and each favourite stop, as one block. Collapsing it uses the same
 * animation as every other widget, and the widgets below follow its edge exactly.
 */
private fun LazyListScope.favouritesWidget(
    state: FavouritesUiState,
    now: Instant,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onAddFromSearch: () -> Unit,
    onToggleFilter: (TransportMode) -> Unit,
    card: @Composable (FavouriteStop, Boolean, Boolean, Modifier) -> Unit,
) {
    val config = state.layout.favourites
    val showTitle = state.layout.showTitles
    item(key = "w_favourites") {
        CollapsibleWidget(
            "Favourites",
            showTitle,
            collapsed,
            onToggle,
            Modifier.animateItem(placementSpec = null),
            trailing = if (config.showAddButton && state.favourites.isNotEmpty()) {
                { TitleAddButton("Add", onAdd) }
            } else null,
        ) {
            if (state.favourites.isEmpty()) {
                EmptyFavourites(onAdd, onAddFromSearch)
                return@CollapsibleWidget
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (config.showModeFilters && state.availableModes.size > 1) {
                    ModeFilterRow(
                        selected = state.filter,
                        onToggle = onToggleFilter,
                        modes = state.availableModes,
                        contentPadding = PaddingValues(0.dp),
                    )
                }
                // A favourite with nothing coming can be hidden, once its departures have loaded.
                val shown: (FavouriteStop) -> Boolean = { fav ->
                    config.showEmptyStops || run {
                        val board = (state.boards[fav.key] as? LoadState.Success)?.data
                        board == null || fav.filter(board.departures).any { d -> d.bestTime?.isAfter(now.minusSeconds(60)) ?: false }
                    }
                }
                // Without a title, the Add button sits on the first heading instead.
                val addOnHeading = !showTitle && config.showAddButton
                if (config.groupByMode) {
                    val firstMode = state.visibleGroups.firstOrNull()?.first
                    state.visibleGroups.forEach { (mode, all) ->
                        val favourites = all.filter(shown)
                        if (favourites.isEmpty()) return@forEach
                        ModeHeading(
                            mode.pluralLabel,
                            trailing = if (addOnHeading && mode == firstMode) {
                                { AddFavouriteButton(onClick = onAdd) }
                            } else null,
                        )
                        favourites.forEach { fav ->
                            androidx.compose.runtime.key(fav.key) { card(fav, all.first() != fav, all.last() != fav, Modifier) }
                        }
                    }
                } else {
                    if (addOnHeading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AddFavouriteButton(onClick = onAdd) }
                    }
                    state.visibleFlat.filter(shown).forEach { fav ->
                        androidx.compose.runtime.key(fav.key) { card(fav, false, false, Modifier) }
                    }
                }
            }
        }
    }
}

/** A small mode heading inside the Favourites widget ("Trains"). */
@Composable
private fun ModeHeading(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun AddFavouriteButton(onClick: () -> Unit) {
    val view = LocalView.current
    FilledTonalButton(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(Sym.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add", style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteStopCard(
    favourite: FavouriteStop,
    board: LoadState<DepartureBoard>?,
    now: Instant,
    departuresToShow: Int,
    timeDisplay: com.ssethhyy.journey.data.repository.TimeDisplay,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onOpenTrip: (com.ssethhyy.journey.data.model.Departure) -> Unit,
    onRetry: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            onOpen()
                        },
                        onLongClick = {
                            HapticUtil.performHeavyHaptic(view)
                            menuOpen = true
                        },
                        onLongClickLabel = "More options",
                    )
                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModePictogram(favourite.mode, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(favourite.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(
                        favourite.nickname?.let { favourite.name },
                        favourite.linesSummary ?: favourite.suburb,
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    HapticIconButton(Sym.MoreVert, "More options for ${favourite.displayName}", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Sym.Edit, null) }, onClick = {
                            menuOpen = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text("Choose lines") }, leadingIcon = { Icon(Sym.Route, null) }, onClick = {
                            menuOpen = false
                            container.editFavouriteLines(favourite)
                        })
                        if (canMoveUp) {
                            DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Sym.ArrowUpward, null) }, onClick = {
                                menuOpen = false
                                onMove(-1)
                            })
                        }
                        if (canMoveDown) {
                            DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Sym.ArrowDownward, null) }, onClick = {
                                menuOpen = false
                                onMove(1)
                            })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Sym.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
            Column(
                Modifier
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(22.dp)),
            ) {
                when (board) {
                    null, LoadState.Loading -> DepartureSkeleton(rows = departuresToShow.coerceAtMost(3))
                    is LoadState.Error -> InlineError(board.error.message ?: "Couldn't load departures", onRetry)
                    is LoadState.Success -> {
                        // Only the lines and directions this favourite was saved with (all of them by default).
                        val upcoming = favourite.filter(board.data.departures).filter { d -> d.bestTime?.let { it.isAfter(now.minusSeconds(60)) } ?: false }
                            .take(departuresToShow)
                        if (upcoming.isEmpty()) {
                            Text(
                                if (favourite.showsEverything) "No upcoming departures" else "No upcoming departures on your chosen lines",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            upcoming.forEachIndexed { index, dep ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                DepartureRow(
                                    departure = dep,
                                    now = now,
                                    display = timeDisplay,
                                    hasDisruption = dep.disruptionIds.isNotEmpty(),
                                    onClick = { onOpenTrip(dep) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavourites(onAddFromNearby: () -> Unit, onAddFromSearch: () -> Unit) {
    Column {
        MessageState(
            icon = Sym.Star,
            title = "No favourites yet",
            message = "Save the stations and stops you use most to see their next departures at a glance.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddFromNearby) {
                    Icon(Sym.NearMe, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nearby")
                }
                FilledTonalButton(onClick = onAddFromSearch) {
                    Icon(Sym.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search")
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(favourite: FavouriteStop, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(favourite.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.Edit, null) },
        title = { Text("Rename favourite") },
        text = {
            Column {
                Text(favourite.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text("Nickname") },
                    placeholder = { Text("e.g. Work, Home") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = {
            Row {
                if (favourite.nickname != null) TextButton(onClick = { onConfirm(null) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.PersonFilled, null) },
        title = { Text("What should we call you?") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                label = { Text("Your name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/**
 * Fades the top of the list as it scrolls up under the pinned greeting header, so content melts into the header
 * instead of being cut off by a hard edge.
 */
private fun Modifier.headerBlend(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 20.dp.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to androidx.compose.ui.graphics.Color.Transparent,
                1f to androidx.compose.ui.graphics.Color.Black,
                startY = 0f,
                endY = fade,
            ),
            size = androidx.compose.ui.geometry.Size(size.width, fade),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
