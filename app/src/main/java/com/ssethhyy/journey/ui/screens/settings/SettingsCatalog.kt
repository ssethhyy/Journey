/*
 * Feature Module: Settings
 * File: SettingsCatalog.kt
 * Description: What the Settings page shows, category by category.
 *
 * Adding a setting
 * ----------------
 * 1. Define it in `Prefs` (data/repository/SettingsRepository.kt).
 * 2. Add one item to its category below. Pick the type that fits:
 *      Toggle      on/off                        Toggle(Prefs.X, "Title", Sym.Icon, "What it does")
 *      Choice      one of a few options          Choice(Prefs.X, "Title", Sym.Icon, label = { it.label })
 *      Slider      a number in a range           Slider(Prefs.X, "Title", Sym.Icon, step = 250, valueLabel = { "$it m" })
 *      TextInput   text or a key, with Save      TextInput(Prefs.X, "Title", Sym.Icon, secret = true)
 *      Modes       transport modes to include    Modes(Prefs.X, "Title")
 *      Action      a button that does something  Action("Title", Sym.Icon) { openUpdates() }
 *      Info        plain text, not editable      Info("Title", Sym.Icon) { "Version ${'$'}{BuildConfig.VERSION_NAME}" }
 *      Custom      anything else                 Custom("key") { scope -> MyRow(scope) }
 *    Every item can be shown only sometimes (`visible = { it[Prefs.GAME_ENABLED] }`) and greyed out (`enabled = { ... }`).
 * 3. That's it: the renderer (SettingsRenderer.kt) draws it, saves changes and keeps it up to date.
 */

package com.ssethhyy.journey.ui.screens.settings

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.ssethhyy.journey.BuildConfig
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.BoolPref
import com.ssethhyy.journey.data.repository.EnumPref
import com.ssethhyy.journey.data.repository.IntPref
import com.ssethhyy.journey.data.repository.ModesPref
import com.ssethhyy.journey.data.repository.Prefs
import com.ssethhyy.journey.data.repository.SettingsCategory
import com.ssethhyy.journey.data.repository.SettingsSnapshot
import com.ssethhyy.journey.data.repository.StringPref
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.navigation.Navigator
import com.ssethhyy.journey.utils.DistanceFormatter

/** What an action or a dynamic label can reach: the current settings and the things a row might need to do. */
class SettingsScope(
    val settings: AppSettings,
    /** Every stored setting, including ones [settings] doesn't carry: `values[Prefs.X]`. */
    val values: com.ssethhyy.journey.data.repository.SettingsSnapshot,
    val context: Context,
    val navigator: Navigator,
    val vm: SettingsViewModel,
    /** Runs a page-level job the catalog can't do itself (file pickers, dialogs, update checks). */
    val run: (SettingsJob) -> Unit,
    val favouriteCount: Int,
    /** True while "Check for updates" is looking. */
    val checkingForUpdates: Boolean = false,
) {
    /** Saves a setting. */
    fun <T> save(pref: com.ssethhyy.journey.data.repository.Pref<T>, value: T) = vm.update { set(pref, value) }

    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
}

/** Page-level jobs: things that need a launcher, a dialog or a coroutine owned by the Settings page. */
enum class SettingsJob { EXPORT_FAVOURITES, IMPORT_FAVOURITES, CLEAR_FAVOURITES, CHECK_UPDATES, CLEAR_RECENT_SEARCHES }

/** One row on the Settings page. */
sealed interface SettingItem {
    /** Stable identity for the list. */
    val id: String
    val visible: (SettingsSnapshot) -> Boolean
    val experimental : Boolean?
        get() = null
}

/** On or off. */
data class Toggle(
    val pref: BoolPref,
    val title: String,
    val icon: ImageVector,
    val description: String? = null,
    val enabled: (SettingsSnapshot) -> Boolean = { true },
    /** Said when tapped while greyed out. */
    val disabledReason: String? = null,
    /** Shown instead of the saved value while greyed out (e.g. off on phones that can't do it). */
    val checkedWhenDisabled: Boolean? = null,
    /** Runs straight after the change is saved, for settings the app applies at once. */
    val onChanged: ((Boolean) -> Unit)? = null,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
) : SettingItem {
    override val id get() = pref.name
}

/** One of a few options, picked from a row of buttons. */
data class Choice<E : Enum<E>>(
    val pref: EnumPref<E>,
    val title: String,
    val icon: ImageVector,
    val label: (E) -> String,
    /** Explains the current choice, if it needs explaining. */
    val describe: ((E) -> String)? = null,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
) : SettingItem {
    override val id get() = pref.name
}

