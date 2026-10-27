/*
 * Feature Module: UI Components
 * File: TransitComponents.kt
 * Description: Pictograms, route badges, departure rows, filters and status views.
 */

package com.ssethhyy.journey.ui.components

import androidx.annotation.DrawableRes
import com.ssethhyy.journey.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.api.PtvException
import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.TimeDisplay
import com.ssethhyy.journey.ui.theme.color
import com.ssethhyy.journey.ui.theme.onColor
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant

val TransportMode.icon: ImageVector
    @Composable get() = when (this) {
        TransportMode.TRAIN -> Sym.Train
        TransportMode.TRAM -> Sym.Tram
        TransportMode.BUS -> Sym.DirectionsBus
        TransportMode.VLINE -> Sym.DirectionsRailway
        TransportMode.NIGHT_BUS -> Sym.NightsStay
    }

/**
 * Official PTV mode pictogram (generated from art/pictograms by aps2vector.py).
 */
val TransportMode.pictogramRes: Int
    @DrawableRes get() = when (this) {
        TransportMode.TRAIN -> R.drawable.pictogram_train
        TransportMode.TRAM -> R.drawable.pictogram_tram
        TransportMode.BUS -> R.drawable.pictogram_bus
        TransportMode.VLINE -> R.drawable.pictogram_vline
        TransportMode.NIGHT_BUS -> R.drawable.pictogram_night_bus
    }

private val GreyscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Transport mode pictogram: the official PTV roundel for the mode. */
@Composable
fun ModePictogram(mode: TransportMode, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Image(
        painter = painterResource(mode.pictogramRes),
        contentDescription = mode.label,
        modifier = modifier.size(size),
    )
}

