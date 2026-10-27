package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.MykiLedger
import com.ssethhyy.journey.data.repository.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The myki wallet from the Plan FAB: empty state, adding a card, the card screen and the stack. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class MykiScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun snap(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun walletFlow() {
        val container = rule.activity.appContainer
        runBlocking {
            container.settingsRepository.setThemeMode(ThemeMode.DARK)
            container.settingsRepository.setDynamicColor(false)
            container.mykiRepository.clear()
            container.favouritesRepository.clear()
        }

        // The myki FAB lives on the Plan tab.
        rule.onNode(hasContentDescription("Plan")).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("myki cards").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("myki cards")[0].performClick()
        waitForText("No mykis added yet")
        snap("myki_01_empty")

        // Add one by number.
        rule.onNodeWithText("Add myki").performClick()
        waitForText("Card number")
        rule.onAllNodesWithText("Card number")[0].performTextInput("308425123456780")
        rule.onNode(hasSetTextAction() and hasText("Name (optional)")).performTextInput("Work")
        rule.onNode(hasSetTextAction() and hasText("Current balance (optional)")).performTextInput("26.16")
        waitForText("Looks good")
        snap("myki_02_add_sheet")
        rule.onAllNodesWithText("Add myki").fetchSemanticsNodes().let { nodes -> rule.onAllNodesWithText("Add myki")[nodes.lastIndex].performClick() }

        waitForText("3 08425 1234 5678 0")
        waitForText("$26.16")
        snap("myki_03_card")
        val saved = runBlocking { container.mykiRepository.cards.first() }
        assertEquals("308425123456780", saved.single().number)
        assertEquals(2_616L, saved.single().balanceCents)

        // Record a top up.
        rule.onNodeWithText("Add money or pass").performClick()
        waitForText("$20")
        rule.onNodeWithText("$20").performClick()
        rule.onNodeWithText("Record $20.00 top up").performClick()
        waitForText("$46.16")
        snap("myki_04_card_topped_up")

        // Log a custom deduction from the card page.
        rule.onNodeWithText("Log trip", useUnmergedTree = true).performClick()
        waitForText("Log a trip")
        snap("myki_06_log_trip_sheet")
        rule.onNodeWithText("Custom amount").performClick()
        rule.onNode(hasSetTextAction() and hasText("Amount to deduct")).performTextInput("3.50")
        rule.onNodeWithText("Deduct $3.50").performClick()
        waitForText("$42.66")
        snap("myki_07_after_deduction")
        val after = runBlocking { container.mykiRepository.cards.first() }.single()
        assertEquals(4_266L, after.balanceCents)
        assertEquals(com.ssethhyy.journey.data.myki.MykiActivityKind.CHARGE, after.activity.first().kind)

        // A second and third card make a stack.
        runBlocking {
            container.mykiRepository.add(MykiCard(MykiLedger.newId(), "308425876543210".let { it.dropLast(1) + com.ssethhyy.journey.data.myki.MykiNumber.luhnDigit(it.dropLast(1)) }, "Concession", MykiFareType.CONCESSION, balanceCents = 840))
            container.mykiRepository.add(MykiCard(MykiLedger.newId(), "308425000011112".let { it.dropLast(1) + com.ssethhyy.journey.data.myki.MykiNumber.luhnDigit(it.dropLast(1)) }, "Spare"))
        }
        rule.activity.onBackPressedDispatcher.onBackPressed()
        waitForText("3 cards")
        snap("myki_05_stack")
    }
}
