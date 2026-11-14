/*
 * Shared Component: Service updates
 * File: ServiceUpdates.kt
 * Description: The quiet, collapsible service updates card on stop and line pages.
 *
 * Only alerts that matter (by default closures, suspensions, replacement buses and changed timetables) bring the card
 * up. Collapsed it is one line; expanded it lists every alert, the important ones first.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.foryou.DisruptionLevel
import com.ssethhyy.journey.data.foryou.ForYouDisruptions
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.utils.HapticUtil

/** The alerts that bring up the card, and the rest that only show once it's expanded. */
data class ServiceUpdateSplit(val important: List<Disruption>, val others: List<Disruption>) {
    companion object {
        fun of(disruptions: List<Disruption>, level: DisruptionLevel): ServiceUpdateSplit {
            val all = disruptions.distinctBy { it.id }
            val (important, others) = all.partition { it.isCurrent && ForYouDisruptions.matchesLevel(it, level) }
            return ServiceUpdateSplit(
                important.sortedWith(compareBy<Disruption> { it.severity }.thenByDescending { it.publishedOn }),
                others.sortedWith(compareBy<Disruption> { it.severity }.thenByDescending { it.publishedOn }),
            )
        }
    }
}

/**
 * Adds the service updates card to a list: nothing when no alert matches [level]; otherwise a one-line summary that
 * expands into every alert.
 */
fun LazyListScope.serviceUpdates(
    disruptions: List<Disruption>,
    level: DisruptionLevel,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val split = ServiceUpdateSplit.of(disruptions, level)
    if (split.important.isEmpty()) return
    item(key = "service_updates") { ServiceUpdatesSummary(split.important, level, collapsed, onToggle) }
    if (!collapsed) {
        items(split.important, key = { "d${it.id}" }) { DisruptionCard(it, Modifier.animateItem()) }
        if (split.others.isNotEmpty()) {
            item(key = "service_updates_other") { SectionLabel("Other updates", Modifier.animateItem()) }
            items(split.others, key = { "d${it.id}" }) { DisruptionCard(it, Modifier.animateItem()) }
        }
    }
}

@Composable
fun ServiceUpdatesSummary(
    important: List<Disruption>,
    level: DisruptionLevel,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val turn by animateFloatAsState(if (collapsed) 0f else 180f, spring(stiffness = Spring.StiffnessMediumLow), label = "service_updates_arrow")
    val kind = when (level) {
        DisruptionLevel.MAJOR -> "major disruption"
        DisruptionLevel.DELAYS -> "disruption"
        DisruptionLevel.ALL -> "service update"
    }
    val worst = important.minOf { it.severity }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show service updates" else "Hide service updates") {
                    HapticUtil.performUIHaptic(view)
                    onToggle()
                }
                .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" }
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The colour stays in the small icon; the card itself matches the page.
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Sym.Warning,
                    null,
                    tint = if (worst == DisruptionSeverity.SEVERE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${important.size} $kind${if (important.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                Text(
                    important.first().title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Sym.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(turn))
            }
        }
    }
}
