/*
 * Feature Module: Plan
 * File: PlanScreen.kt
 * Description: Journey planner tab: from/to inputs, time options, saved places, recents and results.
 */

package com.ssethhyy.journey.ui.screens.plan

import com.ssethhyy.journey.ui.components.Sym
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.data.planner.PlanLocation
import com.ssethhyy.journey.data.planner.TimeMode
import com.ssethhyy.journey.data.repository.PlaceKind
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ActionItem
import com.ssethhyy.journey.ui.components.AutoRefreshEffect
import com.ssethhyy.journey.ui.components.ErrorState
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.JourneyCard
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.LocationSearchSheet
import com.ssethhyy.journey.ui.components.MessageState
import com.ssethhyy.journey.ui.components.PlaceEditorSheet
import com.ssethhyy.journey.ui.components.PlacesRow
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.SegmentedPicker
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.ui.screens.home.ToolbarClearance
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

private enum class Field { FROM, TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen() {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val now by rememberNow()
    var picking by rememberSaveable { mutableStateOf<Field?>(null) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<Pair<PlaceKind, SavedPlace?>?>(null) }

    // Live updates: re-plan every minute while results are on screen.
    AutoRefreshEffect(enabled = state.to != null && state.settings.autoRefresh, intervalMs = 60_000) { vm.liveRefresh() }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "Plan",
                subtitle = state.to?.let { "To ${it.name}" } ?: "Where are you going?",
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = ToolbarClearance + padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "route_input") {
                RouteInputCard(
                    from = state.from,
                    to = state.to,
                    onPickFrom = { picking = Field.FROM },
                    onPickTo = { picking = Field.TO },
                    onSwap = vm::swap,
                    onClearTo = vm::clearDestination,
                )
            }
            item(key = "time") {
                TimeOptions(
                    mode = state.timeMode,
                    time = state.time,
                    onMode = { mode ->
                        if (mode == TimeMode.LEAVE_NOW) vm.setTime(mode) else {
                            vm.setTime(mode, if (state.timeMode == TimeMode.LEAVE_NOW) Instant.now() else state.time)
                            showTimePicker = true
                        }
                    },
                    onEditTime = { showTimePicker = true },
                )
            }

