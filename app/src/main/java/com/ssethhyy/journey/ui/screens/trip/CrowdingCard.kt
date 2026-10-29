/*
 * Feature Module: Trip
 * File: CrowdingCard.kt
 * Description: How busy the service is, which carriages are fullest, and what is known about air conditioning.
 */

package com.ssethhyy.journey.ui.screens.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.crowding.AirConditioning
import com.ssethhyy.journey.data.crowding.Occupancy
import com.ssethhyy.journey.data.crowding.VehicleCrowding
import com.ssethhyy.journey.ui.components.Sym

/** What the Trip screen knows about crowding right now. */
sealed interface CrowdingUi {
    /** Nothing asked yet. */
    data object Idle : CrowdingUi

    /** No Open Data key; the card explains how to add one. */
    data object NotConfigured : CrowdingUi

    /** The feed is fine but has nothing for this service. */
    data object NotFound : CrowdingUi

    data class Unavailable(val reason: String, val badKey: Boolean) : CrowdingUi

    data class Data(val crowding: VehicleCrowding) : CrowdingUi
}

/** Colour for a crowding level: calm green through to red. Not the only cue, the label is always shown too. */
internal fun Occupancy.tone(): Color = when (level) {
    0, 1 -> Color(0xFF3DBE6A)
    2 -> Color(0xFFB4C23A)
    3 -> Color(0xFFF2B12E)
    4 -> Color(0xFFEE7A2E)
    5 -> Color(0xFFE5484D)
    else -> Color(0xFF8A8F98)
}

@Composable
internal fun CrowdingCard(crowding: CrowdingUi, airConditioning: AirConditioning, modifier: Modifier = Modifier) {
    val showCrowding = crowding !is CrowdingUi.Idle
    val acLine = airConditioningLine(airConditioning)
    if (!showCrowding && acLine == null) return
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (crowding) {
                is CrowdingUi.Data -> CrowdingDetail(crowding.crowding)
                CrowdingUi.NotConfigured -> CrowdingNote(
                    "Crowding isn't set up",
                    "Add a free Transport Victoria Open Data key in Settings to see how busy a service is and which carriages are fullest.",
                )
                CrowdingUi.NotFound -> CrowdingNote("No crowding data for this service", "Victoria's feed doesn't list this vehicle right now.")
                is CrowdingUi.Unavailable -> CrowdingNote(
                    if (crowding.badKey) "Open Data key rejected" else "Crowding unavailable",
                    if (crowding.badKey) "Check the key in Settings -> API access." else crowding.reason,
                )
                CrowdingUi.Idle -> Unit
            }
            if (acLine != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (airConditioning is AirConditioning.FaultReported) Sym.Warning else Sym.AcUnit,
                        null,
                        Modifier.size(18.dp),
                        tint = if (airConditioning is AirConditioning.FaultReported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(acLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun airConditioningLine(ac: AirConditioning): String? = when (ac) {
    is AirConditioning.FaultReported -> "Air conditioning fault reported on this line"
    AirConditioning.NotFitted -> "No air conditioning on this vehicle"
    AirConditioning.Available, AirConditioning.Unknown -> null
}

@Composable
private fun CrowdingNote(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Sym.Groups, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CrowdingDetail(crowding: VehicleCrowding) {
    val overall = crowding.overall
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Sym.Groups, null, Modifier.size(24.dp), tint = overall.tone())
        Column(Modifier.weight(1f)) {
            Text(overall.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                crowding.percentage?.let { "About $it% full" } ?: "How busy this service is right now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (overall.hasData) Meter(overall)
    if (crowding.hasCarriageDetail) Carriages(crowding)
}

/** Five segments, filled up to the current level. */
@Composable
private fun Meter(occupancy: Occupancy) {
    val filled = occupancy.level.coerceIn(0, 5)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = occupancy.label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 1..5) {
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(if (i <= filled) occupancy.tone() else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun Carriages(crowding: VehicleCrowding) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("By carriage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            crowding.carriages.forEachIndexed { index, car ->
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "Car ${index + 1}: ${car.occupancy.label}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(22.dp).background(car.occupancy.tone(), RoundedCornerShape(6.dp)))
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        crowding.busiestCarriages.takeIf { it.isNotEmpty() && it.size < crowding.carriages.size }?.let { cars ->
            Text(
                (if (cars.size == 1) "Car ${cars[0]} is" else "Cars ${cars.joinToString(", ")} are") + " fullest",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
