/*
 * Feature Module: Navigation
 * File: KeepAliveHome.kt
 * Description: Keeps the home screen (and its map) alive underneath the pages opened on top of it.
 *
 * Navigation 3 removes a screen from composition when another is pushed over it, which threw the map away (and made
 * it rebuild its style and tiles) every time you opened a stop, a line or search and came back. Instead the home
 * screen is drawn once, beneath the navigation display, and pages cover it. While it's covered:
 * - its lifecycle is held at CREATED, so the map stops drawing and anything that refreshes or polls while started
 *   (departures, the clock, location) pauses, and nothing is fetched for a screen nobody can see;
 * - its Back handlers are parked, so Back goes to the page on top;
 * - it ignores touches and is hidden from accessibility services.
 * Uncovered, everything picks up where it was, instantly.
 */

package com.ssethhyy.journey.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A lifecycle that follows [parent] but stops at CREATED while [capped]. */
internal class CappedLifecycleOwner(private val parent: LifecycleOwner) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    var capped: Boolean = false
        set(value) {
            field = value
            update()
        }

    fun update() {
        val state = parent.lifecycle.currentState
        // Never step down to DESTROYED here: that's final, and only the parent's own end should cause it.
        registry.currentState = when {
            state == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
            capped -> minOf(state, Lifecycle.State.CREATED).coerceAtLeast(Lifecycle.State.CREATED)
            else -> state.coerceAtLeast(Lifecycle.State.CREATED)
        }
    }
}

/** Where a covered screen's Back handlers wait: a dispatcher nothing presses. */
private val ParkedBack = object : OnBackPressedDispatcherOwner {
    private val parked = OnBackPressedDispatcher()
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = parked
    override val lifecycle: Lifecycle get() = owner.lifecycle
}

/** Draws [content] (the home screen) so it stays alive under pages; [covered] while a page is on top. */
@Composable
fun KeepAliveHome(covered: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { CappedLifecycleOwner(parent) }
    DisposableEffect(parent, owner) {
        val observer = LifecycleEventObserver { _, _ -> owner.update() }
        parent.lifecycle.addObserver(observer)
        onDispose { parent.lifecycle.removeObserver(observer) }
    }
    SideEffect { owner.capped = covered }
    val back = LocalOnBackPressedDispatcherOwner.current
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalOnBackPressedDispatcherOwner provides if (covered || back == null) ParkedBack else back,
    ) {
        Box(if (covered) modifier.clearAndSetSemantics {} else modifier) { content() }
    }
}
