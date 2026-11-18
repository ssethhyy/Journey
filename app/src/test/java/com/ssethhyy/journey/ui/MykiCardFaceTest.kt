package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ssethhyy.journey.data.repository.ThemeMode
import com.ssethhyy.journey.ui.screens.myki.MykiCardFace
import com.ssethhyy.journey.ui.theme.JourneyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The myki card face from Open Transit's artwork, in the real card's colours (no wallpaper colours). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class MykiCardFaceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun drawsTheCardInMykiColours() {
        rule.setContent {
            JourneyTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    MykiCardFace(number = "308425123456781", fareLabel = "Concession")
                }
            }
        }
        rule.onNodeWithText("3 08425 1234 5678 1").assertExists()
        rule.onNodeWithText("Concession").assertExists()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("myki_card_face.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
