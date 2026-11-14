/*
 * Feature Module: For You
 * File: WidgetSettingsSheet.kt
 * Description: Each For You widget's own settings, opened from its settings button in edit mode.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ssethhyy.journey.data.foryou.DateStyle
import com.ssethhyy.journey.data.foryou.DisruptionLevel
import com.ssethhyy.journey.data.foryou.DisruptionScope
import com.ssethhyy.journey.data.foryou.ForYouLayout
import com.ssethhyy.journey.data.foryou.ForYouWidget
import com.ssethhyy.journey.data.foryou.Shortcut
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.SettingsRepository
import com.ssethhyy.journey.data.weather.TemperatureUnit
import com.ssethhyy.journey.data.weather.WeatherException
import com.ssethhyy.journey.data.weather.WeatherProvider
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.components.ConfigPickerItem
import com.ssethhyy.journey.ui.components.IconToggleItem
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SliderItem
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.utils.HapticUtil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsSheet(
    widget: ForYouWidget,
    state: FavouritesUiState,
    weatherReady: (WeatherProvider, Map<WeatherProvider, String>) -> Boolean,
    onLayout: ((ForYouLayout) -> ForYouLayout) -> Unit,
    onSettings: (suspend SettingsRepository.() -> Unit) -> Unit,
    onReorderFavourites: (List<String>) -> Unit,
    onRetryWeather: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val layout = state.layout
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Icon(widget.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(widget.title, style = MaterialTheme.typography.headlineSmall)
                    Text(widget.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (widget) {
                ForYouWidget.GREETING -> GreetingSettings(state, weatherReady, onLayout, onSettings, onRetryWeather)
                ForYouWidget.PLACES -> Group("Places") {
                    IconToggleItem(Sym.Home, "Suggest Home and Work", layout.places.showSuggestions, { v -> onLayout { it.copy(places = it.places.copy(showSuggestions = v)) } },
                        description = "Show “Set home” and “Set work” until they're saved")
                    IconToggleItem(Sym.Add, "Add place tile", layout.places.showAddTile, { v -> onLayout { it.copy(places = it.places.copy(showAddTile = v)) } })
                }
                ForYouWidget.FAVOURITES -> FavouritesSettings(state, onLayout, onSettings, onReorderFavourites)
                ForYouWidget.DISRUPTIONS -> DisruptionsSettings(layout, onLayout)
                ForYouWidget.COMMUTES -> Group("Commutes") {
                    IconToggleItem(Sym.History, "Suggested commutes", layout.commutes.showLearned, { v -> onLayout { it.copy(commutes = it.commutes.copy(showLearned = v)) } },
                        description = "Trips Journey noticed you plan often, as well as your own")
                    IconToggleItem(Sym.Schedule, "Backup trip", layout.commutes.showBackup, { v -> onLayout { it.copy(commutes = it.commutes.copy(showBackup = v)) } },
                        description = "The next way to go if you miss the first")
                    IconToggleItem(Sym.Add, "Add button", layout.commutes.showAddButton, { v -> onLayout { it.copy(commutes = it.commutes.copy(showAddButton = v)) } })
                    SliderItem(Sym.Route, "Commutes shown", layout.commutes.maxShown, 1..6, 1, { "$it" }, { v -> onLayout { it.copy(commutes = it.commutes.copy(maxShown = v)) } })
                }
                ForYouWidget.NEAR_YOU -> Group("Near you") {
                    SliderItem(Sym.NearMe, "Stops shown", state.settings.forYouNearbyCount, 1..8, 1, { "$it" }, { v -> onSettings { setForYouNearbyCount(v) } })
                    IconToggleItem(Sym.Map, "Map button", layout.nearYou.showMapButton, { v -> onLayout { it.copy(nearYou = it.nearYou.copy(showMapButton = v)) } })
                    Note("How far to look and which modes count are under Settings → Nearby stops.")
                }
                ForYouWidget.NETWORK_STATUS -> Group("Modes") {
                    TransportMode.entries.forEach { mode ->
                        CheckRow(
                            checked = mode in layout.networkStatus.modes,
                            onChange = { on ->
                                onLayout {
                                    val modes = if (on) it.networkStatus.modes + mode else it.networkStatus.modes - mode
                                    it.copy(networkStatus = it.networkStatus.copy(modes = modes.ifEmpty { setOf(mode) }))
                                }
                            },
                            leading = { ModePictogram(mode, size = 28.dp) },
                            label = mode.pluralLabel,
                        )
                    }
                }
                ForYouWidget.SHORTCUTS -> Group("Shortcuts") {
                    Shortcut.entries.forEach { shortcut ->
                        CheckRow(
                            checked = shortcut in layout.shortcuts.shortcuts,
                            onChange = { on ->
                                onLayout {
                                    val list = if (on) Shortcut.entries.filter { s -> s in it.shortcuts.shortcuts || s == shortcut } else it.shortcuts.shortcuts - shortcut
                                    it.copy(shortcuts = it.shortcuts.copy(shortcuts = list.ifEmpty { listOf(shortcut) }))
                                }
                            },
                            leading = { Icon(shortcut.icon, null, tint = MaterialTheme.colorScheme.primary) },
                            label = shortcut.label,
                        )
                    }
                }
            }
            // The greeting has no title, and the Disruptions card folds from its own header.
            if (widget != ForYouWidget.DISRUPTIONS && widget != ForYouWidget.GREETING && layout.showTitles) {
                Group("Title") {
                    IconToggleItem(
                        Sym.ExpandLess, "Folded", layout.isCollapsed(widget),
                        { onLayout { it.toggleCollapsed(widget) } },
                        description = "Only the title shows until you tap it",
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    HapticUtil.performRejectHaptic(view)
                    onRemove()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Sym.RemoveCircle, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove from For You")
            }
        }
    }
}

@Composable
private fun GreetingSettings(
    state: FavouritesUiState,
    weatherReady: (WeatherProvider, Map<WeatherProvider, String>) -> Boolean,
    onLayout: ((ForYouLayout) -> ForYouLayout) -> Unit,
    onSettings: (suspend SettingsRepository.() -> Unit) -> Unit,
    onRetryWeather: () -> Unit,
) {
    val g = state.layout.greeting
    fun set(transform: (com.ssethhyy.journey.data.foryou.GreetingConfig) -> com.ssethhyy.journey.data.foryou.GreetingConfig) =
        onLayout { it.copy(greeting = transform(it.greeting)) }

    Group("Greeting") {
        IconToggleItem(Sym.PersonFilled, "Greeting", g.showGreeting, { v -> set { it.copy(showGreeting = v) } }, description = "“Good morning”, with your name")
        if (g.showGreeting) {
            TextSetting(
                label = "Your name",
                value = state.settings.userName,
                placeholder = "For a personal greeting",
                maxLength = 30,
                onSave = { name -> onSettings { setUserName(name) } },
            )
        }
        IconToggleItem(Sym.Schedule, "Date", g.showDate, { v -> set { it.copy(showDate = v) } })
        if (g.showDate) {
            ChoiceList(
                items = DateStyle.entries,
                selected = g.dateStyle,
                label = { ForYouDates.format(it, java.time.Instant.now()) },
                onSelect = { v -> set { it.copy(dateStyle = v) } },
            )
        }
    }
    Group("Weather") {
        IconToggleItem(Sym.WeatherPartlyCloudyDay, "Weather", g.showWeather, { v -> set { it.copy(showWeather = v) } }, description = "The temperature and an icon for the conditions")
        if (g.showWeather) {
            IconToggleItem(Sym.ExpandLess, "Fold after a few seconds", g.collapses, { v -> set { it.copy(collapses = v) } },
                description = "Shrinks to one line so you see more of the page. Tap it to open it again")
            if (g.collapses) {
                SliderItem(Sym.Schedule, "Fold after", g.collapseAfterSeconds, 3..20, 1, { "$it s" }, { v -> set { it.copy(collapseAfterSeconds = v) } })
            }
            IconToggleItem(Sym.Info, "Conditions, feels like, high and low", g.showWeatherDetails, { v -> set { it.copy(showWeatherDetails = v) } })
            ConfigPickerItem(Sym.WeatherSunny, "Units", TemperatureUnit.entries, g.unit, { v -> set { it.copy(unit = v) } }, { "${it.label} (${it.symbol})" })
        }
    }
    if (g.showWeather) {
        Group("Weather provider") {
            ChoiceList(
                items = WeatherProvider.entries,
                selected = g.provider,
                label = { it.label },
                detail = { it.note },
                onSelect = { v -> set { it.copy(provider = v) } },
            )
            val provider = g.provider
            if (provider.needsKey) {
                val builtIn = provider == WeatherProvider.DEFAULT && weatherReady(provider, emptyMap())
                TextSetting(
                    label = "${provider.label} API key",
                    value = g.keys[provider].orEmpty(),
                    placeholder = if (builtIn) "Optional: the app's key is used" else "Needed for ${provider.label}",
                    secret = true,
                    onSave = { key -> set { it.copy(keys = if (key.isBlank()) it.keys - provider else it.keys + (provider to key.trim())) } },
                )
                provider.keyUrl?.let { url ->
                    val uri = LocalUriHandler.current
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(start = 8.dp)) {
                        TextButton(onClick = { runCatching { uri.openUri(url) } }) {
                            Text("Get a free ${provider.label} key")
                        }
                    }
                }
            }
            WeatherStatus(state.weather, onRetryWeather)
        }
    }
}

@Composable
private fun WeatherStatus(weather: LoadState<com.ssethhyy.journey.data.weather.WeatherNow>?, onRetry: () -> Unit) {
    val text = when (weather) {
        null, LoadState.Loading -> "Checking the weather…"
        is LoadState.Success -> "Working: ${weather.data.description}, ${TemperatureUnit.CELSIUS.format(weather.data.temperatureC)}C from ${weather.data.provider.label}" +
            (weather.data.place?.let { " ($it)" } ?: "")
        is LoadState.Error -> when (val e = weather.error) {
            is WeatherException.MissingKey -> "${e.provider.label} needs a key. Paste one above, or choose Open-Meteo."
            is WeatherException.Unauthorized -> "${e.provider.label} didn't accept the key. New OpenWeatherMap keys can take an hour or two to start working."
            else -> "Couldn't get the weather: ${e.message ?: "unknown error"}"
        }
    }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Check") }
    }
}

@Composable
private fun FavouritesSettings(
    state: FavouritesUiState,
    onLayout: ((ForYouLayout) -> ForYouLayout) -> Unit,
    onSettings: (suspend SettingsRepository.() -> Unit) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val f = state.layout.favourites
    Group("Favourites") {
        SliderItem(Sym.Schedule, "Departures per stop", state.settings.departuresPerStop, 1..6, 1, { "$it" }, { v -> onSettings { setDeparturesPerStop(v) } })
        IconToggleItem(Sym.Train, "Group by mode", f.groupByMode, { v -> onLayout { it.copy(favourites = it.favourites.copy(groupByMode = v)) } },
            description = "Trains, trams and buses under their own headings")
        IconToggleItem(Sym.Tune, "Mode filters", f.showModeFilters, { v -> onLayout { it.copy(favourites = it.favourites.copy(showModeFilters = v)) } })
        IconToggleItem(Sym.Visibility, "Stops with nothing coming", f.showEmptyStops, { v -> onLayout { it.copy(favourites = it.favourites.copy(showEmptyStops = v)) } })
        IconToggleItem(Sym.Add, "Add button", f.showAddButton, { v -> onLayout { it.copy(favourites = it.favourites.copy(showAddButton = v)) } })
    }
    if (state.favourites.size > 1) {
        Group("Order") {
            Note("Drag the handles to reorder your favourites.")
            if (f.groupByMode) {
                state.favourites.groupBy { it.mode }.toList().sortedBy { it.first.ordinal }.forEach { (mode, list) ->
                    Text(mode.pluralLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
                    ReorderableFavourites(list) { keys ->
                        // Keep the other modes where they are and put this one in its new order.
                        val queue = keys.toMutableList()
                        onReorder(state.favourites.map { fav -> if (fav.mode == mode) queue.removeAt(0) else fav.key })
                    }
                }
            } else {
                ReorderableFavourites(state.favourites, onReorder)
            }
        }
    }
}

/** A short list reordered by dragging each row's handle; the rows are all the same height. */
@Composable
private fun ReorderableFavourites(items: List<FavouriteStop>, onReorder: (List<String>) -> Unit) {
    val view = LocalView.current
    val rowHeight = 60.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var order by remember(items) { mutableStateOf(items) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val target = if (dragIndex < 0) -1 else (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)

    fun move(from: Int, to: Int) {
        if (from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        onReorder(order.map { it.key })
    }

    Column(Modifier.background(MaterialTheme.colorScheme.surfaceBright)) {
        order.forEachIndexed { index, fav ->
            val shift = when {
                dragIndex < 0 || index == dragIndex -> 0f
                index in (dragIndex + 1)..target -> -rowPx
                index in target until dragIndex -> rowPx
                else -> 0f
            }
            val animatedShift by animateFloatAsState(shift, label = "reorder_shift")
            val lifted = index == dragIndex
            Row(
                Modifier
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = if (lifted) dragOffset else animatedShift }
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (lifted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Move up") { move(index, (index - 1).coerceAtLeast(0)); true },
                            CustomAccessibilityAction("Move down") { move(index, (index + 1).coerceAtMost(order.lastIndex)); true },
                        )
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .pointerInput(order) {
                            detectDragGestures(
                                onDragStart = {
                                    HapticUtil.performHeavyHaptic(view)
                                    dragIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val before = (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    dragOffset += amount.y
                                    val after = (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    if (before != after) HapticUtil.performMicroHaptic(view)
                                },
                                onDragEnd = {
                                    val to = (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    val from = dragIndex
                                    dragIndex = -1
                                    dragOffset = 0f
                                    move(from, to)
                                    HapticUtil.performLightHaptic(view)
                                },
                                onDragCancel = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Sym.DragIndicator, contentDescription = "Drag to reorder ${fav.displayName}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ModePictogram(fav.mode, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fav.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    (fav.linesSummary ?: fav.suburb)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisruptionsSettings(layout: ForYouLayout, onLayout: ((ForYouLayout) -> ForYouLayout) -> Unit) {
    val d = layout.disruptions
    Group("Which disruptions") {
        ChoiceList(
            items = DisruptionLevel.entries,
            selected = d.level,
            label = { it.label },
            detail = { it.detail },
            onSelect = { v -> onLayout { it.copy(disruptions = it.disruptions.copy(level = v)) } },
        )
        ConfigPickerItem(Sym.Star, "Affecting", DisruptionScope.entries, d.scope, { v -> onLayout { it.copy(disruptions = it.disruptions.copy(scope = v)) } }, { it.label })
    }
    Group("Display") {
        IconToggleItem(Sym.ExpandLess, "Start folded", layout.isCollapsed(ForYouWidget.DISRUPTIONS), { onLayout { it.toggleCollapsed(ForYouWidget.DISRUPTIONS) } },
            description = "Just a line saying how many, until you tap it")
        IconToggleItem(Sym.VisibilityOff, "Hide when there are none", d.hideWhenClear, { v -> onLayout { it.copy(disruptions = it.disruptions.copy(hideWhenClear = v)) } })
        SliderItem(Sym.Warning, "Most shown", d.maxShown, 1..10, 1, { "$it" }, { v -> onLayout { it.copy(disruptions = it.disruptions.copy(maxShown = v)) } })
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        RoundedCardContainer(content = content)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun <T> ChoiceList(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, detail: ((T) -> String)? = null) {
    val view = LocalView.current
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceBright)) {
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) {
                        HapticUtil.performUIHaptic(view)
                        onSelect(item)
                    }
                    .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = item == selected, onClick = null, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label(item), style = MaterialTheme.typography.bodyLarge)
                    detail?.invoke(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, leading: @Composable () -> Unit, label: String) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(role = Role.Checkbox) {
                HapticUtil.performToggleHaptic(view, !checked)
                onChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** A text field that saves when you press Done or leave it. */
@Composable
private fun TextSetting(
    label: String,
    value: String,
    placeholder: String,
    onSave: (String) -> Unit,
    maxLength: Int = 200,
    secret: Boolean = false,
) {
    var text by rememberSaveable(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(focused) { if (!focused && text != value) onSave(text) }
    // Closing the sheet while typing still saves.
    val latest by rememberUpdatedState(text)
    val save by rememberUpdatedState(onSave)
    DisposableEffect(Unit) { onDispose { if (latest != value) save(latest) } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(maxLength) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (secret && text.isNotEmpty()) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
            onSave(text)
            focus.clearFocus()
        }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .onFocusChanged { focused = it.isFocused },
    )
}
