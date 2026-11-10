package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Stop roulette on the Map tab rolls a stop and offers to plan the trip there. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class StopRouletteScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun restore() {
        runBlocking {
            val settings = rule.activity.appContainer.settingsRepository
            settings.setGameSkipNearby(true)
            settings.setGameRollAnimation(true)
        }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun hasText(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasDescription(text: String) = rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun rollsAStopOnTheMap() {
        runBlocking {
            val settings = rule.activity.appContainer.settingsRepository
            settings.setGameEnabled(true)
            // The fake network's stations are all in the city, so let the roll land close by.
            settings.setGameSkipNearby(false)
            settings.setGameRollAnimation(false)
        }
        rule.waitUntil(15_000) { hasDescription("Map") }
        rule.onAllNodesWithContentDescription("Map").onFirst().performClick()
        rule.waitUntil(15_000) { hasDescription("Roll a random stop") }
        rule.onAllNodesWithContentDescription("Roll a random stop").onFirst().performClick()
        rule.waitUntil(20_000) { hasText("Your destination") }
        rule.waitUntil(5_000) { hasText("Plan trip") && hasText("Roll again") && hasText("Challenge") }
        snap("map_stop_roulette")
    }
}
