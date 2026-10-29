/*
 * Feature Module: Widget
 * File: WidgetRenderer.kt
 * Description: Draws the home-screen widget as a bitmap so it can use Google Sans Flex's variable axes (weight,
 * width, optical size, roundness), which RemoteViews text cannot. Layout follows the design file: a coloured
 * mode circle, a bold wide stop name over a light "400m away", and a right-aligned time over "towards City", one
 * line each. Tap targets are laid over the bitmap by JourneyWidget using the returned row rectangles.
 */

package com.ssethhyy.journey.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.ssethhyy.journey.R
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.pictogramRes
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WidgetRender(
    val bitmap: Bitmap,
    /** Where each drawn departure row sits, in bitmap pixels, top to bottom. */
    val rows: List<RectF>,
    /** The refresh tap target, in bitmap pixels. */
    val refresh: RectF,
)

object WidgetRenderer {
    const val BACKGROUND = 0xFF212121.toInt()
    const val MAX_ROWS = 7

    // Design numbers from the widget file, in its 1049 px wide frame.
    private const val DESIGN_WIDTH = 1049f
    private const val ROW_HEIGHT = 72.667f
    private const val ROW_GAP = 14f
    private const val MARGIN = 36f
    private const val NAME_SIZE = 33f
    private const val DISTANCE_SIZE = 19f
    private const val TIME_SIZE = 38f
    private const val TOWARDS_SIZE = 21f
    private const val LINE = 1.357f
    private const val ROUNDNESS = 100

    private class Style(val weight: Int, val width: Int)

    private val NAME = Style(700, 130)
    private val DISTANCE = Style(131, 151)
    private val TIME = Style(720, 138)
    private val TOWARDS = Style(441, 151)

    private const val MIN_WIDTH_AXIS = 75

    @Volatile private var typeface: Typeface? = null

    private fun face(context: Context): Typeface = typeface ?: synchronized(this) {
        typeface ?: runCatching { Typeface.createFromAsset(context.applicationContext.assets, "fonts/GoogleSansFlex.ttf") }
            .getOrDefault(Typeface.DEFAULT).also { typeface = it }
    }

