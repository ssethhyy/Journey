package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.update.AvailableUpdate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = TestJourneyApp::class)
class UpdateSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun close() = rule.activity.appContainer.updateManager.dismissPrompt()

    @Test
    fun showsTheReleaseNotesAndInstallButton() {
        val notes = File("../docs/releases/v1.2.0.md").takeIf { it.exists() }?.readText() ?: "# Journey 1.2.0\n\n### Added\n- Things"
        rule.activity.appContainer.updateManager.showPrompt(
            AvailableUpdate("1.3.0", "Journey 1.3.0", notes, false, Instant.parse("2026-11-13T00:00:00Z"), "https://example.com/a.apk", "a.apk", 71_000_000, "https://github.com"),
        )
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Install update").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Full-screen alert to get off", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        val bitmap = rule.onAllNodes(isDialog()).onFirst().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("update_sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
