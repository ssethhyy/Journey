/*
 * Feature Module: For You
 * File: ForYouLayout.kt
 * Description: Which widgets For You shows, in what order, and each widget's own settings.
 *
 * Stored as one JSON document in its own DataStore. Settings that other screens share (the user's name,
 * departures per stop, how many nearby stops) stay in AppSettings; the widget settings sheets edit them there.
 */

package com.ssethhyy.journey.data.foryou

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.weather.TemperatureUnit
import com.ssethhyy.journey.data.weather.WeatherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ForYouWidget(val title: String, val description: String) {
    GREETING("Greeting", "The weather, a greeting and today's date"),
    PLACES("Places", "Home, Work and your saved places, one tap from a trip"),
    FAVOURITES("Favourites", "Live departures from your favourite stops"),
    DISRUPTIONS("Disruptions", "Major disruptions on your favourite stops and lines"),
    COMMUTES("Commutes", "When to leave for the trips you make most"),
    NEAR_YOU("Near you", "The closest stops and their next departures"),
    NETWORK_STATUS("Network status", "How the whole network is running right now"),
    SHORTCUTS("Shortcuts", "Plan a trip, the map, disruptions and stop roulette"),
    ;

    companion object {
        /** Favourites sit straight under the places; the extra widgets start in the Add list. */
        val DEFAULT_ORDER = listOf(GREETING, PLACES, FAVOURITES, DISRUPTIONS, COMMUTES, NEAR_YOU)
    }
}

@Serializable
enum class DateStyle(val label: String) {
    SENTENCE("Today is the 14th of November 2026"),
    WEEKDAY("Friday 14 November"),
    SHORT("Fri 14 Nov"),
    NUMERIC("14/11/26"),
}

@Serializable
enum class WeatherSize(val label: String, val temperatureSp: Float) {
    SMALL("Small", 72f),
    MEDIUM("Medium", 94f),
    LARGE("Large", 116f),
    HUGE("Huge", 140f),
}

@Serializable
data class GreetingConfig(
    val showGreeting: Boolean = true,
    val showDate: Boolean = true,
    val dateStyle: DateStyle = DateStyle.SENTENCE,
    val showWeather: Boolean = true,
    /** Collapse to a small bar after [collapseAfterSeconds] so the rest of the page moves up. */
    val collapses: Boolean = true,
    val collapseAfterSeconds: Int = 6,
    /** The condition and today's high and low under the temperature. */
    val showWeatherDetails: Boolean = false,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** How big the temperature is while the greeting is expanded. */
    val weatherSize: WeatherSize = WeatherSize.LARGE,
    /** What the collapsed bar shows: the weather on the left, the time in the middle, the date on the right. */
    val barWeather: Boolean = true,
    val barTime: Boolean = false,
    val barDate: Boolean = false,
    val provider: WeatherProvider = WeatherProvider.DEFAULT,
    /** The user's own keys, per provider. */
    val keys: Map<WeatherProvider, String> = emptyMap(),
)

@Serializable
data class PlacesConfig(
    /** "Set home" and "Set work" tiles while those aren't saved. */
    val showSuggestions: Boolean = true,
    val showAddTile: Boolean = true,
)

@Serializable
data class FavouritesConfig(
    val groupByMode: Boolean = true,
    val showModeFilters: Boolean = true,
    /** Show a favourite even when nothing is coming. */
    val showEmptyStops: Boolean = true,
    val showAddButton: Boolean = true,
)

@Serializable
enum class DisruptionLevel(val label: String, val detail: String) {
    MAJOR("Major only", "Closures, suspensions, buses replacing trains and changed timetables"),
    DELAYS("Major and delays", "Also minor delays"),
    ALL("Everything", "Every current notice, including planned works and information"),
}

@Serializable
enum class DisruptionScope(val label: String) { FAVOURITES("My stops and lines"), NETWORK("Whole network") }

@Serializable
data class DisruptionsConfig(
    val level: DisruptionLevel = DisruptionLevel.MAJOR,
    val scope: DisruptionScope = DisruptionScope.FAVOURITES,
    /** Hide the widget entirely while nothing matches. */
    val hideWhenClear: Boolean = true,
    val maxShown: Int = 4,
)

