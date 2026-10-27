/*
 * Feature Module: UI Components
 * File: Containers.kt
 * Description: RoundedCardContainer, list items, pickers and toggles from the android-common design language.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.utils.HapticUtil
import kotlin.math.roundToInt

/**
 * Groups related list items into a single rounded section. Children are clipped and separated by
 * a small gap so each row reads as a segment of one card.
 */
@Composable
fun RoundedCardContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Label placed above a [RoundedCardContainer]. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Leading icon tile used by list items. */
@Composable
fun LeadingIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * Settings workhorse: leading icon, title, optional description and a trailing switch.
 * When disabled, [onDisabledClick] can explain why the option is unavailable.
 */
@Composable
fun IconToggleItem(
    icon: ImageVector,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    showToggle: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(role = if (onClick == null) Role.Switch else Role.Button) {
                HapticUtil.performVirtualKeyHaptic(view)
                when {
                    !enabled -> onDisabledClick?.invoke()
                    onClick != null -> onClick()
                    else -> onCheckedChange(!isChecked)
                }
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showToggle) {
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = isChecked,
                enabled = enabled,
                onCheckedChange = {
                    HapticUtil.performVirtualKeyHaptic(view)
                    onCheckedChange(it)
                },
            )
        }
    }
}

/** Tappable row with an optional trailing value and chevron. */
@Composable
fun ActionItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailingText: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(role = Role.Button) {
                HapticUtil.performVirtualKeyHaptic(view)
                onClick()
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (showChevron) {
            Icon(
                Sym.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Connected button group that behaves like a segmented control. Generic so it works with enums,
 * strings or data classes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SegmentedPicker(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: (@Composable (T) -> ImageVector?)? = null,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        items.forEachIndexed { index, item ->
            val selected = item == selectedItem
            ToggleButton(
                checked = selected,
                onCheckedChange = {
                    HapticUtil.performUIHaptic(view)
                    onItemSelected(item)
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                iconProvider?.invoke(item)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(labelProvider(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Settings row that pairs a title/description with an inline [SegmentedPicker]. */
@Composable
fun <T> ConfigPickerItem(
    icon: ImageVector,
    title: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingIcon(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        SegmentedPicker(
            items = items,
            selectedItem = selectedItem,
            onItemSelected = onItemSelected,
            labelProvider = labelProvider,
            modifier = Modifier.background(Color.Transparent),
        )
    }
}

/** Slider row with tick haptics on each step change. */
@Composable
fun SliderItem(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueLabel: (Int) -> String,
    onValueChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingIcon(icon)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                valueLabel(sliderValue.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                val snapped = (((it - range.first) / step).roundToInt() * step + range.first).toFloat()
                if (snapped != sliderValue) HapticUtil.performSliderHaptic(view)
                sliderValue = snapped
            },
            onValueChangeFinished = { onValueChangeFinished(sliderValue.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            modifier = Modifier.padding(start = 56.dp),
        )
    }
}

/** Left-aligned card-styled toggle used to expand or collapse long lists. */
@Composable
fun ListExpandToggleButton(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    expandedText: String,
    collapsedText: String,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "list_expand_chevron_rotation")
    Box(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = {
                HapticUtil.performVirtualKeyHaptic(view)
                onToggle()
            },
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(
                Sym.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (isExpanded) expandedText else collapsedText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
