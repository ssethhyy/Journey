package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.planner.SearchRegion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRegionTest {
    @Test
    fun victoriaKeepsVictorianPlacesAndBorderTowns() {
        val vic = SearchRegion.VICTORIA
        assertTrue("Melbourne", vic.contains(-37.8136, 144.9631))
        assertTrue("Mildura", vic.contains(-34.1855, 142.1625))
        assertTrue("Albury, just over the border", vic.contains(-36.0737, 146.9135))
        assertFalse("Sydney", vic.contains(-33.8688, 151.2093))
        assertFalse("Adelaide", vic.contains(-34.9285, 138.6007))
        assertFalse("London", vic.contains(51.5072, -0.1276))
        assertFalse("Melbourne, Florida", vic.contains(28.0836, -80.6081))
    }

    @Test
    fun australiaAllowsInterstateButNotOverseas() {
        val aus = SearchRegion.AUSTRALIA
        assertTrue(aus.contains(-33.8688, 151.2093))
        assertTrue(aus.contains(-27.4698, 153.0251))
        assertTrue(aus.contains(-31.9523, 115.8613))
        assertFalse("Auckland", aus.contains(-36.8485, 174.7633))
        assertFalse("Bali", aus.contains(-8.4095, 115.1889))
    }
}
