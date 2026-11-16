/*
 * Feature Module: Settings
 * File: SettingsScreen.kt
 * Description: The Settings page. It lists every SettingsCategory in order and lets SettingsRenderer draw each one
 * from SettingsCatalog; the page itself only owns what needs it (file pickers, the clear dialog, update checks).
 */

package com.ssethhyy.journey.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.api.PtvApi
import com.ssethhyy.journey.data.api.PtvCredentials
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.SettingsCategory
import com.ssethhyy.journey.data.repository.SettingsRepository
import com.ssethhyy.journey.data.repository.SettingsSnapshot
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.floatingBarBlur
import com.ssethhyy.journey.ui.components.pageBottomInset
import com.ssethhyy.journey.ui.navigation.currentNavigator
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
    val values: StateFlow<SettingsSnapshot> = repo.snapshots.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsSnapshot.Defaults)
    val favouriteCount = container.favouritesRepository.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _connection = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val connection: StateFlow<ConnectionTest> = _connection.asStateFlow()

    fun update(block: suspend SettingsRepository.() -> Unit) = viewModelScope.launch { repo.block() }

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
    val values by vm.values.collectAsStateWithLifecycle()
    val favourites by vm.favouriteCount.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var checkingForUpdates by remember { mutableStateOf(false) }

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

    // The jobs catalog items hand back to the page.
    val run: (SettingsJob) -> Unit = { job ->
        when (job) {
            SettingsJob.EXPORT_FAVOURITES -> exportLauncher.launch("journey-favourites.json")
            SettingsJob.IMPORT_FAVOURITES -> importLauncher.launch(arrayOf("application/json", "text/plain"))
            SettingsJob.CLEAR_FAVOURITES -> confirmClear = true
            SettingsJob.CHECK_UPDATES -> {
                checkingForUpdates = true
                checkForUpdatesNow(context, scope, settings.updatePrereleases) { checkingForUpdates = false }
            }
            SettingsJob.CLEAR_RECENT_SEARCHES -> {
                vm.update { clearRecentSearches() }
                Toast.makeText(context, "Recent searches cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val settingsScope = SettingsScope(
        settings = settings,
        values = values,
        context = context,
        navigator = navigator,
        vm = vm,
        run = run,
        favouriteCount = favourites.size,
        checkingForUpdates = checkingForUpdates,
    )

    Scaffold(
        topBar = { ReusableTopAppBar(title = "Settings", onBackClick = navigator::back) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = pageBottomInset(32.dp)),
        ) {
            // In the order SettingsCategory lists them.
            items(SettingsCategory.entries, key = { it.name }) { category ->
                SettingsCategorySection(category, settingsScope)
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
