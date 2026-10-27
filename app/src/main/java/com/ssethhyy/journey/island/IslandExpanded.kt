/*
 * Feature Module: Island
 * File: IslandExpanded.kt
 * Description: Expanded island views for tracking and disruptions, drawn in Journey's process and
 * embedded by Essentials. Follows the provider rules: own black background clipped to cornerPx,
 * camera clearance at the top, natural height reported with resize(), and snapshots for transitions.
 */

package com.ssethhyy.journey.island

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sameerasw.island.sdk.HostInfo
import com.sameerasw.island.sdk.IslandHostHandle
import com.sameerasw.island.sdk.compose.islandSnapshot
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.IslandIconStyle
import com.ssethhyy.journey.tracking.TrackingStatus
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.pictogramRes
import com.ssethhyy.journey.ui.theme.buildTypography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

data class ExpandedState(
    val target: TrackingTarget? = null,
    val status: TrackingStatus? = null,
    val disruptions: List<Disruption> = emptyList(),
    val scopeLabel: String = "",
    val iconStyle: IslandIconStyle = IslandIconStyle.PICTOGRAM,
)

private val TextPrimary = Color(0xFFF4F4F6)
private val TextSecondary = Color(0xFFB4B6BD)
private val CardColor = Color(0xFF1C1C1F)

@Composable
fun IslandExpandedContent(
    itemKey: String,
    state: ExpandedState,
    info: HostInfo?,
    host: IslandHostHandle?,
    onStopTracking: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val corner = with(density) { (info?.cornerPx ?: 0).toDp() }
    val clearance = with(density) { (info?.cameraClearancePx ?: 0).toDp() }
    val padding = with(density) { (info?.expandedPaddingPx ?: 0).toDp() }.coerceAtLeast(16.dp)
    val accent = info?.accent?.let { Color(it) } ?: Color(0xFFA9C7FF)
    val typography = remember(context) { buildTypography(context) }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, surface = Color.Black, onSurface = TextPrimary), typography = typography) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .onSizeChanged { host?.resize(itemKey, it.height) }
                .islandSnapshot { host?.snapshot(itemKey, it) }
                .clip(RoundedCornerShape(corner))
                .background(Color.Black),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(clearance))
                Column(Modifier.padding(start = padding, end = padding, bottom = padding, top = 4.dp)) {
                    when (itemKey) {
                        JourneyIsland.ITEM_TRACKING -> TrackingExpanded(state, accent, powerSave = info?.powerSave == true, onStop = onStopTracking, onOpen = { host?.openApp(itemKey) })
                        JourneyIsland.ITEM_DISRUPTIONS -> DisruptionsExpanded(state, onOpen = { host?.openApp(itemKey) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackingExpanded(state: ExpandedState, accent: Color, powerSave: Boolean, onStop: () -> Unit, onOpen: () -> Unit) {
    val target = state.target
    val status = state.status
    if (target == null) {
        Text("Not tracking anything", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val mode = status?.mode ?: (target as? TrackingTarget.Service)?.mode ?: TransportMode.TRAIN
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.iconStyle == IslandIconStyle.PICTOGRAM) {
            Image(painterResource(mode.pictogramRes), contentDescription = mode.label, modifier = Modifier.size(44.dp))
        } else {
            Box(Modifier.size(44.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Sym.Train, null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // The big countdown sits beside the title, so services show just the line;
                // journeys keep their step-by-step instruction ("Walk to …", "On …").
                if (target is TrackingTarget.Service || status == null) JourneyIsland.trackingLabel(target) else status.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status?.detail ?: "Getting live times…",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(status?.chip ?: "…", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (status?.isLive == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Color(0xFF6DD58C), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("Live", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val progress = status?.progress ?: 0f
    if (powerSave) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = accent, trackColor = CardColor)
    } else {
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = accent, trackColor = CardColor)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Stop tracking", background = CardColor, content = TextPrimary, modifier = Modifier.weight(1f), onClick = onStop)
        PillButton("Open Journey", background = accent, content = Color.Black, modifier = Modifier.weight(1f), onClick = onOpen)
    }
}

@Composable
private fun DisruptionsExpanded(state: ExpandedState, onOpen: () -> Unit) {
    val list = state.disruptions
    val worst = JourneyIsland.worstSeverity(list)
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Sym.Warning,
                contentDescription = null,
                tint = worst?.let { Color(JourneyIsland.severityColor(it)) } ?: TextSecondary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (list.size) {
                        0 -> "Good service"
                        1 -> "1 service update"
                        else -> "${list.size} service updates"
                    },
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(state.scopeLabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        list.take(4).forEachIndexed { index, d ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .background(Color(JourneyIsland.severityColor(d.severity)), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (d.type ?: JourneyIsland.severityLabel(d.severity)).uppercase(),
                        color = Color(JourneyIsland.severityColor(d.severity)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(d.title, color = TextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (list.size > 4) {
            Spacer(Modifier.height(8.dp))
            Text("+${list.size - 4} more · tap to see all", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PillButton(text: String, background: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}
