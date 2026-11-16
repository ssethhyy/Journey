/*
 * Feature Module: Settings
 * File: SettingsRenderer.kt
 * Description: Draws the items SettingsCatalog declares. Each item type has one composable here; a new type of row
 * is added once, below, and every setting of that type looks and behaves the same.
 */

package com.ssethhyy.journey.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.BuildConfig
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.Prefs
import com.ssethhyy.journey.data.repository.SettingsCategory
import com.ssethhyy.journey.ui.components.ActionItem
import com.ssethhyy.journey.ui.components.ConfigPickerItem
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.IconToggleItem
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.SliderItem
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.utils.HapticUtil
import kotlin.math.roundToInt

/** One category: its title and a card of its visible items, or nothing if none are visible. */
@Composable
fun SettingsCategorySection(category: SettingsCategory, scope: SettingsScope) {
    val items = SettingsCatalog.itemsFor(category).filter { it.visible(scope.values) }
    if (items.isEmpty()) return
    SectionLabel(category.title)
    RoundedCardContainer {
        items.forEach { SettingRow(it, scope) }
    }
}

@Composable
private fun SettingRow(item: SettingItem, scope: SettingsScope) {
    when (item) {
        is Toggle -> ToggleRow(item, scope)
        is Choice<*> -> ChoiceRow(item, scope)
        is Slider -> SliderRow(item, scope)
        is TextInput -> TextInputRow(item, scope)
        is Modes -> ModesRow(item, scope)
        is Action -> ActionItem(
            icon = item.icon,
            title = item.title,
            description = item.describe(scope),
            tint = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            showChevron = item.opensPage,
            onClick = { item.onClick(scope) },
        )
        is Info -> InfoRow(item, scope)
        is Custom -> item.content(scope)
    }
}

@Composable
private fun ToggleRow(item: Toggle, scope: SettingsScope) {
    val enabled = item.enabled(scope.values)
    val checked = if (!enabled && item.checkedWhenDisabled != null) item.checkedWhenDisabled else scope.values[item.pref]
    IconToggleItem(
        icon = item.icon,
        title = item.title,
        description = item.description,
        isChecked = checked,
        enabled = enabled,
        onDisabledClick = { item.disabledReason?.let(scope::toast) },
        onCheckedChange = { value ->
            item.onChanged?.invoke(value)
            scope.save(item.pref, value)
        },
    )
}

@Composable
private fun <E : Enum<E>> ChoiceRow(item: Choice<E>, scope: SettingsScope) {
    val current = scope.values[item.pref]
    ConfigPickerItem(
        icon = item.icon,
        title = item.title,
        items = item.pref.options,
        selectedItem = current,
        onItemSelected = { scope.save(item.pref, it) },
        labelProvider = item.label,
        description = item.describe?.invoke(current),
    )
}

@Composable
private fun SliderRow(item: Slider, scope: SettingsScope) {
    val range = item.pref.range
    SliderItem(
        icon = item.icon,
        title = item.title,
        value = scope.values[item.pref] / item.scale,
        range = (range.first / item.scale)..(range.last / item.scale),
        step = item.step,
        valueLabel = item.valueLabel,
        onValueChangeFinished = { scope.save(item.pref, it * item.scale) },
    )
}

@Composable
private fun ModesRow(item: Modes, scope: SettingsScope) {
    val selected = scope.values[item.pref]
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(vertical = 12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        ModeFilterRow(
            selected = selected,
            onToggle = { mode -> scope.save(item.pref, if (mode in selected) selected - mode else selected + mode) },
        )
    }
}

@Composable
private fun InfoRow(item: Info, scope: SettingsScope) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(item.text(scope), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A titled card with a text field and a Save button. */
@Composable
private fun TextInputRow(item: TextInput, scope: SettingsScope) {
    val view = LocalView.current
    val saved = scope.values[item.pref]
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    val problem = item.validate(text)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardHeading(item.icon, item.title, item.describe(scope.values))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.trim() },
            label = { Text(item.label) },
            singleLine = true,
            visualTransformation = if (item.secret) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            isError = problem != null,
            supportingText = problem?.let { { Text(it) } },
        )
        FilledTonalButton(onClick = {
            HapticUtil.performConfirmHaptic(view)
            scope.save(item.pref, text)
        }) { Text(item.saveLabel) }
    }
}

@Composable
private fun CardHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Custom rows
// ---------------------------------------------------------------------------------------------------------------

