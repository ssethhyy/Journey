package com.ssethhyy.journey.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The For You list must be able to scroll all the way back up, so the big greeting returns to where it started. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class ForYouScrollTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The top bar pill is the only heading titled "For You" (the navbar tab is not a heading). */
    private fun pillCount(): Int = rule.onAllNodes(
        androidx.compose.ui.test.hasText("For You") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
    ).fetchSemanticsNodes().size

    private fun titleTop(): Float =
        rule.onAllNodesWithText("Good", substring = true).onFirst().fetchSemanticsNode().boundsInRoot.top

    @Test
    fun scrollsFullyBackToTheTopAndTheHeaderExpands() {
        val container = rule.activity.appContainer
        runBlocking {
            container.favouritesRepository.clear()
            container.favouritesRepository.add(Stop(1071, TransportMode.TRAIN, "Flinders Street Station", "Melbourne City", -37.8183, 144.9671))
            container.favouritesRepository.add(Stop(2258, TransportMode.TRAM, "Bourke St/Spencer St #1", "Melbourne City", -37.8166, 144.9548))
            container.favouritesRepository.add(Stop(18560, TransportMode.BUS, "Fitzroy St/Grey St", "St Kilda", -37.8597, 144.9789))
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Flinders Street Station", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val expandedTop = titleTop()
        val pillsAtRest = pillCount()
        snap("topbar_for_you_rest")

        val list = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst()
        repeat(3) { list.performTouchInput { swipeUp() } }
        rule.waitForIdle()
        rule.waitUntil(5_000) { pillCount() > pillsAtRest }
        snap("topbar_for_you_scrolled")
        repeat(6) { list.performTouchInput { swipeDown() } }
        rule.waitForIdle()

        assertEquals("greeting is back where it started", expandedTop, titleTop(), 2f)
        // And the pill goes away again once the greeting is back.
        rule.waitUntil(5_000) { pillCount() <= pillsAtRest }
    }
}
