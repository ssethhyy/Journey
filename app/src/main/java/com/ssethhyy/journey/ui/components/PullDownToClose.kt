/*
 * Feature Module: UI components
 * File: PullDownToClose.kt
 * Description: Drag a page down to close it, for pages opened by pulling a map panel up.
 *
 * Once the page's list is at the top, pulling further down moves the whole page with your finger. As it goes it
 * shrinks a little and its corners round, turning back into the map's floating panel. Let go far enough down (or
 * flick) and the page closes, back to the map; otherwise it springs back.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The decision, kept apart so it can be tested: close when pulled past [threshold] or flicked down fast enough. */
object PullDownToCloseLogic {
    const val FLICK = 1_500f

    fun shouldClose(offset: Float, velocity: Float, threshold: Float): Boolean =
        offset > 0f && (offset >= threshold || velocity >= FLICK)
}

@Stable
class PullDownToClose internal constructor(
    private val enabled: Boolean,
    private val scope: CoroutineScope,
    private val onClose: () -> Unit,
    private val haptic: () -> Unit,
    private val cornerPx: Float,
) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var heightPx by mutableFloatStateOf(1f)
    private var closing = false
    private val threshold get() = heightPx * 0.22f

    /** 0 at rest to 1 once it's pulled far enough to close. */
    val progress: Float get() = (offset / threshold.coerceAtLeast(1f)).coerceIn(0f, 1f)

    /** For the page's root: moves and morphs the page as it's pulled. */
    val pageModifier: Modifier = Modifier
        .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            val p = progress
            translationY = offset
            scaleX = 1f - 0.06f * p
            scaleY = 1f - 0.06f * p
            transformOrigin = TransformOrigin(0.5f, 0f)
            shape = RoundedCornerShape(cornerPx * p)
            clip = offset > 0f
        }

    /** For the page's scrolling list (as the list's own nestedScroll, so it comes before pull-to-refresh). */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || closing || source != NestedScrollSource.UserInput) return Offset.Zero
            // Pushing back up while pulled: move the page back before scrolling the list.
            if (offset > 0f && available.y < 0f) {
                val used = maxOf(available.y, -offset)
                offset += used
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || closing || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            val before = progress
            offset += available.y
            if (before < 1f && progress >= 1f) haptic()
            return available
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!enabled || offset <= 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }

    private fun settle(velocity: Float) {
        if (PullDownToCloseLogic.shouldClose(offset, velocity, threshold)) {
            closing = true
            onClose()
        } else {
            scope.launch {
                animate(offset, 0f, initialVelocity = velocity, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                    offset = v
                }
            }
        }
    }
}

/** Pull-down-to-close for a page; does nothing when not [enabled] (the page wasn't opened from the map). */
@Composable
fun rememberPullDownToClose(enabled: Boolean, onClose: () -> Unit): PullDownToClose {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val close by rememberUpdatedState(onClose)
    val corner = with(LocalDensity.current) { 28.dp.toPx() }
    return remember(enabled) {
        PullDownToClose(enabled, scope, onClose = { close() }, haptic = { HapticUtil.performUIHaptic(view) }, cornerPx = corner)
    }
}