    private fun paint(context: Context, sizePx: Float, style: Style, width: Int = style.width): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face(context)
        textSize = sizePx
        color = Color.WHITE
        val opticalSize = (sizePx / context.resources.displayMetrics.density).coerceIn(6f, 144f)
        fontVariationSettings = "'wght' ${style.weight}, 'wdth' $width, 'opsz' ${opticalSize.roundToInt()}, 'ROND' $ROUNDNESS"
    }

    /** Draws [text] on one line inside [maxWidth], narrowing the font's width axis first and trimming with "…" last. */
    private fun fitted(context: Context, text: String, sizePx: Float, style: Style, maxWidth: Float): Pair<Paint, String> {
        var width = style.width
        var p = paint(context, sizePx, style, width)
        while (p.measureText(text) > maxWidth && width > MIN_WIDTH_AXIS) {
            width = max(MIN_WIDTH_AXIS, width - 8)
            p = paint(context, sizePx, style, width)
        }
        if (p.measureText(text) <= maxWidth) return p to text
        var cut = text.length
        while (cut > 1 && p.measureText(text.substring(0, cut).trimEnd() + "…") > maxWidth) cut--
        return p to (text.substring(0, cut).trimEnd() + "…")
    }

    private fun baseline(top: Float, size: Float, p: Paint): Float {
        val box = size * LINE
        val m = p.fontMetrics
        return top + box / 2f - (m.ascent + m.descent) / 2f
    }

    fun render(context: Context, snapshot: WidgetSnapshot, now: Instant, widthPx: Int, heightPx: Int): WidgetRender {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()

        // Horizontal sizes follow the width; vertical ones never go below a readable text size.
        val u = max(w / DESIGN_WIDTH, 13f * density / NAME_SIZE)
        val margin = max(MARGIN * w / DESIGN_WIDTH, 12f * density)
        val contentW = w - 2 * margin
        val rowH = ROW_HEIGHT * u
        val footerH = 24f * density
        val availableH = h - margin - footerH - 6f * density
        val refreshSize = 20f * density
        val refresh = RectF(w - margin - 40f * density, h - footerH - 2f * density, w - margin + 4f * density, h)

        val rowRects = mutableListOf<RectF>()
        val shown = if (snapshot.rows.isEmpty()) 0 else {
            val fit = ((availableH + ROW_GAP * u) / (rowH + ROW_GAP * u)).toInt().coerceAtLeast(1)
            minOf(snapshot.rows.size, MAX_ROWS, fit)
        }
        if (shown == 0) {
            drawEmpty(context, canvas, snapshot, w, margin + availableH / 2f, density)
        } else {
            val gap = if (shown > 1) ((availableH - shown * rowH) / (shown - 1)).coerceIn(ROW_GAP * u, ROW_GAP * u * 2f) else 0f
            snapshot.rows.take(shown).forEachIndexed { i, row ->
                val top = margin + i * (rowH + gap)
                val rect = RectF(margin, top, margin + contentW, top + rowH)
                drawRow(context, canvas, row, now, rect, u)
                rowRects += rect
            }
        }
        drawFooter(context, canvas, snapshot, w, h, margin, footerH, density, refreshSize)
        return WidgetRender(bitmap, rowRects, refresh)
    }

    private fun drawRow(context: Context, canvas: Canvas, row: WidgetRow, now: Instant, r: RectF, u: Float) {
        val mode = TransportMode.fromRouteType(row.routeType)
        val departure = row.departureEpochSec?.let { Instant.ofEpochSecond(it) }?.takeIf { it.isAfter(now.minusSeconds(60)) }
        val rowW = r.width()

        ContextCompat.getDrawable(context, mode.pictogramRes)?.let {
            it.setBounds(r.left.roundToInt(), r.top.roundToInt(), (r.left + r.height()).roundToInt(), r.bottom.roundToInt())
            it.draw(canvas)
        }

        val rss = 22.8f * u
        val textLeft = r.left + max(0.0872f * rowW, r.height() + 10f * u)
        val rightEdge = r.left + min(0.9767f * rowW, rowW - rss - 4f * u)
        if (departure != null && row.isLive) {
            rssBitmap(context)?.let { canvas.drawBitmap(it, null, RectF(r.right - rss, r.top, r.right, r.top + rss), Paint(Paint.FILTER_BITMAP_FLAG)) }
        }

        // Time, top right.
        val timeText = departure?.let { WidgetFormat.countdown(it, now) } ?: "—"
        val (timePaint, time) = fitted(context, timeText, TIME_SIZE * u, TIME, 0.22f * rowW)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(time, rightEdge, baseline(r.top, TIME_SIZE * u, timePaint), timePaint)
        val timeLeft = rightEdge - timePaint.measureText(time)

        // Name, top left, kept clear of the time.
        val nameMax = min(0.6857f * rowW, timeLeft - textLeft - 10f * u)
        val (namePaint, name) = fitted(context, row.name, NAME_SIZE * u, NAME, nameMax.coerceAtLeast(20f))
        canvas.drawText(name, textLeft, baseline(r.top, NAME_SIZE * u, namePaint), namePaint)

        val secondTop = r.top + 0.6154f * r.height()

        // "towards City", right aligned under the time.
        val headsign = row.headsign
        var towardsLeft = rightEdge
        if (headsign != null && departure != null) {
            val (p, text) = fitted(context, "towards $headsign", TOWARDS_SIZE * u, TOWARDS, 0.5f * rowW)
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText(text, rightEdge, baseline(secondTop, TOWARDS_SIZE * u, p), p)
            towardsLeft = rightEdge - p.measureText(text)
        }

        // Distance, under the name.
        row.subtitle?.let {
            val (p, text) = fitted(context, it, DISTANCE_SIZE * u, DISTANCE, (towardsLeft - textLeft - 10f * u).coerceAtLeast(20f))
            canvas.drawText(text, textLeft, baseline(secondTop, DISTANCE_SIZE * u, p), p)
        }
    }

    private fun drawEmpty(context: Context, canvas: Canvas, snapshot: WidgetSnapshot, w: Float, centerY: Float, density: Float) {
        val titleSize = 20f * density
        val (title, titleText) = fitted(context, snapshot.error ?: "No favourites yet", titleSize, NAME, w - 48f * density)
        title.textAlign = Paint.Align.CENTER
        canvas.drawText(titleText, w / 2f, baseline(centerY - titleSize * LINE, titleSize, title), title)
        val subSize = 13f * density
        val (sub, subText) = fitted(context, "Star stops in Journey to see live departures here", subSize, TOWARDS, w - 48f * density)
        sub.textAlign = Paint.Align.CENTER
        canvas.drawText(subText, w / 2f, baseline(centerY + 2f * density, subSize, sub), sub)
    }

    private fun drawFooter(context: Context, canvas: Canvas, snapshot: WidgetSnapshot, w: Float, h: Float, margin: Float, footerH: Float, density: Float, iconSize: Float) {
        val size = 11f * density
        val updated = if (snapshot.updatedAtEpochSec > 0) {
            "Updated ${TimeFormatter.clock(Instant.ofEpochSecond(snapshot.updatedAtEpochSec))}"
        } else {
            "Not updated yet"
        }
        val label = when {
            snapshot.refreshing -> "Refreshing…"
            snapshot.error != null && snapshot.rows.isNotEmpty() -> "${snapshot.error} · $updated"
            else -> updated
        }
        val iconLeft = w - margin - iconSize
        val (p, text) = fitted(context, label, size, TOWARDS, iconLeft - margin - 8f * density)
        p.color = 0xB3FFFFFF.toInt()
        val cy = h - footerH / 2f - 2f * density
        canvas.drawText(text, margin, baseline(cy - size * LINE / 2f, size, p), p)
        ContextCompat.getDrawable(context, R.drawable.ic_widget_refresh)?.mutate()?.let {
            it.setTint(0xB3FFFFFF.toInt())
            it.setBounds(iconLeft.roundToInt(), (cy - iconSize / 2f).roundToInt(), (iconLeft + iconSize).roundToInt(), (cy + iconSize / 2f).roundToInt())
            it.draw(canvas)
        }
    }

    @Volatile private var rss: Bitmap? = null

    private fun rssBitmap(context: Context): Bitmap? = rss ?: BitmapFactory.decodeResource(context.resources, R.drawable.widget_live_rss)?.also { rss = it }
}