/** A number on a slider. [scale] turns the stored value into what the slider shows (metres to km). */
data class Slider(
    val pref: IntPref,
    val title: String,
    val icon: ImageVector,
    val step: Int,
    val valueLabel: (Int) -> String,
    val scale: Int = 1,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
) : SettingItem {
    override val id get() = pref.name
}

/** Text or a key, typed in and saved with a button. */
data class TextInput(
    val pref: StringPref,
    val title: String,
    val icon: ImageVector,
    val label: String,
    val describe: (SettingsSnapshot) -> String? = { null },
    val secret: Boolean = false,
    /** A problem with what's typed, or null when it's fine. */
    val validate: (String) -> String? = { null },
    val saveLabel: String = "Save",
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
) : SettingItem {
    override val id get() = pref.name
}

/** Which transport modes to include. */
data class Modes(
    val pref: ModesPref,
    val title: String,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
) : SettingItem {
    override val id get() = pref.name
}

/** A button: opens something or does something now. */
data class Action(
    override val id: String,
    val title: String,
    val icon: ImageVector,
    val describe: (SettingsScope) -> String? = { null },
    /** Destructive actions are tinted red. */
    val destructive: Boolean = false,
    /** Whether it leads somewhere (shows a chevron) or acts in place. */
    val opensPage: Boolean = true,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
    val onClick: SettingsScope.() -> Unit,
) : SettingItem

/** Plain text that can't be changed. */
data class Info(
    override val id: String,
    val title: String,
    val icon: ImageVector,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
    val text: (SettingsScope) -> String,
) : SettingItem

/** Anything the standard rows can't express, drawn by its own composable. */
data class Custom(
    override val id: String,
    override val visible: (SettingsSnapshot) -> Boolean = { true },
    override val experimental: Boolean? = false,
    val content: @Composable (SettingsScope) -> Unit,
) : SettingItem

