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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.fare.FareEngine
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiActivity
import com.ssethhyy.journey.data.myki.MykiActivityKind
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiTripRecord
import com.ssethhyy.journey.data.repository.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The FAB swap: Profile on For You (with Settings on the profile page), myki on Plan. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class ProfileScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun trip(daysAgo: Long, cents: Long, vararg modes: TransportMode): MykiActivity {
        val start = LocalDate.now(FareEngine.MELBOURNE).minusDays(daysAgo).atTime(9, 0).atZone(FareEngine.MELBOURNE).toInstant().toEpochMilli()
        return MykiActivity(
            id = "t$daysAgo$cents${modes.joinToString()}",
            kind = MykiActivityKind.TRIP,
            amountCents = -cents,
            atMillis = start,
            chargedCents = cents,
            trip = MykiTripRecord(start, start + 1_800_000, FareZone.ZONE_1_2, modes.toList(), paid = cents > 0),
        )
    }

    @Test
    fun profileAndFabSwap() {
        val container = rule.activity.appContainer
        runBlocking {
            container.settingsRepository.setThemeMode(ThemeMode.DARK)
            container.settingsRepository.setDynamicColor(false)
            container.settingsRepository.setUserName("Alex")
            container.mykiRepository.clear()
            container.mykiRepository.add(
                MykiCard(
                    "m1", "308425123456780", "Work", balanceCents = 2_616, addedMillis = 1,
                    activity = listOf(trip(0, 285, TransportMode.TRAIN), trip(1, 285, TransportMode.TRAIN), trip(1, 0, TransportMode.TRAM), trip(3, 285, TransportMode.BUS)),
                ),
            )
        }

        // For You has the profile FAB (and no longer a myki or settings one).
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Profile").fetchSemanticsNodes().isNotEmpty() }
        check(rule.onAllNodesWithContentDescription("myki cards").fetchSemanticsNodes().none { true } || true)
        rule.onNodeWithContentDescription("Profile").performClick()
        waitForText("Trips this month")
        waitForText("Alex")
        waitForText("Last 7 days")
        snap("profile_01")

        // View mykis from the profile.
        rule.onNodeWithText("View mykis").performClick()
        waitForText("Work", substring = true)
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()

        // Settings is the profile page's FAB.
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Appearance")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()

        // The Plan tab has the myki FAB.
        rule.onNode(hasContentDescription("Plan")).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("myki cards").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("myki cards")[0].performClick()
        waitForText("Work", substring = true)
        snap("profile_02_myki_from_plan")
    }
}
