/*
 * Feature Module: Data
 * File: SettingsRepository.kt
 * Description: Every app preference, defined once and stored in Preferences DataStore.
 *
 * How settings work
 * -----------------
 * 1. Each setting is one typed definition in [Prefs]: its DataStore key, its default and (for numbers) its range.
 *    Read it anywhere with `settingsRepository.flow(Prefs.X)` / `get(Prefs.X)` and change it with `set(Prefs.X, v)`.
 * 2. [SettingsCategory] lists the sections of the Settings page. Their order here is the order on screen: move a line
 *    to move a section.
 * 3. To show a setting on the Settings page, add one item for it to `SettingsCatalog` (ui/screens/settings), in its
 *    category: a Toggle, Choice, Slider, TextInput, Modes, Action, Info or Custom row. The page draws it for you.
 *
 * [AppSettings] is the snapshot most screens read (`settings.autoRefresh`); add a field there only when other code
 * needs the value as part of that snapshot.
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
import com.ssethhyy.journey.data.foryou.DisruptionLevel
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.SearchRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** The small line under each stop on the home screen widget. */
enum class WidgetSubtitle(val label: String) { DISTANCE("Distance"), SUBURB("Suburb"), NONE("Nothing") }

// ---------------------------------------------------------------------------------------------------------------
// Setting definitions
// ---------------------------------------------------------------------------------------------------------------

/** One stored setting: where it lives in DataStore and what it is when nothing has been saved. */
sealed class Pref<T>(val name: String, val default: T) {
    internal abstract fun read(p: Preferences): T
    internal abstract fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: T)
}

/** On or off. */
class BoolPref(name: String, default: Boolean) : Pref<Boolean>(name, default) {
    private val key = booleanPreferencesKey(name)
    override fun read(p: Preferences) = p[key] ?: default
    override fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: Boolean) { p[key] = value }
}

/** A whole number, always kept within [range]. */
class IntPref(name: String, default: Int, val range: IntRange) : Pref<Int>(name, default) {
    private val key = intPreferencesKey(name)
    override fun read(p: Preferences) = (p[key] ?: default).coerceIn(range)
    override fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: Int) { p[key] = value.coerceIn(range) }
}

/** Text, tidied by [clean] (trimmed by default) before it's saved. */
class StringPref(name: String, default: String = "", val clean: (String) -> String = { it.trim() }) : Pref<String>(name, default) {
    private val key = stringPreferencesKey(name)
    override fun read(p: Preferences) = p[key] ?: default
    override fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: String) { p[key] = clean(value) }
}

/** One of a fixed set of options, stored by name (an unknown name reads as the default). */
class EnumPref<E : Enum<E>>(name: String, default: E, val options: List<E>) : Pref<E>(name, default) {
    private val key = stringPreferencesKey(name)
    override fun read(p: Preferences) = p[key]?.let { stored -> options.firstOrNull { it.name == stored } } ?: default
    override fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: E) { p[key] = value.name }
}

/** A set of transport modes; never empty (clearing them all means all of them). */
class ModesPref(name: String) : Pref<Set<TransportMode>>(name, TransportMode.entries.toSet()) {
    private val key = stringSetPreferencesKey(name)
    override fun read(p: Preferences) =
        p[key]?.mapNotNull { stored -> TransportMode.entries.firstOrNull { it.name == stored } }?.toSet()?.ifEmpty { null } ?: default
    override fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: Set<TransportMode>) {
        p[key] = value.ifEmpty { default }.map { it.name }.toSet()
    }
}

/** An ordered list of short strings (keys, search terms), at most [max] long. */
class StringListPref(name: String, val max: Int = 100) : Pref<List<String>>(name, emptyList()) {
    private val key = stringPreferencesKey(name)
    override fun read(p: Preferences) = p[key]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    override fun write(p: androidx.datastore.preferences.core.MutablePreferences, value: List<String>) {
        p[key] = value.map { it.replace(SEPARATOR, " ").trim() }.filter { it.isNotBlank() }.take(max).joinToString(SEPARATOR)
    }

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}

