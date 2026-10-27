/*
 * Feature Module: Settings
 * File: SettingsScreen.kt
 * Description: Appearance, departures, nearby, map, haptics, API access, data and about.
 */

package com.ssethhyy.journey.ui.screens.settings

import com.ssethhyy.journey.ui.components.Sym
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.BuildConfig
import com.ssethhyy.journey.data.api.PtvApi
import com.ssethhyy.journey.data.api.PtvCredentials
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.ThemeMode
import com.ssethhyy.journey.data.repository.TimeDisplay
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ActionItem
import com.ssethhyy.journey.ui.components.ConfigPickerItem
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.IconToggleItem
import com.ssethhyy.journey.ui.components.ModeFilterRow
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.RoundedCardContainer
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.SliderItem
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data object Success : ConnectionTest
    data class Failed(val message: String) : ConnectionTest
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settingsRepository
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val favouriteCount = container.favouritesRepository.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _connection = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val connection: StateFlow<ConnectionTest> = _connection.asStateFlow()

    fun update(block: suspend com.ssethhyy.journey.data.repository.SettingsRepository.() -> Unit) = viewModelScope.launch { repo.block() }

    fun saveCredentials(devId: String, apiKey: String) = viewModelScope.launch {
        repo.setPtvCredentials(devId, apiKey)
        container.ptvRepository.clearCaches()
        _connection.value = ConnectionTest.Idle
    }

    fun testConnection(devId: String, apiKey: String) = viewModelScope.launch {
        _connection.value = ConnectionTest.Running
        val creds = if (devId.isNotBlank() && apiKey.isNotBlank()) PtvCredentials(devId.trim(), apiKey.trim()) else settings.value.ptvCredentials
        _connection.value = runCatching {
            PtvApi(credentialsProvider = { creds }).search("Flinders Street", listOf(TransportMode.TRAIN.routeType))
        }.fold(
            onSuccess = { ConnectionTest.Success },
            onFailure = { ConnectionTest.Failed(it.message ?: "Connection failed") },
        )
    }

    suspend fun exportFavourites(): String = container.favouritesRepository.exportJson()
    suspend fun importFavourites(raw: String): Int = container.favouritesRepository.importJson(raw)
    fun clearFavourites() = viewModelScope.launch { container.favouritesRepository.clear() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favourites by vm.favouriteCount.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = vm.exportFavourites()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.isSuccess
            Toast.makeText(context, if (ok) "Favourites exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val added = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                vm.importFavourites(raw)
            }.getOrNull()
            Toast.makeText(context, added?.let { "Imported $it favourite${if (it == 1) "" else "s"}" } ?: "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { ReusableTopAppBar(title = "Settings", onBackClick = navigator::back) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
        ) {
            item(key = "personal") {
                SectionLabel("You")
                RoundedCardContainer {
                    NameField(current = settings.userName, onSave = { name -> vm.update { setUserName(name) } })
                }
            }

            item(key = "fares") {
                SectionLabel("myki")
                RoundedCardContainer {
                    ActionItem(
                        icon = Sym.ReceiptLong,
                        title = "Fares and discounts",
                        description = "Prices, half-price and free travel, used when you log trips",
                        onClick = navigator::openFares,
                    )
                }
            }

            item(key = "island") {
                SectionLabel("Essentials island")
                RoundedCardContainer {
                    ActionItem(
                        icon = Sym.Widgets,
                        title = "Island",
                        description = "Live tracking and disruption alerts on the Essentials island",
                        onClick = navigator::openIslandSettings,
                    )
                }
            }

            item(key = "appearance") {
                SectionLabel("Appearance")
                RoundedCardContainer {
                    ConfigPickerItem(
                        icon = Sym.Brightness4,
                        title = "Theme",
                        items = ThemeMode.entries,
                        selectedItem = settings.themeMode,
                        onItemSelected = { vm.update { setThemeMode(it) } },
                        labelProvider = { it.label },
                    )
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    IconToggleItem(
                        icon = Sym.Palette,
                        title = "Dynamic colour",
                        description = if (dynamicSupported) "Match colours to your wallpaper" else "Requires Android 12 or newer",
                        isChecked = settings.dynamicColor && dynamicSupported,
                        enabled = dynamicSupported,
                        onDisabledClick = { Toast.makeText(context, "Dynamic colour needs Android 12+", Toast.LENGTH_SHORT).show() },
                        onCheckedChange = { vm.update { setDynamicColor(it) } },
                    )
                    IconToggleItem(
                        icon = Sym.Contrast,
                        title = "Pitch black",
                        description = "Pure black backgrounds in dark theme for OLED screens",
                        isChecked = settings.pitchBlack,
                        onCheckedChange = { vm.update { setPitchBlack(it) } },
                    )
                }
            }

            item(key = "departures") {
                SectionLabel("Departures")
                RoundedCardContainer {
                    ConfigPickerItem(
                        icon = Sym.Schedule,
                        title = "Show times as",
                        items = TimeDisplay.entries,
                        selectedItem = settings.timeDisplay,
                        onItemSelected = { vm.update { setTimeDisplay(it) } },
                        labelProvider = { it.label },
                    )
                    SliderItem(
                        icon = Sym.FormatListNumbered,
                        title = "Departures per stop",
                        value = settings.departuresPerStop,
                        range = 1..6,
                        step = 1,
                        valueLabel = { "$it" },
                        onValueChangeFinished = { vm.update { setDeparturesPerStop(it) } },
                    )
                    IconToggleItem(
                        icon = Sym.Refresh,
                        title = "Auto refresh",
                        description = "Update live times every 30 seconds while open",
                        isChecked = settings.autoRefresh,
                        onCheckedChange = { vm.update { setAutoRefresh(it) } },
                    )
                }
            }

            item(key = "nearby") {
                SectionLabel("Nearby stops on the map")
                RoundedCardContainer {
                    SliderItem(
                        icon = Sym.NearMe,
                        title = "Search radius",
                        value = settings.nearbyRadiusMeters,
                        range = 250..3000,
                        step = 250,
                        valueLabel = { DistanceFormatter.distance(it.toDouble()) },
                        onValueChangeFinished = { vm.update { setNearbyRadius(it) } },
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceBright)
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            "Transport modes",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        ModeFilterRow(
                            selected = settings.nearbyModes,
                            onToggle = { mode ->
                                val next = if (mode in settings.nearbyModes) settings.nearbyModes - mode else settings.nearbyModes + mode
                                vm.update { setNearbyModes(next.ifEmpty { TransportMode.entries.toSet() }) }
                            },
                        )
                    }
                }
            }

            item(key = "map") {
                SectionLabel("Map")
                RoundedCardContainer {
                    IconToggleItem(
                        icon = Sym.Map,
                        title = "Night map in dark theme",
                        description = "Switch the map lighting with the app theme",
                        isChecked = settings.mapFollowsTheme,
                        onCheckedChange = { vm.update { setMapFollowsTheme(it) } },
                    )
                    IconToggleItem(
                        icon = Sym.ViewInAr,
                        title = "3D buildings & landmarks",
                        isChecked = settings.map3dObjects,
                        onCheckedChange = { vm.update { setMap3d(it) } },
                    )
                }
            }

            item(key = "interaction") {
                SectionLabel("Interaction")
                RoundedCardContainer {
                    IconToggleItem(
                        icon = Sym.Vibration,
                        title = "Haptic feedback",
                        description = "Feel taps, toggles and page changes",
                        isChecked = settings.hapticsEnabled,
                        onCheckedChange = { enabled ->
                            HapticUtil.isAppHapticsEnabled.value = enabled
                            vm.update { setHaptics(enabled) }
                        },
                    )
                    ActionItem(
                        icon = Sym.Widgets,
                        title = "Add home screen widget",
                        description = "Live departures from your favourites",
                        onClick = {
                            val manager = android.appwidget.AppWidgetManager.getInstance(context)
                            val provider = android.content.ComponentName(context, com.ssethhyy.journey.widget.JourneyWidgetReceiver::class.java)
                            if (manager.isRequestPinAppWidgetSupported) {
                                manager.requestPinAppWidget(provider, null, null)
                            } else {
                                Toast.makeText(context, "Long-press your home screen, tap Widgets, then find Journey", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    ActionItem(
                        icon = Sym.History,
                        title = "Clear recent searches",
                        showChevron = false,
                        onClick = {
                            vm.update { clearRecentSearches() }
                            Toast.makeText(context, "Recent searches cleared", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            item(key = "api") {
                SectionLabel("API access")
                ApiAccessCard(
                    settings = settings,
                    connection = connection,
                    onSave = vm::saveCredentials,
                    onTest = vm::testConnection,
                    onSaveMapbox = { token -> vm.update { setMapboxToken(token) } },
                )
            }

            item(key = "data") {
                SectionLabel("Data")
                RoundedCardContainer {
                    ActionItem(
                        icon = Sym.Upload,
                        title = "Export favourites",
                        description = "Save ${favourites.size} favourite${if (favourites.size == 1) "" else "s"} to a JSON file",
                        onClick = { exportLauncher.launch("journey-favourites.json") },
                    )
                    ActionItem(
                        icon = Sym.Download,
                        title = "Import favourites",
                        description = "Merge favourites from a backup file",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    )
                    ActionItem(
                        icon = Sym.DeleteSweep,
                        title = "Clear all favourites",
                        tint = MaterialTheme.colorScheme.error,
                        showChevron = false,
                        onClick = { confirmClear = true },
                    )
                }
            }

            item(key = "about") {
                SectionLabel("About")
                AboutSection()
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Sym.DeleteSweep, null) },
            title = { Text("Clear all favourites?") },
            text = { Text("This removes all ${favourites.size} saved stops. Export them first if you might want them back.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearFavourites()
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApiAccessCard(
    settings: AppSettings,
    connection: ConnectionTest,
    onSave: (String, String) -> Unit,
    onTest: (String, String) -> Unit,
    onSaveMapbox: (String) -> Unit,
) {
    val view = LocalView.current
    var devId by rememberSaveable(settings.ptvDevIdOverride) { mutableStateOf(settings.ptvDevIdOverride) }
    var apiKey by rememberSaveable(settings.ptvApiKeyOverride) { mutableStateOf(settings.ptvApiKeyOverride) }
    var mapbox by rememberSaveable(settings.mapboxTokenOverride) { mutableStateOf(settings.mapboxTokenOverride) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    val builtIn = BuildConfig.PTV_DEV_ID.isNotBlank() && BuildConfig.PTV_API_KEY.isNotBlank()

    RoundedCardContainer {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceBright)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Key, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PTV Timetable API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            settings.usingCustomCredentials -> "Using your saved developer ID"
                            builtIn -> "Using credentials built into this app"
                            else -> "Not configured — live data unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = devId,
                onValueChange = { devId = it.trim() },
                label = { Text("Developer ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    HapticIconButton(
                        if (showKey) Sym.VisibilityOff else Sym.Visibility,
                        if (showKey) "Hide key" else "Show key",
                        onClick = { showKey = !showKey },
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    HapticUtil.performConfirmHaptic(view)
                    onSave(devId, apiKey)
                }) { Text("Save") }
                FilledTonalButton(onClick = { onTest(devId, apiKey) }, enabled = connection != ConnectionTest.Running) { Text("Test connection") }
                when (connection) {
                    ConnectionTest.Running -> LoadingIndicator(Modifier.size(32.dp))
                    ConnectionTest.Success -> Text("Connected ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    is ConnectionTest.Failed -> Unit
                    ConnectionTest.Idle -> Unit
                }
            }
            if (connection is ConnectionTest.Failed) {
                Text(connection.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Request a free key from PTV (Timetable API) — your developer ID and key are stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceBright)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Map, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mapbox", style = MaterialTheme.typography.titleMedium)
                    Text("Public token (pk.…) for map tiles. Restart the app after changing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = mapbox,
                onValueChange = { mapbox = it.trim() },
                label = { Text("Access token") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                isError = mapbox.isNotEmpty() && !mapbox.startsWith("pk."),
                supportingText = if (mapbox.isNotEmpty() && !mapbox.startsWith("pk.")) {
                    { Text("Use a public token starting with pk.") }
                } else {
                    null
                },
            )
            FilledTonalButton(onClick = {
                HapticUtil.performConfirmHaptic(view)
                onSaveMapbox(mapbox)
            }) { Text("Save token") }
        }
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    RoundedCardContainer {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceBright)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                TransportMode.entries.forEach { ModePictogram(it, size = 40.dp) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Journey", style = MaterialTheme.typography.headlineSmall)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "Live Victorian public transport, designed with Material 3 Expressive. Not affiliated with Public Transport Victoria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        ActionItem(
            icon = Sym.Info,
            title = "Data sources",
            description = "Timetable data licensed from Public Transport Victoria under CC BY 4.0. Journey planning by Transitous (MOTIS) using Transport Victoria open data. Maps © Mapbox © OpenStreetMap.",
            onClick = { runCatching { uriHandler.openUri("https://www.ptv.vic.gov.au/footer/data-and-reporting/datasets/ptv-timetable-api/") } },
        )
    }
}

@Composable
private fun NameField(current: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf(current) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.ssethhyy.journey.ui.components.LeadingIcon(Sym.Person)
        Spacer(Modifier.width(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(30) },
            label = { Text("Your name") },
            supportingText = { Text("Used in your For You greeting") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                onSave(text)
                focus.clearFocus()
            }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused && text != current) onSave(text) },
        )
    }
}
