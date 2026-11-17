package com.ssethhyy.journey.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.Prefs
import com.ssethhyy.journey.data.repository.SettingsCategory
import com.ssethhyy.journey.data.repository.ThemeMode
import com.ssethhyy.journey.data.repository.WidgetPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPrefsTest {
    @Test
    fun numbersStayInRangeAndUnsetValuesAreDefaults() {
        val p = mutablePreferencesOf()
        assertEquals(1000, Prefs.NEARBY_RADIUS.read(p))
        Prefs.NEARBY_RADIUS.write(p, 99_999)
        assertEquals(3000, Prefs.NEARBY_RADIUS.read(p))
    }

    @Test
    fun anUnknownStoredOptionReadsAsTheDefault() {
        val p = mutablePreferencesOf(stringPreferencesKey("theme_mode") to "PURPLE")
        assertEquals(ThemeMode.SYSTEM, Prefs.THEME.read(p))
        Prefs.THEME.write(p, ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, Prefs.THEME.read(p))
    }

    @Test
    fun modesAreNeverEmptyAndListsKeepTheirOrder() {
        val p = mutablePreferencesOf()
        Prefs.MAP_MODES.write(p, emptySet())
        assertEquals(TransportMode.entries.toSet(), Prefs.MAP_MODES.read(p))
        Prefs.RECENT_SEARCHES.write(p, (1..12).map { "term $it" })
        assertEquals((1..8).map { "term $it" }, Prefs.RECENT_SEARCHES.read(p))
        Prefs.USER_NAME.write(p, "  Seth  ")
        assertEquals("Seth", Prefs.USER_NAME.read(p))
    }

    @Test
    fun theWidgetFollowsItsOwnOrderAndLeavesHiddenStopsOut() {
        val prefs = WidgetPrefs(order = listOf("c", "a"), hidden = setOf("b"))
        assertEquals(listOf("c", "a", "d"), prefs.arrange(listOf("a", "b", "c", "d")) { it })
        assertEquals(listOf("a", "b"), WidgetPrefs().arrange(listOf("a", "b")) { it })
    }

    @Test
    fun keysAreUniqueAndEveryCategoryHasATitle() {
        val names = listOf(
            Prefs.THEME, Prefs.BAR_STYLE, Prefs.DYNAMIC_COLOUR, Prefs.PITCH_BLACK, Prefs.TIME_DISPLAY, Prefs.AUTO_REFRESH,
            Prefs.DEPARTURES_PER_STOP, Prefs.STOP_DISRUPTION_LEVEL, Prefs.STOP_DISRUPTIONS_COLLAPSED, Prefs.NEARBY_RADIUS,
            Prefs.NEARBY_MODES, Prefs.FOR_YOU_NEARBY, Prefs.FOR_YOU_NEARBY_COUNT, Prefs.SEARCH_REGION, Prefs.MAP_FOLLOWS_THEME,
            Prefs.MAP_MODES, Prefs.WIDGET_ORDER, Prefs.WIDGET_HIDDEN, Prefs.WIDGET_SUBTITLE, Prefs.WIDGET_SHOW_DESTINATION,
            Prefs.WIDGET_SHOW_LIVE, Prefs.STOP_ALERTS, Prefs.STOP_ALERT_VIBRATE, Prefs.CROWDING_ALERTS, Prefs.CROWDING_LEVEL,
            Prefs.GAME_ENABLED, Prefs.GAME_MODE, Prefs.GAME_RADIUS, Prefs.GAME_SKIP_NEARBY, Prefs.GAME_CHALLENGES,
            Prefs.GAME_ANIMATION, Prefs.ISLAND_ENABLED, Prefs.ISLAND_TRACKING, Prefs.ISLAND_STYLE, Prefs.ISLAND_DISRUPTIONS,
            Prefs.ISLAND_SCOPE, Prefs.ISLAND_SEVERITY, Prefs.ISLAND_ICONS, Prefs.ISLAND_PINNED, Prefs.ISLAND_ALERTS,
            Prefs.HAPTICS, Prefs.UPDATE_CHECKS, Prefs.UPDATE_PRERELEASES, Prefs.PTV_DEV_ID, Prefs.PTV_API_KEY,
            Prefs.MAPBOX_TOKEN, Prefs.OPEN_DATA_KEY, Prefs.USER_NAME, Prefs.RECENT_SEARCHES,
        ).map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(SettingsCategory.entries.all { it.title.isNotBlank() })
    }
}