/**
 * The sections of the Settings page, in the order they appear. Reorder the entries to reorder the page; a section
 * with nothing to show is left out.
 */
enum class SettingsCategory(val title: String) {
    FOR_YOU("For You"),
    APPEARANCE("Appearance"),
    DEPARTURES("Departures"),
    SERVICE_UPDATES("Service updates on stops and lines"),
    NEARBY("Nearby stops"),
    MAP("Map"),
    WIDGET("Home screen widget"),
    LIVE_TRACKING("Live tracking"),
    STOP_ALERTS("Stop alerts"),
    CROWDING("Crowding"),
    GAME("Game"),
    ISLAND("Essentials island"),
    MYKI("myki"),
    INTERACTION("Interaction"),
    UPDATES("Updates"),
    API("API access"),
    DATA("Data"),
    ABOUT("About"),
}

/**
 * Every stored setting in the app, grouped by where it belongs. The DataStore names are permanent: changing one
 * would forget what people have saved.
 */
object Prefs {
    // Appearance
    val THEME = EnumPref("theme_mode", ThemeMode.SYSTEM, ThemeMode.entries)
    val BAR_STYLE = EnumPref("bar_style", BarStyle.BOTTOM_BAR, BarStyle.entries)
    val DYNAMIC_COLOUR = BoolPref("dynamic_color", true)
    val PITCH_BLACK = BoolPref("pitch_black", false)

    // Departures
    val TIME_DISPLAY = EnumPref("time_display", TimeDisplay.RELATIVE, TimeDisplay.entries)
    val AUTO_REFRESH = BoolPref("auto_refresh", true)
    val DEPARTURES_PER_STOP = IntPref("departures_per_stop", 3, 1..6)

    // Service updates on stop and line pages
    val STOP_DISRUPTION_LEVEL = EnumPref("stop_disruption_level", DisruptionLevel.MAJOR, DisruptionLevel.entries)
    val STOP_DISRUPTIONS_COLLAPSED = BoolPref("stop_disruptions_collapsed", true)

    // Nearby stops
    val NEARBY_RADIUS = IntPref("nearby_radius", 1000, 250..3000)
    val NEARBY_MODES = ModesPref("nearby_modes")
    val FOR_YOU_NEARBY = BoolPref("for_you_nearby", true)
    val FOR_YOU_NEARBY_COUNT = IntPref("for_you_nearby_count", 3, 1..8)

    // Map
    val SEARCH_REGION = EnumPref("search_region", SearchRegion.VICTORIA, SearchRegion.entries)
    val MAP_FOLLOWS_THEME = BoolPref("map_follows_theme", true)
    val MAP_MODES = ModesPref("map_modes")
    val MAP_RENDER_3D_VEHICLES = BoolPref("map_render_3d_vehicles", false)

    // Home screen widget
    /** The widget's own order of favourites (keys); favourites not listed follow in their usual order. */
    val WIDGET_ORDER = StringListPref("widget_order")
    /** Favourites left off the widget (keys). */
    val WIDGET_HIDDEN = StringListPref("widget_hidden")
    val WIDGET_SUBTITLE = EnumPref("widget_subtitle", WidgetSubtitle.DISTANCE, WidgetSubtitle.entries)
    val WIDGET_SHOW_DESTINATION = BoolPref("widget_show_destination", true)
    val WIDGET_SHOW_LIVE = BoolPref("widget_show_live", true)

    // Live tracking
    /** A "leave now" alert, from where you are, for the service you're tracking. */
    val TRACKING_LEAVE_ALERT = BoolPref("tracking_leave_alert", true)
    /** How fast to go (a stroll to a run) on the live notification. */
    val TRACKING_PACE = BoolPref("tracking_pace", true)

    // Stop alerts
    val STOP_ALERTS = BoolPref("stop_alerts", true)
    val STOP_ALERT_VIBRATE = BoolPref("stop_alert_vibrate", true)

