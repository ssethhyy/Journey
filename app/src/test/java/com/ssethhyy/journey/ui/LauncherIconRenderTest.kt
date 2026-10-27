package com.ssethhyy.journey.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.ssethhyy.journey.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the adaptive launcher icon (circle mask, as on Pixel) and its layers to build/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LauncherIconRenderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun layer(id: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val d = ContextCompat.getDrawable(context, id)
        assertNotNull("drawable $id failed to inflate", d)
        d!!
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    @Test
    fun renderIcon() {
        val size = 432 // 108dp at 4x
        val bg = layer(R.drawable.ic_launcher_background, size)
        val fg = layer(R.drawable.ic_launcher_foreground, size)
        val mono = layer(R.drawable.ic_launcher_monochrome, size)

        // The launcher shows the centre 72/108 of the layers, masked to a circle.
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val visible = size * 72f / 108f
        val path = Path().apply { addCircle(size / 2f, size / 2f, visible / 2f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bg, 0f, 0f, paint)
        canvas.drawBitmap(fg, 0f, 0f, paint)
        canvas.restore()

        // Themed (monochrome) preview: tint the mask on a tonal background.
        val themed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(themed).apply {
            save(); clipPath(path); drawColor(0xFF3A3530.toInt()); restore()
            val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = android.graphics.PorterDuffColorFilter(0xFFE9DFD6.toInt(), PorterDuff.Mode.SRC_IN) }
            drawBitmap(mono, 0f, 0f, tint)
        }

        fun save(name: String, b: Bitmap) = File(out, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        save("icon_launcher.png", icon)
        save("icon_themed.png", themed)
        save("icon_layer_background.png", bg)
        save("icon_layer_foreground.png", fg)
    }
}
