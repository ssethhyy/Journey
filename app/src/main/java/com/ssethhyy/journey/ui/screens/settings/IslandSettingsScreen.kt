/*
 * Feature Module: Settings
 * File: IslandSettingsScreen.kt
 * Description: Settings for Journey on the Essentials island: connection status, what to show,
 * how it looks, and test tools (preview and peek).
 */

package com.ssethhyy.journey.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameerasw.island.sdk.HostGrantStore
import com.sameerasw.island.sdk.IslandSchema
import com.ssethhyy.journey.data.repository.IslandDisruptionScope
import com.ssethhyy.journey.data.repository.IslandIconStyle
import com.ssethhyy.journey.data.repository.IslandSeverity
import com.ssethhyy.journey.data.repository.IslandStyle
import com.ssethhyy.journey.island.IslandConnection
import com.ssethhyy.journey.island.JourneyIsland
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ConfigPickerItem
import com.ssethhyy.journey.ui.components.IconToggleItem
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.rememberNow
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.delay

private const val HOST = IslandSchema.DEFAULT_HOST_PACKAGE

private fun isInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getPackageInfo(HOST, 0)
    true
}.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by IslandConnection.state.collectAsStateWithLifecycle()
    val preview by IslandConnection.previewUntil.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val context = LocalContext.current
    val now by rememberNow(5_000)

    // Re-check install/approval every few seconds while open, so returning from Essentials updates it.
    val status by produceState(initialValue = Triple(false, false, null as String?), now) {
        val installed = isInstalled(context)
        val approved = installed && runCatching { HostGrantStore(context).isAuthorized(HOST) }.getOrDefault(false)
        val version = if (installed) runCatching { context.packageManager.getPackageInfo(HOST, 0).versionName }.getOrNull() else null
        value = Triple(installed, approved, version)
        delay(0)
    }
    val (installed, approved, version) = status

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "Island",
                subtitle = "Journey on the Essentials island",
                onBackClick = navigator::back,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
        ) {
            item(key = "status") {
                ConnectionCard(
                    installed = installed,
                    approved = approved,
                    attached = connection.attached,
                    version = version,
                    lastPublish = connection.lastPublishAt?.let { TimeFormatter.clock(it) },
                    items = connection.itemsShown,
                    onOpenEssentials = {
                        val launch = context.packageManager.getLaunchIntentForPackage(HOST)
                        if (launch != null) {
                            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            Toast.makeText(context, "Essentials isn't installed", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            item(key = "show") {
                SectionLabel("Show on the island")
                RoundedCardContainer {
                    IconToggleItem(
                        icon = Sym.Widgets,
                        title = "Journey on the island",
                        description = "Master switch for everything below",
                        isChecked = settings.islandEnabled,
                        onCheckedChange = { vm.update { setIslandEnabled(it) } },
                    )
                    IconToggleItem(
                        icon = Sym.NotificationsActive,
                        title = "Live tracking",
                        description = "Countdown for the service or journey you're tracking",
                        isChecked = settings.islandTracking,
                        enabled = settings.islandEnabled,
                        onCheckedChange = { vm.update { setIslandTracking(it) } },
                    )
                    IconToggleItem(
                        icon = Sym.Warning,
                        title = "Service disruptions",
                        description = "Alert icon coloured by severity, with the number of disruptions",
                        isChecked = settings.islandDisruptions,
                        enabled = settings.islandEnabled,
                        onCheckedChange = { vm.update { setIslandDisruptions(it) } },
                    )
                }
            }

            item(key = "tracking") {
                SectionLabel("Tracking")
                RoundedCardContainer {
                    ConfigPickerItem(
                        icon = Sym.Schedule,
                        title = "Style",
                        description = when (settings.islandStyle) {
                            IslandStyle.LINE -> "Peeks across the island with the route, countdown and stop at 5 min, 2 min and departure"
                            IslandStyle.COMPACT -> "Stays in the pill as an icon and the time. Tap to expand"
                        },
                        items = IslandStyle.entries,
                        selectedItem = settings.islandStyle,
                        onItemSelected = { vm.update { setIslandStyle(it) } },
                        labelProvider = { it.label },
                    )
                    ConfigPickerItem(
                        icon = Sym.Train,
                        title = "Icon",
                        items = IslandIconStyle.entries,
                        selectedItem = settings.islandIconStyle,
                        onItemSelected = { vm.update { setIslandIcons(it) } },
                        labelProvider = { it.label },
                    )
                    IconToggleItem(
                        icon = Sym.MyLocation,
                        title = "Keep beside the camera",
                        description = "Pin the tracking pill instead of letting the island reorder it",
                        isChecked = settings.islandPinned,
                        onCheckedChange = { vm.update { setIslandPinned(it) } },
                    )
                }
            }

            item(key = "disruptions") {
                SectionLabel("Disruptions")
                RoundedCardContainer {
                    ConfigPickerItem(
                        icon = Sym.Route,
                        title = "Which disruptions",
                        items = IslandDisruptionScope.entries,
                        selectedItem = settings.islandDisruptionScope,
                        onItemSelected = { vm.update { setIslandScope(it) } },
                        labelProvider = { it.label },
                    )
                    ConfigPickerItem(
                        icon = Sym.Error,
                        title = "Minimum severity",
                        description = when (settings.islandMinSeverity) {
                            IslandSeverity.ALL -> "Everything, including planned works and service information"
                            IslandSeverity.DELAYS -> "Delays, suspensions and closures"
                            IslandSeverity.MAJOR -> "Only major delays, suspensions and closures"
                        },
                        items = IslandSeverity.entries,
                        selectedItem = settings.islandMinSeverity,
                        onItemSelected = { vm.update { setIslandSeverity(it) } },
                        labelProvider = { it.label },
                    )
                    IconToggleItem(
                        icon = Sym.NotificationsActive,
                        title = "Alert for major disruptions",
                        description = "Peek and move to the front when something serious starts",
                        isChecked = settings.islandAlerts,
                        onCheckedChange = { vm.update { setIslandAlerts(it) } },
                    )
                    SeverityLegend()
                }
            }

            item(key = "test") {
                SectionLabel("Try it")
                RoundedCardContainer {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceBright)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Preview shows a demo Hurstbridge line countdown on the island for 90 seconds, sped up so it passes the 5 and 2 minute marks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (preview == null) {
                                Button(onClick = { IslandConnection.startPreview() }, enabled = connection.attached) { Text("Start preview") }
                            } else {
                                Button(onClick = { IslandConnection.stopPreview() }) { Text("Stop preview") }
                            }
                            FilledTonalButton(
                                onClick = {
                                    val key = connection.itemsShown.firstOrNull() ?: JourneyIsland.ITEM_TRACKING
                                    IslandConnection.requestPeek(key)
                                },
                                enabled = connection.attached && connection.itemsShown.isNotEmpty(),
                            ) { Text("Peek now") }
                        }
                        Text(
                            "Essentials allows one peek every 10 seconds and opens an expanded view at most every 30 seconds, so Journey only peeks at meaningful moments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    installed: Boolean,
    approved: Boolean,
    attached: Boolean,
    version: String?,
    lastPublish: String?,
    items: List<String>,
    onOpenEssentials: () -> Unit,
) {
    val (title, body, color) = when {
        !installed -> Triple("Essentials not installed", "Install Essentials by sameerasw to use the island.", MaterialTheme.colorScheme.errorContainer)
        !approved -> Triple(
            "Approve Journey in Essentials",
            "Open Essentials > Island > Island apps and turn on Journey. Essentials shows an approval sheet with Journey's signing key.",
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        !attached -> Triple("Approved, waiting for the island", "Journey connects whenever the Essentials island is running.", MaterialTheme.colorScheme.secondaryContainer)
        else -> Triple(
            "Connected to the island",
            buildString {
                append(if (items.isEmpty()) "Nothing to show right now" else "Showing " + items.joinToString(" and ") { if (it == JourneyIsland.ITEM_TRACKING) "live tracking" else "disruptions" })
                lastPublish?.let { append(" · updated $it") }
            },
            MaterialTheme.colorScheme.primaryContainer,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(color, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(if (attached) Color(0xFF3DBE6A) else if (approved) Color(0xFFFFC23D) else Color(0xFFB8BCC4), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (version != null) {
            Text("Essentials $version", style = MaterialTheme.typography.labelMedium)
        }
        if (installed) {
            FilledTonalButton(onClick = onOpenEssentials) { Text("Open Essentials") }
        }
    }
}

@Composable
private fun SeverityLegend() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Alert icon colours", style = MaterialTheme.typography.titleSmall)
        com.ssethhyy.journey.data.model.DisruptionSeverity.entries.forEach { severity ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Sym.Warning, null, tint = Color(JourneyIsland.severityColor(severity)), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(JourneyIsland.severityLabel(severity), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
