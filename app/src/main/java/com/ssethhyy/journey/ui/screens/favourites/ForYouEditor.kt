/*
 * Feature Module: For You
 * File: ForYouEditor.kt
 * Description: Edit mode for For You: drag widgets into a new order, open each one's settings, remove and add them.
 *
 * Dragging works like moving a widget on a Pixel home screen. Hold a widget (or grab its handle) and it lifts off
 * the list and follows your finger. A filled rounded shape shows where it will land, and the other widgets move
 * aside as it passes their middle. Let go and it settles into that place. The gesture is read on the whole list
 * (not on the widget, which moves as the order changes), and the list scrolls by itself near its top and bottom
 * edges.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ssethhyy.journey.data.foryou.ForYouLayout
import com.ssethhyy.journey.data.foryou.ForYouWidget
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val HEADER_KEY = "editor_header"

@Composable
fun ForYouEditor(
    layout: ForYouLayout,
    summary: (ForYouWidget) -> String,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onReorder: (List<ForYouWidget>) -> Unit,
    onRemove: (ForYouWidget) -> Unit,
    onSettings: (ForYouWidget) -> Unit,
    onAdd: () -> Unit,
    onShowTitles: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // A local copy, so the order changes instantly while dragging and is saved once on release.
    var order by remember { mutableStateOf(layout.widgets) }
    var dragging by remember { mutableStateOf<ForYouWidget?>(null) }
    LaunchedEffect(layout.widgets) { if (dragging == null) order = layout.widgets }

    // Where the lifted widget is drawn: its top edge in the list's own coordinates, and its height.
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableIntStateOf(0) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var listHeight by remember { mutableIntStateOf(0) }
    // Converts a lazy item's offset to the list's coordinates (the top content padding).
    val paddingPx = with(density) { (topPadding + 8.dp).toPx() }
    val handleWidthPx = with(density) { 64.dp.toPx() }
    val edgePx = with(density) { 96.dp.toPx() }

    val currentOrder by rememberUpdatedState(order)

    fun itemTop(key: Any): Float? = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        ?.let { it.offset.toFloat() - listState.layoutInfo.viewportStartOffset }

    fun widgetAt(y: Float): Pair<ForYouWidget, Float>? = listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
        val widget = ForYouWidget.entries.firstOrNull { it.name == info.key } ?: return@firstNotNullOfOrNull null
        val top = info.offset.toFloat() - listState.layoutInfo.viewportStartOffset
        if (y >= top && y <= top + info.size) widget to top else null
    }

    /** Swaps the lifted widget past a neighbour once its middle passes the neighbour's middle. */
    fun settleOrder() {
        val widget = dragging ?: return
        val center = dragTop + dragHeight / 2f
        var changed = true
        while (changed) {
            changed = false
            val list = order
            val index = list.indexOf(widget)
            val info = listState.layoutInfo.visibleItemsInfo
            val before = list.getOrNull(index - 1)?.let { prev -> info.firstOrNull { it.key == prev.name } }
            val after = list.getOrNull(index + 1)?.let { next -> info.firstOrNull { it.key == next.name } }
            val start = listState.layoutInfo.viewportStartOffset
            if (before != null && center < before.offset - start + before.size / 2f) {
                order = list.toMutableList().apply { add(index - 1, removeAt(index)) }
                changed = true
            } else if (after != null && center > after.offset - start + after.size / 2f) {
                order = list.toMutableList().apply { add(index + 1, removeAt(index)) }
                changed = true
            }
            if (changed) HapticUtil.performMicroHaptic(view)
        }
    }

    fun endDrag() {
        val widget = dragging ?: return
        scope.launch {
            // Settle into the placeholder, then drop.
            val target = itemTop(widget.name)
            if (target != null) animate(dragTop, target, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragTop = v }
            dragging = null
            if (order != layout.widgets) onReorder(order)
        }
        HapticUtil.performLightHaptic(view)
    }

    // Scrolls while the finger rests near the top or bottom edge.
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        while (dragging != null) {
            withFrameNanos { }
            val speed = when {
                fingerY < edgePx -> -((edgePx - fingerY) / edgePx) * 28f
                fingerY > listHeight - edgePx -> ((fingerY - (listHeight - edgePx)) / edgePx) * 28f
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed)
                settleOrder()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { listHeight = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val hit = widgetAt(down.position.y) ?: return@awaitEachGesture
                    // The handle lifts straight away; anywhere else on the widget needs a hold, so the list still scrolls.
                    val onHandle = down.position.x - with(density) { 16.dp.toPx() } < handleWidthPx
                    if (!onHandle) {
                        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                if (!change.pressed) return@withTimeoutOrNull true
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                            }
                            @Suppress("UNREACHABLE_CODE")
                            true
                        }
                        if (ended != null) return@awaitEachGesture
                    }
                    down.consume()
                    val (widget, top) = hit
                    dragHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == widget.name }?.size ?: 0
                    dragTop = top
                    fingerY = down.position.y
                    dragging = widget
                    HapticUtil.performHeavyHaptic(view)
                    var last: Offset = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val dy = change.position.y - last.y
                        last = change.position
                        change.consume()
                        fingerY = change.position.y
                        dragTop += dy
                        settleOrder()
                    }
                    endDrag()
                }
            },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = dragging == null,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = HEADER_KEY) {
                EditorHeader(showTitles = layout.showTitles, onShowTitles = onShowTitles, onDone = onDone)
            }
            items(order, key = { it.name }) { widget ->
                val lifted = widget == dragging
                Box(Modifier.animateItem(placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))) {
                    // The tile always lays out, so the placeholder is exactly its size.
                    EditorTile(
                        widget = widget,
                        summary = summary(widget),
                        onSettings = { onSettings(widget) },
                        onRemove = { onRemove(widget) },
                        onMove = { delta ->
                            val index = order.indexOf(widget)
                            val target = (index + delta).coerceIn(0, order.lastIndex)
                            if (target != index) {
                                val next = order.toMutableList().apply { add(target, removeAt(index)) }
                                order = next
                                onReorder(next)
                            }
                        },
                        modifier = Modifier.graphicsLayer { alpha = if (lifted) 0f else 1f },
                    )
                    if (lifted) {
                        // Where the widget lands if you let go now.
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
                        )
                    }
                }
            }
            item(key = "editor_footer") {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalButton(
                        onClick = {
                            HapticUtil.performUIHaptic(view)
                            onAdd()
                        },
                        enabled = layout.available.isNotEmpty(),
                        contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Sym.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (layout.available.isEmpty()) "Every widget is on For You" else "Add a widget")
                    }
                    TextButton(onClick = { HapticUtil.performUIHaptic(view); onReset() }) {
                        Icon(Sym.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset to the default layout")
                    }
                }
            }
        }

        val lifted = dragging
        if (lifted != null) {
            EditorTile(
                widget = lifted,
                summary = summary(lifted),
                onSettings = {},
                onRemove = {},
                onMove = {},
                lifted = true,
                modifier = Modifier
                    .zIndex(1f)
                    .padding(horizontal = 16.dp)
                    .offset { IntOffset(0, dragTop.roundToInt()) }
                    .graphicsLayer {
                        scaleX = 1.03f
                        scaleY = 1.03f
                    },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorHeader(showTitles: Boolean, onShowTitles: (Boolean) -> Unit, onDone: () -> Unit) {
    val view = LocalView.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Edit For You", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    "Hold a widget, or grab its handle, and drag it to move it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { HapticUtil.performConfirmHaptic(view); onDone() }, contentPadding = PaddingValues(start = 14.dp, end = 18.dp)) {
                Icon(Sym.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done")
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            ListItem(
                headlineContent = { Text("Widget titles") },
                supportingContent = { Text("A title over each widget, with an arrow to fold it away") },
                leadingContent = { Icon(Sym.Title, null) },
                trailingContent = {
                    Switch(checked = showTitles, onCheckedChange = {
                        HapticUtil.performToggleHaptic(view, it)
                        onShowTitles(it)
                    })
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }
}

@Composable
private fun EditorTile(
    widget: ForYouWidget,
    summary: String,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
) {
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Move up") { onMove(-1); true },
                    CustomAccessibilityAction("Move down") { onMove(1); true },
                )
            },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.DragIndicator, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(widget.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(widget.title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { HapticUtil.performUIHaptic(view); onSettings() }) {
                Icon(Sym.Settings, contentDescription = "${widget.title} settings")
            }
            IconButton(onClick = { HapticUtil.performRejectHaptic(view); onRemove() }) {
                Icon(Sym.RemoveCircle, contentDescription = "Remove ${widget.title}", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The widgets that can be added: removed ones and those For You doesn't show by default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetSheet(available: List<ForYouWidget>, onAdd: (ForYouWidget) -> Unit, onDismiss: () -> Unit) {
    val view = LocalView.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add a widget", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            available.forEach { widget ->
                Surface(
                    onClick = {
                        HapticUtil.performConfirmHaptic(view)
                        onAdd(widget)
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(widget.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(widget.title, style = MaterialTheme.typography.titleMedium)
                            Text(widget.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Sym.Add, contentDescription = "Add ${widget.title}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
