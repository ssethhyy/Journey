/*
 * Feature Module: Map
 * File: JourneyMapPanel.kt
 * Description: The floating panel shown over the Map tab while exploring a planned journey. Each leg can be
 * tapped to zoom the map to it and see where to get on and off, how many stops it is and how long it takes,
 * with buttons to open those stops' pages.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.ui.screens.plan.journeyItems
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
    /** Where it starts and ends, as the planner named them. */
    origin: String,
    destination: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onOpenStop: (JourneyLeg, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 0 at open to 1 at full page, as it moves. */
    onFullProgress: (Float) -> Unit = {},
    /** The journey's own page, opened when the panel is pulled all the way up, where the panel was scrolled to. */
    onFullPage: ((SheetScroll) -> Unit)? = null,
) {
    val live = com.ssethhyy.journey.ui.screens.plan.journeyLiveViewModel(journey.id)
    val liveJourney by live.journey.collectAsStateWithLifecycle()
    val updatedAt by live.updatedAt.collectAsStateWithLifecycle()
    val refreshing by live.refreshing.collectAsStateWithLifecycle()
    val shown = liveJourney ?: journey
    val now by com.ssethhyy.journey.ui.components.rememberNow()
    // Live times while the journey is still to come or under way.
    com.ssethhyy.journey.ui.components.AutoRefreshEffect(enabled = shown.end.isAfter(now), intervalMs = 30_000) { live.refresh() }
    var step by androidx.compose.runtime.saveable.rememberSaveable(journey.id) { androidx.compose.runtime.mutableStateOf(SheetStep.OPEN) }
    val listState = rememberLazyListState()
    // Bring the chosen step, with its details and stop buttons, into view.
    LaunchedEffect(selected) { selected?.let { listState.animateScrollToItem(com.ssethhyy.journey.ui.screens.plan.JOURNEY_FIRST_STEP_ITEM + it) } }
    val trackFab = if (!shown.isWalkOnly) {
        com.ssethhyy.journey.ui.components.rememberTrackFab(com.ssethhyy.journey.tracking.TrackingTarget.Trip(journey.id, origin, destination), "Track journey")
    } else {
        null
    }
    // Pull it up into the journey's page, or down to just its name.
    MapSheet(
        step = step,
        onStepChange = { step = it },
        name = "journey",
        modifier = modifier,
        openHeight = if (selected == null) 330.dp else 420.dp,
        onFullProgress = onFullProgress,
        onFullPage = onFullPage,
        listState = listState,
        panelPadding = 8.dp,
        pagePadding = 16.dp,
        spacing = 6.dp,
        header = {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("To ${destination.ifBlank { "your destination" }}", style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "From ${origin.ifBlank { "your start" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected != null) {
                    TextButton(onClick = hapticClick { onSelect(null) }) { Text("Whole route") }
                }
                trackFab?.let { HapticIconButton(it.icon, it.contentDescription, onClick = it.onClick, tint = MaterialTheme.colorScheme.primary) }
                HapticIconButton(Sym.Close, "Close journey", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        journeyItems(
            journey = shown,
            destination = destination,
            now = now,
            updatedAt = updatedAt,
            refreshing = refreshing,
            onRefresh = live::refresh,
            selected = selected,
            onSelect = onSelect,
            onOpenStop = onOpenStop,
        )
    }
}
