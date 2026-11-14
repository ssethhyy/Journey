/*
 * Feature Module: Commutes
 * File: CommutesSection.kt
 * Description: The Commutes section on For You: each commute shows when to leave, a backup if that is missed,
 * and buttons to track it or open it in the planner. Also the sheet for adding and editing your own.
 */

package com.ssethhyy.journey.ui.screens.favourites

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.commute.Commute
import com.ssethhyy.journey.data.commute.CommuteEnd
import com.ssethhyy.journey.data.commute.CommuteKind
import com.ssethhyy.journey.data.commute.CommuteLogic
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.tracking.TrackingLogic
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.components.LocationSearchSheet
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.formatDuration
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant

/** What the user can do from a commute card. */
class CommuteActions(
    val onPlan: (Commute) -> Unit,
    val onOpen: (Commute, Journey) -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (Commute) -> Unit,
    val onRemove: (Commute) -> Unit,
)

fun LazyListScope.commutesSection(
    state: CommutesUiState,
    now: Instant,
    actions: CommuteActions,
    config: com.ssethhyy.journey.data.foryou.CommutesConfig = com.ssethhyy.journey.data.foryou.CommutesConfig(),
    showTitle: Boolean = true,
    collapsed: Boolean = false,
    onToggle: () -> Unit = {},
) {
    if (showTitle) {
        item(key = "commutes_header") {
            WidgetTitle(
                "Commutes",
                collapsed = collapsed,
                onToggle = onToggle,
                modifier = Modifier.animateItem(),
                trailing = if (config.showAddButton) {
                    { TitleAddButton("Add", actions.onAdd) }
                } else null,
            )
        }
        if (collapsed) return
    }
    val cards = state.cards.filter { config.showLearned || it.commute.manual }.take(config.maxShown)
    if (cards.isEmpty()) {
        item(key = "commutes_empty") {
            Surface(onClick = actions.onAdd, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().animateItem()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CommuteBadge(CommuteKind.OTHER)
                    Column {
                        Text("No commutes yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add one, or plan the same trip a couple of times and Journey will suggest it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else {
        items(cards.size, key = { "commute_${cards[it].commute.id}" }) { index ->
            CommuteCard(cards[index], now, actions, config.showBackup, Modifier.animateItem())
        }
    }
}

@Composable
private fun CommuteBadge(kind: CommuteKind) {
    Box(
        Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when (kind) {
                CommuteKind.HOME -> Sym.Home
                CommuteKind.WORK -> Sym.Work
                CommuteKind.OTHER -> Sym.Directions
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CommuteCard(card: CommuteCardState, now: Instant, actions: CommuteActions, showBackup: Boolean, modifier: Modifier = Modifier) {
    val commute = card.commute
    val view = LocalView.current
    var menuOpen by remember { mutableStateOf(false) }
    val trip = (card.trip as? LoadState.Success)?.data
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CommuteBadge(commute.kind)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(commute.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            "From ${commute.fromLabel}",
                            commute.usualHour?.let { "Usually ${CommuteLogic.usualText(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    com.ssethhyy.journey.ui.components.HapticIconButton(Sym.MoreVert, "More options for ${commute.title}", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(16.dp)) {
                        if (commute.manual) {
                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Sym.Edit, null) }, onClick = {
                                menuOpen = false
                                actions.onEdit(commute)
                            })
                        }
                        DropdownMenuItem(
                            text = { Text(if (commute.manual) "Remove" else "Don't suggest this") },
                            leadingIcon = { Icon(Sym.Delete, null) },
                            onClick = {
                                menuOpen = false
                                actions.onRemove(commute)
                            },
                        )
                    }
                }
            }
            when (val t = card.trip) {
                null, LoadState.Loading -> Text("Finding your next trip…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                is LoadState.Error -> Text("Couldn't plan this one right now. Pull down to try again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                is LoadState.Success -> if (trip == null) {
                    Text("No trips found right now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                } else {
                    PrimaryTrip(trip.primary, now, onOpen = { actions.onOpen(commute, trip.primary) })
                    trip.backup?.takeIf { showBackup }?.let { BackupTrip(commute, it, trip.primary, onOpen = { actions.onOpen(commute, it) }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                        TrackTripButton(commute, trip.primary, label = "Track", modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                HapticUtil.performUIHaptic(view)
                                actions.onPlan(commute)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Sym.Directions, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Plan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryTrip(journey: Journey, now: Instant, onOpen: () -> Unit) {
    val countdown = TrackingLogic.countdown(journey.start, now)
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (countdown == "Now") "Leave now" else "Leave in $countdown",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    "Arrive ${TimeFormatter.clock(journey.end)} · ${formatDuration(journey.durationMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                journey.transitLegs.take(3).forEach { leg ->
                    leg.mode?.let { ModePictogram(it, size = 22.dp) }
                    Text(leg.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                journey.firstTransit?.platform?.let {
                    Text("Platform $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The next trip after the main one, for when it is missed. */
@Composable
private fun BackupTrip(commute: Commute, backup: Journey, primary: Journey, onOpen: () -> Unit) {
    val later = (java.time.Duration.between(primary.start, backup.start).toMinutes()).coerceAtLeast(1)
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.History, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("If you miss it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Leave ${TimeFormatter.clock(backup.start)} · $later min later",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Arrive ${TimeFormatter.clock(backup.end)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrackTripButton(commute, backup, label = "Track", compact = true)
        }
    }
}

/** Starts or stops following [journey] as a tracked trip. */
@Composable
private fun TrackTripButton(commute: Commute, journey: Journey, label: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val manager = LocalContext.current.appContainer.trackingManager
    val view = LocalView.current
    val current by manager.target.collectAsState()
    val target = TrackingTarget.Trip(journey.id, if (commute.from.isCurrent) "Your location" else commute.from.name, commute.to.name)
    val tracking = current?.id == target.id
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Track regardless; without permission the in-app banner still shows progress.
        manager.start(target)
    }
    val onClick = {
        if (tracking) {
            HapticUtil.performUIHaptic(view)
            manager.stop()
        } else {
            HapticUtil.performConfirmHaptic(view)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !manager.canPostNotifications()) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                manager.start(target)
            }
        }
    }
    val text = if (tracking) "Stop" else label
    if (compact) {
        TextButton(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier) {
            Icon(if (tracking) Sym.NotificationsOff else Sym.NotificationsActive, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (tracking) "Stop tracking" else label)
        }
    }
}

/** Add or edit one of your own commutes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommuteEditorSheet(existing: Commute?, newId: () -> String, onSave: (Commute) -> Unit, onDismiss: () -> Unit) {
    val view = LocalView.current
    var name by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var from by remember { mutableStateOf(existing?.from ?: CommuteEnd(name = "Current location", isCurrent = true)) }
    var to by remember { mutableStateOf(existing?.to) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = to

    StandardBottomSheet(
        onDismissRequest = onDismiss,
        title = if (existing == null) "Add commute" else "Edit commute",
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("Name (optional)") },
            placeholder = { Text(to?.let { CommuteLogic.title(it) } ?: "e.g. To work") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        EndPicker("From", from.name, from.subtitle ?: if (from.isCurrent) "Wherever you are when you open Journey" else null) { picking = true }
        EndPicker("To", to?.name ?: "Choose a stop, station or place", to?.subtitle) { picking = false }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Button(
                enabled = to != null,
                onClick = {
                    val destination = to ?: return@Button
                    HapticUtil.performConfirmHaptic(view)
                    onSave(
                        Commute(
                            id = existing?.id ?: newId(),
                            title = name.trim().ifBlank { CommuteLogic.title(destination) },
                            from = from,
                            to = destination,
                            manual = true,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save commute") }
        }
    }

    picking?.let { isFrom ->
        LocationSearchSheet(
            title = if (isFrom) "Start from" else "Going to",
            allowCurrentLocation = isFrom,
            onDismiss = { picking = null },
            onPick = { location ->
                if (isFrom) from = CommuteEnd.from(location) else to = CommuteEnd.from(location)
                picking = null
            },
        )
    }
}

@Composable
private fun EndPicker(label: String, value: String, supporting: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
