package com.ssethhyy.journey.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullDownToCloseTest {
    @Test
    fun goesBackWhenPulledPastTheThresholdOrFlicked() {
        assertTrue(PullDownToCloseLogic.shouldClose(80f, 0f, 60f))
        assertTrue(PullDownToCloseLogic.shouldClose(10f, 2_000f, 60f))
        assertFalse(PullDownToCloseLogic.shouldClose(30f, 300f, 60f))
        assertFalse(PullDownToCloseLogic.shouldClose(0f, 3_000f, 60f))
    }
}
