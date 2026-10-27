/*
 * Feature Module: UI Components
 * File: Timeline.kt
 * Description: Vertical line-diagram rows used by route and stopping-pattern screens.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.utils.HapticUtil

/**
 * One stop on a line diagram. The coloured spine connects to neighbours unless [isFirst]/[isLast].
 */
@Composable
fun TimelineRow(
    title: String,
    lineColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    trailingSubtitle: String? = null,
    /** Extra small line under the trailing time, e.g. "Scheduled 7:42 pm". */
    trailingDetail: String? = null,
    trailingColor: Color? = null,
    /** When set, the live vehicle is at this stop: its icon replaces the stop dot. */
    vehicleMode: TransportMode? = null,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val surface = MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
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
            .padding(horizontal = 16.dp)
            .alpha(if (dimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(28.dp)
                .fillMaxHeight(),
        ) {
            Canvas(Modifier.matchParentSizeCompat()) {
                val x = size.width / 2
                val stroke = 6.dp.toPx()
                val top = if (isFirst) size.height / 2 else 0f
                val bottom = if (isLast) size.height / 2 else size.height
                drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
                val radius = if (highlighted) 9.dp.toPx() else 6.dp.toPx()
                drawCircle(surface, radius, Offset(x, size.height / 2))
                drawCircle(lineColor, radius, Offset(x, size.height / 2), style = Stroke(width = 3.dp.toPx()))
                if (highlighted) drawCircle(lineColor, radius / 2, Offset(x, size.height / 2))
            }
            if (vehicleMode != null) VehicleBadge(vehicleMode, Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .padding(vertical = 10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                title,
                style = if (highlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlighted || isFirst || isLast) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (trailing != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = trailingColor ?: MaterialTheme.colorScheme.onSurface,
                )
                if (trailingSubtitle != null) {
                    Text(trailingSubtitle, style = MaterialTheme.typography.labelSmall, color = trailingColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (trailingDetail != null) {
                    Text(trailingDetail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The vehicle's pictogram in a ring, sitting on the line. */
@Composable
fun VehicleBadge(mode: TransportMode, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(mode.pictogramRes),
            contentDescription = "${mode.label} location",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A row between two stops showing the live vehicle on the line. */
@Composable
fun TimelineVehicleRow(
    mode: TransportMode,
    lineColor: Color,
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).fillMaxHeight().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val x = size.width / 2
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            }
            VehicleBadge(mode)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "N stops" row that stands in for hidden stops on the line. */
@Composable
fun TimelineCollapsedRow(count: Int, lineColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable {
                HapticUtil.performUIHaptic(view)
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).fillMaxHeight().heightIn(min = 48.dp)) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val x = size.width / 2
                drawLine(
                    lineColor.copy(alpha = 0.55f),
                    Offset(x, 0f),
                    Offset(x, size.height),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx())),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (count == 1) "1 stop" else "$count stops",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp).weight(1f),
        )
        Icon(Sym.ChevronRight, contentDescription = "Show all stops", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().fillMaxHeight()
