/*
 * Feature Module: UI Components
 * File: JourneyComponents.kt
 * Description: Journey summary cards, leg chips and the leg-by-leg timeline.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.ui.theme.onColor
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant

fun JourneyLeg.badgeColor(): Color = mode?.color ?: routeColor?.let { Color(it) } ?: Color(0xFF5F6368)
fun JourneyLeg.badgeContentColor(): Color = mode?.onColor ?: routeTextColor?.let { Color(it) } ?: Color.White

/** Compact chip for one leg: walk minutes or a coloured route badge. */
@Composable
fun LegChip(leg: JourneyLeg, modifier: Modifier = Modifier) {
    if (leg.kind == LegKind.WALK) {
        Row(
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .semantics { contentDescription = "Walk ${leg.durationMinutes} minutes" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Sym.DirectionsWalk, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${leg.durationMinutes}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Row(
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(leg.badgeColor())
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leg.mode?.icon ?: Sym.DirectionsTransit, null, Modifier.size(15.dp), tint = leg.badgeContentColor())
            Spacer(Modifier.width(4.dp))
            Text(
                leg.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = leg.badgeContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Summary card for a planned journey. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyCard(journey: Journey, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val first = journey.firstTransit
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${TimeFormatter.clock(journey.start)} – ${TimeFormatter.clock(journey.end)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val leave = when {
                        journey.isWalkOnly -> "Walk all the way"
                        first != null -> {
                            val mins = TimeFormatter.minutesUntil(first.start, now)
                            val stop = first.fromName.ifBlank { "your stop" }
                            if (mins <= 0) "Departs now from $stop" else "Departs in $mins min from $stop"
                        }
                        else -> ""
                    }
                    Text(leave, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDuration(journey.durationMinutes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (first?.realTime == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot()
                            Spacer(Modifier.width(4.dp))
                            Text("Live", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                journey.legs.filter { it.kind != LegKind.WALK || it.durationMinutes >= 2 || journey.isWalkOnly }.forEachIndexed { index, leg ->
                    if (index > 0) {
                        Icon(Sym.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    LegChip(leg)
                }
            }
            val details = buildList {
                first?.platform?.let { add("Platform $it") }
                add(
                    when (journey.transfers) {
                        0 -> if (journey.isWalkOnly) "No transport" else "Direct"
                        1 -> "1 change"
                        else -> "${journey.transfers} changes"
                    },
                )
                if (journey.walkMeters >= 50) add("${DistanceFormatter.distance(journey.walkMeters)} walk")
                first?.departureDelayMinutes?.let { TimeFormatter.punctuality(it) }?.let { add(it) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (journey.hasCancellation) {
                    Icon(Sym.Warning, "Includes a cancelled service", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60}"
}

/** Leg-by-leg breakdown of a journey. */
@Composable
fun JourneyTimeline(journey: Journey, destinationName: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        journey.legs.forEachIndexed { index, leg ->
            when (leg.kind) {
                LegKind.WALK -> WalkLegRow(leg, isLast = index == journey.legs.lastIndex, destinationName = destinationName)
                else -> TransitLegBlock(leg)
            }
        }
        StopPoint(time = journey.end, title = destinationName.ifBlank { "Arrive" }, subtitle = "Arrive", color = MaterialTheme.colorScheme.primary, isEnd = true)
    }
}

@Composable
private fun WalkLegRow(leg: JourneyLeg, isLast: Boolean, destinationName: String) {
    val to = if (isLast) destinationName else leg.toName
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp))
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) {
                    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.DirectionsWalk, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append("Walk ${leg.durationMinutes} min")
                    leg.distanceMeters?.takeIf { it > 0 }?.let { append(" (${DistanceFormatter.distance(it)})") }
                    if (to.isNotBlank()) append(" to $to")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransitLegBlock(leg: JourneyLeg) {
    val view = LocalView.current
    var showStops by rememberSaveable(leg.start.epochSecond, leg.label) { mutableStateOf(false) }
    val lineColor = leg.badgeColor()
    StopPoint(
        time = leg.start,
        title = leg.fromName,
        subtitle = leg.platform?.let { "Platform $it" },
        color = lineColor,
        delayMinutes = leg.departureDelayMinutes,
        realTime = leg.realTime,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.width(56.dp))
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(lineColor, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceBright)
                .clickable(enabled = leg.intermediateStops.isNotEmpty()) {
                    HapticUtil.performUIHaptic(view)
                    showStops = !showStops
                }
                .padding(12.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegChip(leg)
                Spacer(Modifier.width(8.dp))
                Text(
                    leg.headsign?.let { "towards $it" } ?: leg.routeLongName.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (leg.cancelled) {
                Text("This service is cancelled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val stopCount = leg.intermediateStops.size + 1
                Text(
                    "$stopCount ${if (stopCount == 1) "stop" else "stops"} · ${formatDuration(leg.durationMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (leg.intermediateStops.isNotEmpty()) {
                    Icon(
                        if (showStops) Sym.ExpandLess else Sym.ExpandMore,
                        if (showStops) "Hide stops" else "Show stops",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (showStops) {
                leg.intermediateStops.forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    StopPoint(
        time = leg.end,
        title = leg.toName,
        subtitle = leg.arrivalPlatform?.let { "Platform $it" } ?: "Get off",
        color = lineColor,
    )
}

@Composable
private fun StopPoint(
    time: Instant,
    title: String,
    subtitle: String?,
    color: Color,
    isEnd: Boolean = false,
    delayMinutes: Long? = null,
    realTime: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text(TimeFormatter.clock(time), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (realTime) {
                Text(
                    TimeFormatter.punctuality(delayMinutes) ?: "Live",
                    style = MaterialTheme.typography.labelSmall,
                    color = if ((delayMinutes ?: 0) >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(if (isEnd) 18.dp else 16.dp)
                    .background(color, CircleShape)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
