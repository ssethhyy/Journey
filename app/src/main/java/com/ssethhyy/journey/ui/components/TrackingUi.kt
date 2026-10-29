/*
 * Feature Module: UI Components
 * File: TrackingUi.kt
 * Description: Track/Stop button and the floating banner shown while something is being tracked.
 */

package com.ssethhyy.journey.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.tracking.TrackingTarget
import com.ssethhyy.journey.utils.HapticUtil

/**
 * Extended FAB that starts or stops tracking [target]. Asks for notification permission first
 * (Android 13+) so the live countdown can be shown.
 */
@Composable
fun TrackButton(target: TrackingTarget, modifier: Modifier = Modifier, label: String = "Track") {
    val manager = LocalContext.current.appContainer.trackingManager
    val view = LocalView.current
    val current by manager.target.collectAsStateWithLifecycle()
    val tracking = current?.id == target.id
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Track regardless; without permission the in-app banner still shows progress.
        manager.start(target)
    }
    ExtendedFloatingActionButton(
        onClick = {
            if (tracking) {
                HapticUtil.performUIHaptic(view)
                manager.stop()
            } else {
                HapticUtil.performConfirmHaptic(view)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !manager.canPostNotifications()) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    manager.start(target)
                }
            }
        },
        modifier = modifier,
        icon = { Icon(if (tracking) Sym.NotificationsOff else Sym.NotificationsActive, null) },
        text = { Text(if (tracking) "Stop tracking" else label) },
        containerColor = if (tracking) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (tracking) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/**
 * The same track / stop-tracking action as [TrackButton], as a toolbar button for the bottom-bar style.
 * [label] names the action while nothing is being tracked.
 */
@Composable
fun rememberTrackFab(target: TrackingTarget, label: String = "Track"): ToolbarFab {
    val manager = LocalContext.current.appContainer.trackingManager
    val view = LocalView.current
    val current by manager.target.collectAsStateWithLifecycle()
    val tracking = current?.id == target.id
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Track regardless; without permission the in-app banner still shows progress.
        manager.start(target)
    }
    return ToolbarFab(
        icon = if (tracking) Sym.NotificationsOff else Sym.NotificationsActive,
        contentDescription = if (tracking) "Stop tracking" else label,
        onClick = {
            if (tracking) {
                HapticUtil.performUIHaptic(view)
                manager.stop()
            } else {
                HapticUtil.performConfirmHaptic(view)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !manager.canPostNotifications()) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    manager.start(target)
                }
            }
        },
    )
}

/** Compact live banner while tracking, shown above the floating toolbar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackingBanner(onOpen: (TrackingTarget) -> Unit, modifier: Modifier = Modifier) {
    val manager = LocalContext.current.appContainer.trackingManager
    val view = LocalView.current
    val target by manager.target.collectAsStateWithLifecycle()
    val status by manager.status.collectAsStateWithLifecycle()
    val t = target ?: return
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onOpen(t)
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (status?.isLive == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.inversePrimary, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(targetState = status?.title ?: "Starting live tracking…", label = "tracking_title") {
                        Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        status?.detail ?: when (t) {
                            is TrackingTarget.Service -> t.stopName
                            is TrackingTarget.Trip -> "To ${t.toName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HapticIconButton(Sym.Close, "Stop tracking", onClick = manager::stop, tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
            status?.let {
                LinearWavyProgressIndicator(
                    progress = { it.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                )
            }
        }
    }
}
