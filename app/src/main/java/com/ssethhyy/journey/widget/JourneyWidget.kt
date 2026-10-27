/*
 * Feature Module: Widget
 * File: JourneyWidget.kt
 * Description: Glance home-screen widget listing favourite stops with their next live departure.
 * Responsive: rows, detail lines and icon sizes adapt to the widget's size.
 */

package com.ssethhyy.journey.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.R
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.pictogramRes
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant

class JourneyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL, NARROW_TALL, WIDE_SHORT, MEDIUM, LARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snapshot = WidgetStore.read(context)
            GlanceTheme { WidgetContent(snapshot, Instant.now()) }
        }
    }

    companion object {
        val SMALL = DpSize(120.dp, 110.dp)
        val NARROW_TALL = DpSize(120.dp, 250.dp)
        val WIDE_SHORT = DpSize(250.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 250.dp)
        val LARGE = DpSize(320.dp, 380.dp)

        const val EXTRA_STOP_ID = "widget_stop_id"
        const val EXTRA_ROUTE_TYPE = "widget_route_type"
        const val EXTRA_STOP_NAME = "widget_stop_name"
    }
}

class JourneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JourneyWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}

/** Refresh button: fetch live departures immediately. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.refresh(context)
        WidgetRedrawWorker.start(context)
    }
}

object WidgetUpdater {
    /** Shows the spinner, fetches fresh departures and redraws every widget instance. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        if (GlanceAppWidgetManager(app).getGlanceIds(JourneyWidget::class.java).isEmpty()) return
        WidgetStore.setRefreshing(app, true)
        JourneyWidget().updateAll(app)
        val snapshot = runCatching { WidgetSnapshotBuilder.build(app.appContainer) }
            .getOrElse { WidgetStore.read(app).copy(error = "Couldn't refresh") }
        // Keep the previous rows if this refresh failed entirely.
        val previous = WidgetStore.read(app)
        val merged = if (snapshot.error != null && previous.rows.isNotEmpty() && snapshot.rows.none { it.departureEpochSec != null }) {
            previous.copy(error = snapshot.error, refreshing = false)
        } else {
            snapshot.copy(refreshing = false)
        }
        WidgetStore.write(app, merged)
        JourneyWidget().updateAll(app)
    }

    /** Redraws without fetching (keeps countdowns current between network refreshes). */
    suspend fun redraw(context: Context) {
        JourneyWidget().updateAll(context.applicationContext)
    }
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

private data class Density(
    val rows: Int,
    val iconSize: Int,
    val titleSize: TextUnit,
    val countdownSize: TextUnit,
    val showSubtitle: Boolean,
    val showHeadsign: Boolean,
    val compact: Boolean,
    /** Spare room lets each row show its following departures too. */
    val showLater: Boolean,
)

@Composable
private fun densityFor(width: Float, height: Float, available: Int): Density {
    val compact = width < 200
    val rowHeight = when {
        compact -> 52f
        height < 160 -> 50f
        else -> 62f
    }
    // Space for padding and the footer.
    val fit = ((height - 52f) / rowHeight).toInt().coerceAtLeast(1)
    val showLater = available > 0 && height - 52f >= available * (rowHeight + 22f)
    return Density(
        rows = minOf(fit, available).coerceAtLeast(1),
        iconSize = if (compact || height < 160) 32 else 40,
        titleSize = if (compact) 14.sp else 16.sp,
        countdownSize = if (compact) 15.sp else 20.sp,
        showSubtitle = !compact || height >= 200,
        showHeadsign = !compact,
        compact = compact,
        showLater = showLater,
    )
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot, now: Instant) {
    val size = LocalSize.current
    val context = LocalContext.current
    val density = densityFor(size.width.value, size.height.value, snapshot.rows.size)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (snapshot.rows.isEmpty()) {
            EmptyState(snapshot, modifier = GlanceModifier.defaultWeight())
        } else {
            snapshot.rows.take(density.rows).forEachIndexed { index, row ->
                if (index > 0) Spacer(GlanceModifier.height(if (density.compact) 6.dp else 10.dp))
                DepartureRow(row, now, density, context)
                if (density.showLater) {
                    WidgetFormat.later(row.laterEpochSec.map { Instant.ofEpochSecond(it) }, now)?.let { later ->
                        Text(
                            text = later,
                            maxLines = 1,
                            modifier = GlanceModifier.padding(start = (density.iconSize + 10).dp, top = 2.dp),
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        }
        Footer(snapshot)
    }
}

@Composable
private fun EmptyState(snapshot: WidgetSnapshot, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = snapshot.error ?: "No favourites yet",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "Star stops in Journey to see live departures here",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun DepartureRow(row: WidgetRow, now: Instant, density: Density, context: Context) {
    val mode = TransportMode.fromRouteType(row.routeType)
    val open = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(JourneyWidget.EXTRA_STOP_ID, row.stopId)
        .putExtra(JourneyWidget.EXTRA_ROUTE_TYPE, row.routeType)
        .putExtra(JourneyWidget.EXTRA_STOP_NAME, row.name)
        .setData(android.net.Uri.parse("journey://stop/${row.routeType}/${row.stopId}"))
    val departure = row.departureEpochSec?.let { Instant.ofEpochSecond(it) }?.takeIf { it.isAfter(now.minusSeconds(60)) }

    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(mode.pictogramRes),
            contentDescription = mode.label,
            modifier = GlanceModifier.size(density.iconSize.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = density.titleSize, fontWeight = FontWeight.Bold),
            )
            if (density.showSubtitle && row.subtitle != null) {
                Text(
                    text = row.subtitle,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = departure?.let { WidgetFormat.countdown(it, now) } ?: "—",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = density.countdownSize, fontWeight = FontWeight.Bold),
                )
                if (departure != null && row.isLive) {
                    Spacer(GlanceModifier.width(2.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_live),
                        contentDescription = "Live",
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        modifier = GlanceModifier.size(if (density.compact) 12.dp else 15.dp),
                    )
                }
            }
            val headsign = row.headsign
            if (density.showHeadsign && headsign != null && departure != null) {
                Text(
                    text = "towards $headsign",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.End),
                )
            }
        }
    }
}

@Composable
private fun Footer(snapshot: WidgetSnapshot) {
    Spacer(GlanceModifier.height(6.dp))
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val updated = if (snapshot.updatedAtEpochSec > 0) {
            "Updated ${TimeFormatter.clock(Instant.ofEpochSecond(snapshot.updatedAtEpochSec))}"
        } else {
            "Not updated yet"
        }
        Text(
            text = snapshot.error?.takeIf { snapshot.rows.isNotEmpty() }?.let { "$it · $updated" } ?: updated,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionRunCallback<RefreshWidgetAction>()),
            contentAlignment = Alignment.Center,
        ) {
            if (snapshot.refreshing) {
                CircularProgressIndicator(modifier = GlanceModifier.size(18.dp), color = GlanceTheme.colors.onSecondaryContainer)
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
        }
    }
}