/** Pill showing a route number (tram/bus) or line name (train) in the mode colour. */
@Composable
fun RouteBadge(route: Route, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(route.mode.color)
            .widthIn(min = if (compact) 32.dp else 40.dp)
            .padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            route.badgeLabel,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = route.mode.onColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Large countdown/clock text with live indicator. */
@Composable
fun DepartureTime(departure: Departure, now: Instant, display: TimeDisplay, modifier: Modifier = Modifier) {
    val time = departure.bestTime
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (time == null) {
            Text("—", style = MaterialTheme.typography.titleLarge)
            return@Column
        }
        val primary = when {
            departure.isCancelled -> "Cancelled"
            display == TimeDisplay.RELATIVE -> TimeFormatter.countdown(time, now)
            else -> TimeFormatter.clock(time)
        }
        AnimatedContent(
            targetState = primary,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "departure_time",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    departure.isCancelled -> MaterialTheme.colorScheme.error
                    departure.isLive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (departure.isCancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
        }
        val secondary = when {
            departure.isCancelled -> null
            display == TimeDisplay.RELATIVE && TimeFormatter.minutesUntil(time, now) < 60 -> TimeFormatter.clock(time)
            display == TimeDisplay.CLOCK -> TimeFormatter.countdown(time, now).takeIf { it != TimeFormatter.clock(time) }
            else -> null
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (departure.isLive && !departure.isCancelled) {
                LiveDot()
                Spacer(Modifier.width(4.dp))
            }
            if (secondary != null) {
                Text(secondary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!departure.isLive && !departure.isCancelled) {
                Text("Scheduled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .semantics { contentDescription = "Live" },
    )
}

/** One departure: route badge, destination, platform/status and countdown. */
@Composable
fun DepartureRow(
    departure: Departure,
    now: Instant,
    display: TimeDisplay,
    modifier: Modifier = Modifier,
    hasDisruption: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        HapticUtil.performUIHaptic(view)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (departure.isCancelled) 0.7f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        departure.route?.let { RouteBadge(it) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    departure.headsign,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasDisruption) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Sym.Warning,
                        contentDescription = "Disruption",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val details = buildList {
                departure.platform?.let { add("Platform $it") }
                if (departure.isExpress) add("Express")
                TimeFormatter.punctuality(departure.delayMinutes)?.takeIf { !departure.isCancelled }?.let { add(it) }
                if (departure.vehicle?.lowFloor == true) add("Low floor")
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        DepartureTime(departure, now, display)
    }
}

/** Star toggle with a playful scale animation and haptics. */
@Composable
fun FavouriteToggle(isFavourite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    IconToggleButton(
        checked = isFavourite,
        onCheckedChange = {
            if (it) HapticUtil.performConfirmHaptic(view) else HapticUtil.performUIHaptic(view)
            onToggle()
        },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = isFavourite,
            transitionSpec = { scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith scaleOut() },
            label = "favourite_toggle",
        ) { fav ->
            Icon(
                if (fav) Sym.StarFilled else Sym.Star,
                contentDescription = if (fav) "Remove from favourites" else "Add to favourites",
                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontally scrolling mode filter chips. Selecting none is treated as "all". */
@Composable
fun ModeFilterRow(
    selected: Set<TransportMode>,
    onToggle: (TransportMode) -> Unit,
    modifier: Modifier = Modifier,
    modes: List<TransportMode> = TransportMode.entries,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    containerColor: Color = Color.Transparent,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { mode ->
            val isSelected = mode in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    onToggle(mode)
                },
                label = { Text(mode.pluralLabel) },
                leadingIcon = {
                    Image(
                        painter = painterResource(mode.pictogramRes),
                        contentDescription = null,
                        colorFilter = if (isSelected) null else GreyscaleFilter,
                        alpha = if (isSelected) 1f else 0.6f,
                        modifier = Modifier.size(20.dp),
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = containerColor,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

/** Expressive loading state using the M3 morphing [LoadingIndicator]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.size(56.dp))
        if (label != null) {
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Friendly empty/error state with an expressive shape behind the icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

/** Maps API exceptions to a readable state with the right recovery action. */
@Composable
fun ErrorState(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    when (error) {
        is PtvException.MissingCredentials, is PtvException.InvalidCredentials -> MessageState(
            icon = Sym.Key,
            title = if (error is PtvException.MissingCredentials) "Connect to PTV" else "PTV key rejected",
            message = if (error is PtvException.MissingCredentials) {
                "Add your PTV developer ID and API key in Settings to load live departures."
            } else {
                "Check your developer ID and API key in Settings."
            },
            modifier = modifier,
            actions = {
                if (onOpenSettings != null) Button(onClick = onOpenSettings) { Text("Open settings") }
            },
        )
        is PtvException.Network -> MessageState(
            icon = Sym.CloudOff,
            title = "You're offline",
            message = error.message.orEmpty(),
            modifier = modifier,
            actions = { RetryButton(onRetry) },
        )
        else -> MessageState(
            icon = Sym.Error,
            title = "Something went wrong",
            message = error.message ?: "Please try again.",
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            actions = { RetryButton(onRetry) },
        )
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    val view = LocalView.current
    FilledTonalButton(onClick = {
        HapticUtil.performUIHaptic(view)
        onRetry()
    }) {
        Icon(Sym.Refresh, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Try again")
    }
}

/** Compact inline error used inside cards. */
@Composable
fun InlineError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Sym.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun DisruptionSeverity.containerColor(): Color = when (this) {
    DisruptionSeverity.SEVERE, DisruptionSeverity.MAJOR -> MaterialTheme.colorScheme.errorContainer
    DisruptionSeverity.MINOR -> MaterialTheme.colorScheme.tertiaryContainer
    DisruptionSeverity.PLANNED, DisruptionSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun DisruptionSeverity.contentColor(): Color = when (this) {
    DisruptionSeverity.SEVERE, DisruptionSeverity.MAJOR -> MaterialTheme.colorScheme.onErrorContainer
    DisruptionSeverity.MINOR -> MaterialTheme.colorScheme.onTertiaryContainer
    DisruptionSeverity.PLANNED, DisruptionSeverity.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
}

/** Expandable disruption card with severity colouring and a link to full details. */
@Composable
fun DisruptionCard(disruption: Disruption, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false) {
    val view = LocalView.current
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable(disruption.id) { mutableStateOf(initiallyExpanded) }
    val container = disruption.severity.containerColor()
    val content = disruption.severity.contentColor()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        onClick = {
            HapticUtil.performUIHaptic(view)
            expanded = !expanded
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (disruption.severity <= DisruptionSeverity.MINOR) Sym.Warning else Sym.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    disruption.type?.let {
                        Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        disruption.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (disruption.routes.isNotEmpty()) {
                Row(
                    Modifier
                        .padding(start = 32.dp, top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    disruption.routes.take(12).forEach { RouteBadge(it, compact = true) }
                }
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(start = 32.dp, top = 8.dp)) {
                    if (disruption.description.isNotBlank() && disruption.description != disruption.title) {
                        Text(disruption.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    TimeFormatter.dateRange(disruption.fromDate, disruption.toDate)?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium)
                    }
                    disruption.url?.let { url ->
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(url) } },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            Text("More details", color = content)
                            Spacer(Modifier.width(4.dp))
                            Icon(Sym.OpenInNew, null, tint = content, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Placeholder rows that match the geometry of [DepartureRow] while loading. */
@Composable
fun DepartureSkeleton(rows: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
                Box(
                    Modifier
                        .size(width = 48.dp, height = 20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}

/** Small circular icon button with tonal background, used over the map. */
@Composable
fun MapOverlayButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

/** Icon button used in top app bars with haptics. */
@Composable
fun HapticIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val view = LocalView.current
    IconButton(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
