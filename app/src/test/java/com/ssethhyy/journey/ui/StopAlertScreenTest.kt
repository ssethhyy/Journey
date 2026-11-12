package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.tracking.StopAlertScreen
import com.ssethhyy.journey.ui.theme.JourneyTheme
import org.junit.Assert.assertTrue
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
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class StopAlertScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun showsTheStopAndDismisses() {
        var dismissed = false
        rule.setContent {
            JourneyTheme(dynamicColor = false) {
                StopAlertScreen(
                    stopName = "Macleod Station",
                    instruction = "Get ready, your stop is next",
                    detail = "Arriving 6:50 pm · Platform 2",
                    route = "Hurstbridge line",
                    mode = TransportMode.TRAIN,
                    arrival = Instant.now().plusSeconds(90),
                    onDismiss = { dismissed = true },
                    onOpen = {},
                )
            }
        }
        rule.onNodeWithText("Macleod Station").assertExists()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("stop_alert.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Got it").performClick()
        assertTrue(dismissed)
    }
}