@Serializable
data class CommutesConfig(
    /** Commutes Journey learned from planned trips, as well as your own. */
    val showLearned: Boolean = true,
    val showBackup: Boolean = true,
    val maxShown: Int = 3,
    val showAddButton: Boolean = true,
)

@Serializable
data class NearYouConfig(
    val showMapButton: Boolean = true,
)

@Serializable
data class NetworkStatusConfig(
    val modes: Set<TransportMode> = setOf(TransportMode.TRAIN, TransportMode.TRAM, TransportMode.BUS, TransportMode.VLINE),
)

@Serializable
enum class Shortcut(val label: String) { PLAN("Plan a trip"), MAP("Map"), DISRUPTIONS("Disruptions"), ROULETTE("Stop roulette"), SEARCH("Search") }

@Serializable
data class ShortcutsConfig(
    val shortcuts: List<Shortcut> = listOf(Shortcut.PLAN, Shortcut.MAP, Shortcut.DISRUPTIONS, Shortcut.ROULETTE),
)

@Serializable
data class ForYouLayout(
    val widgets: List<ForYouWidget> = ForYouWidget.DEFAULT_ORDER,
    /** A title with a collapse arrow over each widget, or none at all. */
    val showTitles: Boolean = true,
    val collapsed: Set<ForYouWidget> = setOf(ForYouWidget.DISRUPTIONS),
    val greeting: GreetingConfig = GreetingConfig(),
    val places: PlacesConfig = PlacesConfig(),
    val favourites: FavouritesConfig = FavouritesConfig(),
    val disruptions: DisruptionsConfig = DisruptionsConfig(),
    val commutes: CommutesConfig = CommutesConfig(),
    val nearYou: NearYouConfig = NearYouConfig(),
    val networkStatus: NetworkStatusConfig = NetworkStatusConfig(),
    val shortcuts: ShortcutsConfig = ShortcutsConfig(),
) {
    /** Widgets that can be added: removed ones and those not shown by default. */
    val available: List<ForYouWidget> get() = ForYouWidget.entries.filter { it !in widgets }

    fun isCollapsed(widget: ForYouWidget) = widget in collapsed

    /** Moves the widget at [from] so it ends up at [to]. */
    fun move(from: Int, to: Int): ForYouLayout {
        if (from !in widgets.indices || from == to) return this
        val list = widgets.toMutableList()
        list.add(to.coerceIn(0, list.lastIndex), list.removeAt(from))
        return copy(widgets = list)
    }

    fun remove(widget: ForYouWidget) = copy(widgets = widgets - widget)

    fun add(widget: ForYouWidget, at: Int = widgets.size): ForYouLayout =
        if (widget in widgets) this else copy(widgets = widgets.toMutableList().apply { add(at.coerceIn(0, size), widget) })

    fun toggleCollapsed(widget: ForYouWidget) =
        copy(collapsed = if (widget in collapsed) collapsed - widget else collapsed + widget)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun encode(layout: ForYouLayout): String = json.encodeToString(serializer(), layout)

        /** Null for anything unreadable, so a bad document falls back to the default layout. */
        fun decode(raw: String?): ForYouLayout? = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

private val Context.forYouStore: DataStore<Preferences> by preferencesDataStore(name = "for_you")

class ForYouRepository(
    context: Context,
    /** Read once, the first time, so people who had turned Near you off don't get it back. */
    private val nearbyWasOn: suspend () -> Boolean = { true },
) {
    private val store = context.applicationContext.forYouStore
    private val key = stringPreferencesKey("layout")

    val layout: Flow<ForYouLayout> = store.data.map { p -> ForYouLayout.decode(p[key]) ?: ForYouLayout() }

    suspend fun current(): ForYouLayout = layout.first()

    /** Writes the first layout from the old settings, if none has been saved yet. */
    suspend fun migrate() {
        if (store.data.first()[key] != null) return
        val initial = if (nearbyWasOn()) ForYouLayout() else ForYouLayout().remove(ForYouWidget.NEAR_YOU)
        store.edit { if (it[key] == null) it[key] = ForYouLayout.encode(initial) }
    }

    suspend fun update(transform: (ForYouLayout) -> ForYouLayout) = store.edit { p ->
        val current = ForYouLayout.decode(p[key]) ?: ForYouLayout()
        p[key] = ForYouLayout.encode(transform(current))
    }

    suspend fun reset() = store.edit { it[key] = ForYouLayout.encode(ForYouLayout()) }
}