object SettingsCatalog {
    /** The items of [category], in order. (Composable only because the app's icons are looked up while drawing.) */
    @Composable
    fun itemsFor(category: SettingsCategory): List<SettingItem> = when (category) {
        SettingsCategory.FOR_YOU -> listOf(
            // Your name, the weather, departures per stop and the rest live with each For You widget.
            Action("customise_for_you", "Customise For You", Sym.DashboardCustomize, { "Reorder, add and remove widgets, and set up the greeting, weather, favourites and more" }) {
                context.appContainer.requestForYouEdit()
                navigator.goToTab(com.ssethhyy.journey.ui.navigation.HomeTab.FOR_YOU)
            },
        )

        SettingsCategory.APPEARANCE -> listOf(
            Choice(Prefs.THEME, "Theme", Sym.Brightness4, label = { it.label }),
            Choice(Prefs.BAR_STYLE, "Navigation bar", Sym.Widgets, label = { it.label }),
            Toggle(
                Prefs.DYNAMIC_COLOUR, "Dynamic colour", Sym.Palette,
                description = if (dynamicColourSupported) "Match colours to your wallpaper" else "Requires Android 12 or newer",
                enabled = { dynamicColourSupported },
                disabledReason = "Dynamic colour needs Android 12+",
                checkedWhenDisabled = false,
            ),
            Toggle(Prefs.PITCH_BLACK, "Pitch black", Sym.Contrast, "Pure black backgrounds in dark theme for OLED screens"),
        )

        SettingsCategory.DEPARTURES -> listOf(
            Choice(Prefs.TIME_DISPLAY, "Show times as", Sym.Schedule, label = { it.label }),
            Toggle(Prefs.AUTO_REFRESH, "Auto refresh", Sym.Refresh, "Update live times every 30 seconds while open"),
        )

        SettingsCategory.SERVICE_UPDATES -> listOf(
            Choice(
                Prefs.STOP_DISRUPTION_LEVEL, "Show", Sym.Warning,
                label = { it.shortLabel },
                describe = { "${it.label}: ${it.detail.replaceFirstChar { c -> c.lowercase() }}. Expand the card to see every alert" },
            ),
            Toggle(Prefs.STOP_DISRUPTIONS_COLLAPSED, "Start collapsed", Sym.ExpandLess, "One quiet line until you tap it"),
        )

        SettingsCategory.NEARBY -> listOf(
            Slider(Prefs.NEARBY_RADIUS, "Search radius", Sym.NearMe, step = 250, valueLabel = { DistanceFormatter.distance(it.toDouble()) }),
            Modes(Prefs.NEARBY_MODES, "Transport modes"),
        )

        SettingsCategory.MAP -> listOf(
            Choice(Prefs.SEARCH_REGION, "Search and map area", Sym.Search, label = { it.label }),
            Toggle(Prefs.MAP_FOLLOWS_THEME, "Night map in dark theme", Sym.Map, "Switch the map lighting with the app theme"),
            Toggle(Prefs.MAP_RENDER_3D_VEHICLES, "Render 3D Vehicles", Sym.ViewInAr, "Some tracked vehicles are displayed as a 3D model in the map view", experimental = true)
        )

        SettingsCategory.WIDGET -> listOf(
            Action("add_widget", "Add to home screen", Sym.Widgets, { "Live departures from your favourites" }) { pinWidget(context) },
            Custom("widget_order") { scope -> WidgetOrderSetting(scope) },
            Choice(
                Prefs.WIDGET_SUBTITLE, "Under each stop", Sym.NearMe, label = { it.label },
                describe = {
                    when (it) {
                        com.ssethhyy.journey.data.repository.WidgetSubtitle.DISTANCE -> "How far away it is (its suburb when your location isn't known)"
                        com.ssethhyy.journey.data.repository.WidgetSubtitle.SUBURB -> "The stop's suburb"
                        com.ssethhyy.journey.data.repository.WidgetSubtitle.NONE -> "Just the stop's name"
                    }
                },
            ),
            Toggle(Prefs.WIDGET_SHOW_DESTINATION, "Destination", Sym.Directions, "Where the next service is going, under its time"),
            Toggle(Prefs.WIDGET_SHOW_LIVE, "Live indicator", Sym.Schedule, "Mark times that come from live tracking"),
        )

        SettingsCategory.STOP_ALERTS -> listOf(
            Toggle(
                Prefs.STOP_ALERTS, "Full-screen alert to get off", Sym.NotificationsActive,
                "While a journey is tracked, fills the screen when it's time to get off, from your location and the service's arrival time",
            ),
            Toggle(Prefs.STOP_ALERT_VIBRATE, "Vibrate", Sym.Vibration, "Never rings. Follows your phone: no vibration when it's on silent", visible = { it[Prefs.STOP_ALERTS] }),
            // Only while Android hasn't allowed it yet, which only the phone can say.
            Custom("full_screen_permission", visible = { it[Prefs.STOP_ALERTS] }) { scope -> FullScreenPermissionSetting(scope) },
            Action("preview_alert", "Preview the alert", Sym.Visibility, opensPage = false, visible = { it[Prefs.STOP_ALERTS] }) { previewStopAlert(this) },
        )

        SettingsCategory.CROWDING -> listOf(
            Toggle(Prefs.CROWDING_ALERTS, "Crowding alerts", Sym.Groups, "Tell me when a service I'm tracking gets busy"),
            Choice(
                Prefs.CROWDING_LEVEL, "Alert me when it is", Sym.Warning,
                // Short labels fit the row; the full one is spelt out underneath.
                label = {
                    when (it) {
                        com.ssethhyy.journey.data.crowding.CrowdingAlertLevel.BUSY -> "Standing"
                        com.ssethhyy.journey.data.crowding.CrowdingAlertLevel.VERY_BUSY -> "Crowded"
                        com.ssethhyy.journey.data.crowding.CrowdingAlertLevel.FULL -> "Full"
                    }
                },
                describe = { "${it.label} or busier" },
                visible = { it[Prefs.CROWDING_ALERTS] },
            ),
        )

        SettingsCategory.GAME -> listOf(
            Toggle(Prefs.GAME_ENABLED, "Stop roulette", Sym.Casino, "Roll a random stop to travel to, from the Map tab (from MelbourneTrainGame)"),
            // Five across: "Night" keeps Night Bus readable.
            Choice(Prefs.GAME_MODE, "Roll a", Sym.DirectionsTransit, label = { if (it == TransportMode.NIGHT_BUS) "Night" else it.label }, visible = { it[Prefs.GAME_ENABLED] }),
            Slider(Prefs.GAME_RADIUS, "Search radius", Sym.NearMe, step = 1, scale = 1000, valueLabel = { "$it km" }, visible = { it[Prefs.GAME_ENABLED] }),
            Toggle(Prefs.GAME_SKIP_NEARBY, "Skip stops within walking distance", Sym.DirectionsWalk, "Nothing closer than 1.5 km, so every roll is a real trip", visible = { it[Prefs.GAME_ENABLED] }),
            Toggle(Prefs.GAME_CHALLENGES, "Travel challenges", Sym.StarFilled, "Add a twist to the trip, like no phone or getting off a stop early", visible = { it[Prefs.GAME_ENABLED] }),
            Toggle(Prefs.GAME_ANIMATION, "Rolling animation", Sym.Refresh, "Shuffle through stops before landing on one", visible = { it[Prefs.GAME_ENABLED] }),
        )

        SettingsCategory.ISLAND -> listOf(
            Action("island", "Island", Sym.Widgets, { "Live tracking and disruption alerts on the Essentials island" }) { navigator.openIslandSettings() },
        )

        SettingsCategory.MYKI -> listOf(
            Action("fares", "Fares and discounts", Sym.ReceiptLong, { "Prices, half-price and free travel, used when you log trips" }) { navigator.openFares() },
        )

        SettingsCategory.INTERACTION -> listOf(
            Toggle(
                Prefs.HAPTICS, "Haptic feedback", Sym.Vibration, "Feel taps, toggles and page changes",
                onChanged = { com.ssethhyy.journey.utils.HapticUtil.isAppHapticsEnabled.value = it },
            ),
            Action("clear_recent", "Clear recent searches", Sym.History, opensPage = false) { run(SettingsJob.CLEAR_RECENT_SEARCHES) },
        )

        SettingsCategory.UPDATES -> listOf(
            Action("check_updates", "Check for updates", Sym.Download, { s -> if (s.checkingForUpdates) "Checking…" else "You have Journey ${BuildConfig.VERSION_NAME}" }, opensPage = false) {
                if (!checkingForUpdates) run(SettingsJob.CHECK_UPDATES)
            },
            Toggle(
                Prefs.UPDATE_CHECKS, "Check when Journey opens", Sym.NotificationsActive,
                "Looks on GitHub at most every few hours and notifies you about a new version. Never checks in the background",
            ),
            Toggle(Prefs.UPDATE_PRERELEASES, "Include pre-releases", Sym.Info, "Also offer beta versions that may be less polished"),
        )

        SettingsCategory.API -> listOf(
            // The developer ID and key are saved together and can be tested, so they get their own card.
            Custom("ptv_credentials") { scope -> PtvCredentialsSetting(scope) },
            TextInput(
                Prefs.MAPBOX_TOKEN, "Mapbox", Sym.Map, label = "Access token",
                describe = { "Public token (pk.…) for map tiles. Restart the app after changing." },
                validate = { if (it.isNotEmpty() && !it.startsWith("pk.")) "Use a public token starting with pk." else null },
                saveLabel = "Save token",
            ),
            TextInput(
                Prefs.OPEN_DATA_KEY, "Transport Victoria Open Data", Sym.Groups, label = "Open Data API key",
                describe = {
                    if (it[Prefs.OPEN_DATA_KEY].isNotBlank() || BuildConfig.OPENDATA_KEY.isNotBlank()) "Key added: crowding is on"
                    else "Optional. A free key shows how busy a service and its carriages are."
                },
                secret = true,
                saveLabel = "Save key",
            ),
        )

        SettingsCategory.DATA -> listOf(
            Action("export", "Export favourites", Sym.Upload, { s -> "Save ${s.favouriteCount} favourite${if (s.favouriteCount == 1) "" else "s"} to a JSON file" }) {
                run(SettingsJob.EXPORT_FAVOURITES)
            },
            Action("import", "Import favourites", Sym.Download, { "Merge favourites from a backup file" }) { run(SettingsJob.IMPORT_FAVOURITES) },
            Action("clear_favourites", "Clear all favourites", Sym.DeleteSweep, destructive = true, opensPage = false) { run(SettingsJob.CLEAR_FAVOURITES) },
        )

        SettingsCategory.ABOUT -> listOf(
            Custom("about") { AboutCard() },
            Info("version", "Version", Sym.Info) { BuildConfig.VERSION_NAME },
            Action(
                "open_transit", "Open Transit", Sym.CreditCard,
                { "The myki card art and NFC animation come from Open Transit by Aston Bolwell" },
            ) { openLink(context, "https://github.com/AstonBolwell/Open-Transit-App") },
            Action(
                "data_sources", "Data sources", Sym.Info,
                {
                    "Timetable data licensed from Public Transport Victoria under CC BY 4.0. Journey planning by Transitous (MOTIS) " +
                        "using Transport Victoria open data. Maps © Mapbox © OpenStreetMap."
                },
            ) { openLink(context, "https://www.ptv.vic.gov.au/footer/data-and-reporting/datasets/ptv-timetable-api/") },
        )
    }

    private val dynamicColourSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
