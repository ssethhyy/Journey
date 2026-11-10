package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.ThemeMode
import com.ssethhyy.journey.tracking.TrackingStatus
import com.ssethhyy.journey.tracking.TrackingTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** The in-app tracking banner, in light and dark, for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class TrackingBannerTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun clean() {
        rule.activity.appContainer.trackingManager.clear()
        runBlocking { rule.activity.appContainer.settingsRepository.setThemeMode(ThemeMode.SYSTEM) }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun bannerFitsTheAppInBothThemes() {
        val manager = rule.activity.appContainer.trackingManager
        manager.start(TrackingTarget.Service("1-1", TransportMode.TRAIN, 1071, "Flinders Street", "Frankston line", "Frankston"))
        manager.publish(TrackingStatus("Frankston line in 4 min", "Flinders Street · Platform 6 · to Frankston", "4 min", 0.4f, true, false, TransportMode.TRAIN, Instant.now()))
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Frankston line").fetchSemanticsNodes().isNotEmpty() }
        snap("tracking_banner_light")
        runBlocking { rule.activity.appContainer.settingsRepository.setThemeMode(ThemeMode.DARK) }
        rule.mainClock.advanceTimeBy(1_000)
        snap("tracking_banner_dark")
    }
}
