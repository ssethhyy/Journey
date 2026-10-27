/*
 * Feature Module: UI Components
 * File: Places.kt
 * Description: Saved place tiles, the place editor sheet and the location search sheet.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.location.LocationProvider
import com.ssethhyy.journey.data.planner.GeoPoint
import com.ssethhyy.journey.data.planner.GeocodeResult
import com.ssethhyy.journey.data.planner.PlanLocation
import com.ssethhyy.journey.data.repository.PlaceIcon
import com.ssethhyy.journey.data.repository.PlaceKind
import com.ssethhyy.journey.data.repository.PlacesRepository
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.loadCatching
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

val PlaceIcon.vector: ImageVector
    @Composable get() = when (this) {
        PlaceIcon.STAR -> Sym.StarFilled
        PlaceIcon.HEART -> Sym.FavoriteFilled
        PlaceIcon.SCHOOL -> Sym.School
        PlaceIcon.FITNESS -> Sym.FitnessCenter
        PlaceIcon.SHOPPING -> Sym.ShoppingBag
        PlaceIcon.FOOD -> Sym.Restaurant
        PlaceIcon.COFFEE -> Sym.Coffee
        PlaceIcon.PARK -> Sym.Park
        PlaceIcon.HOSPITAL -> Sym.LocalHospital
        PlaceIcon.FAMILY -> Sym.FamilyRestroom
    }

val SavedPlace.vector: ImageVector
    @Composable get() = when (kind) {
        PlaceKind.HOME -> Sym.Home
        PlaceKind.WORK -> Sym.Work
        PlaceKind.CUSTOM -> icon.vector
    }

/** Each kind of place gets its own expressive shape so they are recognisable at a glance. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaceKind.shape(): Shape = when (this) {
    PlaceKind.HOME -> MaterialShapes.Cookie4Sided.toShape()
    PlaceKind.WORK -> MaterialShapes.Cookie6Sided.toShape()
    PlaceKind.CUSTOM -> MaterialShapes.Sunny.toShape()
}

@Composable
fun PlaceKind.containerColor(): Color = when (this) {
    PlaceKind.HOME -> MaterialTheme.colorScheme.primaryContainer
    PlaceKind.WORK -> MaterialTheme.colorScheme.tertiaryContainer
    PlaceKind.CUSTOM -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun PlaceKind.contentColor(): Color = when (this) {
    PlaceKind.HOME -> MaterialTheme.colorScheme.onPrimaryContainer
    PlaceKind.WORK -> MaterialTheme.colorScheme.onTertiaryContainer
    PlaceKind.CUSTOM -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
fun PlaceBadge(kind: PlaceKind, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier
            .size(size)
            .clip(kind.shape())
            .background(kind.containerColor()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = kind.contentColor(), modifier = Modifier.size(size * 0.48f))
    }
}

/**
 * Horizontal row of saved places. Unset Home/Work show as "Set home"/"Set work" prompts, followed
 * by an "Add place" tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlacesRow(
    places: List<SavedPlace>,
    onOpen: (SavedPlace) -> Unit,
    onEdit: (SavedPlace) -> Unit,
    onCreate: (PlaceKind) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val view = LocalView.current
    val home = places.firstOrNull { it.kind == PlaceKind.HOME }
    val work = places.firstOrNull { it.kind == PlaceKind.WORK }
    val custom = places.filter { it.kind == PlaceKind.CUSTOM }

    LazyRow(modifier = modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "home") {
            if (home != null) PlaceTile(home, onClick = { onOpen(home) }, onLongClick = { onEdit(home) })
            else PlaceTilePrompt(PlaceKind.HOME, Sym.Home, "Set home", onClick = { onCreate(PlaceKind.HOME) })
        }
        item(key = "work") {
            if (work != null) PlaceTile(work, onClick = { onOpen(work) }, onLongClick = { onEdit(work) })
            else PlaceTilePrompt(PlaceKind.WORK, Sym.Work, "Set work", onClick = { onCreate(PlaceKind.WORK) })
        }
        items(custom, key = { it.id }) { place ->
            PlaceTile(place, onClick = { onOpen(place) }, onLongClick = { onEdit(place) })
        }
        item(key = "add") {
            PlaceTilePrompt(PlaceKind.CUSTOM, Sym.Add, "Add place", onClick = {
                HapticUtil.performUIHaptic(view)
                onCreate(PlaceKind.CUSTOM)
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceTile(place: SavedPlace, onClick: () -> Unit, onLongClick: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    onClick()
                },
                onLongClick = {
                    HapticUtil.performHeavyHaptic(view)
                    onLongClick()
                },
                onClickLabel = "Plan a trip to ${place.label}",
                onLongClickLabel = "Edit ${place.label}",
            )
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaceBadge(place.kind, place.vector)
        Spacer(Modifier.size(8.dp))
        Text(place.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            place.address ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaceTilePrompt(kind: PlaceKind, icon: ImageVector, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                HapticUtil.performUIHaptic(view)
                onClick()
            }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(kind.shape())
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(" ", style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------------------------------------------------------------------------------------
// Location search
// ---------------------------------------------------------------------------------------------

class LocationSearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<LoadState<List<GeocodeResult>>?>(null)
    val results: StateFlow<LoadState<List<GeocodeResult>>?> = _results.asStateFlow()

    val places = container.placesRepository.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var near: GeoPoint? = null

    init {
        viewModelScope.launch {
            near = container.locationProvider.currentLocation()?.let { GeoPoint(it.latitude, it.longitude) }
                ?: LocationProvider.MELBOURNE_CBD.let { GeoPoint(it.latitude, it.longitude) }
        }
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query.debounce(300).distinctUntilChanged().collect { text ->
                if (text.trim().length < 2) {
                    _results.value = null
                } else {
                    _results.value = LoadState.Loading
                    _results.value = loadCatching { container.journeyRepository.searchLocations(text, near) }
                }
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun reset() {
        _query.value = ""
        _results.value = null
    }

    /** Resolves "current location" to coordinates (falls back to the CBD). */
    suspend fun currentLocation(): PlanLocation {
        val here = container.locationProvider.currentLocation()
        return PlanLocation(
            name = "Current location",
            latitude = here?.latitude ?: LocationProvider.MELBOURNE_CBD.latitude,
            longitude = here?.longitude ?: LocationProvider.MELBOURNE_CBD.longitude,
            subtitle = if (here == null) "Location unavailable — using the city" else null,
            isCurrentLocation = true,
        )
    }
}

