/*
 * Feature Module: Data
 * File: SettingsRepository.kt
 * Description: App preferences backed by Preferences DataStore.
 */

package com.ssethhyy.journey.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssethhyy.journey.BuildConfig
import com.ssethhyy.journey.data.api.PtvCredentials
import com.ssethhyy.journey.data.crowding.CrowdingAlertLevel
import com.ssethhyy.journey.data.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** How live tracking appears on the Essentials island. */
enum class IslandStyle(val label: String) {
    /** Peeks the full line (route, countdown, stop) across the island at each milestone. */
    LINE("Line"),

    /** Stays in the compact pill: icon and countdown only. */
    COMPACT("Compact"),
}

enum class IslandDisruptionScope(val label: String) { FAVOURITES("My stops"), NETWORK("Whole network") }

/** Lowest disruption severity counted on the island. */
enum class IslandSeverity(val label: String) { ALL("All"), DELAYS("Delays+"), MAJOR("Major") }

/**
 * Where page navigation lives. BOTTOM_BAR keeps the floating navbar on screen everywhere: on a page it turns into
 * a back button, the page name and a related action button. TOP_PILL is the original look: a floating title pill at
 * the top of each page, with the navbar only on the three main tabs.
 */
enum class BarStyle(val label: String) { BOTTOM_BAR("Bottom bar"), TOP_PILL("Top pill") }

enum class IslandIconStyle(val label: String) { PICTOGRAM("PTV pictograms"), MONOCHROME("Monochrome") }

enum class TimeDisplay(val label: String) { RELATIVE("Minutes"), CLOCK("Clock time") }

