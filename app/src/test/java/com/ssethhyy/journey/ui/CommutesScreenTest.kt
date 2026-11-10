package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.commute.Commute
import com.ssethhyy.journey.data.commute.CommuteEnd
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** For You shows a commute with when to leave, a backup if it is missed, and Track / Plan buttons. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class CommutesScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun clean() {
        runBlocking { rule.activity.appContainer.commuteRepository.clear() }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun has(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun showsTheNextTripAndABackup() {
        val container = rule.activity.appContainer
        runBlocking {
            container.commuteRepository.clear()
            container.commuteRepository.save(
                Commute(
                    id = "c1",
                    title = "To Work",
                    from = CommuteEnd("Current location", isCurrent = true),
                    to = CommuteEnd("Work", -37.8097, 144.9637, "Melbourne Central"),
                    manual = true,
                ),
            )
        }
        try {
            rule.waitUntil(20_000) { has("To Work") }
            rule.waitUntil(20_000) { has("Leave in", substring = true) || has("Leave now") }
        } catch (e: Throwable) {
            snap("commutes_failure")
            throw e
        }
        assertTrue("a backup is offered", has("If you miss it"))
        assertTrue(has("Plan"))
        snap("commutes_for_you")
    }

    @Test
    fun explainsHowCommutesAppearWhenThereAreNone() {
        runBlocking { rule.activity.appContainer.commuteRepository.clear() }
        rule.waitUntil(20_000) { has("No commutes yet") }
        snap("commutes_empty")
    }

    @Test
    fun forYouShowsTheClosestStops() {
        runBlocking {
            rule.activity.appContainer.commuteRepository.clear()
            rule.activity.appContainer.settingsRepository.setForYouNearby(true)
        }
        rule.waitUntil(20_000) { has("Near you") }
        rule.waitUntil(20_000) { has("Flinders Street", substring = true) }
        assertTrue("location help is offered without permission", has("Allow location to see stops near you"))
        snap("for_you_nearby")
    }
}
