/*
 * Feature Module: Home
 * File: HomeScreen.kt
 * Description: Tab host (For You, Plan, Map) with swipe navigation and the floating toolbar + contextual FAB.
 */

package com.ssethhyy.journey.ui.screens.home

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import com.ssethhyy.journey.ui.components.BlurDirection
import com.ssethhyy.journey.ui.components.progressiveBlur
import com.ssethhyy.journey.ui.components.rememberBlurAvailable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.ui.components.JourneyFloatingToolbar
import com.ssethhyy.journey.ui.components.LocalTabActive
import com.ssethhyy.journey.ui.components.ToolbarFab
import com.ssethhyy.journey.ui.components.ToolbarItem
import com.ssethhyy.journey.ui.components.TrackingBanner
import com.ssethhyy.journey.ui.navigation.HomeTab
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.favourites.AddFavouriteSheet
import com.ssethhyy.journey.ui.screens.favourites.FavouritesScreen
import com.ssethhyy.journey.ui.screens.map.MapScreen
import com.ssethhyy.journey.ui.screens.plan.PlanScreen
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Height reserved at the bottom of scrollable tabs so content clears the floating toolbar. */
val ToolbarClearance = 112.dp

/**
 * The pager is the single source of truth for the selected tab: swipes and toolbar taps both just
 * move the pager, and the toolbar reads from it. Requests from elsewhere in the app (for example
 * showing a stop on the map) arrive through [requestedTab] and are applied once.
 */
@Composable
fun HomeScreen(requestedTab: HomeTab?, onTabRequestHandled: () -> Unit) {
    val navigator = currentNavigator
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val tabs = HomeTab.entries
    val pagerState = rememberPagerState(initialPage = requestedTab?.ordinal ?: 0, pageCount = { tabs.size })
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var addSheetStartOnSearch by rememberSaveable { mutableStateOf(false) }
    // The floating toolbar and its FAB hide together while scrolling down and return on scroll up.
    var barsVisible by remember { mutableStateOf(true) }
    var tabJob by remember { mutableStateOf<Job?>(null) }

    // Highlight follows where the pager is heading, so the toolbar reacts as soon as a swipe or tap starts.
    val selectedTab by remember { derivedStateOf { tabs[pagerState.targetPage] } }
    // The tab actually on screen once motion stops; drives "is this tab visible" work.
    val settledTab by remember { derivedStateOf { tabs[pagerState.settledPage] } }

    // The map is expensive to create, so once it has been opened all tabs stay composed.
    var mapVisited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settledTab) {
        if (settledTab == HomeTab.MAP) mapVisited = true
    }

    fun goTo(tab: HomeTab) {
        if (pagerState.targetPage == tab.ordinal && !pagerState.isScrollInProgress) return
        tabJob?.cancel()
        tabJob = scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
    }

    LaunchedEffect(requestedTab) {
        val tab = requestedTab ?: return@LaunchedEffect
        onTabRequestHandled()
        tabJob?.cancel()
        tabJob = launch { pagerState.animateScrollToPage(tab.ordinal) }
    }

    // A light tick and a fully expanded toolbar each time a new tab settles.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().drop(1).collect {
            barsVisible = true
            HapticUtil.performLightHaptic(view)
        }
    }

    // Hide the toolbar + FAB while scrolling down a list; bring them back on scroll up or at the end.
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -6f) barsVisible = false
                    if (available.y > 6f) barsVisible = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Reaching the top (nothing left to scroll up) always shows the bars.
                if (available.y > 0f) barsVisible = true
                return Offset.Zero
            }
        }
    }
    val showBars = barsVisible || settledTab == HomeTab.MAP

    // Soft progressive blur of whatever scrolls beneath the toolbar (all tabs, including the map).
    val density = LocalDensity.current
    val blurAvailable = rememberBlurAvailable()
    val navInset = WindowInsets.navigationBars.getBottom(density)
    val blurRadius by animateFloatAsState(
        targetValue = if (blurAvailable && showBars) with(density) { 18.dp.toPx() } else 0f,
        animationSpec = tween(350),
        label = "nav_blur",
    )
    val blurHeight = navInset + with(density) { 104.dp.toPx() }

    val openAddSheet: (Boolean) -> Unit = { search ->
        addSheetStartOnSearch = search
        showAddSheet = true
    }

    val fab = when (selectedTab) {
        HomeTab.FOR_YOU -> ToolbarFab(Sym.PersonFilled, "Profile") { navigator.openProfile() }
        HomeTab.PLAN -> ToolbarFab(Sym.MykiCard, "myki cards") { navigator.openMyki() }
        HomeTab.MAP -> ToolbarFab(Sym.Search, "Search stops and lines") { navigator.openSearch() }
    }

    val items = listOf(
        ToolbarItem(Sym.ForYou, Sym.ForYouFilled, "For You", { goTo(HomeTab.FOR_YOU) }),
        ToolbarItem(Sym.Directions, Sym.DirectionsFilled, "Plan", { goTo(HomeTab.PLAN) }),
        ToolbarItem(Sym.Map, Sym.MapFilled, "Map", { goTo(HomeTab.MAP) }),
    )

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .progressiveBlur(blurRadiusPx = blurRadius, heightPx = blurHeight.toFloat(), direction = BlurDirection.BOTTOM)
                .nestedScroll(hideOnScroll),
            // The map consumes horizontal drags itself, so swiping is disabled once it has settled on
            // screen. Using the settled page (not the page that is more than half visible) means a
            // swipe towards the map is never cut off half way.
            userScrollEnabled = settledTab != HomeTab.MAP,
            beyondViewportPageCount = if (mapVisited) tabs.size - 1 else 1,
            key = { tabs[it].name },
        ) { page ->
            val tab = tabs[page]
            CompositionLocalProvider(LocalTabActive provides (tab == settledTab)) {
                when (tab) {
                    HomeTab.FOR_YOU -> FavouritesScreen(
                        onAddFromNearby = { openAddSheet(false) },
                        onAddFromSearch = { openAddSheet(true) },
                    )
                    HomeTab.PLAN -> PlanScreen()
                    HomeTab.MAP -> MapScreen(isActive = settledTab == HomeTab.MAP)
                }
            }
        }

        // Live tracking banner above the toolbar (the map shows its own under the filters).
        if (selectedTab != HomeTab.MAP) {
            // Drops down to the bottom edge while the toolbar is hidden.
            val bannerBottom by animateDpAsState(if (showBars) ToolbarClearance - 8.dp else 12.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "banner_bottom")
            TrackingBanner(
                onOpen = { target -> navigator.openTracked(target) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = bannerBottom),
            )
        }

        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            JourneyFloatingToolbar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 12.dp),
                items = items,
                selectedIndex = selectedTab.ordinal,
                fab = fab,
                expanded = true,
            )
        }
    }

    if (showAddSheet) {
        AddFavouriteSheet(
            startOnSearch = addSheetStartOnSearch,
            onDismiss = { showAddSheet = false },
        )
    }
}
