/*
 * Feature Module: Map
 * File: MapSheet.kt
 * Description: The floating panel the map's stop, line and journey views sit in.
 *
 * It has three steps: just its header, open (a short list over the map) and full page. Drag the header, or keep
 * scrolling past either end of the list, and it follows your finger; let go and it springs to the nearest step or on
 * in the direction of a flick. On the way to full page it morphs: the corners tighten, the colour settles into the
 * page's surface and the list takes the whole height, while a sliver of map stays visible down each side. Pull it
 * back down and it morphs back into the floating panel.
 *
 * Open is never taller than the list needs, so a short list doesn't leave empty space under it.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far a map sheet is pulled up. */
enum class SheetStep { PEEK, OPEN, FULL }

/** The step a sheet lands on when let go at [height] with [velocity] (negative is upward), given its [anchors]. */
internal object MapSheetSettle {
    const val FLICK = 800f

    fun target(height: Float, velocity: Float, anchors: List<Float>): Float = when {
        velocity < -FLICK -> anchors.firstOrNull { it > height + 1f } ?: anchors.last()
        velocity > FLICK -> anchors.lastOrNull { it < height - 1f } ?: anchors.first()
        else -> anchors.minBy { kotlin.math.abs(it - height) }
    }

    /** 0 at open, 1 at full page. */
    fun fullProgress(height: Float, openPx: Float, fullPx: Float): Float =
        if (fullPx <= openPx) 0f else ((height - openPx) / (fullPx - openPx)).coerceIn(0f, 1f)
}

/** The colours and shape a sheet has [progress] of the way (0 to 1) from floating panel to full page. */
@Composable
fun rememberSheetMorph(progress: Float): Pair<RoundedCornerShape, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    val radius = (28f - 8f * progress).dp
    return RoundedCornerShape(radius) to lerp(scheme.surfaceContainerHigh, scheme.surfaceContainerLow, progress)
}

@Composable
fun MapSheet(
    step: SheetStep,
    onStepChange: (SheetStep) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** The tallest the list gets when open (it's shorter if the list is). */
    openHeight: Dp = 340.dp,
    /** 0 at open to 1 at full page, as it moves; lets the map tuck its controls away. */
    onFullProgress: (Float) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
    /** What the panel shows, for its accessibility actions ("Collapse Flinders Street Station"). */
    name: String = "panel",
    content: LazyListScope.() -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.getTop(density)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        var headerPx by remember { mutableFloatStateOf(with(density) { 72.dp.toPx() }) }
        val maxPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else with(density) { 800.dp.toPx() }
        val openCap = with(density) { openHeight.toPx() }
        // What the list actually needs, once it has been laid out with room to spare.
        val natural by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                if (last == null || listState.canScrollForward || listState.canScrollBackward || last.index != info.totalItemsCount - 1) Float.MAX_VALUE
                else (last.offset + last.size + info.afterContentPadding).toFloat()
            }
        }
        val fullPx = (maxPx - statusTop - headerPx - with(density) { 8.dp.toPx() }).coerceAtLeast(openCap * 0.5f)
        val openPx = minOf(openCap, natural, fullPx)
        val anchors = listOf(0f, openPx, fullPx).distinct().sorted()

        fun targetFor(s: SheetStep) = when (s) {
            SheetStep.PEEK -> 0f
            SheetStep.OPEN -> openPx
            SheetStep.FULL -> fullPx
        }

        fun stepFor(px: Float) = when {
            px <= 0.5f -> SheetStep.PEEK
            kotlin.math.abs(px - fullPx) < 0.5f && fullPx > openPx + 0.5f -> SheetStep.FULL
            else -> SheetStep.OPEN
        }

        var height by remember { mutableFloatStateOf(targetFor(step)) }
        var dragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val fullProgress = MapSheetSettle.fullProgress(height, openPx, fullPx)
        val reportFull by rememberUpdatedState(onFullProgress)
        LaunchedEffect(fullProgress) { reportFull(fullProgress) }

        fun animateTo(target: Float, velocity: Float = 0f) {
            settleJob?.cancel()
            settleJob = scope.launch {
                animate(height, target, initialVelocity = -velocity, animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    height = value
                }
            }
        }

        // Changed from outside (Back, a new stop) or the list's natural height changed: follow unless a finger is on it.
        LaunchedEffect(step, openPx, fullPx) {
            val target = targetFor(step)
            if (!dragging && height != target) animateTo(target)
        }

        fun dragBy(delta: Float) {
            settleJob?.cancel()
            height = (height - delta).coerceIn(0f, fullPx)
        }

        fun settle(velocity: Float) {
            dragging = false
            val target = MapSheetSettle.target(height, velocity, anchors)
            val next = stepFor(target)
            if (next != step) {
                HapticUtil.performUIHaptic(view)
                onStepChange(next)
            }
            animateTo(target, velocity)
        }

        fun atAnchor() = anchors.any { kotlin.math.abs(it - height) < 0.5f }

        BackHandler(enabled = step == SheetStep.FULL) { onStepChange(SheetStep.OPEN) }

        val dragState = rememberDraggableState { dragBy(it) }
        val listScroll = remember(anchors) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    if (dragging && !atAnchor()) {
                        dragBy(available.y)
                        return available
                    }
                    if (available.y < 0 && height < openPx - 0.5f) {
                        dragging = true
                        dragBy(available.y)
                        return available
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                    dragging = true
                    dragBy(available.y)
                    return available
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!dragging) return Velocity.Zero
                    settle(available.y)
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (dragging) settle(available.y)
                    return Velocity.Zero
                }
            }
        }

        val (shape, color) = rememberSheetMorph(fullProgress)
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = shape,
            color = color,
            shadowElevation = 6.dp,
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerPx = it.height.toFloat() }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { dragging = true },
                            onDragStopped = { velocity -> settle(velocity) },
                        )
                        .semantics {
                            stateDescription = when (step) {
                                SheetStep.FULL -> "Full page"
                                SheetStep.OPEN -> "Expanded"
                                SheetStep.PEEK -> "Collapsed"
                            }
                            customActions = listOf(
                                CustomAccessibilityAction(if (step == SheetStep.PEEK) "Expand $name" else "Collapse $name") {
                                    onStepChange(if (step == SheetStep.PEEK) SheetStep.OPEN else SheetStep.PEEK)
                                    true
                                },
                                CustomAccessibilityAction(if (step == SheetStep.FULL) "Show the map" else "Open as a full page") {
                                    onStepChange(if (step == SheetStep.FULL) SheetStep.OPEN else SheetStep.FULL)
                                    true
                                },
                            )
                        },
                ) {
                    Box(Modifier.padding(top = 6.dp)) { header() }
                    // A slim handle above the title: long names wrap to two lines, so it can't share their line.
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .graphicsLayer { alpha = 1f - fullProgress * 0.5f }
                            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
                if (height > 0.5f) {
                    val listHeight = with(density) { height.toDp() }
                    Box(Modifier.fillMaxWidth().height(listHeight).clipToBounds()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                // Laid out at least open-height tall, so it knows how much it really needs.
                                .height(maxOf(listHeight, openHeight))
                                .nestedScroll(listScroll)
                                .graphicsLayer { alpha = (height / openPx.coerceAtLeast(1f) * 1.6f).coerceAtMost(1f) },
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}
