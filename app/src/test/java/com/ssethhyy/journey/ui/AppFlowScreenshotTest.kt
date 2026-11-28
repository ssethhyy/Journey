package com.ssethhyy.journey.ui

import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.JourneyApp
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.api.PtvCredentials
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.PlaceIcon
import com.ssethhyy.journey.data.repository.SavedPlace
import com.ssethhyy.journey.data.repository.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Constructor

/** Application that points the PTV client at a local fake server. */
class TestJourneyApp : JourneyApp() {
    override fun createContainer(): AppContainer =
        AppContainer(
            this,
            ptvBaseUrl = baseUrl,
            credentialsOverride = PtvCredentials("1234567", "test-key"),
            mapRenderingEnabled = false,
            plannerBaseUrl = baseUrl,
            crowdingBaseUrl = baseUrl,
            githubBaseUrl = baseUrl,
            openDataKeyOverride = "test-open-data-key",
        )

    companion object {
        val fake = FakePtvServer()
        val baseUrl: String by lazy { fake.start() }
    }
}

/**
 * Drives the real app end-to-end (Robolectric + native graphics) against [FakePtvServer] and
 * saves screenshots of every main screen to app/build/screenshots for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class AppFlowScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.After
    fun resetForYou() {
        // The For You layout outlives a test within the test JVM.
        runBlocking { rule.activity.appContainer.forYouRepository.reset() }
    }

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 15_000) {
        try {
            rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            snap("failure_waiting_for_${text.filter { it.isLetterOrDigit() }}")
            throw e
        }
    }

    /**
     * Neighbouring home tabs stay composed off-screen, so a label can exist more than once in the
     * tree. Click the copy that is actually on screen.
     */
    private fun clickVisibleText(text: String) {
        val width = rule.onRoot().fetchSemanticsNode().size.width
        val nodes = rule.onAllNodesWithText(text)
        val index = nodes.fetchSemanticsNodes().indexOfFirst { node ->
            val bounds = node.boundsInRoot
            // Unplaced off-screen pages report an empty box at the origin.
            bounds.width > 0f && bounds.left >= 0f && bounds.right <= width
        }
        check(index >= 0) { "No on-screen node with text $text" }
        nodes[index].performClick()
    }

    private fun enumConstant(type: Class<*>, name: String): Any? {
        val raw = type.enumConstants ?: return null
        return raw.firstOrNull { (it as Enum<*>).name == name } ?: raw.firstOrNull()
    }

    @Suppress("SpreadOperator")
    private fun construct(ctor: Constructor<*>, vararg args: Any?): SavedPlace {
        return ctor.newInstance(*args) as SavedPlace
    }

    private fun savedPlace(
        id: String,
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        kindName: String,
        iconName: String,
    ): SavedPlace {
        val ctor = SavedPlace::class.java.declaredConstructors.first()
        return when (ctor.parameterCount) {
            7 -> construct(
                ctor,
                id,
                enumConstant(ctor.parameterTypes[1], kindName),
                name,
                address,
                latitude,
                longitude,
                enumConstant(ctor.parameterTypes[6], iconName),
            )
            6 -> construct(
                ctor,
                id,
                name,
                address,
                latitude,
                longitude,
                enumConstant(ctor.parameterTypes[5], iconName),
            )
            else -> error("Unsupported SavedPlace constructor with ${ctor.parameterCount} parameters")
        }
    }

    private fun seedFavourites(container: AppContainer) = runBlocking {
        // This walk-through is about favourites; the Near you section (tested elsewhere) would push them off screen.
        container.settingsRepository.setForYouNearby(false)
        container.forYouRepository.update {
            it.remove(com.ssethhyy.journey.data.foryou.ForYouWidget.NEAR_YOU)
                .copy(greeting = it.greeting.copy(collapses = false))
                .copy(disruptions = it.disruptions.copy(scope = com.ssethhyy.journey.data.foryou.DisruptionScope.NETWORK))
        }
        container.favouritesRepository.clear()
        container.favouritesRepository.add(Stop(1071, TransportMode.TRAIN, "Flinders Street Station", "Melbourne City", -37.8183, 144.9671))
        container.favouritesRepository.add(Stop(2258, TransportMode.TRAM, "Bourke St/Spencer St #1", "Melbourne City", -37.8166, 144.9548))
        container.favouritesRepository.add(Stop(18560, TransportMode.BUS, "Fitzroy St/Grey St", "St Kilda", -37.8597, 144.9789))
        container.favouritesRepository.rename("0:1071", "Commute")
    }

    private fun seedPlaces(container: AppContainer) = runBlocking {
        container.placesRepository.save(
            SavedPlace(
                id = "home",
                name = "Home",
                address = "12 Smith Street, Fitzroy",
                latitude = -37.80,
                longitude = 144.98,
                icon = PlaceIcon.HOME,
            ),
        )
        container.placesRepository.save(
            SavedPlace(
                id = "work",
                name = "Work",
                address = "Caulfield",
                latitude = -37.877,
                longitude = 145.042,
                icon = PlaceIcon.WORK,
            ),
        )
        container.placesRepository.save(
            SavedPlace(
                id = "gym",
                name = "Gym",
                address = "St Kilda",
                latitude = -37.86,
                longitude = 144.98,
                icon = PlaceIcon.FITNESS,
            ),
        )
    }

    @Test
    fun walkThroughMainScreens() {
        val container = rule.activity.appContainer
        runBlocking {
            container.settingsRepository.setBarStyle(com.ssethhyy.journey.data.repository.BarStyle.BOTTOM_BAR)
            container.settingsRepository.setThemeMode(ThemeMode.LIGHT)
            // Robolectric has no real wallpaper palette, so show the app's own fallback scheme.
            container.settingsRepository.setDynamicColor(false)
        }

        // Empty favourites state.
        runBlocking { container.favouritesRepository.clear() }
        waitForText("No favourites yet")
        snap("01_favourites_empty")

        // Favourites with live departures and saved places.
        seedFavourites(container)
        seedPlaces(container)
        waitForText("Gym")
        waitForText("Commute")
        waitForText("Carrum")
        rule.onNodeWithText("Commute").assertIsDisplayed()
        snap("02_favourites")

        // Stop details, in the map's stop panel.
        rule.onNodeWithText("Commute").performClick()
        waitForText("Departures")
        snap("04_stop_detail")

        // Trip / stopping pattern from a departure (the one in the panel, not the hidden Favourites tab's).
        rule.onAllNodesWithText("Carrum").fetchSemanticsNodes().indexOfFirst { node -> node.layoutInfo.isPlaced && node.boundsInRoot.height > 0f && node.boundsInRoot.top < rule.onRoot().fetchSemanticsNode().size.height }.let { i -> rule.onAllNodesWithText("Carrum")[i.coerceAtLeast(0)].performClick() }
        waitForText("Stopping pattern")
        // Crowding from the Open Data feed: the whole train and each carriage.
        waitForText("Standing room only")
        waitForText("By carriage")
        waitForText("Car 3 is fullest")
        snap("05_trip")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        // The stop opened on the map, so Back leaves you there: go back to For You.
        rule.onAllNodesWithContentDescription("For You").onFirst().performClick()
        rule.waitForIdle()

        // Disruptions from the Disruptions widget: expand it, then see them all.
        rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .onFirst().performScrollToNode(androidx.compose.ui.test.hasText("major disruption", substring = true))
        rule.onNodeWithText("major disruption", substring = true).performClick()
        waitForText("See all", substring = true)
        rule.onNodeWithText("See all", substring = true).performClick()
        waitForText("Route 96: Major delays", substring = true)
        snap("06_disruptions")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .onFirst().performScrollToIndex(0)
        rule.waitForIdle()

        // Plan tab: saved places and recent journeys. Scrolling down the page tucked the toolbar away; a small pull
        // brings it back, as it would for anyone.
        rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .onFirst().performTouchInput { swipeDown() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("Plan")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("Plan")).performClick()
        waitForText("Plan a journey")
        snap("07_plan")

        // Tap Home to plan a journey from the current location.
        clickVisibleText("Home")
        waitForText("Departs in", substring = true)
        snap("08_plan_results")

        // A journey opens on the map: when to leave and its steps in the panel.
        rule.onAllNodesWithText("Departs in", substring = true).onFirst().performClick()
        waitForText("Steps")
        snap("09_journey_map")
        // Pulled up, the panel becomes the journey's page.
        rule.onNodeWithText("From ", substring = true).performTouchInput {
            swipe(center, androidx.compose.ui.geometry.Offset(center.x, center.y - 3_000f), durationMillis = 400)
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Log trip").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        snap("09_journey_detail")

        // Log the journey on the default myki (no myki yet, then one).
        runBlocking { container.mykiRepository.clear() }
        rule.onNodeWithContentDescription("Log trip").performClick()
        waitForText("Add a myki first", substring = true)
        snap("09b_log_journey_no_myki")
        // Adding mykis while the sheet is open turns it into the logging form.
        runBlocking {
            container.mykiRepository.add(
                com.ssethhyy.journey.data.myki.MykiCard("m1", "308425123456780", "Work", balanceCents = 2_616, addedMillis = 1),
            )
            container.mykiRepository.add(
                com.ssethhyy.journey.data.myki.MykiCard("m2", "308425000011112".let { it.dropLast(1) + com.ssethhyy.journey.data.myki.MykiNumber.luhnDigit(it.dropLast(1)) }, "Spare", fareType = com.ssethhyy.journey.data.myki.MykiFareType.CONCESSION, balanceCents = 840, addedMillis = 2),
            )
        }
        waitForText("Deduct from")
        waitForText("Work · Default")
        waitForText("Log trip · ", substring = true)
        snap("09c_log_journey_sheet")
        rule.onNodeWithText("Log trip · ", substring = true).performClick()
        rule.waitUntil(15_000) { runBlocking { container.mykiRepository.cards.first() }.first { it.id == "m1" }.balanceCents != 2_616L }
        val logged = runBlocking { container.mykiRepository.cards.first() }.first { it.id == "m1" }
        org.junit.Assert.assertEquals("one trip entry on the default myki", 1, logged.activity.count { it.kind == com.ssethhyy.journey.data.myki.MykiActivityKind.TRIP })
        org.junit.Assert.assertTrue("fare was deducted", logged.balanceCents!! < 2_616L)
        org.junit.Assert.assertEquals("other myki untouched", 840L, runBlocking { container.mykiRepository.cards.first() }.first { it.id == "m2" }.balanceCents)
        runBlocking { container.mykiRepository.clear() }
        // Back to the journey's panel on the map, then Back again closes it and returns to Plan.
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        waitForText("Departs in", substring = true)

        // Profile from the For You FAB, then Settings from the Profile FAB.
        runBlocking { container.settingsRepository.setUserName("Alex") }
        rule.onNode(hasContentDescription("For You")).performClick()
        waitForText("Commute")
        rule.onNodeWithContentDescription("Profile").performClick()
        waitForText("Trips this month")
        waitForText("No mykis yet")
        snap("10a_profile")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Appearance")
        snap("10_settings")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()

        // Map tab with the floating Nearby panel (no Mapbox token in tests -> guidance card behind).
        rule.onNode(hasContentDescription("Map")).performClick()
        waitForText("Map needs a Mapbox token")
        waitForText("stops near the city", substring = true)
        waitForText("min walk", substring = true)
        rule.onNodeWithContentDescription("Search stops and lines").assertIsDisplayed()
        snap("11_map_nearby")

        // Search.
        rule.onNodeWithContentDescription("Search stops and lines").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("flin")
        waitForText("Lines & routes")
        snap("12_search")
        rule.onAllNodesWithText("Frankston").onFirst().performClick()
        waitForText("Stops")
        snap("13_route_detail")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()

        // Dark theme.
        runBlocking { container.settingsRepository.setThemeMode(ThemeMode.DARK) }
        snap("14_map_nearby_dark")
        rule.onNode(hasContentDescription("Plan")).performClick()
        waitForText("Departs in", substring = true)
        snap("15_plan_results_dark")
        rule.onNode(hasContentDescription("For You")).performClick()
        waitForText("Commute")
        snap("16_favourites_dark")

        // The "Add" button on the first favourites header opens the add sheet (last: the sheet is its own window).
        rule.onNode(hasContentDescription("For You")).performClick()
        waitForText("Commute")
        clickVisibleText("Add")
        waitForText("Add favourite")
        waitForText("Southern Cross Station")
        snap("03_add_favourite_sheet_dark")
    }
}
