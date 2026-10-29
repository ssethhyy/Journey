/*
 * Feature Module: UI Components
 * File: AppChrome.kt
 * Description: The "bottom bar" navigation style. One floating toolbar lives above the whole navigation
 * stack and is always on screen: on the main tabs it shows the three tab icons with the tab's action button,
 * and on any other page it morphs into a back button, the page name and an action button for that page.
 *
 * Screens don't draw the toolbar. They *register* what it should show (see [RegisterChrome]), and
 * [AppChromeHost] draws the most recently registered entry. This file also holds the setting-aware helpers the
 * pages use so the original "top pill" style keeps working unchanged (see [BarStyle]).
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.repository.BarStyle

/** What the toolbar should show right now. */
sealed interface ChromeSpec {
    /** The three main tabs. [visible] lets the home screen hide the bar while a list scrolls down. */
    data class Tabs(
        val items: List<ToolbarItem>,
        val selectedIndex: Int,
        val fab: ToolbarFab?,
        val visible: Boolean = true,
    ) : ChromeSpec

    /** Any other page: back button, name and the actions that belong to it. */
    data class Page(
        val title: String,
        val subtitle: String?,
        val onBack: () -> Unit,
        /** The big button on the right, e.g. "Track this service". */
        val fab: ToolbarFab? = null,
        /** Smaller icon buttons beside the title, e.g. "Show on map". */
        val actions: List<ToolbarFab> = emptyList(),
    ) : ChromeSpec
}

/** Registered entries, newest last. The newest one is what the toolbar draws. */
@Stable
class AppChrome {
    private class Entry(val owner: Any, var spec: ChromeSpec)

    private val entries = mutableStateListOf<Entry>()

    val current: ChromeSpec? get() = entries.lastOrNull()?.spec

    fun register(owner: Any, spec: ChromeSpec) {
        val existing = entries.indexOfFirst { it.owner === owner }
        if (existing >= 0) {
            // Replace in place so the entry keeps its position (and recomposes readers).
            entries[existing] = Entry(owner, spec)
        } else {
            entries.add(Entry(owner, spec))
        }
    }

    fun unregister(owner: Any) {
        entries.removeAll { it.owner === owner }
    }
}

val LocalAppChrome = compositionLocalOf<AppChrome?> { null }

/** The style currently in force. Defaults to the original top pill so previews and tests that don't set it are unchanged. */
val LocalBarStyle = compositionLocalOf { BarStyle.TOP_PILL }

/** True when pages should hand their title and actions to the bottom bar instead of drawing a top pill. */
@Composable
fun usesBottomBar(): Boolean = LocalBarStyle.current == BarStyle.BOTTOM_BAR

/**
 * Registers what the toolbar should show for as long as the caller is on screen. The newest registration wins,
 * so a page that appears on top of another takes over, and the one underneath returns when it leaves.
 */
@Composable
fun RegisterChrome(spec: ChromeSpec) {
    val chrome = LocalAppChrome.current ?: return
    val owner = remember { Any() }
    DisposableEffect(chrome, owner) { onDispose { chrome.unregister(owner) } }
    SideEffect { chrome.register(owner, spec) }
}

/** Space a scrolling page leaves at the bottom: the toolbar is always there in the bottom-bar style. */
@Composable
fun pageBottomInset(classicExtra: Dp = 32.dp): Dp {
    val navigationBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navigationBars + if (usesBottomBar()) PageToolbarClearance else classicExtra
}

/** How far a page's content must stay clear of the floating toolbar (toolbar height plus its gap to the edge). */
val PageToolbarClearance = 104.dp

/**
 * Draws the toolbar above the whole navigation stack. Tabs hide it on scroll; pages always show it. Changing
 * between tabs and a page (or between two pages) morphs the bar instead of swapping it abruptly.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppChromeHost(chrome: AppChrome, modifier: Modifier = Modifier) {
    val spec = chrome.current
    val latest = rememberUpdatedState(spec)
    val visible = when (spec) {
        is ChromeSpec.Tabs -> spec.visible
        is ChromeSpec.Page -> true
        null -> false
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it } + fadeOut(),
        modifier = modifier,
    ) {
        // Animate only when the kind of bar (or the page) changes, not on every tab or title tweak.
        AnimatedContent(
            targetState = spec,
            contentKey = { s ->
                when (s) {
                    is ChromeSpec.Tabs -> "tabs"
                    is ChromeSpec.Page -> "page:${s.title}"
                    null -> "none"
                }
            },
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f)) togetherWith
                    (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.9f))
            },
            label = "app_chrome",
        ) { shown ->
            // While animating out, the old content keeps the spec it had; otherwise follow the latest one.
            val s = if (shown?.let { kindOf(it) } == latest.value?.let { kindOf(it) }) latest.value else shown
            val barModifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp)
            when (s) {
                is ChromeSpec.Tabs -> JourneyFloatingToolbar(
                    modifier = barModifier,
                    items = s.items,
                    selectedIndex = s.selectedIndex,
                    fab = s.fab,
                    expanded = true,
                )
                is ChromeSpec.Page -> JourneyFloatingToolbar(
                    modifier = barModifier,
                    title = s.title,
                    subtitle = s.subtitle,
                    onBackClick = s.onBack,
                    actions = s.actions,
                    fab = s.fab,
                )
                null -> Unit
            }
        }
    }
}

private fun kindOf(spec: ChromeSpec): String = when (spec) {
    is ChromeSpec.Tabs -> "tabs"
    is ChromeSpec.Page -> "page:${spec.title}"
}
