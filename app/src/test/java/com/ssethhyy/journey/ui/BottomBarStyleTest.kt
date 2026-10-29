package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.BarStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The bottom-bar style: one toolbar that is always on screen. On a page it shows a back button, the page name and
 * that page's action; going back returns the three tabs. The original top pill is covered by ForYouScrollTest.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class BottomBarStyleTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun restore() {
        runBlocking { rule.activity.appContainer.settingsRepository.setBarStyle(BarStyle.BOTTOM_BAR) }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun count(description: String) = rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun waitForDescription(description: String) = rule.waitUntil(15_000) { count(description) > 0 }

    @Test
    fun theToolbarStaysAndBecomesAPageBar() {
        val container = rule.activity.appContainer
        runBlocking {
            container.settingsRepository.setBarStyle(BarStyle.BOTTOM_BAR)
            container.favouritesRepository.clear()
            container.favouritesRepository.add(Stop(1071, TransportMode.TRAIN, "Flinders Street Station", "Melbourne City", -37.8183, 144.9671))
        }

        // Tabs mode: the three tab icons and the Profile action.
        waitForDescription("Profile")
        assertTrue("tab icons are shown", count("Plan") > 0 && count("Map") > 0)
        assertEquals("no back button on a main tab", 0, count("Back"))
        snap("bottombar_01_tabs")

        // Open a stop: the same toolbar now has Back, the stop name and a favourite action.
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Flinders Street Station", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Flinders Street Station", substring = true)[0].performClick()
        waitForDescription("Back")
        waitForDescription("Remove from favourites")
        assertTrue("the stop name is the bar's title", rule.onAllNodesWithText("Flinders Street Station", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("the tab icons are replaced while on a page", 0, count("Plan"))
        snap("bottombar_02_stop_page")

        // Back returns the tabs.
        rule.onNodeWithContentDescription("Back").performClick()
        waitForDescription("Plan")
        assertEquals(0, count("Back"))
    }

    @Test
    fun profileHasSettingsAsItsAction() {
        runBlocking { rule.activity.appContainer.settingsRepository.setBarStyle(BarStyle.BOTTOM_BAR) }
        waitForDescription("Profile")
        rule.onNodeWithContentDescription("Profile").performClick()
        waitForDescription("Settings")
        waitForDescription("Back")
        snap("bottombar_03_profile")
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Navigation bar").fetchSemanticsNodes().isNotEmpty() }
        snap("bottombar_04_settings")
    }

    @Test
    fun classicStyleStillShowsTheTopPill() {
        runBlocking { rule.activity.appContainer.settingsRepository.setBarStyle(BarStyle.TOP_PILL) }
        waitForDescription("Profile")
        rule.onNodeWithContentDescription("Profile").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Trips this month").fetchSemanticsNodes().isNotEmpty() }
        // In the classic style the page carries its own back button and Settings FAB, and the tabs aren't shown.
        assertTrue(count("Back") > 0)
        assertEquals(0, count("Plan"))
        snap("bottombar_05_classic_profile")
    }
}
