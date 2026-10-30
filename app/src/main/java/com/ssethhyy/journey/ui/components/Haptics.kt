/*
 * Feature Module: UI Components
 * File: Haptics.kt
 * Description: Small wrappers that add the right haptic to a click, so screens don't repeat LocalView plumbing.
 * The feelings themselves live in HapticUtil (the android-common haptics utility).
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import com.ssethhyy.journey.utils.HapticUtil

/** An ordinary tap: the standard UI click. */
@Composable
fun hapticClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    val latest by rememberUpdatedState(onClick)
    return remember(view) {
        {
            HapticUtil.performUIHaptic(view)
            latest()
        }
    }
}

/** Choosing between options (chips, segments): a light tick. */
@Composable
fun hapticSelect(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    val latest by rememberUpdatedState(onClick)
    return remember(view) {
        {
            HapticUtil.performLightHaptic(view)
            latest()
        }
    }
}

/** Committing something (save, record, confirm): the system confirm feel. */
@Composable
fun hapticConfirm(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    val latest by rememberUpdatedState(onClick)
    return remember(view) {
        {
            HapticUtil.performConfirmHaptic(view)
            latest()
        }
    }
}

/** A switch or checkbox: the on/off feel for the state it is changing to. */
@Composable
fun hapticToggle(onChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val view = LocalView.current
    val latest by rememberUpdatedState(onChange)
    return remember(view) {
        { on ->
            HapticUtil.performToggleHaptic(view, on)
            latest(on)
        }
    }
}
