/*
 * Feature Module: Favourites
 * File: NearbySection.kt
 * Description: The closest stops on For You, with their next departure. Shares the Map tab's nearby data, so
 * both always agree and the location is only looked up once.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.hapticClick
import com.ssethhyy.journey.ui.screens.map.NearbyStopRow
import com.ssethhyy.journey.ui.screens.nearby.LocationStatus
import com.ssethhyy.journey.ui.screens.nearby.NearbyUiState
import com.ssethhyy.journey.utils.DistanceFormatter
import java.time.Instant

fun LazyListScope.nearbySection(
    state: NearbyUiState,
    count: Int,
    now: Instant,
    onOpenStop: (Stop) -> Unit,
    onOpenMap: () -> Unit,
    onEnableLocation: () -> Unit,
) {
    item(key = "nearby_header") {
        SectionLabel(
            "Near you",
            trailing = {
                TextButton(onClick = hapticClick(onOpenMap)) {
                    Icon(Sym.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Map")
                }
            },
        )
    }
    item(key = "nearby_body") {
        val stops = state.stops.dataOrNull.orEmpty().take(count)
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val blocked = state.locationStatus == LocationStatus.NO_PERMISSION || state.locationStatus == LocationStatus.SERVICES_OFF
                if (blocked) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Sym.LocationOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (state.locationStatus == LocationStatus.SERVICES_OFF) "Location is off, showing the city" else "Allow location to see stops near you",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onEnableLocation,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                        ) { Text(if (state.locationStatus == LocationStatus.SERVICES_OFF) "Turn on" else "Allow") }
                    }
                }
                when {
                    state.stops is LoadState.Loading && stops.isEmpty() -> NearbyNote("Finding stops near you…")
                    state.stops is LoadState.Error && stops.isEmpty() -> NearbyNote("Couldn't load nearby stops. Pull down to try again.")
                    stops.isEmpty() -> NearbyNote("No stops within ${DistanceFormatter.distance(state.settings.nearbyRadiusMeters.toDouble())}")
                    else -> stops.forEach { stop ->
                        NearbyStopRow(stop, state.boards[stop.key], now, state.settings.timeDisplay, onClick = hapticClick { onOpenStop(stop) })
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NearbyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
