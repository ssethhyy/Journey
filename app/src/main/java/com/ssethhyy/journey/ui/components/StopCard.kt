/*
 * Feature Module: UI Components
 * File: StopCard.kt
 * Description: Stop card with header, routes, and an inline departure board.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.repository.TimeDisplay
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import java.time.Instant

@Composable
fun StopCard(
    stop: Stop,
    board: LoadState<DepartureBoard>?,
    now: Instant,
    departuresToShow: Int,
    timeDisplay: TimeDisplay,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onOpen: () -> Unit,
    onLoadDepartures: () -> Unit,
    onOpenTrip: (Departure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        HapticUtil.performUIHaptic(view)
                        onOpen()
                    }
                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModePictogram(stop.mode, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stop.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(stop.distanceMeters?.let(DistanceFormatter::distanceAndWalk), stop.suburb)
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FavouriteToggle(isFavourite, onToggleFavourite)
            }
            if (stop.routes.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 74.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stop.routes.take(10).forEach { RouteBadge(it, compact = true) }
                    if (stop.routes.size > 10) {
                        Text("+${stop.routes.size - 10}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(
                Modifier
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(22.dp)),
            ) {
                when (board) {
                    null -> TextButton(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            onLoadDepartures()
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Show departures") }
                    LoadState.Loading -> DepartureSkeleton(rows = departuresToShow.coerceAtMost(2))
                    is LoadState.Error -> InlineError(board.error.message ?: "Couldn't load departures", onLoadDepartures)
                    is LoadState.Success -> {
                        val upcoming = board.data.departures.filter { it.bestTime?.isAfter(now.minusSeconds(60)) == true }.take(departuresToShow)
                        if (upcoming.isEmpty()) {
                            Text(
                                "No upcoming departures",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        upcoming.forEachIndexed { i, dep ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DepartureRow(dep, now, timeDisplay, hasDisruption = dep.disruptionIds.isNotEmpty(), onClick = { onOpenTrip(dep) })
                        }
                    }
                }
            }
        }
    }
}
