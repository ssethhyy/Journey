/*
 * Feature Module: Disruptions
 * File: DisruptionsScreen.kt
 * Description: Network-wide service updates, filterable by transport mode.
 */

package com.ssethhyy.journey.ui.screens.disruptions

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.FloatingPullToRefreshBox
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.DisruptionCard
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.loadCatching
import com.ssethhyy.journey.ui.navigation.currentNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DisruptionsUiState(
    val disruptions: LoadState<List<Disruption>> = LoadState.Loading,
    val filter: Set<TransportMode> = emptySet(),
    val refreshing: Boolean = false,
) {
    val grouped: List<Pair<String, List<Disruption>>>
        get() = disruptions.dataOrNull.orEmpty()
            .filter { filter.isEmpty() || it.mode in filter }
            .groupBy { it.mode }
            .toList()
            .sortedBy { it.first?.ordinal ?: Int.MAX_VALUE }
            .map { (mode, list) -> (mode?.pluralLabel ?: "General") to list }
}

class DisruptionsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DisruptionsUiState())
    val state: StateFlow<DisruptionsUiState> = _state.asStateFlow()

    init {
        load(force = false)
    }

    fun load(force: Boolean = true, userInitiated: Boolean = false) = viewModelScope.launch {
        if (userInitiated) _state.update { it.copy(refreshing = true) }
        val result = loadCatching { container.ptvRepository.disruptions(force = force) }
        _state.update { it.copy(disruptions = result, refreshing = false) }
    }

    fun toggle(mode: TransportMode) = _state.update { s -> s.copy(filter = if (mode in s.filter) s.filter - mode else s.filter + mode) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisruptionsScreen() {
    val vm = appViewModel { DisruptionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val count = state.disruptions.dataOrNull?.size

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "Service updates",
                subtitle = count?.let { if (it == 1) "1 current disruption" else "$it current disruptions" },
                onBackClick = navigator::back,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        FloatingPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.load(userInitiated = true) },
            topInset = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val d = state.disruptions) {
                LoadState.Loading -> LoadingState()
                is LoadState.Error -> ErrorState(d.error, onRetry = { vm.load() }, onOpenSettings = navigator::openSettings)
                is LoadState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "filters") { ModeFilterRow(selected = state.filter, onToggle = vm::toggle, contentPadding = PaddingValues(0.dp)) }
                    if (state.grouped.isEmpty()) {
                        item(key = "empty") {
                            MessageState(Sym.CheckCircle, "Good service", "No current disruptions reported for the selected modes.")
                        }
                    }
                    state.grouped.forEach { (label, list) ->
                        item(key = "label_$label") { SectionLabel("$label · ${list.size}") }
                        items(list, key = { "${label}_${it.id}" }) { DisruptionCard(it) }
                    }
                }
            }
        }
    }
}
