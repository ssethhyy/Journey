/*
 * Feature Module: Map
 * File: JourneyMapPanel.kt
 * Description: The floating panel shown over the Map tab while exploring a planned journey. Each leg can be
 * tapped to zoom the map to it and see where to get on and off, how many stops it is and how long it takes,
 * with buttons to open those stops' pages.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.LegChip
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.formatDuration
import com.ssethhyy.journey.ui.components.hapticClick
import com.ssethhyy.journey.ui.components.mapColor
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.TimeFormatter

object JourneyLegText {
    /** "Walk 290 m to Westgarth Station", "Hurstbridge to Flinders Street". */
    fun title(leg: JourneyLeg, isLast: Boolean, destination: String): String = when (leg.kind) {
        LegKind.WALK -> buildString {
            append("Walk")
            leg.distanceMeters?.takeIf { it >= 20 }?.let { append(" ${DistanceFormatter.distance(it)}") }
            val to = if (isLast) destination else leg.toName
            if (to.isNotBlank()) append(" to $to")
        }
        else -> listOfNotNull(lineName(leg), leg.headsign?.takeIf { it.isNotBlank() }?.let { "to $it" }).joinToString(" ")
    }

    /** Trains are named by line ("Frankston line"), so a train to Frankston doesn't read "Frankston to Frankston". */
    fun lineName(leg: JourneyLeg): String = when (leg.mode) {
        com.ssethhyy.journey.data.model.TransportMode.TRAIN, com.ssethhyy.journey.data.model.TransportMode.VLINE ->
            if (leg.label.endsWith(" line", ignoreCase = true)) leg.label else "${leg.label} line"
        else -> leg.label
    }

    /** How many stops you ride, counting the one you get off at. */
    fun stopCount(leg: JourneyLeg): Int = leg.intermediateStops.size + 1
}

@Composable
fun JourneyMapPanel(
    journey: Journey,
    destination: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onOpenStop: (JourneyLeg, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val legs = journey.legs
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("To ${destination.ifBlank { "your destination" }}", style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(
                            "${TimeFormatter.clock(journey.start)} – ${TimeFormatter.clock(journey.end)}",
                            formatDuration(journey.durationMinutes),
                            when (journey.transfers) {
                                0 -> "No changes"
                                1 -> "1 change"
                                else -> "${journey.transfers} changes"
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected != null) {
                    TextButton(onClick = hapticClick { onSelect(null) }) { Text("Whole route") }
                }
                HapticIconButton(Sym.Close, "Close journey", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val listState = rememberLazyListState()
            // Bring the chosen leg, with its details and stop buttons, fully into view.
            LaunchedEffect(selected) { selected?.let { listState.animateScrollToItem(it) } }
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(legs, key = { index, leg -> "$index-${leg.start.epochSecond}" }) { index, leg ->
                    LegRow(
                        leg = leg,
                        title = JourneyLegText.title(leg, isLast = index == legs.lastIndex, destination = destination),
                        expanded = index == selected,
                        onClick = { onSelect(if (index == selected) null else index) },
                        onOpenStop = { boarding -> onOpenStop(leg, boarding) },
                    )
                }
            }
        }
    }
}

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

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
