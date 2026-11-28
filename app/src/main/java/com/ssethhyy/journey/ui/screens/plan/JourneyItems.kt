/*
 * Feature Module: Plan
 * File: JourneyItems.kt
 * Description: A planned journey as list items: when to leave and how long it takes, then its steps, then the rest.
 *
 * Shared by the map's journey panel and the journey page it pulls up into, so the panel turns into the page without
 * anything moving. [JourneyLiveViewModel] keeps the journey's times up to date for both.
 */

package com.ssethhyy.journey.ui.screens.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.tracking.TrackingLogic
import com.ssethhyy.journey.ui.components.JourneyTimeline
import com.ssethhyy.journey.ui.components.LegChip
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.formatDuration
import com.ssethhyy.journey.ui.components.hapticClick
import com.ssethhyy.journey.ui.components.mapColor
import com.ssethhyy.journey.ui.screens.map.JourneyLegText
import com.ssethhyy.journey.ui.sharedAppViewModel
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/** A planned journey, refreshed with live times while it's still to come or under way. */
class JourneyLiveViewModel(private val container: AppContainer, private val journeyId: String) : ViewModel() {
    private val _journey = MutableStateFlow(container.journeyRepository.journey(journeyId))
    val journey: StateFlow<Journey?> = _journey.asStateFlow()
    private val _updatedAt = MutableStateFlow<Instant?>(Instant.now())
    val updatedAt: StateFlow<Instant?> = _updatedAt.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { container.journeyRepository.refreshJourney(journeyId) }
                .onSuccess { updated ->
                    if (updated != null) {
                        _journey.value = updated
                        _updatedAt.value = Instant.now()
                    }
                }
            _refreshing.value = false
        }
    }
}

/** The journey's live view model, shared by its map panel and its page. */
@Composable
fun journeyLiveViewModel(journeyId: String): JourneyLiveViewModel =
    sharedAppViewModel(key = "journey_$journeyId") { JourneyLiveViewModel(it, journeyId) }

/** The words at the top of a journey: when to leave, and the times either end. */
object JourneySummaryText {
    /** "Leave in 12 min", "Leave now", "On your way", "This trip has finished". */
    fun leave(journey: Journey, now: Instant): String = when {
        now.isBefore(journey.start) -> TrackingLogic.countdown(journey.start, now).let { if (it == "Now") "Leave now" else "Leave in $it" }
        now.isBefore(journey.end) -> "On your way"
        else -> "This trip has finished"
    }

    /** "Leave 8:12 · Arrive 8:45 · 33 min". */
    fun times(journey: Journey): String =
        "Leave ${TimeFormatter.clock(journey.start)} · Arrive ${TimeFormatter.clock(journey.end)} · ${formatDuration(journey.durationMinutes)}"

    /** "Catch the 8:20 Frankston line from platform 2", or null for a walk-only trip. */
    fun firstService(journey: Journey): String? {
        val leg = journey.firstTransit ?: return null
        return buildString {
            append("Catch the ${TimeFormatter.clock(leg.start)} ${JourneyLegText.lineName(leg)}")
            leg.platform?.let { append(" from platform $it") } ?: leg.fromName.takeIf { it.isNotBlank() }?.let { append(" at $it") }
        }
    }

    fun changes(journey: Journey): String = when (journey.transfers) {
        0 -> "No changes"
        1 -> "1 change"
        else -> "${journey.transfers} changes"
    }
}

/** Items before the first step, so the panel can scroll a chosen step into view. */
const val JOURNEY_FIRST_STEP_ITEM = 2

/**
 * The journey's list items: the summary (when to leave, how long it takes), its steps, then the stop-by-stop
 * detail and where the times come from.
 */
