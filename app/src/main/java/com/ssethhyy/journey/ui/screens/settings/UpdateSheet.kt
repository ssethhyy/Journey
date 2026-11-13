/*
 * Feature Module: Settings
 * File: UpdateSheet.kt
 * Description: The card that pops up for an available update: the version, the release notes from GitHub rendered
 * from Markdown, and a button at the bottom that downloads the update inside the app and opens Android's install
 * prompt. Also the Updates section of Settings.
 */

package com.ssethhyy.journey.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.BuildConfig
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.update.AvailableUpdate
import com.ssethhyy.journey.data.update.InstallState
import com.ssethhyy.journey.ui.components.MarkdownText
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.hapticConfirm
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shown above every screen while an update's details are open. */
@Composable
fun UpdateSheetHost() {
    val manager = LocalContext.current.appContainer.updateManager
    val update by manager.prompt.collectAsStateWithLifecycle()
    update?.let { UpdateSheet(it, onDismiss = manager::dismissPrompt) }
}

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheet(update: AvailableUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = context.appContainer.updateManager
    val install by manager.install.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    StandardBottomSheet(
        onDismissRequest = onDismiss,
        title = "Update available",
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Download, null, Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    // Release titles can be long ("Release v1.2.0: Full-screen arrival alerts, …"), so keep them compact.
                    Text(
                        update.title,
                        style = if (update.title.length > 28) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            "You have ${BuildConfig.VERSION_NAME}",
                            update.publishedAt?.let { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(it) },
                            update.apkSize.takeIf { it > 0 }?.let(::megabytes),
                            if (update.prerelease) "Pre-release" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                // Shorter while a download or a permission note is showing, so the buttons below always fit.
                .heightIn(max = if (install is InstallState.Idle) 340.dp else 180.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (update.notes.isBlank()) {
                Text("No release notes for this version.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MarkdownText(update.notes)
            }
        }
        when (val state = install) {
            is InstallState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…", style = MaterialTheme.typography.labelLarge)
                if (state.progress != null) {
                    LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            InstallState.Installing -> Text("Confirm the update in Android's prompt to finish.", style = MaterialTheme.typography.bodyMedium)
            InstallState.NeedsPermission -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Allow Journey to install updates", style = MaterialTheme.typography.titleSmallEmphasized)
                    Text("Android asks once before an app can install its own updates. Turn on Allow from this source, then come back and tap Install update again.", style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = { runCatching { context.startActivity(manager.installPermissionIntent()) } },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    ) { Text("Open settings") }
                }
            }
            is InstallState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            InstallState.Idle -> Unit
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Later") }
            Spacer(Modifier.weight(1f))
            val busy = install is InstallState.Downloading || install is InstallState.Installing
            Button(
                enabled = !busy,
                onClick = hapticConfirm { scope.launch { manager.downloadAndInstall(update) } },
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Sym.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (install is InstallState.Failed) "Try again" else "Install update", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/** Runs a manual check: opens the update card, or says Journey is up to date. */
fun checkForUpdatesNow(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, includePrereleases: Boolean, onDone: () -> Unit = {}) {
    val manager = context.appContainer.updateManager
    scope.launch {
        val result = runCatching { manager.check(includePrereleases) }
        onDone()
        result.onSuccess { update ->
            if (update != null) {
                manager.showPrompt(update)
            } else {
                Toast.makeText(context, "Journey ${BuildConfig.VERSION_NAME} is the latest version", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, it.message ?: "Couldn't check for updates", Toast.LENGTH_SHORT).show()
        }
    }
}
