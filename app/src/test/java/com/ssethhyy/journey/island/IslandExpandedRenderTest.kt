package com.ssethhyy.journey.island

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.sameerasw.island.sdk.HostInfo
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.tracking.TrackingStatus
import com.ssethhyy.journey.tracking.TrackingTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** Renders the island's expanded views at a phone island width to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class IslandExpandedRenderTest {
    @get:Rule val rule = createComposeRule()

    private val info = HostInfo(hostPackage = "com.sameerasw.essentials", accent = 0xFFA9C7FF.toInt(), density = 3f, cornerPx = 132, cameraClearancePx = 96, expandedPaddingPx = 54)

    private fun disruption(id: Long, type: String, title: String) = Disruption(
        id, title, "", null, "Current", type, Instant.now(), null, null, emptyList(), emptyList(), TransportMode.TRAIN,
    )

    private fun render(name: String, itemKey: String, state: ExpandedState) {
        rule.setContent {
            Box(Modifier.background(Color(0xFF6B7F5A)).padding(12.dp).width(380.dp)) {
                IslandExpandedContent(itemKey = itemKey, state = state, info = info, host = null, onStopTracking = {})
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun trackingExpanded() = render(
        "island_expanded_tracking",
        JourneyIsland.ITEM_TRACKING,
        ExpandedState(
            target = TrackingTarget.Service("1", TransportMode.TRAIN, 1, "Westgarth", "Hurstbridge line", "Flinders Street"),
            status = TrackingStatus("Hurstbridge line in 4 min", "Westgarth · Platform 1 · to Flinders Street · 1 min late", "4 min", 0.6f, true, false, TransportMode.TRAIN, Instant.now()),
        ),
    )

    @Test
    fun disruptionsExpanded() = render(
        "island_expanded_disruptions",
        JourneyIsland.ITEM_DISRUPTIONS,
        ExpandedState(
            disruptions = listOf(
                disruption(1, "Major Delays", "Hurstbridge line: Major delays due to a police request near Clifton Hill"),
                disruption(2, "Minor Delays", "Route 86: Minor delays due to an earlier collision"),
                disruption(3, "Planned Works", "Buses replace trains between Clifton Hill and Eltham from 8:30pm"),
                disruption(4, "Service Information", "Westgarth Station: Lift out of service"),
                disruption(5, "Planned Works", "Car park closures"),
            ),
            scopeLabel = "On your stops",
        ),
    )
}
