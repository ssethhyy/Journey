package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.ssethhyy.journey.JourneyMapRequest
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.PlanLocation
import com.ssethhyy.journey.data.planner.TimeMode
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class JourneyMapAndFavouritesTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun clean() {
        rule.activity.appContainer.closeJourneyMap()
        rule.activity.appContainer.dismissFavouritePicker()
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun has(text: String, substring: Boolean = true) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aJourneyOpensOnTheMapWithItsLegs() {
        val container = rule.activity.appContainer
        val journey = runBlocking {
            container.journeyRepository.plan(
                PlanLocation("Home", -37.80, 144.98), PlanLocation("St Kilda", -37.86, 144.98), TimeMode.LEAVE_NOW, null,
            ).journeys.first()
        }
        container.showJourneyOnMap(JourneyMapRequest(journey.id, "Home", "St Kilda"))
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Map").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("Map").onFirst().performClick()
        rule.waitUntil(15_000) { has("To St Kilda") }
        snap("map_journey_overview")
        // Tap the first train leg: it opens with where to get on and off and how many stops.
        rule.onAllNodesWithText("Frankston line to Frankston").onFirst().performClick()
        rule.waitUntil(5_000) { has("Get on") && has("Get off") && has("stops ·") }
        assertTrue("whole-route button appears", has("Whole route"))
        snap("map_journey_leg")
    }

    @Test
    fun favouritingAStopWithSeveralLinesAsksWhichOnes() {
        val container = rule.activity.appContainer
        runBlocking { container.favouritesRepository.clear() }
        val stop = Stop(1071, TransportMode.TRAIN, "Flinders Street Station", "Melbourne City", -37.8183, 144.9671)
        container.toggleFavourite(stop)
        rule.waitUntil(15_000) { has("All lines and directions") }
        rule.waitUntil(15_000) { has("Only some") }
        snap("favourite_picker")
        // Pick one direction and save.
        rule.onAllNodesWithText("To ", substring = true).onFirst().performClick()
        // The sheet's title says the same thing; the button is the last match.
        rule.onAllNodesWithText("Add to favourites", substring = false).onLast().performClick()
        rule.waitUntil(5_000) { runBlocking { container.favouritesRepository.favourites.first().isNotEmpty() } }
        val saved = runBlocking { container.favouritesRepository.favourites.first() }
        assertEquals(1, saved.size)
        assertEquals(1, saved[0].lines.size)
        runBlocking { container.favouritesRepository.clear() }
    }
}
