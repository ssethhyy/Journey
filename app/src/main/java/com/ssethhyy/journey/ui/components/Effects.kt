/*
 * Feature Module: UI Components
 * File: Effects.kt
 * Description: Lifecycle-aware ticking helpers for live countdowns and auto refresh.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * False for home tabs that are composed but not currently on screen (the pager keeps neighbours
 * alive so switching is instant). Periodic work below pauses while this is false.
 */
val LocalTabActive = compositionLocalOf { true }

/** Current time, refreshed every [intervalMs] while the UI is started and visible. */
@Composable
fun rememberNow(intervalMs: Long = 15_000): State<Instant> {
    val state = remember { mutableStateOf(Instant.now()) }
    val owner = LocalLifecycleOwner.current
    val active = LocalTabActive.current
    LaunchedEffect(owner, intervalMs, active) {
        if (!active) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.value = Instant.now()
                delay(intervalMs)
            }
        }
    }
    return state
}

/**
 * Calls [onRefresh] every [intervalMs] while started, visible and [enabled]. Also refreshes
 * straight away when the app returns to the foreground or a hidden tab is shown again.
 */
@Composable
fun AutoRefreshEffect(enabled: Boolean, intervalMs: Long = 30_000, onRefresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onRefresh)
    val active = LocalTabActive.current
    val hasRun = remember { mutableStateOf(false) }
    LaunchedEffect(owner, enabled, intervalMs, active) {
        if (!enabled || !active) return@LaunchedEffect
        var first = !hasRun.value
        hasRun.value = true
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!first) callback()
            first = false
            while (true) {
                delay(intervalMs)
                callback()
            }
        }
    }
}
