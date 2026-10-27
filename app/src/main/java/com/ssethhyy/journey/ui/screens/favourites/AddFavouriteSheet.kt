/*
 * Feature Module: Favourites
 * File: AddFavouriteSheet.kt
 * Description: Bottom sheet for adding favourites from nearby stops or by searching.
 */

package com.ssethhyy.journey.ui.screens.favourites

import com.ssethhyy.journey.ui.components.Sym
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.location.LatLng
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.FavouriteToggle
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SegmentedPicker
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.loadCatching
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddFavouriteViewModel(private val container: AppContainer) : ViewModel() {
    private val _nearby = MutableStateFlow<LoadState<List<Stop>>>(LoadState.Loading)
    val nearby: StateFlow<LoadState<List<Stop>>> = _nearby.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<LoadState<List<Stop>>?>(null)
    val results: StateFlow<LoadState<List<Stop>>?> = _results.asStateFlow()

    val favouriteKeys = container.favouritesRepository.favouriteKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val hasLocationPermission: Boolean get() = container.locationProvider.hasPermission()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query.debounce(350).distinctUntilChanged().collect { search(it) }
        }
    }

    fun loadNearby() {
        viewModelScope.launch {
            _nearby.value = LoadState.Loading
            _nearby.value = loadCatching {
                val settings = container.settingsRepository.current()
                val location: LatLng = container.locationProvider.currentLocation() ?: com.ssethhyy.journey.data.location.LocationProvider.MELBOURNE_CBD
                container.ptvRepository.nearbyStops(location.latitude, location.longitude, settings.nearbyModes, settings.nearbyRadiusMeters.coerceAtLeast(800), maxResults = 40)
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    private suspend fun search(term: String) {
        if (term.trim().length < 2) {
            _results.value = null
            return
        }
        _results.value = LoadState.Loading
        _results.value = loadCatching { container.ptvRepository.search(term).stops }
    }

    fun toggle(stop: Stop) = viewModelScope.launch { container.favouritesRepository.toggle(stop) }
}

private enum class AddSource(val label: String) { NEARBY("Nearby"), SEARCH("Search") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFavouriteSheet(startOnSearch: Boolean, onDismiss: () -> Unit) {
    val vm = appViewModel(key = "add_favourite") { AddFavouriteViewModel(it) }
    val navigator = currentNavigator
    var source by rememberSaveable { mutableStateOf(if (startOnSearch) AddSource.SEARCH else AddSource.NEARBY) }
    val favouriteKeys by vm.favouriteKeys.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionAsked = true
        vm.loadNearby()
    }

    LaunchedEffect(source) {
        if (source == AddSource.NEARBY) {
            vm.loadNearby()
            if (!vm.hasLocationPermission && !permissionAsked) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    StandardBottomSheet(onDismissRequest = onDismiss, title = "Add favourite", sheetState = sheetState) {
        SegmentedPicker(
            items = AddSource.entries,
            selectedItem = source,
            onItemSelected = { source = it },
            labelProvider = { it.label },
            iconProvider = { if (it == AddSource.NEARBY) Sym.NearMe else Sym.Search },
        )
        AnimatedContent(targetState = source, label = "add_source") { target ->
            Column(Modifier.heightIn(min = 320.dp)) {
                when (target) {
                    AddSource.NEARBY -> {
                        val nearby by vm.nearby.collectAsStateWithLifecycle()
                        if (!vm.hasLocationPermission) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Sym.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Location is off — showing stops near the CBD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        StopPickList(nearby, favouriteKeys, onToggle = vm::toggle, onRetry = vm::loadNearby, onOpenSettings = {
                            onDismiss()
                            navigator.openSettings()
                        })
                    }
                    AddSource.SEARCH -> {
                        val query by vm.query.collectAsStateWithLifecycle()
                        val results by vm.results.collectAsStateWithLifecycle()
                        TextField(
                            value = query,
                            onValueChange = vm::setQuery,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Station, stop or suburb") },
                            leadingIcon = { Icon(Sym.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) HapticIconButton(Sym.Close, "Clear search", onClick = { vm.setQuery("") })
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            ),
                        )
                        Spacer(Modifier.size(12.dp))
                        when (val r = results) {
                            null -> MessageState(Sym.Search, "Find a stop", "Type at least two letters to search every train station, tram stop and bus stop.")
                            else -> StopPickList(r, favouriteKeys, onToggle = vm::toggle, onRetry = { vm.setQuery(query) }, onOpenSettings = {
                                onDismiss()
                                navigator.openSettings()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopPickList(
    state: LoadState<List<Stop>>,
    favouriteKeys: Set<String>,
    onToggle: (Stop) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingState()
        is LoadState.Error -> ErrorState(state.error, onRetry = onRetry, onOpenSettings = onOpenSettings)
        is LoadState.Success -> if (state.data.isEmpty()) {
            MessageState(Sym.SearchOff, "No stops found", "Try a different name or widen the nearby radius in Settings.")
        } else {
            RoundedCardContainer {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.data, key = { it.key }) { stop ->
                        PickableStopRow(stop, isFavourite = stop.key in favouriteKeys, onToggle = { onToggle(stop) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickableStopRow(stop: Stop, isFavourite: Boolean, onToggle: () -> Unit) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickableWithHaptic(view, onToggle)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModePictogram(stop.mode, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(stop.distanceMeters?.let(DistanceFormatter::distance), stop.suburb, stop.mode.label).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FavouriteToggle(isFavourite = isFavourite, onToggle = onToggle)
    }
}

private fun Modifier.clickableWithHaptic(view: android.view.View, onClick: () -> Unit): Modifier =
    clickable {
        HapticUtil.performUIHaptic(view)
        onClick()
    }
