package com.ssethhyy.journey.utils

import android.content.Context
import android.os.Vibrator
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticUtilTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vibrator get() = shadowOf(context.getSystemService(Vibrator::class.java))

    @After
    fun reset() {
        HapticUtil.isAppHapticsEnabled.value = true
    }

    @Test
    fun successAndAttentionHapticsVibrate() {
        HapticUtil.performSuccessHaptic(context)
        HapticUtil.performAttentionHaptic(context)
        HapticUtil.performCustomHaptic(context, 0.3f)
        // Robolectric's default vibrator has no primitives, so these take the waveform / one-shot fallbacks.
        assertTrue(vibrator.isVibrating)
    }

    @Test
    fun nothingVibratesWhenHapticsAreOff() {
        HapticUtil.isAppHapticsEnabled.value = false
        HapticUtil.performSuccessHaptic(context)
        HapticUtil.performAttentionHaptic(context)
        HapticUtil.performToggleHaptic(View(context), true)
        assertFalse(vibrator.isVibrating)
    }

    @Test
    fun toggleHapticDoesNotThrowOnAnyDirection() {
        val view = View(context)
        HapticUtil.performToggleHaptic(view, true)
        HapticUtil.performToggleHaptic(view, false)
        HapticUtil.performRefreshReadyHaptic(view)
    }
}
