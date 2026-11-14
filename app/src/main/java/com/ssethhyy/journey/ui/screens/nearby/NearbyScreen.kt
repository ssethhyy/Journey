/*
 * Feature Module: Nearby
 * File: NearbyScreen.kt
 * Description: Every stop within a kilometre, closest first, opened by pulling the Map tab's Nearby panel all the
 * way up.
 */

package com.ssethhyy.journey.ui.screens.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.FloatingPullToRefreshBox
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.floatingBarBlur
import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.map.NearbyStopRow
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.flow.distinctUntilChanged

/** How far the full Nearby page looks. */
const val NEARBY_PAGE_RADIUS_M = 1_000

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen() {
    val vm = appViewModel(key = "nearby_page") { NearbyViewModel(it, radiusMeters = NEARBY_PAGE_RADIUS_M, maxResults = 150) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val view = LocalView.current
    val now by rememberNow()
    val listState = rememberLazyListState()
    val stops = state.visibleStops

    LaunchedEffect(Unit) { vm.load() }
    // Departures only for the stops you can see, as you scroll to them (each stop is fetched once, then refreshed).
    LaunchedEffect(listState, stops) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { info -> stops.firstOrNull { it.key == info.key } } }
            .distinctUntilChanged()
            .collect { visible -> if (visible.isNotEmpty()) vm.loadBoards(visible) }
    }
    AutoRefreshEffect(enabled = state.settings.autoRefresh && stops.isNotEmpty()) { vm.refreshBoards() }

    val subtitle = when {
        state.stops !is LoadState.Success -> null
        state.locationStatus == LocationStatus.NO_PERMISSION || state.locationStatus == LocationStatus.SERVICES_OFF ->
            "${stops.size} stops near the city"
        else -> "${stops.size} stops within 1 km"
    }

    Scaffold(
        topBar = { ReusableTopAppBar(title = "Nearby", subtitle = subtitle, onBackClick = navigator::back) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        FloatingPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.load(force = true, userInitiated = true) },
            topInset = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val s = state.stops) {
                LoadState.Loading -> LoadingState()
                is LoadState.Error -> ErrorState(s.error, onRetry = { vm.load(force = true) })
                is LoadState.Success -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = pageBottomInset(32.dp)),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.availableModes.size > 1) {
                        item(key = "filters") {
                            ModeFilterRow(selected = state.filter, onToggle = vm::toggleFilter, contentPadding = PaddingValues(0.dp))
                        }
                    }
                    if (stops.isEmpty()) {
                        item(key = "empty") { MessageState(Sym.NearMe, "No stops nearby", "There are no stops within a kilometre of you.") }
                    }
                    items(stops, key = { it.key }) { stop ->
                        NearbyStopRow(stop, state.boards[stop.key], now, state.settings.timeDisplay, onClick = {
                            HapticUtil.performUIHaptic(view)
                            navigator.openStop(stop)
                        })
                    }
                }
            }
        }
    }
}