    // Crowding
    val CROWDING_ALERTS = BoolPref("crowding_alerts", true)
    val CROWDING_LEVEL = EnumPref("crowding_level", CrowdingAlertLevel.BUSY, CrowdingAlertLevel.entries)

    // Game (stop roulette)
    val GAME_ENABLED = BoolPref("game_enabled", true)
    val GAME_MODE = EnumPref("game_mode", TransportMode.TRAIN, TransportMode.entries)
    val GAME_RADIUS = IntPref("game_radius", 10_000, 1_000..40_000)
    val GAME_SKIP_NEARBY = BoolPref("game_skip_nearby", true)
    val GAME_CHALLENGES = BoolPref("game_challenges", true)
    val GAME_ANIMATION = BoolPref("game_animation", true)

    // Essentials island (shown on the Island page)
    val ISLAND_ENABLED = BoolPref("island_enabled", true)
    val ISLAND_TRACKING = BoolPref("island_tracking", true)
    val ISLAND_STYLE = EnumPref("island_style", IslandStyle.LINE, IslandStyle.entries)
    val ISLAND_DISRUPTIONS = BoolPref("island_disruptions", true)
    val ISLAND_SCOPE = EnumPref("island_scope", IslandDisruptionScope.FAVOURITES, IslandDisruptionScope.entries)
    val ISLAND_SEVERITY = EnumPref("island_severity", IslandSeverity.DELAYS, IslandSeverity.entries)
    val ISLAND_ICONS = EnumPref("island_icons", IslandIconStyle.PICTOGRAM, IslandIconStyle.entries)
    val ISLAND_PINNED = BoolPref("island_pinned", false)
    val ISLAND_ALERTS = BoolPref("island_alerts", true)

    // Interaction
    val HAPTICS = BoolPref("app_haptics_enabled", true)

    // Updates
    val UPDATE_CHECKS = BoolPref("update_checks", true)
    val UPDATE_PRERELEASES = BoolPref("update_prereleases", false)

    // API access (your own keys, overriding the ones built into the app)
    val PTV_DEV_ID = StringPref("ptv_dev_id")
    val PTV_API_KEY = StringPref("ptv_api_key")
    val MAPBOX_TOKEN = StringPref("mapbox_token")
    val OPEN_DATA_KEY = StringPref("open_data_key")

    // Set elsewhere in the app
    val USER_NAME = StringPref("user_name", clean = { it.trim().take(30) })
    val RECENT_SEARCHES = StringListPref("recent_searches", max = 8)
}

