/*
 * Feature Module: Map
 * File: NearbyPage.kt
 * Description: The Nearby panel, pulled up into a page: every stop nearby with its next departure.
 *
 * Laid out exactly as the panel is at its full height, so the panel turns into it seamlessly, and pulling down at the
 * top of the list hands back to the panel on the map.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.floatingBarBlur
import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.components.rememberPullDownToClose
import com.ssethhyy.journey.ui.navigation.NearbyKey
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.nearby.NearbyViewModel
import com.ssethhyy.journey.ui.sharedAppViewModel

@Composable
fun NearbyPage(key: NearbyKey) {
    // The same one the map's panel uses, so the page opens showing exactly what the panel did.
    val vm = sharedAppViewModel(key = "map_nearby") { NearbyViewModel(it, maxResults = NEARBY_MAX_STOPS) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val now by rememberNow()
    val pullDown = rememberPullDownToClose(enabled = true, onClose = navigator::back)
    val listState = rememberLazyListState(key.scrollIndex, key.scrollOffset)
    LoadVisibleBoards(listState, state.stops.dataOrNull.orEmpty()) { vm.loadBoards(it) }

    Scaffold(
        topBar = { ReusableTopAppBar(title = "Nearby", subtitle = nearbySummary(state), onBackClick = navigator::back) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .floatingBarBlur(padding.calculateTopPadding())
                .nestedScroll(pullDown.connection),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = padding.calculateTopPadding(), bottom = pageBottomInset(32.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            nearbyItems(
                state = state,
                now = now,
                // A stop opens on the map, where its panel shows it.
                onSelect = navigator::openStop,
                // Location is allowed from the map (its prompt lives there).
                onAllowLocation = navigator::back,
            )
        }
    }
}
