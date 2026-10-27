/*
 * Feature Module: Search
 * File: SearchScreen.kt
 * Description: Full-screen search across stops, stations and lines with recent searches.
 */

package com.ssethhyy.journey.ui.screens.search

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.SearchResults
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.FavouriteToggle
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RouteBadge
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.loadCatching
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _modes = MutableStateFlow<Set<TransportMode>>(emptySet())
    val modes: StateFlow<Set<TransportMode>> = _modes.asStateFlow()

    private val _results = MutableStateFlow<LoadState<SearchResults>?>(null)
    val results: StateFlow<LoadState<SearchResults>?> = _results.asStateFlow()

    val recent = container.settingsRepository.settings.map { it.recentSearches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favouriteKeys = container.favouritesRepository.favouriteKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            combine(_query.debounce(300), _modes) { q, m -> q.trim() to m }.collect { (q, m) -> search(q, m) }
        }
    }

    private suspend fun search(term: String, modes: Set<TransportMode>) {
        if (term.length < 2) {
            _results.value = null
            return
        }
        _results.value = LoadState.Loading
        _results.value = loadCatching {
            container.ptvRepository.search(term, modes.ifEmpty { TransportMode.entries.toSet() })
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleMode(mode: TransportMode) {
        _modes.value = if (mode in _modes.value) _modes.value - mode else _modes.value + mode
    }

    fun commitRecent() = viewModelScope.launch { container.settingsRepository.addRecentSearch(_query.value) }

    fun clearRecent() = viewModelScope.launch { container.settingsRepository.clearRecentSearches() }

    fun toggleFavourite(stop: Stop) = viewModelScope.launch { container.favouritesRepository.toggle(stop) }

    fun retry() {
        viewModelScope.launch { search(_query.value.trim(), _modes.value) }
    }
}

@Composable
fun SearchScreen() {
    val vm = appViewModel { SearchViewModel(it) }
    val navigator = currentNavigator
    val query by vm.query.collectAsStateWithLifecycle()
    val modes by vm.modes.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favouriteKeys by vm.favouriteKeys.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val openStop: (Stop) -> Unit = {
        vm.commitRecent()
        keyboard?.hide()
        navigator.openStop(it)
    }
    val openRoute: (Route) -> Unit = {
        vm.commitRecent()
        keyboard?.hide()
        navigator.openRoute(it)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .imePadding(),
        ) {
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search stations, stops and lines") },
                leadingIcon = { HapticIconButton(Sym.ArrowBack, "Back", onClick = navigator::back) },
                trailingIcon = {
                    if (query.isNotEmpty()) HapticIconButton(Sym.Close, "Clear search", onClick = { vm.setQuery("") })
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    vm.commitRecent()
                    keyboard?.hide()
                }),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            ModeFilterRow(selected = modes, onToggle = vm::toggleMode)
            Spacer(Modifier.padding(4.dp))

            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            when (val r = results) {
                null -> RecentSearches(recent, onSelect = vm::setQuery, onClear = vm::clearRecent, bottomPadding = bottomPadding)
                LoadState.Loading -> LoadingState()
                is LoadState.Error -> ErrorState(r.error, onRetry = vm::retry, onOpenSettings = navigator::openSettings)
                is LoadState.Success -> if (r.data.isEmpty) {
                    MessageState(Sym.SearchOff, "No matches", "Check the spelling or try a suburb, line number or station name.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding)) {
                        if (r.data.routes.isNotEmpty()) {
                            item(key = "routes_header") { SectionLabel("Lines & routes") }
                            itemsIndexed(r.data.routes.take(15), key = { _, it -> "route_${it.key}" }) { index, route ->
                                SegmentRow(index, r.data.routes.take(15).size) { RouteResultRow(route, onClick = { openRoute(route) }) }
                            }
                        }
                        if (r.data.stops.isNotEmpty()) {
                            item(key = "stops_header") { SectionLabel("Stations & stops") }
                            itemsIndexed(r.data.stops, key = { _, it -> "stop_${it.key}" }) { index, stop ->
                                SegmentRow(index, r.data.stops.size) {
                                    StopResultRow(
                                        stop = stop,
                                        isFavourite = stop.key in favouriteKeys,
                                        onClick = { openStop(stop) },
                                        onToggleFavourite = { vm.toggleFavourite(stop) },
                                        onShowOnMap = {
                                            vm.commitRecent()
                                            keyboard?.hide()
                                            navigator.showOnMap(stop)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gives list rows the grouped "segmented card" shape: rounded at the ends, tight in the middle. */
@Composable
private fun SegmentRow(index: Int, count: Int, content: @Composable () -> Unit) {
    val top = if (index == 0) 24.dp else 6.dp
    val bottom = if (index == count - 1) 24.dp else 6.dp
    Column(
        Modifier
            .padding(bottom = 2.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
            ),
    ) { content() }
}

@Composable
private fun RouteResultRow(route: Route, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                HapticUtil.performUIHaptic(view)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteBadge(route)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(route.mode.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StopResultRow(stop: Stop, isFavourite: Boolean, onClick: () -> Unit, onToggleFavourite: () -> Unit, onShowOnMap: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                HapticUtil.performUIHaptic(view)
                onClick()
            }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModePictogram(stop.mode, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(stop.suburb, stop.mode.label).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HapticIconButton(Sym.Map, "Show ${stop.name} on map", onClick = onShowOnMap, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        FavouriteToggle(isFavourite, onToggleFavourite)
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onSelect: (String) -> Unit, onClear: () -> Unit, bottomPadding: androidx.compose.ui.unit.Dp) {
    if (recent.isEmpty()) {
        MessageState(
            icon = Sym.Search,
            title = "Search the network",
            message = "Find any train station, tram stop or bus stop — or a line like \"96\" or \"Frankston\".",
        )
        return
    }
    val view = LocalView.current
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            SectionLabel("Recent") { TextButton(onClick = onClear) { Text("Clear") } }
        }
        itemsIndexed(recent) { index, term ->
            SegmentRow(index, recent.size) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            onSelect(term)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Sym.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(term, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
