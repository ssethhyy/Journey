package com.ssethhyy.journey.data

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.ssethhyy.journey.widget.WidgetRenderer
import com.ssethhyy.journey.widget.WidgetRow
import com.ssethhyy.journey.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetRenderTest {
    private val now = Instant.parse("2026-10-29T08:00:00Z")

    private fun rows(n: Int) = listOf(
        WidgetRow(1, 0, "Flinders Street Station", "400m away", now.epochSecond + 130, true, "City"),
        WidgetRow(2, 1, "Bourke St/Spencer St #1", "1.2km away", now.epochSecond + 400, true, "Docklands"),
        WidgetRow(3, 2, "Southern Cross Station (Collins St) with a very long name", "5.2km away", now.epochSecond + 1500, false, "Elsternwick via Glen Huntly Road"),
        WidgetRow(4, 3, "Frankston Station", "30km away", now.epochSecond + 3700, true, "Frankston"),
        WidgetRow(5, 0, "Richmond Station", "2km away", now.epochSecond + 60, true, "Belgrave"),
        WidgetRow(6, 2, "Caulfield Station", "3km away", null),
        WidgetRow(7, 0, "Hawksburn Station", "3.1km away", now.epochSecond + 900, true, "City"),
    ).take(n)

    private fun save(name: String, source: Bitmap) {
        // The widget draws on a dark background supplied by the launcher host; composite it for the screenshot.
        val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply { drawColor(WidgetRenderer.BACKGROUND); drawBitmap(source, 0f, 0f, null) }
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rendersLikeTheDesign() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = WidgetSnapshot(rows(7), now.epochSecond - 30)
        val large = WidgetRenderer.render(context, snapshot, now, 1047, 860)
        save("widget_design_size", large.bitmap)
        assertEquals(7, large.rows.size)
        val phone = WidgetRenderer.render(context, snapshot, now, 1080, 780)
        save("widget_phone_size", phone.bitmap)
        assertTrue(phone.rows.isNotEmpty())
        assertTrue("rows never overlap", phone.rows.zipWithNext().all { (a, b) -> a.bottom <= b.top })
        val empty = WidgetRenderer.render(context, WidgetSnapshot(), now, 1080, 500)
        save("widget_empty", empty.bitmap)
    }
}