data class AppSettings(
    val userName: String = "",
    val islandEnabled: Boolean = true,
    val islandTracking: Boolean = true,
    val islandStyle: IslandStyle = IslandStyle.LINE,
    val islandDisruptions: Boolean = true,
    val islandDisruptionScope: IslandDisruptionScope = IslandDisruptionScope.FAVOURITES,
    val islandMinSeverity: IslandSeverity = IslandSeverity.DELAYS,
    val islandIconStyle: IslandIconStyle = IslandIconStyle.PICTOGRAM,
    val islandPinned: Boolean = false,
    val islandAlerts: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pitchBlack: Boolean = false,
    val barStyle: BarStyle = BarStyle.BOTTOM_BAR,
    /** Optional Transport Victoria Open Data key for crowding; overrides the one built into the app. */
    val openDataKeyOverride: String = "",
    val crowdingAlerts: Boolean = true,
    val crowdingAlertLevel: CrowdingAlertLevel = CrowdingAlertLevel.BUSY,
    val hapticsEnabled: Boolean = true,
    val timeDisplay: TimeDisplay = TimeDisplay.RELATIVE,
    val departuresPerStop: Int = 3,
    val autoRefresh: Boolean = true,
    val nearbyRadiusMeters: Int = 1000,
    val nearbyModes: Set<TransportMode> = TransportMode.entries.toSet(),
    val mapModes: Set<TransportMode> = TransportMode.entries.toSet(),
    val mapFollowsTheme: Boolean = true,
    val map3dObjects: Boolean = true,
    val mapSatellite: Boolean = false,
    val ptvDevIdOverride: String = "",
    val ptvApiKeyOverride: String = "",
    val mapboxTokenOverride: String = "",
    val recentSearches: List<String> = emptyList(),
) {
    val ptvCredentials: PtvCredentials
        get() = if (ptvDevIdOverride.isNotBlank() && ptvApiKeyOverride.isNotBlank()) {
            PtvCredentials(ptvDevIdOverride.trim(), ptvApiKeyOverride.trim())
        } else {
            PtvCredentials(BuildConfig.PTV_DEV_ID, BuildConfig.PTV_API_KEY)
        }

    val usingCustomCredentials: Boolean get() = ptvDevIdOverride.isNotBlank() && ptvApiKeyOverride.isNotBlank()
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "journey_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val PITCH_BLACK = booleanPreferencesKey("pitch_black")
        val BAR_STYLE = stringPreferencesKey("bar_style")
        val OPEN_DATA_KEY = stringPreferencesKey("open_data_key")
        val CROWDING_ALERTS = booleanPreferencesKey("crowding_alerts")
        val CROWDING_LEVEL = stringPreferencesKey("crowding_level")
        val HAPTICS = booleanPreferencesKey("app_haptics_enabled")
        val TIME_DISPLAY = stringPreferencesKey("time_display")
        val DEPARTURES_PER_STOP = intPreferencesKey("departures_per_stop")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        val NEARBY_RADIUS = intPreferencesKey("nearby_radius")
        val NEARBY_MODES = stringSetPreferencesKey("nearby_modes")
        val MAP_MODES = stringSetPreferencesKey("map_modes")
        val MAP_FOLLOWS_THEME = booleanPreferencesKey("map_follows_theme")
        val MAP_3D = booleanPreferencesKey("map_3d")
        val MAP_SATELLITE = booleanPreferencesKey("map_satellite")
        val PTV_DEV_ID = stringPreferencesKey("ptv_dev_id")
        val PTV_API_KEY = stringPreferencesKey("ptv_api_key")
        val MAPBOX_TOKEN = stringPreferencesKey("mapbox_token")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val USER_NAME = stringPreferencesKey("user_name")
        val ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        val ISLAND_TRACKING = booleanPreferencesKey("island_tracking")
        val ISLAND_STYLE = stringPreferencesKey("island_style")
        val ISLAND_DISRUPTIONS = booleanPreferencesKey("island_disruptions")
        val ISLAND_SCOPE = stringPreferencesKey("island_scope")
        val ISLAND_SEVERITY = stringPreferencesKey("island_severity")
        val ISLAND_ICONS = stringPreferencesKey("island_icons")
        val ISLAND_PINNED = booleanPreferencesKey("island_pinned")
        val ISLAND_ALERTS = booleanPreferencesKey("island_alerts")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            pitchBlack = p[Keys.PITCH_BLACK] ?: false,
            barStyle = p[Keys.BAR_STYLE].enumOr(BarStyle.BOTTOM_BAR),
            openDataKeyOverride = p[Keys.OPEN_DATA_KEY].orEmpty(),
            crowdingAlerts = p[Keys.CROWDING_ALERTS] ?: true,
            crowdingAlertLevel = p[Keys.CROWDING_LEVEL].enumOr(CrowdingAlertLevel.BUSY),
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            timeDisplay = p[Keys.TIME_DISPLAY]?.let { runCatching { TimeDisplay.valueOf(it) }.getOrNull() } ?: TimeDisplay.RELATIVE,
            departuresPerStop = (p[Keys.DEPARTURES_PER_STOP] ?: 3).coerceIn(1, 6),
            autoRefresh = p[Keys.AUTO_REFRESH] ?: true,
            nearbyRadiusMeters = (p[Keys.NEARBY_RADIUS] ?: 1000).coerceIn(250, 3000),
            nearbyModes = p[Keys.NEARBY_MODES]?.toModes() ?: TransportMode.entries.toSet(),
            mapModes = p[Keys.MAP_MODES]?.toModes() ?: TransportMode.entries.toSet(),
            mapFollowsTheme = p[Keys.MAP_FOLLOWS_THEME] ?: true,
            map3dObjects = p[Keys.MAP_3D] ?: true,
            mapSatellite = p[Keys.MAP_SATELLITE] ?: false,
            ptvDevIdOverride = p[Keys.PTV_DEV_ID].orEmpty(),
            ptvApiKeyOverride = p[Keys.PTV_API_KEY].orEmpty(),
            mapboxTokenOverride = p[Keys.MAPBOX_TOKEN].orEmpty(),
            userName = p[Keys.USER_NAME].orEmpty(),
            islandEnabled = p[Keys.ISLAND_ENABLED] ?: true,
            islandTracking = p[Keys.ISLAND_TRACKING] ?: true,
            islandStyle = p[Keys.ISLAND_STYLE].enumOr(IslandStyle.LINE),
            islandDisruptions = p[Keys.ISLAND_DISRUPTIONS] ?: true,
            islandDisruptionScope = p[Keys.ISLAND_SCOPE].enumOr(IslandDisruptionScope.FAVOURITES),
            islandMinSeverity = p[Keys.ISLAND_SEVERITY].enumOr(IslandSeverity.DELAYS),
            islandIconStyle = p[Keys.ISLAND_ICONS].enumOr(IslandIconStyle.PICTOGRAM),
            islandPinned = p[Keys.ISLAND_PINNED] ?: false,
            islandAlerts = p[Keys.ISLAND_ALERTS] ?: true,
            recentSearches = p[Keys.RECENT_SEARCHES]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
        )
    }

    suspend fun setIslandEnabled(v: Boolean) = store.edit { it[Keys.ISLAND_ENABLED] = v }
    suspend fun setIslandTracking(v: Boolean) = store.edit { it[Keys.ISLAND_TRACKING] = v }
    suspend fun setIslandStyle(v: IslandStyle) = store.edit { it[Keys.ISLAND_STYLE] = v.name }
    suspend fun setIslandDisruptions(v: Boolean) = store.edit { it[Keys.ISLAND_DISRUPTIONS] = v }
    suspend fun setIslandScope(v: IslandDisruptionScope) = store.edit { it[Keys.ISLAND_SCOPE] = v.name }
    suspend fun setIslandSeverity(v: IslandSeverity) = store.edit { it[Keys.ISLAND_SEVERITY] = v.name }
    suspend fun setIslandIcons(v: IslandIconStyle) = store.edit { it[Keys.ISLAND_ICONS] = v.name }
    suspend fun setIslandPinned(v: Boolean) = store.edit { it[Keys.ISLAND_PINNED] = v }
    suspend fun setIslandAlerts(v: Boolean) = store.edit { it[Keys.ISLAND_ALERTS] = v }

    suspend fun setUserName(name: String) = store.edit { it[Keys.USER_NAME] = name.trim().take(30) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setPitchBlack(enabled: Boolean) = store.edit { it[Keys.PITCH_BLACK] = enabled }
    suspend fun setBarStyle(style: BarStyle) = store.edit { it[Keys.BAR_STYLE] = style.name }
    suspend fun setOpenDataKey(key: String) = store.edit { it[Keys.OPEN_DATA_KEY] = key.trim() }
    suspend fun setCrowdingAlerts(enabled: Boolean) = store.edit { it[Keys.CROWDING_ALERTS] = enabled }
    suspend fun setCrowdingAlertLevel(level: CrowdingAlertLevel) = store.edit { it[Keys.CROWDING_LEVEL] = level.name }
    suspend fun setHaptics(enabled: Boolean) = store.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setTimeDisplay(display: TimeDisplay) = store.edit { it[Keys.TIME_DISPLAY] = display.name }
    suspend fun setDeparturesPerStop(count: Int) = store.edit { it[Keys.DEPARTURES_PER_STOP] = count.coerceIn(1, 6) }
    suspend fun setAutoRefresh(enabled: Boolean) = store.edit { it[Keys.AUTO_REFRESH] = enabled }
    suspend fun setNearbyRadius(meters: Int) = store.edit { it[Keys.NEARBY_RADIUS] = meters.coerceIn(250, 3000) }
    suspend fun setNearbyModes(modes: Set<TransportMode>) = store.edit { it[Keys.NEARBY_MODES] = modes.map { m -> m.name }.toSet() }
    suspend fun setMapModes(modes: Set<TransportMode>) = store.edit { it[Keys.MAP_MODES] = modes.map { m -> m.name }.toSet() }
    suspend fun setMapFollowsTheme(enabled: Boolean) = store.edit { it[Keys.MAP_FOLLOWS_THEME] = enabled }
    suspend fun setMap3d(enabled: Boolean) = store.edit { it[Keys.MAP_3D] = enabled }
    suspend fun setMapSatellite(enabled: Boolean) = store.edit { it[Keys.MAP_SATELLITE] = enabled }

    suspend fun setPtvCredentials(devId: String, apiKey: String) = store.edit {
        it[Keys.PTV_DEV_ID] = devId.trim()
        it[Keys.PTV_API_KEY] = apiKey.trim()
    }

    suspend fun setMapboxToken(token: String) = store.edit { it[Keys.MAPBOX_TOKEN] = token.trim() }

    suspend fun addRecentSearch(term: String) = store.edit { p ->
        val clean = term.trim().replace(RECENT_SEPARATOR, " ")
        if (clean.isBlank()) return@edit
        val existing = p[Keys.RECENT_SEARCHES]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        p[Keys.RECENT_SEARCHES] = (listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) })
            .take(8)
            .joinToString(RECENT_SEPARATOR)
    }

    suspend fun clearRecentSearches() = store.edit { it.remove(Keys.RECENT_SEARCHES) }

    private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
        this?.let { name -> runCatching { enumValueOf<E>(name) }.getOrNull() } ?: default

    private fun Set<String>.toModes(): Set<TransportMode> = mapNotNull { runCatching { TransportMode.valueOf(it) }.getOrNull() }.toSet()

    private companion object {
        const val RECENT_SEPARATOR = "\u001F"
    }
}
