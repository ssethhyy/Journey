/*
 * Feature Module: UI Components
 * File: FloatingTopBar.kt
 * Description: The app's top bar: a floating pill with the page title (and a line underneath),
 * a round back button and an actions pill, styled like the floating navbar. Content scrolls under
 * it, and a progressive blur softens whatever passes beneath. The bar reports its real height, so
 * Scaffold's top padding is exactly the space content needs to leave clear at rest.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.utils.HapticUtil

private val PillHeight = 52.dp
private val BarVerticalPadding = 8.dp

/** Height of the floating bar including the status bar: how far content must start below the top. */
@Composable
fun floatingBarInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + PillHeight + BarVerticalPadding * 2

/**
 * The floating top bar. [visible] fades and drops it in or out (For You shows it only once its big
 * greeting has scrolled away); the space it reserves stays the same either way.
 */
@Composable
fun ReusableTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    visible: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "top_bar_visibility",
    )
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(PillHeight + BarVerticalPadding * 2),
    ) {
        // Nothing is composed while fully hidden, so an invisible bar never takes touches.
        if (shown > 0.02f) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = BarVerticalPadding)
                    .graphicsLayer {
                        alpha = shown.coerceIn(0f, 1f)
                        translationY = -(1f - shown) * with(density) { 28.dp.toPx() }
                    },
                verticalAlignment = Alignment.CenterVertically,
                // A lone title pill (no buttons) sits in the middle; with buttons it fills the gap between them.
                horizontalArrangement = if (onBackClick == null && actions == null) Arrangement.Center else Arrangement.spacedBy(8.dp),
            ) {
                if (onBackClick != null) {
                    Surface(
                        onClick = {
                            HapticUtil.performVirtualKeyHaptic(view)
                            onBackClick()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(PillHeight),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(Sym.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .weight(1f, fill = onBackClick != null || actions != null)
                        .height(PillHeight),
                ) {
                    Column(
                        Modifier.padding(horizontal = 22.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                if (actions != null) {
                    Surface(
                        shape = RoundedCornerShape(PillHeight / 2),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 4.dp,
                    ) {
                        Row(Modifier.height(PillHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                    }
                } else if (onBackClick != null) {
                    // Balance the back button so the title stays centred.
                    Spacer(Modifier.width(0.dp))
                }
            }
        }
    }
}

/** Progressive blur over the top [topInset] of scrolling content, so it softens as it passes under the bar. */
@Composable
fun Modifier.floatingBarBlur(topInset: Dp): Modifier {
    val density = LocalDensity.current
    val available = rememberBlurAvailable()
    val radius = if (available) with(density) { 18.dp.toPx() } else 0f
    return this.progressiveBlur(radius, with(density) { topInset.toPx() }, BlurDirection.TOP)
}

/** Pull to refresh whose spinner appears below the floating bar rather than behind it. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FloatingPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
            )
        },
        content = content,
    )
}
