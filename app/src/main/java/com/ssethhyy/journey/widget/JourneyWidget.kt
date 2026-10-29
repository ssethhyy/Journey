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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import java.time.Instant
import kotlin.math.roundToInt

class JourneyWidget : GlanceAppWidget() {

    // Exact, so the picture is drawn at the widget's real size.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snapshot = WidgetStore.read(context)
            WidgetContent(snapshot, Instant.now())
        }
    }

    companion object {
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

/**
 * The widget is one picture (see [WidgetRenderer]) with transparent tap targets laid over it: each departure row
 * opens its stop, and the footer icon refreshes.
 */
@Composable
private fun WidgetContent(snapshot: WidgetSnapshot, now: Instant) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).roundToInt()
    val heightPx = (size.height.value * density).roundToInt()
    val render = remember(snapshot, now.epochSecond / 30, widthPx, heightPx) {
        WidgetRenderer.render(context, snapshot, now, widthPx, heightPx)
    }
    fun dp(px: Float) = (px / density).dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .background(Color(WidgetRenderer.BACKGROUND)),
    ) {
        Image(
            provider = ImageProvider(render.bitmap),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        if (snapshot.rows.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                content = {},
            )
        }
        // Tap targets, placed with spacers so they line up with the drawn rows.
        Column(modifier = GlanceModifier.fillMaxSize()) {
            var y = 0f
            render.rows.forEachIndexed { i, r ->
                Spacer(GlanceModifier.height(dp(r.top - y)))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(dp(r.height()))
                        .clickable(actionStartActivity(openStop(context, snapshot.rows[i]))),
                    content = {},
                )
                y = r.bottom
            }
            Spacer(GlanceModifier.height(dp(render.refresh.top - y).coerceAtLeast(0.dp)))
            Row(modifier = GlanceModifier.fillMaxWidth().height(dp(render.refresh.height()))) {
                Spacer(GlanceModifier.width(dp(render.refresh.left)))
                Box(
                    modifier = GlanceModifier
                        .width(dp(render.refresh.width()))
                        .fillMaxHeight()
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                    content = {},
                )
            }
        }
    }
}

private fun openStop(context: Context, row: WidgetRow): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(JourneyWidget.EXTRA_STOP_ID, row.stopId)
        .putExtra(JourneyWidget.EXTRA_ROUTE_TYPE, row.routeType)
        .putExtra(JourneyWidget.EXTRA_STOP_NAME, row.name)
        .setData(android.net.Uri.parse("journey://stop/${row.routeType}/${row.stopId}"))