/**
 * Full-height sheet for picking a location: current location, saved places, or a geocoded
 * address/stop/landmark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchSheet(
    title: String,
    onDismiss: () -> Unit,
    onPick: (PlanLocation) -> Unit,
    allowCurrentLocation: Boolean = true,
    showSavedPlaces: Boolean = true,
) {
    val vm = appViewModel(key = "location_search") { LocationSearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        vm.reset()
        runCatching { focus.requestFocus() }
    }

    StandardBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            placeholder = { Text("Address, place or stop") },
            leadingIcon = { Icon(Sym.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) HapticIconButton(Sym.Close, "Clear", onClick = { vm.setQuery("") }) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            ),
        )
        LazyColumn(Modifier.heightIn(min = 360.dp, max = 560.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (query.isBlank()) {
                if (allowCurrentLocation) {
                    item(key = "current") {
                        LocationRow(Sym.MyLocation, "Current location", "Use where you are now", tint = MaterialTheme.colorScheme.primary) {
                            scope.launch { onPick(vm.currentLocation()) }
                        }
                    }
                }
                if (showSavedPlaces && places.isNotEmpty()) {
                    item(key = "places_label") { SectionLabel("Saved places") }
                    items(places, key = { "p_${it.id}" }) { place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPick(place.toPlanLocation()) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaceBadge(place.kind, place.vector, size = 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(place.label, style = MaterialTheme.typography.titleSmall)
                                place.address?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            } else {
                when (val r = results) {
                    null -> Unit
                    LoadState.Loading -> item { LoadingState() }
                    is LoadState.Error -> item {
                        InlineError(r.error.message ?: "Search failed", onRetry = { vm.setQuery(query.trim() + " ") })
                    }
                    is LoadState.Success -> if (r.data.isEmpty()) {
                        item { MessageState(Sym.Search, "No matches", "Try a street address, suburb or landmark.") }
                    } else {
                        items(r.data, key = { "${it.name}_${it.latitude}_${it.longitude}" }) { result ->
                            LocationRow(
                                icon = if (result.type.equals("STOP", true)) Sym.Train else if (result.type.equals("ADDRESS", true)) Sym.LocationOn else Sym.LocationOn,
                                title = result.name,
                                subtitle = result.subtitle,
                            ) {
                                onPick(PlanLocation(result.name, result.latitude, result.longitude, result.subtitle))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRow(icon: ImageVector, title: String, subtitle: String?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                HapticUtil.performUIHaptic(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Place editor
// ---------------------------------------------------------------------------------------------

class PlaceEditorViewModel(private val container: AppContainer) : ViewModel() {
    fun save(place: SavedPlace) = viewModelScope.launch { container.placesRepository.save(place) }
    fun remove(id: String) = viewModelScope.launch { container.placesRepository.remove(id) }
}

/**
 * Create or edit a saved place. [existing] null means a new place of [initialKind].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorSheet(
    initialKind: PlaceKind,
    existing: SavedPlace?,
    onDismiss: () -> Unit,
) {
    val vm = appViewModel(key = "place_editor") { PlaceEditorViewModel(it) }
    val view = LocalView.current
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: initialKind) }
    var name by rememberSaveable { mutableStateOf(existing?.takeIf { it.kind == PlaceKind.CUSTOM }?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: PlaceIcon.STAR) }
    var location by remember {
        mutableStateOf(existing?.let { PlanLocation(it.address ?: it.label, it.latitude, it.longitude, it.address) })
    }
    var pickingLocation by rememberSaveable { mutableStateOf(false) }

    val canSave = location != null && (kind != PlaceKind.CUSTOM || name.isNotBlank())

    StandardBottomSheet(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add place" else "Edit ${existing.label}",
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SegmentedPicker(
            items = PlaceKind.entries,
            selectedItem = kind,
            onItemSelected = { kind = it },
            labelProvider = {
                when (it) {
                    PlaceKind.HOME -> "Home"
                    PlaceKind.WORK -> "Work"
                    PlaceKind.CUSTOM -> "Other"
                }
            },
            iconProvider = {
                when (it) {
                    PlaceKind.HOME -> Sym.Home
                    PlaceKind.WORK -> Sym.Work
                    PlaceKind.CUSTOM -> Sym.StarFilled
                }
            },
        )
        if (kind == PlaceKind.CUSTOM) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Name") },
                placeholder = { Text("e.g. Uni, Gym, Mum's") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Icon", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlaceIcon.entries) { option ->
                    FilledTonalIconToggleButton(
                        checked = icon == option,
                        onCheckedChange = {
                            HapticUtil.performUIHaptic(view)
                            icon = option
                        },
                    ) { Icon(option.vector, contentDescription = option.label) }
                }
            }
        }
        Surface(
            onClick = { pickingLocation = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceBright,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(location?.name ?: "Choose location", style = MaterialTheme.typography.titleSmall)
                    Text(
                        location?.subtitle ?: if (location == null) "Search for an address or place" else "Tap to change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (existing != null) {
                TextButton(onClick = {
                    vm.remove(existing.id)
                    onDismiss()
                }) {
                    Icon(Sym.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = canSave,
                onClick = {
                    val loc = location ?: return@Button
                    HapticUtil.performConfirmHaptic(view)
                    vm.save(
                        SavedPlace(
                            id = existing?.id ?: PlacesRepository.newId(),
                            kind = kind,
                            name = if (kind == PlaceKind.CUSTOM) name.trim() else kind.name.lowercase().replaceFirstChar { it.uppercase() },
                            address = if (loc.isCurrentLocation) "Pinned location" else listOfNotNull(loc.name, loc.subtitle).joinToString(", "),
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            icon = icon,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save place") }
        }
    }

    if (pickingLocation) {
        LocationSearchSheet(
            title = "Choose location",
            showSavedPlaces = false,
            onDismiss = { pickingLocation = false },
            onPick = {
                location = it
                pickingLocation = false
            },
        )
    }
}