fun LazyListScope.journeyItems(
    journey: Journey,
    destination: String,
    now: Instant,
    updatedAt: Instant?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onOpenStop: (JourneyLeg, Boolean) -> Unit,
) {
    item(key = "summary") { JourneySummary(journey, now, updatedAt, refreshing, onRefresh) }
    item(key = "steps_label") { SectionLabel("Steps") }
    val legs = journey.legs
    itemsIndexed(legs, key = { index, leg -> "leg-$index-${leg.kind}" }) { index, leg ->
        LegRow(
            leg = leg,
            title = JourneyLegText.title(leg, isLast = index == legs.lastIndex, destination = destination),
            expanded = index == selected,
            onClick = { onSelect(if (index == selected) null else index) },
            onOpenStop = { boarding -> onOpenStop(leg, boarding) },
        )
    }
    item(key = "timeline_label") { SectionLabel("Stop by stop") }
    item(key = "timeline") {
        RoundedCardContainer(spacing = 0.dp) {
            Box(Modifier.padding(vertical = 12.dp)) {
                JourneyTimeline(journey, destinationName = destination, onStopClick = onOpenStop)
            }
        }
    }
    item(key = "attribution") {
        Text(
            "Times include live updates where available. Planning by Transitous using Transport Victoria open data.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun JourneySummary(journey: Journey, now: Instant, updatedAt: Instant?, refreshing: Boolean, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(JourneySummaryText.leave(journey, now), style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(JourneySummaryText.times(journey), style = MaterialTheme.typography.bodyLarge)
        JourneySummaryText.firstService(journey)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val chipColors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            AssistChip(onClick = {}, label = { Text(JourneySummaryText.changes(journey)) }, leadingIcon = { Icon(Sym.SyncAlt, null, Modifier.size(18.dp)) }, shape = CircleShape, colors = chipColors, border = null)
            if (journey.walkMeters >= 50) {
                AssistChip(
                    onClick = {},
                    label = { Text("${DistanceFormatter.distance(journey.walkMeters)} walking") },
                    leadingIcon = { Icon(Sym.DirectionsWalk, null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                    colors = chipColors,
                    border = null,
                )
            }
        }
        LiveStatusRow(updatedAt = updatedAt, refreshing = refreshing, onRefresh = onRefresh)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LegRow(leg: JourneyLeg, title: String, expanded: Boolean, onClick: () -> Unit, onOpenStop: (Boolean) -> Unit) {
    val transit = leg.kind != LegKind.WALK
    Surface(
        onClick = hapticClick(onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val mode = leg.mode
                if (transit && mode != null) {
                    ModePictogram(mode, size = 32.dp)
                } else {
                    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Sym.DirectionsWalk, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${TimeFormatter.clock(leg.start)} – ${TimeFormatter.clock(leg.end)} · ${formatDuration(leg.durationMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The leg's colour on the map, so the row and the line are easy to match up.
                Box(Modifier.size(width = 6.dp, height = 28.dp).background(leg.mapColor(), RoundedCornerShape(3.dp)))
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 10.dp, start = 44.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (transit) {
                        Row(verticalAlignment = Alignment.CenterVertically) { LegChip(leg) }
                        Detail("Get on", listOfNotNull(leg.fromName.ifBlank { null }, leg.platform?.let { "Platform $it" }, TimeFormatter.clock(leg.start)).joinToString(" · "))
                        val stops = JourneyLegText.stopCount(leg)
                        Detail("Ride", "$stops ${if (stops == 1) "stop" else "stops"} · ${formatDuration(leg.durationMinutes)}")
                        Detail("Get off", listOfNotNull(leg.toName.ifBlank { null }, leg.arrivalPlatform?.let { "Platform $it" }, TimeFormatter.clock(leg.end)).joinToString(" · "))
                        if (leg.intermediateStops.isNotEmpty()) {
                            Text(
                                "Via " + leg.intermediateStops.take(6).joinToString(", ") + if (leg.intermediateStops.size > 6) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                            FilledTonalButton(onClick = hapticClick { onOpenStop(true) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f)) {
                                Text(leg.fromName.ifBlank { "Get-on stop" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(onClick = hapticClick { onOpenStop(false) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f)) {
                                Text(leg.toName.ifBlank { "Get-off stop" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Detail("Walk", listOfNotNull(leg.distanceMeters?.takeIf { it > 0 }?.let(DistanceFormatter::distance), formatDuration(leg.durationMinutes)).joinToString(" · "))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