            if (state.to == null) {
                item(key = "places_label") { SectionLabel("Your places", Modifier.padding(start = 0.dp)) }
                item(key = "places") {
                    PlacesRow(
                        places = state.places,
                        onOpen = { vm.setTo(it.toPlanLocation()) },
                        onEdit = { editingPlace = it.kind to it },
                        onCreate = { kind -> editingPlace = kind to null },
                        contentPadding = PaddingValues(0.dp),
                    )
                }
                if (state.recents.isNotEmpty()) {
                    item(key = "recents_label") {
                        SectionLabel("Recent journeys") { TextButton(onClick = { vm.clearRecents() }) { Text("Clear") } }
                    }
                    item(key = "recents") {
                        RoundedCardContainer {
                            state.recents.forEach { trip ->
                                ActionItem(
                                    icon = Sym.History,
                                    title = trip.toName,
                                    description = "From ${trip.fromName}",
                                    onClick = { vm.openRecent(trip) },
                                )
                            }
                        }
                    }
                } else {
                    item(key = "hint") {
                        MessageState(
                            icon = Sym.Directions,
                            title = "Plan a journey",
                            message = "Choose a destination to see the best trains, trams and buses, with live times and walking directions.",
                        )
                    }
                }
            } else {
                when (val r = state.results) {
                    null, LoadState.Loading -> item(key = "loading") { LoadingState(label = "Finding the best journeys") }
                    is LoadState.Error -> item(key = "error") { ErrorState(r.error, onRetry = vm::plan) }
                    is LoadState.Success -> {
                        item(key = "live_status") {
                            LiveStatusRow(updatedAt = state.updatedAt, refreshing = state.refreshing, onRefresh = { vm.plan(silent = true) })
                        }
                        if (r.data.journeys.isEmpty()) {
                            item(key = "empty") {
                                MessageState(Sym.EventBusy, "No journeys found", "Try a different time, or check the start and end points.")
                            }
                        }
                        items(r.data.journeys, key = { it.id }) { journey ->
                            JourneyCard(
                                journey = journey,
                                now = now,
                                onClick = { navigator.openJourney(journey.id, state.from.name, state.to?.name.orEmpty()) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (r.data.nextPageCursor != null && r.data.journeys.isNotEmpty()) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (state.loadingMore) {
                                        LoadingState(Modifier.height(96.dp))
                                    } else {
                                        FilledTonalButton(onClick = vm::loadMore) { Text("Later journeys") }
                                    }
                                }
                            }
                        }
                        item(key = "attribution") {
                            Text(
                                "Journey planning by Transitous (MOTIS) using Transport Victoria open data",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    picking?.let { field ->
        LocationSearchSheet(
            title = if (field == Field.FROM) "Start from" else "Going to",
            allowCurrentLocation = true,
            onDismiss = { picking = null },
            onPick = { location ->
                picking = null
                if (field == Field.FROM) vm.setFrom(location) else vm.setTo(location)
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = state.time,
            title = if (state.timeMode == TimeMode.ARRIVE_BY) "Arrive by" else "Depart at",
            onDismiss = { showTimePicker = false },
            onConfirm = { instant ->
                showTimePicker = false
                vm.setTime(if (state.timeMode == TimeMode.LEAVE_NOW) TimeMode.DEPART_AT else state.timeMode, instant)
            },
        )
    }

    editingPlace?.let { (kind, place) ->
        PlaceEditorSheet(initialKind = kind, existing = place, onDismiss = { editingPlace = null })
    }
}

@Composable
private fun RouteInputCard(
    from: PlanLocation,
    to: PlanLocation?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onClearTo: () -> Unit,
) {
    val view = LocalView.current
    var rotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(rotation, label = "swap_rotation")
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LocationField(
                    icon = if (from.isCurrentLocation) Sym.MyLocation else Sym.LocationOn,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = "From",
                    value = from.name,
                    placeholder = "Choose start",
                    onClick = onPickFrom,
                )
                HorizontalDivider(Modifier.padding(start = 56.dp, end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                LocationField(
                    icon = Sym.LocationOn,
                    iconTint = MaterialTheme.colorScheme.error,
                    label = "To",
                    value = to?.name,
                    placeholder = "Where to?",
                    onClick = onPickTo,
                    trailing = if (to != null) {
                        { HapticIconButton(Sym.Close, "Clear destination", onClick = onClearTo, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        null
                    },
                )
            }
            FilledTonalIconButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    rotation += 180f
                    onSwap()
                },
                enabled = to != null,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Icon(Sym.SwapVert, contentDescription = "Swap start and destination", modifier = Modifier.rotate(animatedRotation))
            }
        }
    }
}

@Composable
private fun LocationField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choose $label location") {
                HapticUtil.performUIHaptic(view)
                onClick()
            }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(iconTint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value ?: placeholder,
                style = MaterialTheme.typography.titleMedium,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun TimeOptions(mode: TimeMode, time: Instant, onMode: (TimeMode) -> Unit, onEditTime: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedPicker(
            items = TimeMode.entries,
            selectedItem = mode,
            onItemSelected = onMode,
            labelProvider = {
                when (it) {
                    TimeMode.LEAVE_NOW -> "Now"
                    TimeMode.DEPART_AT -> "Depart"
                    TimeMode.ARRIVE_BY -> "Arrive"
                }
            },
        )
        if (mode != TimeMode.LEAVE_NOW) {
            val zoned = time.atZone(TimeFormatter.MELBOURNE)
            val today = LocalDate.now(TimeFormatter.MELBOURNE)
            val day = when (zoned.toLocalDate()) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> zoned.toLocalDate().toString()
            }
            FilterChip(
                selected = true,
                onClick = onEditTime,
                label = { Text("${if (mode == TimeMode.ARRIVE_BY) "Arrive by" else "Depart at"} ${TimeFormatter.clock(time)} · $day") },
                leadingIcon = { Icon(Sym.Schedule, null, Modifier.size(18.dp)) },
                shape = CircleShape,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: Instant, title: String, onDismiss: () -> Unit, onConfirm: (Instant) -> Unit) {
    val zoned = initial.atZone(TimeFormatter.MELBOURNE)
    val pickerState = rememberTimePickerState(initialHour = zoned.hour, initialMinute = zoned.minute)
    var tomorrow by rememberSaveable { mutableStateOf(zoned.toLocalDate().isAfter(LocalDate.now(TimeFormatter.MELBOURNE))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text("Today") }, shape = CircleShape)
                    FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text("Tomorrow") }, shape = CircleShape)
                }
                Spacer(Modifier.height(12.dp))
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = LocalDate.now(TimeFormatter.MELBOURNE).plusDays(if (tomorrow) 1 else 0)
                val chosen = ZonedDateTime.of(date, LocalTime.of(pickerState.hour, pickerState.minute), TimeFormatter.MELBOURNE)
                onConfirm(chosen.toInstant())
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveStatusRow(updatedAt: Instant?, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.ssethhyy.journey.ui.components.LiveDot()
        Spacer(Modifier.width(8.dp))
        Text(
            updatedAt?.let { "Live · updated ${TimeFormatter.clock(it)}" } ?: "Live",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (refreshing) {
            androidx.compose.material3.LoadingIndicator(Modifier.size(32.dp))
        } else {
            HapticIconButton(com.ssethhyy.journey.ui.components.Sym.Refresh, "Refresh journeys", onClick = onRefresh, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