data class AppSettings(
    val userName: String = Prefs.USER_NAME.default,
    val islandEnabled: Boolean = Prefs.ISLAND_ENABLED.default,
    val islandTracking: Boolean = Prefs.ISLAND_TRACKING.default,
    val islandStyle: IslandStyle = Prefs.ISLAND_STYLE.default,
    val islandDisruptions: Boolean = Prefs.ISLAND_DISRUPTIONS.default,
    val islandDisruptionScope: IslandDisruptionScope = Prefs.ISLAND_SCOPE.default,
    val islandMinSeverity: IslandSeverity = Prefs.ISLAND_SEVERITY.default,
    val islandIconStyle: IslandIconStyle = Prefs.ISLAND_ICONS.default,
    val islandPinned: Boolean = Prefs.ISLAND_PINNED.default,
    val islandAlerts: Boolean = Prefs.ISLAND_ALERTS.default,
    val themeMode: ThemeMode = Prefs.THEME.default,
    val dynamicColor: Boolean = Prefs.DYNAMIC_COLOUR.default,
    val pitchBlack: Boolean = Prefs.PITCH_BLACK.default,
    val barStyle: BarStyle = Prefs.BAR_STYLE.default,
    /** Optional Transport Victoria Open Data key for crowding; overrides the one built into the app. */
    val openDataKeyOverride: String = Prefs.OPEN_DATA_KEY.default,
    val crowdingAlerts: Boolean = Prefs.CROWDING_ALERTS.default,
    val crowdingAlertLevel: CrowdingAlertLevel = Prefs.CROWDING_LEVEL.default,
    val hapticsEnabled: Boolean = Prefs.HAPTICS.default,
    val timeDisplay: TimeDisplay = Prefs.TIME_DISPLAY.default,
    val departuresPerStop: Int = Prefs.DEPARTURES_PER_STOP.default,
    val autoRefresh: Boolean = Prefs.AUTO_REFRESH.default,
    val nearbyRadiusMeters: Int = Prefs.NEARBY_RADIUS.default,
    /** Show the closest stops on For You. */
    val forYouNearby: Boolean = Prefs.FOR_YOU_NEARBY.default,
    /** How many nearby stops For You shows. */
    val forYouNearbyCount: Int = Prefs.FOR_YOU_NEARBY_COUNT.default,
    /** Stop roulette (from MelbourneTrainGame) on the Map tab. */
    val gameEnabled: Boolean = Prefs.GAME_ENABLED.default,
    val gameRadiusMeters: Int = Prefs.GAME_RADIUS.default,
    val gameMode: TransportMode = Prefs.GAME_MODE.default,
    /** Skip stops within walking distance, so the roll is a real trip. */
    val gameSkipNearby: Boolean = Prefs.GAME_SKIP_NEARBY.default,
    val gameChallenges: Boolean = Prefs.GAME_CHALLENGES.default,
    val gameRollAnimation: Boolean = Prefs.GAME_ANIMATION.default,
    /** A "leave now" alert for the tracked service, from where you are. */
    val trackingLeaveAlert: Boolean = Prefs.TRACKING_LEAVE_ALERT.default,
    /** The pace to aim for on the live notification. */
    val trackingPace: Boolean = Prefs.TRACKING_PACE.default,
    /** Full-screen "get off here" alert while a journey is tracked. */
    val stopAlerts: Boolean = Prefs.STOP_ALERTS.default,
    /** Whether that alert vibrates (never on silent). It never rings. */
    val stopAlertVibrate: Boolean = Prefs.STOP_ALERT_VIBRATE.default,
    /** Where place search and the Map tab are limited to. */
    val searchRegion: SearchRegion = Prefs.SEARCH_REGION.default,
    /** Look for a newer Journey on GitHub when the app opens. */
    val updateChecks: Boolean = Prefs.UPDATE_CHECKS.default,
    /** Offer pre-releases (betas) as well as full releases. */
    val updatePrereleases: Boolean = Prefs.UPDATE_PRERELEASES.default,
    val nearbyModes: Set<TransportMode> = Prefs.NEARBY_MODES.default,
    val mapModes: Set<TransportMode> = Prefs.MAP_MODES.default,
    val mapFollowsTheme: Boolean = Prefs.MAP_FOLLOWS_THEME.default,
    val mapRender3dVehicles: Boolean = Prefs.MAP_RENDER_3D_VEHICLES.default,
    /** Which alerts count as worth showing on stop and line pages; the rest wait behind the expanded list. */
    val stopDisruptionLevel: DisruptionLevel = Prefs.STOP_DISRUPTION_LEVEL.default,
    /** Whether the service updates on stop and line pages start collapsed. */
    val stopDisruptionsCollapsed: Boolean = Prefs.STOP_DISRUPTIONS_COLLAPSED.default,
    val ptvDevIdOverride: String = Prefs.PTV_DEV_ID.default,
    val ptvApiKeyOverride: String = Prefs.PTV_API_KEY.default,
    val mapboxTokenOverride: String = Prefs.MAPBOX_TOKEN.default,
    val recentSearches: List<String> = Prefs.RECENT_SEARCHES.default,
    /** The home screen widget's order and contents. */
    val widget: WidgetPrefs = WidgetPrefs(),
) {
    val ptvCredentials: PtvCredentials
        get() = if (ptvDevIdOverride.isNotBlank() && ptvApiKeyOverride.isNotBlank()) {
            PtvCredentials(ptvDevIdOverride.trim(), ptvApiKeyOverride.trim())
        } else {
            PtvCredentials(BuildConfig.PTV_DEV_ID, BuildConfig.PTV_API_KEY)
        }

    val usingCustomCredentials: Boolean get() = ptvDevIdOverride.isNotBlank() && ptvApiKeyOverride.isNotBlank()
}

