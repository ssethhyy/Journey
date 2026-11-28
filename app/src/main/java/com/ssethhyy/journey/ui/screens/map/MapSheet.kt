/*
 * Feature Module: Map
 * File: MapSheet.kt
 * Description: The floating panel the map's Nearby, stop, line and journey views sit in.
 *
 * It has three steps: just its header, open (a short list over the map) and full page. Drag the header, or keep
 * scrolling past either end of the list, and it follows your finger; let go and it springs to the nearest step or on
 * in the direction of a flick.
 *
 * Between open and full page it turns into the page, frame by frame with your finger: it widens to the screen's edges
 * and down behind the toolbar, its corners straighten, its colour becomes the page's, its header folds away (the page
 * shows its title in the toolbar), the list's padding becomes the page's and the top makes room for the status bar.
 * By the time it lands everything is where the page has it, so opening the real page ([onFullPage], with no
 * transition) only changes the toolbar. Coming back, the panel starts as the page and shrinks back down.
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/** Where a page opened from a panel should start scrolled to, so it carries on from where the panel was. */
data class SheetScroll(val index: Int, val offset: Int)

/** Snappy, barely bouncing: the panel keeps up with a flick and lands quickly. */
private val SheetSpring = spring<Float>(dampingRatio = 0.92f, stiffness = Spring.StiffnessMedium)

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
    /** 0 with just the header to 1 at open, as it moves. */
    onProgress: (Float) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    /** The list's side padding as a panel, and on the page it becomes. */
    panelPadding: Dp = 12.dp,
    pagePadding: Dp = 16.dp,
    /** Gap between list items (the same on the page). */
    spacing: Dp = 8.dp,
    /** What the panel shows, for its accessibility actions ("Collapse Flinders Street Station"). */
    name: String = "panel",
    /**
     * Opens the panel's own page once it has turned into it, starting at the same scroll position. Coming back, the
     * panel starts full and shrinks back down to open.
     */
    onFullPage: ((SheetScroll) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val pageBottom = pageBottomInset(32.dp)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        var headerPx by remember { mutableIntStateOf(with(density) { 64.dp.roundToPx() }) }
        // How far the panel sits from the screen's edges, so it can grow out to them.
        var leftGap by remember { mutableFloatStateOf(0f) }
        var rightGap by remember { mutableFloatStateOf(0f) }
        var bottomGap by remember { mutableFloatStateOf(0f) }
        val screenH = view.height.toFloat().takeIf { it > 0f } ?: with(density) { 800.dp.toPx() }
        val openCap = with(density) { openHeight.toPx() }
        val panelBottomPx = with(density) { 12.dp.toPx() }
        // What the list actually needs, once it has been laid out with room to spare. Measured with the panel's own
        // bottom padding (not the page's, which grows with the morph), whole pixels, so it can't chase itself.
        val natural by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                if (last == null || listState.canScrollForward || listState.canScrollBackward || last.index != info.totalItemsCount - 1) Float.MAX_VALUE
                else kotlin.math.ceil(last.offset + last.size + panelBottomPx)
            }
        }
        // At full page the header has folded away and the status bar's height is reserved, so the list fills the rest.
        val fullPx = (screenH - statusTop).coerceAtLeast(openCap + 1f)
        val openPx = minOf(openCap, natural, fullPx - 1f)
        val anchors = listOf(0f, openPx, fullPx)

        fun targetFor(s: SheetStep) = when (s) {
            SheetStep.PEEK -> 0f
            SheetStep.OPEN -> openPx
            SheetStep.FULL -> fullPx
        }

        fun stepFor(px: Float) = when {
            px <= 0.5f -> SheetStep.PEEK
            kotlin.math.abs(px - fullPx) < 0.5f -> SheetStep.FULL
            else -> SheetStep.OPEN
        }

        var height by remember { mutableFloatStateOf(targetFor(step)) }
        var dragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        var settleTarget by remember { mutableFloatStateOf(-1f) }
        val fullProgress = MapSheetSettle.fullProgress(height, openPx, fullPx)
        val openProgress = (height / openPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val reportFull by rememberUpdatedState(onFullProgress)
        val reportOpen by rememberUpdatedState(onProgress)
        LaunchedEffect(fullProgress) { reportFull(fullProgress) }
        LaunchedEffect(openProgress) { reportOpen(openProgress) }
        val fullPage by rememberUpdatedState(onFullPage)

        fun animateTo(target: Float, velocity: Float = 0f, then: () -> Unit = {}) {
            settleJob?.cancel()
            settleTarget = target
            settleJob = scope.launch {
                animate(height, target, initialVelocity = -velocity, animationSpec = SheetSpring) { value, _ -> height = value }
                then()
            }
        }

        // Back from the panel's page: it was left as the page, so shrink back down to open.
        LaunchedEffect(Unit) { if (fullPage != null && step == SheetStep.FULL) onStepChange(SheetStep.OPEN) }

        // Changed from outside (Back, a new stop) or the list's natural height changed: follow unless a finger is on it.
        LaunchedEffect(step, openPx, fullPx) {
            val target = targetFor(step)
            // Already on its way there (a settle that opens the page when it lands): leave it be.
            if (settleJob?.isActive == true && settleTarget == target) return@LaunchedEffect
            if (!dragging && kotlin.math.abs(height - target) > 0.5f) animateTo(target)
        }

        fun dragBy(delta: Float) {
            settleJob?.cancel()
            height = (height - delta).coerceIn(0f, fullPx)
        }

        fun openPage() {
            fullPage?.invoke(SheetScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset))
        }

        fun settle(velocity: Float) {
            dragging = false
            val target = MapSheetSettle.target(height, velocity, anchors)
            val next = stepFor(target)
            if (next != step) {
                HapticUtil.performUIHaptic(view)
                onStepChange(next)
            }
            if (next == SheetStep.FULL && fullPage != null) animateTo(target, velocity, then = ::openPage) else animateTo(target, velocity)
        }

        fun atAnchor() = anchors.any { kotlin.math.abs(it - height) < 0.5f }

        BackHandler(enabled = step == SheetStep.FULL && fullPage == null) { onStepChange(SheetStep.OPEN) }

        val dragState = rememberDraggableState { dragBy(it) }
        val listScroll = remember(openPx, fullPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    if (dragging && !atAnchor()) {
                        dragBy(available.y)
                        return available
                    }
                    // Pushing up grows the panel first (all the way into its page); the list only scrolls once it's
                    // as big as it gets.
                    if (available.y < 0 && height < fullPx - 0.5f) {
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

        val p = fullProgress
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { c ->
                    // Measured at rest (as a panel), so the gaps are the panel's own margins.
                    if (p == 0f) {
                        val pos = c.positionInRoot()
                        leftGap = pos.x
                        rightGap = (view.width - pos.x - c.size.width).coerceAtLeast(0f)
                        bottomGap = (screenH - pos.y - c.size.height).coerceAtLeast(0f)
                    }
                }
                // Grows out to the screen's edges as it becomes the page. Its own size (what the map sees) stays
                // within the space it was given; the rest is drawn outside it.
                .layout { measurable, constraints ->
                    val extraL = (leftGap * p).roundToInt()
                    val extraR = (rightGap * p).roundToInt()
                    val extraB = (bottomGap * p).roundToInt()
                    val total = (statusTop * p + headerPx * (1f - p) + height).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + extraL + extraR, total))
                    val reported = (total - extraB).coerceIn(0, if (constraints.hasBoundedHeight) constraints.maxHeight else total)
                    layout(constraints.maxWidth, reported) { placeable.place(-extraL, reported + extraB - total) }
                },
            shape = RoundedCornerShape(28.dp * (1f - p)),
            color = lerp(scheme.surfaceContainerHigh, scheme.surface, p),
            shadowElevation = 6.dp * (1f - p),
        ) {
            Column {
                // Room for the status bar, as the page has.
                Spacer(Modifier.height(with(density) { (statusTop * p).toDp() }))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        // Folds away as it becomes the page (whose title is in the toolbar).
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            if (placeable.height != headerPx && p == 0f) headerPx = placeable.height
                            val h = (placeable.height * (1f - p)).roundToInt()
                            layout(placeable.width, h) {
                                placeable.placeWithLayer(0, h - placeable.height) { alpha = (1f - p * 1.8f).coerceIn(0f, 1f) }
                            }
                        }
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
                                    if (step == SheetStep.FULL) {
                                        onStepChange(SheetStep.OPEN)
                                    } else {
                                        onStepChange(SheetStep.FULL)
                                        if (fullPage != null) animateTo(fullPx, then = ::openPage)
                                    }
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
                            .background(scheme.outlineVariant, CircleShape),
                    )
                }
                if (height > 0.5f) {
                    val listHeight = with(density) { height.toDp() }
                    val side = lerp(panelPadding, pagePadding, p)
                    Box(Modifier.fillMaxWidth().height(listHeight).clipToBounds()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                // Laid out at least open-height tall, so it knows how much it really needs.
                                .height(maxOf(listHeight, openHeight))
                                .nestedScroll(listScroll)
                                .graphicsLayer { alpha = (openProgress * 1.6f).coerceAtMost(1f) },
                            contentPadding = PaddingValues(start = side, end = side, bottom = lerp(12.dp, pageBottom, p)),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}
