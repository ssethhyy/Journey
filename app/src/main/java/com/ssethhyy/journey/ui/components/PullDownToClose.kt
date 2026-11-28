/*
 * Feature Module: UI components
 * File: PullDownToClose.kt
 * Description: Drag a page down to go back to the map, for pages a map panel turned into.
 *
 * The page and the panel at its full height look the same, so as soon as you pull down past the top of the page's
 * list it hands straight back to the map, where the panel (starting as the page) shrinks back down. That keeps the
 * change seamless: the page never slides away or jumps.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** The decision, kept apart so it can be tested: go back once pulled past [threshold], or on a downward flick. */
object PullDownToCloseLogic {
    const val FLICK = 1_200f

    fun shouldClose(pulled: Float, velocity: Float, threshold: Float): Boolean =
        pulled > 0f && (pulled >= threshold || velocity >= FLICK)
}

@Stable
class PullDownToClose internal constructor(
    private val enabled: Boolean,
    private val threshold: Float,
    private val onClose: () -> Unit,
) {
    private var pulled = 0f
    private var closed = false

    /** Kept for the page's root; the page itself doesn't move (the panel takes over from it). */
    val pageModifier: Modifier = Modifier

    private fun close() {
        if (closed) return
        closed = true
        onClose()
    }

    /** For the page's scrolling list (as the list's own nestedScroll, so it comes before pull-to-refresh). */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (enabled && available.y < 0f) pulled = 0f
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || closed || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            pulled += available.y
            if (PullDownToCloseLogic.shouldClose(pulled, 0f, threshold)) close()
            return available
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!enabled || pulled <= 0f) return Velocity.Zero
            if (PullDownToCloseLogic.shouldClose(pulled, available.y, threshold)) close()
            pulled = 0f
            return available
        }
    }
}

/** Pull-down-to-go-back for a page; does nothing when not [enabled] (the page wasn't opened from the map). */
@Composable
fun rememberPullDownToClose(enabled: Boolean, onClose: () -> Unit): PullDownToClose {
    val close by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    return remember(enabled) { PullDownToClose(enabled, threshold) { close() } }
}