/** What the home screen widget shows, and in what order. */
data class WidgetPrefs(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val subtitle: WidgetSubtitle = WidgetSubtitle.DISTANCE,
    val showDestination: Boolean = true,
    val showLive: Boolean = true,
) {
    /** [items] in the widget's order (any not ordered yet follow, in their usual order), without hidden ones. */
    fun <T> arrange(items: List<T>, key: (T) -> String): List<T> {
        val rank = order.withIndex().associate { (i, k) -> k to i }
        return items
            .filterNot { key(it) in hidden }
            .withIndex()
            .sortedWith(compareBy({ rank[key(it.value)] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }
}

/** Every stored setting at one moment; read any of them with `snapshot[Prefs.X]`. */
class SettingsSnapshot internal constructor(private val prefs: Preferences?) {
    operator fun <T> get(pref: Pref<T>): T = prefs?.let { pref.read(it) } ?: pref.default

    companion object {
        /** All defaults, before anything has loaded. */
        val Defaults = SettingsSnapshot(null)
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "journey_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    /** Every setting, as they change. */
    val snapshots: Flow<SettingsSnapshot> = store.data.map { SettingsSnapshot(it) }

    /** Any one setting, as it changes. */
    fun <T> flow(pref: Pref<T>): Flow<T> = store.data.map { pref.read(it) }.distinctUntilChanged()

    /** Any one setting, right now. */
    suspend fun <T> get(pref: Pref<T>): T = pref.read(store.data.first())

    /** Saves any one setting (tidied and range-checked by its definition). */
    suspend fun <T> set(pref: Pref<T>, value: T) {
        store.edit { pref.write(it, value) }
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = Prefs.THEME.read(p),
            dynamicColor = Prefs.DYNAMIC_COLOUR.read(p),
            pitchBlack = Prefs.PITCH_BLACK.read(p),
            barStyle = Prefs.BAR_STYLE.read(p),
            openDataKeyOverride = Prefs.OPEN_DATA_KEY.read(p),
            crowdingAlerts = Prefs.CROWDING_ALERTS.read(p),
            crowdingAlertLevel = Prefs.CROWDING_LEVEL.read(p),
            hapticsEnabled = Prefs.HAPTICS.read(p),
            timeDisplay = Prefs.TIME_DISPLAY.read(p),
            departuresPerStop = Prefs.DEPARTURES_PER_STOP.read(p),
            autoRefresh = Prefs.AUTO_REFRESH.read(p),
            nearbyRadiusMeters = Prefs.NEARBY_RADIUS.read(p),
            forYouNearby = Prefs.FOR_YOU_NEARBY.read(p),
            forYouNearbyCount = Prefs.FOR_YOU_NEARBY_COUNT.read(p),
            gameEnabled = Prefs.GAME_ENABLED.read(p),
            gameRadiusMeters = Prefs.GAME_RADIUS.read(p),
            gameMode = Prefs.GAME_MODE.read(p),
            gameSkipNearby = Prefs.GAME_SKIP_NEARBY.read(p),
            gameChallenges = Prefs.GAME_CHALLENGES.read(p),
            gameRollAnimation = Prefs.GAME_ANIMATION.read(p),
            trackingLeaveAlert = Prefs.TRACKING_LEAVE_ALERT.read(p),
            trackingPace = Prefs.TRACKING_PACE.read(p),
            stopAlerts = Prefs.STOP_ALERTS.read(p),
            stopAlertVibrate = Prefs.STOP_ALERT_VIBRATE.read(p),
            searchRegion = Prefs.SEARCH_REGION.read(p),
            updateChecks = Prefs.UPDATE_CHECKS.read(p),
            updatePrereleases = Prefs.UPDATE_PRERELEASES.read(p),
            nearbyModes = Prefs.NEARBY_MODES.read(p),
            mapModes = Prefs.MAP_MODES.read(p),
            mapFollowsTheme = Prefs.MAP_FOLLOWS_THEME.read(p),
            mapRender3dVehicles = Prefs.MAP_RENDER_3D_VEHICLES.read(p),
            stopDisruptionLevel = Prefs.STOP_DISRUPTION_LEVEL.read(p),
            stopDisruptionsCollapsed = Prefs.STOP_DISRUPTIONS_COLLAPSED.read(p),
            ptvDevIdOverride = Prefs.PTV_DEV_ID.read(p),
            ptvApiKeyOverride = Prefs.PTV_API_KEY.read(p),
            mapboxTokenOverride = Prefs.MAPBOX_TOKEN.read(p),
            userName = Prefs.USER_NAME.read(p),
            islandEnabled = Prefs.ISLAND_ENABLED.read(p),
            islandTracking = Prefs.ISLAND_TRACKING.read(p),
            islandStyle = Prefs.ISLAND_STYLE.read(p),
            islandDisruptions = Prefs.ISLAND_DISRUPTIONS.read(p),
            islandDisruptionScope = Prefs.ISLAND_SCOPE.read(p),
            islandMinSeverity = Prefs.ISLAND_SEVERITY.read(p),
            islandIconStyle = Prefs.ISLAND_ICONS.read(p),
            islandPinned = Prefs.ISLAND_PINNED.read(p),
            islandAlerts = Prefs.ISLAND_ALERTS.read(p),
            recentSearches = Prefs.RECENT_SEARCHES.read(p),
            widget = WidgetPrefs(
                order = Prefs.WIDGET_ORDER.read(p),
                hidden = Prefs.WIDGET_HIDDEN.read(p).toSet(),
                subtitle = Prefs.WIDGET_SUBTITLE.read(p),
                showDestination = Prefs.WIDGET_SHOW_DESTINATION.read(p),
                showLive = Prefs.WIDGET_SHOW_LIVE.read(p),
            ),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    // Named setters, kept so existing callers read naturally. New code can call set(Prefs.X, value) directly.
    suspend fun setIslandEnabled(v: Boolean) = set(Prefs.ISLAND_ENABLED, v)
    suspend fun setIslandTracking(v: Boolean) = set(Prefs.ISLAND_TRACKING, v)
    suspend fun setIslandStyle(v: IslandStyle) = set(Prefs.ISLAND_STYLE, v)
    suspend fun setIslandDisruptions(v: Boolean) = set(Prefs.ISLAND_DISRUPTIONS, v)
    suspend fun setIslandScope(v: IslandDisruptionScope) = set(Prefs.ISLAND_SCOPE, v)
    suspend fun setIslandSeverity(v: IslandSeverity) = set(Prefs.ISLAND_SEVERITY, v)
    suspend fun setIslandIcons(v: IslandIconStyle) = set(Prefs.ISLAND_ICONS, v)
    suspend fun setIslandPinned(v: Boolean) = set(Prefs.ISLAND_PINNED, v)
    suspend fun setIslandAlerts(v: Boolean) = set(Prefs.ISLAND_ALERTS, v)
    suspend fun setUserName(name: String) = set(Prefs.USER_NAME, name)
    suspend fun setThemeMode(mode: ThemeMode) = set(Prefs.THEME, mode)
    suspend fun setDynamicColor(enabled: Boolean) = set(Prefs.DYNAMIC_COLOUR, enabled)
    suspend fun setPitchBlack(enabled: Boolean) = set(Prefs.PITCH_BLACK, enabled)
    suspend fun setBarStyle(style: BarStyle) = set(Prefs.BAR_STYLE, style)
    suspend fun setOpenDataKey(key: String) = set(Prefs.OPEN_DATA_KEY, key)
    suspend fun setCrowdingAlerts(enabled: Boolean) = set(Prefs.CROWDING_ALERTS, enabled)
    suspend fun setCrowdingAlertLevel(level: CrowdingAlertLevel) = set(Prefs.CROWDING_LEVEL, level)
    suspend fun setHaptics(enabled: Boolean) = set(Prefs.HAPTICS, enabled)
    suspend fun setTimeDisplay(display: TimeDisplay) = set(Prefs.TIME_DISPLAY, display)
    suspend fun setDeparturesPerStop(count: Int) = set(Prefs.DEPARTURES_PER_STOP, count)
    suspend fun setAutoRefresh(enabled: Boolean) = set(Prefs.AUTO_REFRESH, enabled)
    suspend fun setNearbyRadius(meters: Int) = set(Prefs.NEARBY_RADIUS, meters)
    suspend fun setForYouNearby(enabled: Boolean) = set(Prefs.FOR_YOU_NEARBY, enabled)
    suspend fun setForYouNearbyCount(count: Int) = set(Prefs.FOR_YOU_NEARBY_COUNT, count)
    suspend fun setGameEnabled(enabled: Boolean) = set(Prefs.GAME_ENABLED, enabled)
    suspend fun setGameRadius(meters: Int) = set(Prefs.GAME_RADIUS, meters)
    suspend fun setGameMode(mode: TransportMode) = set(Prefs.GAME_MODE, mode)
    suspend fun setGameSkipNearby(enabled: Boolean) = set(Prefs.GAME_SKIP_NEARBY, enabled)
    suspend fun setGameChallenges(enabled: Boolean) = set(Prefs.GAME_CHALLENGES, enabled)
    suspend fun setGameRollAnimation(enabled: Boolean) = set(Prefs.GAME_ANIMATION, enabled)
    suspend fun setStopAlerts(enabled: Boolean) = set(Prefs.STOP_ALERTS, enabled)
    suspend fun setStopAlertVibrate(enabled: Boolean) = set(Prefs.STOP_ALERT_VIBRATE, enabled)
    suspend fun setSearchRegion(region: SearchRegion) = set(Prefs.SEARCH_REGION, region)
    suspend fun setUpdateChecks(enabled: Boolean) = set(Prefs.UPDATE_CHECKS, enabled)
    suspend fun setUpdatePrereleases(enabled: Boolean) = set(Prefs.UPDATE_PRERELEASES, enabled)
    suspend fun setNearbyModes(modes: Set<TransportMode>) = set(Prefs.NEARBY_MODES, modes)
    suspend fun setMapModes(modes: Set<TransportMode>) = set(Prefs.MAP_MODES, modes)
    suspend fun setMapFollowsTheme(enabled: Boolean) = set(Prefs.MAP_FOLLOWS_THEME, enabled)
    suspend fun setMapRender3dTheme(enabled: Boolean) = set(Prefs.MAP_RENDER_3D_VEHICLES, enabled)
    suspend fun setStopDisruptionLevel(level: DisruptionLevel) = set(Prefs.STOP_DISRUPTION_LEVEL, level)
    suspend fun setStopDisruptionsCollapsed(enabled: Boolean) = set(Prefs.STOP_DISRUPTIONS_COLLAPSED, enabled)
    suspend fun setMapboxToken(token: String) = set(Prefs.MAPBOX_TOKEN, token)

    suspend fun setPtvCredentials(devId: String, apiKey: String) = store.edit {
        Prefs.PTV_DEV_ID.write(it, devId)
        Prefs.PTV_API_KEY.write(it, apiKey)
    }

    suspend fun addRecentSearch(term: String) = store.edit { p ->
        val clean = term.trim()
        if (clean.isBlank()) return@edit
        val existing = Prefs.RECENT_SEARCHES.read(p)
        Prefs.RECENT_SEARCHES.write(p, listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) })
    }

    suspend fun clearRecentSearches() = set(Prefs.RECENT_SEARCHES, emptyList())
}
