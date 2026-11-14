/*
 * Feature Module: For You
 * File: ForYouWidgets.kt
 * Description: Pieces shared by the For You widgets: collapsible titles, the Disruptions card, Network status and
 * Shortcuts.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.foryou.DisruptionLevel
import com.ssethhyy.journey.data.foryou.ForYouWidget
import com.ssethhyy.journey.data.foryou.Shortcut
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.utils.HapticUtil

val ForYouWidget.icon: ImageVector
    @Composable get() = when (this) {
        ForYouWidget.GREETING -> Sym.WeatherPartlyCloudyDay
        ForYouWidget.PLACES -> Sym.Home
        ForYouWidget.FAVOURITES -> Sym.Star
        ForYouWidget.DISRUPTIONS -> Sym.Warning
        ForYouWidget.COMMUTES -> Sym.Route
        ForYouWidget.NEAR_YOU -> Sym.NearMe
        ForYouWidget.NETWORK_STATUS -> Sym.CheckCircle
        ForYouWidget.SHORTCUTS -> Sym.Bolt
    }

/** A simple widget title with an arrow showing, and changing, whether the widget is collapsed. */
@Composable
fun WidgetTitle(
    text: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    val turn by animateFloatAsState(if (collapsed) 0f else 180f, spring(stiffness = Spring.StiffnessMediumLow), label = "title_arrow")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show $text" else "Hide $text") {
                HapticUtil.performUIHaptic(view)
                onToggle()
            }
            .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        // Actions like "Add" only make sense while the widget is open.
        if (!collapsed) trailing?.invoke()
        // The arrow's edge lines up with the cards' right edge.
        Box(Modifier.size(width = 32.dp, height = 40.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(Sym.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(turn))
        }
    }
}

/**
 * Wraps a widget's content with its title (when titles are on) and hides it when collapsed. Without titles
 * there's nothing to expand it with, so the content always shows.
 */
@Composable
fun CollapsibleWidget(
    title: String,
    showTitle: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (showTitle) WidgetTitle(title, collapsed, onToggle, trailing = trailing)
        AnimatedVisibility(
            visible = !showTitle || !collapsed,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            Column(Modifier.padding(top = if (showTitle) 4.dp else 0.dp)) { content() }
        }
    }
}

/** A small "Add" button for widget titles. */
@Composable
fun TitleAddButton(label: String, onClick: () -> Unit) {
    val view = LocalView.current
    TextButton(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(Sym.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

/**
 * The Disruptions widget: a quiet card whose header is its title, collapsed by default. It names how many disruptions
 * match and what they affect; expanded it lists them.
 */
@Composable
fun DisruptionsWidget(
    disruptions: List<Disruption>,
    level: DisruptionLevel,
    maxShown: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val turn by animateFloatAsState(if (collapsed) 0f else 180f, spring(stiffness = Spring.StiffnessMediumLow), label = "disruption_arrow")
    val clear = disruptions.isEmpty()
    val worst = disruptions.minOfOrNull { it.severity }
    val kind = when (level) {
        DisruptionLevel.MAJOR -> "major disruption"
        DisruptionLevel.DELAYS -> "disruption"
        DisruptionLevel.ALL -> "service update"
    }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = !clear, role = Role.Button, onClickLabel = if (collapsed) "Show disruptions" else "Hide disruptions") {
                        HapticUtil.performUIHaptic(view)
                        onToggle()
                    }
                    .semantics { if (!clear) stateDescription = if (collapsed) "Collapsed" else "Expanded" }
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The colour stays in the small icon; the card itself matches the rest of the page.
                val tint = when {
                    clear -> MaterialTheme.colorScheme.primary
                    worst == DisruptionSeverity.SEVERE -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (clear) Sym.CheckCircle else Sym.Warning, null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (clear) "No ${kind}s on your lines" else "${disruptions.size} $kind${if (disruptions.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!clear) {
                        val affected = disruptions.flatMap { d -> d.routes.map { it.badgeLabel.ifBlank { it.name } } }.distinct()
                        Text(
                            affected.take(4).joinToString(", ") + if (affected.size > 4) " and more" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!clear) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Sym.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(turn))
                    }
                }
            }
            AnimatedVisibility(
                visible = !clear && !collapsed,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .padding(bottom = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(22.dp)),
                ) {
                    disruptions.take(maxShown).forEachIndexed { index, d ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    HapticUtil.performUIHaptic(view)
                                    onOpenAll()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val lines = listOfNotNull(
                                d.type?.takeIf { it.isNotBlank() },
                                d.routes.map { it.badgeLabel.ifBlank { it.name } }.distinct().take(3).joinToString(", ").takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (lines.isNotBlank()) {
                                Text(lines, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    TextButton(onClick = { HapticUtil.performUIHaptic(view); onOpenAll() }, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                        Text(if (disruptions.size > maxShown) "See all ${disruptions.size}" else "See all disruptions")
                    }
                }
            }
        }
    }
}

/** How the network is running, one line per mode. */
@Composable
fun NetworkStatusWidget(disruptions: List<Disruption>, modes: Set<TransportMode>, onOpen: () -> Unit) {
    val view = LocalView.current
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onOpen()
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            TransportMode.entries.filter { it in modes }.forEach { mode ->
                val forMode = disruptions.filter { d -> d.mode == mode || d.routes.any { it.mode == mode } }
                val major = forMode.count { com.ssethhyy.journey.data.foryou.ForYouDisruptions.isMajor(it) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.ssethhyy.journey.ui.components.ModePictogram(mode, size = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(mode.pluralLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val (label, color) = when {
                        major > 0 -> "$major major" to MaterialTheme.colorScheme.error
                        forMode.isNotEmpty() -> "${forMode.size} update${if (forMode.size == 1) "" else "s"}" to MaterialTheme.colorScheme.tertiary
                        else -> "Good service" to MaterialTheme.colorScheme.primary
                    }
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

val Shortcut.icon: ImageVector
    @Composable get() = when (this) {
        Shortcut.PLAN -> Sym.Directions
        Shortcut.MAP -> Sym.Map
        Shortcut.DISRUPTIONS -> Sym.Warning
        Shortcut.ROULETTE -> Sym.Casino
        Shortcut.SEARCH -> Sym.Search
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutsWidget(shortcuts: List<Shortcut>, onShortcut: (Shortcut) -> Unit) {
    val view = LocalView.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.forEach { shortcut ->
            FilledTonalButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    onShortcut(shortcut)
                },
                contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
            ) {
                Icon(shortcut.icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(shortcut.label)
            }
        }
    }
}