/** The PTV developer ID and key: saved together, and testable before saving. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PtvCredentialsSetting(scope: SettingsScope) {
    val view = LocalView.current
    val connection by scope.vm.connection.collectAsStateWithLifecycle()
    val savedId = scope.values[Prefs.PTV_DEV_ID]
    val savedKey = scope.values[Prefs.PTV_API_KEY]
    var devId by rememberSaveable(savedId) { mutableStateOf(savedId) }
    var apiKey by rememberSaveable(savedKey) { mutableStateOf(savedKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    val builtIn = BuildConfig.PTV_DEV_ID.isNotBlank() && BuildConfig.PTV_API_KEY.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardHeading(
            Sym.Key,
            "PTV Timetable API",
            when {
                scope.settings.usingCustomCredentials -> "Using your saved developer ID"
                builtIn -> "Using credentials built into this app"
                else -> "Not configured — live data unavailable"
            },
        )
        OutlinedTextField(
            value = devId,
            onValueChange = { devId = it.trim() },
            label = { Text("Developer ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim() },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                HapticIconButton(if (showKey) Sym.VisibilityOff else Sym.Visibility, if (showKey) "Hide key" else "Show key", onClick = { showKey = !showKey })
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                HapticUtil.performConfirmHaptic(view)
                scope.vm.saveCredentials(devId, apiKey)
            }) { Text("Save") }
            FilledTonalButton(onClick = { scope.vm.testConnection(devId, apiKey) }, enabled = connection != ConnectionTest.Running) { Text("Test connection") }
            when (connection) {
                ConnectionTest.Running -> LoadingIndicator(Modifier.size(32.dp))
                ConnectionTest.Success -> Text("Connected ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else -> Unit
            }
        }
        (connection as? ConnectionTest.Failed)?.let {
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Request a free key from PTV (Timetable API) — your developer ID and key are stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The app's name, modes and a line about it. */
@Composable
internal fun AboutCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            TransportMode.entries.forEach { ModePictogram(it, size = 40.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Journey", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Live Victorian public transport, designed with Material 3 Expressive. Not affiliated with Public Transport Victoria.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The widget's favourites in its own order: drag the handles to reorder, and switch any off to leave it off the
 * widget. Saved straight away, and the widget redraws.
 */
@Composable
internal fun WidgetOrderSetting(scope: SettingsScope) {
    val favourites by scope.context.appContainer.favouritesRepository.favourites.collectAsStateWithLifecycle(emptyList())
    val order = scope.values[Prefs.WIDGET_ORDER]
    val hidden = scope.values[Prefs.WIDGET_HIDDEN].toSet()
    val arranged = remember(favourites, order) {
        com.ssethhyy.journey.data.repository.WidgetPrefs(order = order).arrange(favourites) { it.key }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.ssethhyy.journey.ui.components.LeadingIcon(Sym.DragIndicator)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Order and stops", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (arranged.isEmpty()) "Favourite a stop and it shows here" else "Drag to reorder, and switch off any you'd rather leave off the widget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (arranged.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ReorderableStops(
                items = arranged,
                hidden = hidden,
                onReorder = { keys -> scope.save(Prefs.WIDGET_ORDER, keys) },
                onShownChange = { key, shown ->
                    scope.save(Prefs.WIDGET_HIDDEN, if (shown) (hidden - key).toList() else (hidden + key).toList())
                },
            )
        }
    }
}

/** Stops reordered by dragging each row's handle, each with a switch to show or hide it. */
@Composable
private fun ReorderableStops(
    items: List<FavouriteStop>,
    hidden: Set<String>,
    onReorder: (List<String>) -> Unit,
    onShownChange: (String, Boolean) -> Unit,
) {
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

    Column(Modifier.padding(horizontal = 8.dp)) {
        order.forEachIndexed { index, fav ->
            val shift = when {
                dragIndex < 0 || index == dragIndex -> 0f
                index in (dragIndex + 1)..target -> -rowPx
                index in target until dragIndex -> rowPx
                else -> 0f
            }
            val animatedShift by animateFloatAsState(shift, label = "widget_reorder_shift")
            val lifted = index == dragIndex
            val shown = fav.key !in hidden
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
                    },
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
                Column(Modifier.weight(1f).graphicsLayer { alpha = if (shown) 1f else 0.5f }) {
                    Text(fav.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    (fav.linesSummary ?: fav.suburb)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Switch(
                    checked = shown,
                    onCheckedChange = {
                        HapticUtil.performLightHaptic(view)
                        onShownChange(fav.key, it)
                    },
                    modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = "Show ${fav.displayName} on the widget" },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Helpers the catalog's actions call
// ---------------------------------------------------------------------------------------------------------------

/** Android's permission for alerts that fill the screen, offered only while it's missing. */
@Composable
internal fun FullScreenPermissionSetting(scope: SettingsScope) {
    if (com.ssethhyy.journey.tracking.StopAlertNotifier.canShowFullScreen(scope.context)) return
    ActionItem(
        icon = Sym.Warning,
        title = "Allow full-screen alerts",
        description = "Android needs your OK before an app can fill the screen",
        onClick = { runCatching { scope.context.startActivity(com.ssethhyy.journey.tracking.StopAlertNotifier.fullScreenSettingsIntent(scope.context)) } },
    )
}

/** Asks the launcher to add the widget, or says how to when it can't. */
internal fun pinWidget(context: Context) {
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    val provider = android.content.ComponentName(context, com.ssethhyy.journey.widget.JourneyWidgetReceiver::class.java)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(context, "Long-press your home screen, tap Widgets, then find Journey", Toast.LENGTH_LONG).show()
    }
}

/** Shows the stop alert with sample details. */
internal fun previewStopAlert(scope: SettingsScope) {
    val sample = com.ssethhyy.journey.tracking.StopAlert(
        key = "preview",
        stopName = "Flinders Street Station",
        mode = TransportMode.TRAIN,
        instruction = com.ssethhyy.journey.tracking.StopAlertLogic.instruction(TransportMode.TRAIN),
        detail = "Platform 6",
        arrival = java.time.Instant.now().plusSeconds(90),
        routeLabel = "Frankston line",
    )
    com.ssethhyy.journey.tracking.StopAlertNotifier.show(scope.context, sample, vibrate = scope.values[Prefs.STOP_ALERT_VIBRATE])
}

internal fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
