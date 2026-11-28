package com.ssethhyy.journey.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullDownToCloseTest {
    @Test
    fun closesWhenPulledFarEnoughOrFlicked() {
        assertTrue(PullDownToCloseLogic.shouldClose(400f, 0f, 300f))
        assertTrue(PullDownToCloseLogic.shouldClose(60f, 2_000f, 300f))
        assertFalse(PullDownToCloseLogic.shouldClose(200f, 300f, 300f))
        assertFalse(PullDownToCloseLogic.shouldClose(0f, 3_000f, 300f))
    }
}
