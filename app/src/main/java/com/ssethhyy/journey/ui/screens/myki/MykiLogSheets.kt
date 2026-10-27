/*
 * Feature Module: myki
 * File: MykiLogSheets.kt
 * Description: Logging trips against a myki. One sheet logs a planned journey from Plan; another logs
 * a trip (or a custom amount) from a myki's own page. Both work out the fare with the fare engine,
 * let the user change the zone or amount, and deduct it from the chosen myki (the default one to start).
 */

package com.ssethhyy.journey.ui.screens.myki

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.fare.FareQuote
import com.ssethhyy.journey.data.fare.FareReason
import com.ssethhyy.journey.data.fare.FareTrip
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.ResolvedTrip
import com.ssethhyy.journey.data.myki.formatDollars
import com.ssethhyy.journey.data.myki.parseDollars
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Toasts must be made on the main thread, whichever dispatcher the caller resumed on. */
private fun showToast(context: android.content.Context, message: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
}

private fun cardLabel(card: MykiCard) = buildString {
    append(card.nickname)
    if (card.isDefault) append(" · Default")
}

private fun cardSupporting(card: MykiCard) = buildString {
    append(card.fareType.label)
    card.balanceCents?.let { append(" · ").append(formatDollars(it)) }
}

/** The default myki is selected first; the dropdown switches to another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MykiPicker(cards: List<MykiCard>, selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.id == selectedId } ?: cards.firstOrNull()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && cards.size > 1 }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let(::cardLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Deduct from") },
            supportingText = selected?.let { { Text(cardSupporting(it)) } },
            leadingIcon = { Icon(Sym.CreditCard, null) },
            trailingIcon = { if (cards.size > 1) Icon(if (expanded) Sym.ExpandLess else Sym.ExpandMore, contentDescription = null) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cards.forEach { card ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(cardLabel(card))
                            Text(cardSupporting(card), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Icon(if (card.isDefault) Sym.StarFilled else Sym.CreditCard, null) },
                    onClick = {
                        expanded = false
                        onSelect(card.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoMykiNotice(onDismiss: () -> Unit) {
    val navigator = currentNavigator
    Text(
        "Add a myki first, then Journey can deduct fares from it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = { onDismiss(); navigator.openMyki() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Sym.AddCard, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add a myki")
    }
}

/**
 * The fare for one trip on one myki: what it costs and why, the zone (changeable), the amount
 * (changeable) and the button that deducts it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripFareSection(
    card: MykiCard,
    resolved: ResolvedTrip?,
    resolving: Boolean,
    logTitle: String,
    logDetail: String?,
    onLogged: () -> Unit,
) {
    val context = LocalContext.current
    val manager = context.appContainer.mykiManager
    val scope = rememberCoroutineScope()
    var zoneOverride by rememberSaveable(resolved?.trip?.startMillis) { mutableStateOf<FareZone?>(null) }
    var amountText by rememberSaveable(card.id, resolved?.trip?.startMillis) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    if (resolved == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (resolving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Checking fare zones…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("There are no train, tram or bus legs to charge.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val trip: FareTrip = remember(resolved, zoneOverride) { zoneOverride?.let { manager.withZone(resolved, it) } ?: resolved.trip }
    val quote by produceState<FareQuote?>(null, card, trip) { value = manager.quote(card, trip) }
    val q = quote
    if (q == null) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        return
    }

    val typed = amountText?.let(::parseDollars)
    val charge: Long? = if (amountText != null) typed else q.chargeCents
    val manual = q.reason == FareReason.MANUAL
    val amountError = amountText != null && typed == null

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Fare", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    Text(
                        charge?.let { formatDollars(it) } ?: "Enter amount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val list = q.listPriceCents
                if (list != null && charge != null && list > charge && amountText == null) {
                    Text(
                        formatDollars(list),
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            q.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            val after = card.balanceCents?.let { b -> charge?.let { b - it } }
            Text(
                when {
                    after != null -> "Balance after: ${formatDollars(after)}"
                    card.balanceCents == null -> "Set a balance on this myki to track what's left"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (after != null && card.lowBalanceCents > 0 && after < card.lowBalanceCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
        }
    }

    if (!manual) {
        Text("Fare zone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FareZone.ZONE_1_2, FareZone.ZONE_2).forEach { zone ->
                FilterChip(selected = trip.zone == zone, onClick = { zoneOverride = zone }, label = { Text(zone.label) })
            }
            if (trip.zone == FareZone.FREE_TRAM) FilterChip(selected = true, onClick = {}, label = { Text(FareZone.FREE_TRAM.label) })
        }
        Text(
            if (resolved.zonesKnown) "Zones come from PTV's stop data. A trip through Zone 1 between two Zone 2 stops costs Zone 1+2."
            else "Couldn't check the zones, so Zone 1+2 is assumed. Change it if you travelled within Zone 2 only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (amountText != null || manual) {
        OutlinedTextField(
            value = amountText.orEmpty(),
            onValueChange = { amountText = it.take(9) },
            label = { Text("Amount to deduct") },
            prefix = { Text("$") },
            isError = amountError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!manual) TextButton(onClick = { amountText = null }) { Text("Use the calculated fare") }
    } else {
        TextButton(onClick = { amountText = q.chargeCents?.let { formatDollars(it).removePrefix("$") } ?: "" }) { Text("Edit amount") }
    }

    Button(
        onClick = {
            val cents = charge ?: return@Button
            saving = true
            scope.launch {
                val after = manager.logTrip(card.id, trip, q, logTitle, logDetail, amountOverride = if (amountText != null) cents else null)
                saving = false
                val left = after?.balanceCents?.let { " · ${formatDollars(it)} left" }.orEmpty()
                showToast(context, "Deducted ${formatDollars(cents)} from ${card.nickname}$left")
                onLogged()
            }
        },
        enabled = charge != null && !amountError && !saving,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(charge?.let { if (it == 0L) "Log trip (free)" else "Log trip · ${formatDollars(it)}" } ?: "Log trip") }
}

/** Log a journey planned in Plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogJourneySheet(journey: Journey, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val cards by container.mykiRepository.cards.collectAsStateWithLifecycle(initialValue = null)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val resolved by produceState<ResolvedTrip?>(null, journey.id) { value = container.mykiManager.tripFor(journey) }
    var resolving by remember { mutableStateOf(true) }
    LaunchedEffect(resolved) { if (resolved != null) resolving = false else { delay(8_000); resolving = false } }

    val transit = journey.legs.filter { it.kind == LegKind.TRANSIT }
    val title = if (transit.isEmpty()) "Journey" else "${transit.first().fromName} to ${transit.last().toName}"
    val detail = transit.joinToString(" · ") { it.label }

    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = "Log trip") {
        val list = cards
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                list == null -> CircularProgressIndicator(Modifier.size(24.dp))
                list.isEmpty() -> NoMykiNotice(onDismiss)
                else -> {
                    val card = list.firstOrNull { it.id == selectedId } ?: list.first()
                    MykiPicker(list, card.id, onSelect = { selectedId = it })
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            transit.forEach { leg ->
                                Text("${leg.label}: ${leg.fromName} → ${leg.toName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${TimeFormatter.clock(journey.start)} – ${TimeFormatter.clock(journey.end)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TripFareSection(card, resolved, resolving, title, detail, onLogged = onDismiss)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val loggableModes = listOf(TransportMode.TRAIN, TransportMode.TRAM, TransportMode.BUS, TransportMode.VLINE)

private enum class LogKind { TRIP, CUSTOM }

/** Log a trip between two stops, or just a custom amount, on one myki's page. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogManualTripSheet(card: MykiCard, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val cards by container.mykiRepository.cards.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedId by rememberSaveable { mutableStateOf(card.id) }
    val active = cards.firstOrNull { it.id == selectedId } ?: card

    var kind by rememberSaveable { mutableStateOf(LogKind.TRIP) }
    var mode by rememberSaveable { mutableStateOf(TransportMode.TRAIN) }
    var from by remember { mutableStateOf<Stop?>(null) }
    var to by remember { mutableStateOf<Stop?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = to
    var minutesAgo by rememberSaveable { mutableStateOf(0) }
    var customAmount by rememberSaveable { mutableStateOf("") }
    var customNote by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val startMillis = remember(minutesAgo) { System.currentTimeMillis() - minutesAgo * 60_000L }
    val resolved by produceState<ResolvedTrip?>(null, mode, from, to, startMillis) {
        val a = from
        val b = to
        value = if (a != null && b != null) container.mykiManager.tripFor(mode, a, b, startMillis) else null
    }

    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = "Log a trip") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val choosing = picking
            if (choosing != null) {
                StopSearch(
                    mode = mode,
                    title = if (choosing) "From which stop?" else "To which stop?",
                    onPick = { stop ->
                        if (choosing) from = stop else to = stop
                        picking = null
                    },
                    onBack = { picking = null },
                )
            } else {
                MykiPicker(cards.ifEmpty { listOf(card) }, active.id, onSelect = { selectedId = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == LogKind.TRIP, onClick = { kind = LogKind.TRIP }, label = { Text("Trip fare") }, leadingIcon = { Icon(Sym.Route, null, Modifier.size(18.dp)) })
                    FilterChip(selected = kind == LogKind.CUSTOM, onClick = { kind = LogKind.CUSTOM }, label = { Text("Custom amount") }, leadingIcon = { Icon(Sym.Toll, null, Modifier.size(18.dp)) })
                }
                if (kind == LogKind.TRIP) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        loggableModes.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = {
                                    if (mode != m) {
                                        mode = m
                                        from = null
                                        to = null
                                    }
                                },
                                label = { Text(m.label) },
                            )
                        }
                    }
                    StopField("From", from) { picking = true }
                    StopField("To", to) { picking = false }
                    Text("When", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Now", 30 to "30 min ago", 60 to "1 hour ago", 120 to "2 hours ago").forEach { (m, label) ->
                            FilterChip(selected = minutesAgo == m, onClick = { minutesAgo = m }, label = { Text(label) })
                        }
                    }
                    when {
                        from == null || to == null -> Text(
                            "Pick the stops to work out the fare, or switch to a custom amount.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> TripFareSection(
                            card = active,
                            resolved = resolved,
                            resolving = resolved == null,
                            logTitle = "${from!!.name} to ${to!!.name}",
                            logDetail = "${mode.label} · ${TimeFormatter.clock(java.time.Instant.ofEpochMilli(startMillis))}",
                            onLogged = onDismiss,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it.take(9) },
                        label = { Text("Amount to deduct") },
                        prefix = { Text("$") },
                        isError = customAmount.isNotBlank() && parseDollars(customAmount) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { customNote = it.take(40) },
                        label = { Text("What was it for? (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val cents = parseDollars(customAmount)
                    Button(
                        onClick = {
                            val amount = cents ?: return@Button
                            saving = true
                            scope.launch {
                                val after = container.mykiManager.deductCustom(active.id, amount, customNote.trim())
                                showToast(
                                    context,
                                    "Deducted ${formatDollars(amount)} from ${active.nickname}${after?.balanceCents?.let { " · ${formatDollars(it)} left" }.orEmpty()}",
                                )
                                onDismiss()
                            }
                        },
                        enabled = cents != null && cents > 0 && !saving,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(cents?.takeIf { it > 0 }?.let { "Deduct ${formatDollars(it)}" } ?: "Deduct") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StopField(label: String, stop: Stop?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stop?.name ?: "Choose a stop", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Icon(Sym.ChevronRight, null)
    }
}

/** Searches PTV stops of one mode by name. */
@Composable
private fun StopSearch(mode: TransportMode, title: String, onPick: (Stop) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    var term by remember { mutableStateOf("") }
    val results by produceState<List<Stop>>(emptyList(), term, mode) {
        if (term.trim().length < 2) {
            value = emptyList()
            return@produceState
        }
        delay(300)
        value = runCatching { container.ptvRepository.search(term, setOf(mode)).stops }.getOrDefault(emptyList())
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
    OutlinedTextField(
        value = term,
        onValueChange = { term = it },
        label = { Text("Search ${mode.label.lowercase()} stops") },
        leadingIcon = { Icon(Sym.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (results.isEmpty()) {
        Text(
            if (term.trim().length < 2) "Type a stop name" else "No stops found",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(results.take(30), key = { it.key }) { stop ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .then(Modifier),
                ) {
                    TextButton(onClick = { onPick(stop) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            stop.suburb?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
