/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: UI Module
 * File: JourneyFloatingToolbar.kt
 * Description: Floating bottom toolbar (tabbed or standard mode) with a contextual FAB.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.utils.HapticUtil

data class ToolbarItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
    val onClick: () -> Unit,
    val hasBadge: Boolean = false,
)

/** Describes the contextual FAB shown beside the toolbar. */
data class ToolbarFab(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JourneyFloatingToolbar(
    modifier: Modifier = Modifier,
    // Tabbed Mode
    items: List<ToolbarItem> = emptyList(),
    selectedIndex: Int = -1,
    // Standard Mode
    title: String? = null,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    /** Small icon buttons beside the title (standard mode). */
    actions: List<ToolbarFab> = emptyList(),
    // FAB / Action
    fab: ToolbarFab? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    expanded: Boolean = true,
) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale

    // Hide label if font scale is large or screen width is too small
    val shouldHideLabel = fontScale > 1.25f || (configuration.screenWidthDp < 400 && items.size > 3)

    val finalFab: @Composable () -> Unit = {
        if (fab != null) {
            FloatingActionButton(
                onClick = {
                    HapticUtil.performUIHaptic(view)
                    fab.onClick()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                // Morph between tab actions with an expressive spin-and-scale.
                AnimatedContent(
                    targetState = fab,
                    contentKey = { it.icon },
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.4f) + fadeOut())
                    },
                    label = "fab_icon",
                ) { target ->
                    Icon(target.icon, contentDescription = target.contentDescription)
                }
            }
        }
    }

    HorizontalFloatingToolbar(
        modifier = modifier.padding(horizontal = 16.dp),
        expanded = expanded,
        floatingActionButton = finalFab,
        scrollBehavior = scrollBehavior,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            toolbarContainerColor = MaterialTheme.colorScheme.primary,
        ),
        content = {
            if (onBackClick != null) {
                // BACK BUTTON - Unified with Tabbed style (pop-out effect)
                IconButton(
                    onClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        onBackClick()
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                ) {
                    Icon(Sym.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                }

                if (title != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // Leave room for the back button, the FAB and any action buttons on narrow screens.
                    val titleMax = (configuration.screenWidthDp - 32 - 16 - 56 - 12 - 48 - 8 - 24 - actions.size * 44).coerceIn(90, 260).dp
                    Column(
                        modifier = Modifier
                            .widthIn(min = 90.dp, max = titleMax)
                            .padding(horizontal = 8.dp)
                            .align(Alignment.CenterVertically),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }
                    actions.forEach { action ->
                        IconButton(
                            onClick = {
                                HapticUtil.performUIHaptic(view)
                                action.onClick()
                            },
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        ) {
                            Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            } else {
                // TABBED MODE - Expanding labels
                items.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    val springSpec = spring<androidx.compose.ui.unit.Dp>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    )
                    val itemWidth by animateDpAsState(if (expanded || isSelected) 48.dp else 0.dp, springSpec, label = "item_width_$index")
                    val labelWidth by animateDpAsState(if (isSelected && !shouldHideLabel) 88.dp else 0.dp, springSpec, label = "label_width_$index")
                    val spacerWidth by animateDpAsState(if (index < items.size - 1) 8.dp else 0.dp, springSpec, label = "spacer_width_$index")

                    if (itemWidth > 0.dp || isSelected) {
                        IconButton(
                            onClick = {
                                HapticUtil.performVirtualKeyHaptic(view)
                                item.onClick()
                            },
                            modifier = Modifier
                                .width(itemWidth + labelWidth)
                                .height(48.dp)
                                .semantics {
                                    role = Role.Tab
                                    selected = isSelected
                                },
                            colors = if (isSelected) {
                                IconButtonDefaults.filledIconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.background,
                                )
                            } else {
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.primary,
                                )
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Box {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    if (item.hasBadge) {
                                        Canvas(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .align(Alignment.TopEnd),
                                        ) { drawCircle(color = Color.Red) }
                                    }
                                }
                                if (isSelected && !shouldHideLabel) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.basicMarquee(),
                                    )
                                }
                            }
                        }

                        // Animated spacing between buttons
                        if (index < items.size - 1) {
                            Spacer(modifier = Modifier.width(spacerWidth))
                        }
                    }
                }
            }
        },
    )
}
