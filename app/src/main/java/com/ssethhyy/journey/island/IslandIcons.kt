/*
 * Feature Module: Island
 * File: IslandIcons.kt
 * Description: Renders the icons Journey sends to the island as small bitmaps (96 px, well under
 * the 256 px host limit), plus shared connection state shown in Settings.
 */

package com.ssethhyy.journey.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.ssethhyy.journey.R
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.IslandIconStyle
import com.ssethhyy.journey.ui.components.pictogramRes
import com.ssethhyy.journey.ui.components.symbolResId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

object IslandIcons {
    const val SIZE_PX = 96

    private fun render(context: Context, @DrawableRes res: Int, tint: Int?): Bitmap? {
        if (res == 0) return null
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return null
        if (tint != null) drawable.setTint(tint)
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun symbol(context: Context, base: String): Int = symbolResId(context, base)

    private fun modeSymbol(mode: TransportMode): String = when (mode) {
        TransportMode.TRAIN, TransportMode.VLINE -> "rounded_train"
        TransportMode.TRAM -> "rounded_tram"
        TransportMode.BUS -> "rounded_directions_bus"
        TransportMode.NIGHT_BUS -> "rounded_nights_stay"
    }

    /** Draws the bitmap for an icon id produced by [JourneyIsland.Icons]. */
    fun bitmapFor(context: Context, iconId: String): Bitmap? {
        DisruptionSeverity.entries.firstOrNull { JourneyIsland.Icons.alert(it) == iconId }?.let { severity ->
            return render(context, symbol(context, "rounded_warning_fill").takeIf { it != 0 } ?: symbol(context, "rounded_warning"), JourneyIsland.severityColor(severity))
        }
        for (mode in TransportMode.entries) {
            if (iconId == JourneyIsland.Icons.mode(mode, IslandIconStyle.PICTOGRAM)) return render(context, mode.pictogramRes, null)
            // Monochrome glyphs are white; the host tints them with its accent (tone "accent").
            if (iconId == JourneyIsland.Icons.mode(mode, IslandIconStyle.MONOCHROME)) return render(context, symbol(context, modeSymbol(mode)), 0xFFFFFFFF.toInt())
        }
        if (iconId == JourneyIsland.Icons.JOURNEY) return render(context, R.drawable.ic_stat_tracking, 0xFFFFFFFF.toInt())
        return null
    }

    fun bitmaps(context: Context, ids: Set<String>): Map<String, Bitmap> =
        ids.mapNotNull { id -> bitmapFor(context, id)?.let { id to it } }.toMap()
}

/** Live connection state with Essentials, shown on the Island settings page. */
object IslandConnection {
    data class State(
        val attached: Boolean = false,
        val hostPackage: String? = null,
        val lastPublishAt: Instant? = null,
        val itemsShown: List<String> = emptyList(),
        val lastPeek: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }

    /** Until this time the island shows a demo tracking card (Settings > Island > Preview). */
    private val _previewUntil = MutableStateFlow<Instant?>(null)
    val previewUntil: StateFlow<Instant?> = _previewUntil.asStateFlow()

    fun startPreview(seconds: Long = 90) {
        _previewUntil.value = Instant.now().plusSeconds(seconds)
    }

    fun stopPreview() {
        _previewUntil.value = null
    }

    /** One-shot request from Settings to peek the given item now (subject to the host's limits). */
    private val _peekRequests = MutableStateFlow<Pair<String, Long>?>(null)
    val peekRequests: StateFlow<Pair<String, Long>?> = _peekRequests.asStateFlow()

    fun requestPeek(itemKey: String) {
        _peekRequests.value = itemKey to System.nanoTime()
    }

    internal fun consumePeek() {
        _peekRequests.value = null
    }
}
