package com.ssethhyy.journey.ui

import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.symbolResId
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every icon in [Sym] must have its Material Symbols Rounded drawable bundled and inflatable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SymbolsTest {
    @Test
    fun allSymbolsAreBundled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val missing = Sym.allResourceNames.filter { symbolResId(context, it) == 0 }
        assertTrue("Missing Material Symbols drawables: $missing", missing.isEmpty())
        Sym.allResourceNames.forEach { name ->
            assertNotNull(name, ContextCompat.getDrawable(context, symbolResId(context, name)))
        }
    }

    /**
     * Symbols are looked up by name, which the release resource shrinker cannot see. Without this keep rule
     * every symbol (and the island's alert icons) vanished from release builds.
     */
    @Test
    fun symbolsSurviveReleaseShrinking() {
        val keep = java.io.File("src/main/res/raw/keep.xml").readText()
        assertTrue("keep.xml must keep the symbol drawables", keep.contains("@drawable/rounded_*"))
        assertTrue("keep.xml must keep the myki art", keep.contains("@drawable/myki_card_art*"))
    }

    @Test
    fun noBaseMaterialIconsAreBundled() {
        val deps = java.io.File("build.gradle.kts").readText()
        assertTrue("the old Material Icons library is not a dependency", !deps.contains("material.icons"))
    }
}
