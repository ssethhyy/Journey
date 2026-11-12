package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.StopMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopMatchTest {
    private val flinders = Stop(1071, TransportMode.TRAIN, "Flinders Street Station", null, -37.8183, 144.9671)
    private val tram = Stop(2000, TransportMode.TRAM, "Flinders St/Swanston St #13", null, -37.8178, 144.9668)

    @Test
    fun matchesByNameIgnoringStationWords() {
        assertEquals(2, StopMatch.nameScore("Flinders Street", "Flinders Street Station"))
        assertEquals(flinders, StopMatch.best(listOf(tram, flinders), "Flinders Street", -37.8180, 144.9670))
    }

    @Test
    fun fallsBackToTheNearestStopOnlyWhenItIsClose() {
        assertEquals(tram, StopMatch.best(listOf(tram), "Somewhere else", -37.8178, 144.9669))
        assertNull(StopMatch.best(listOf(tram), "Somewhere else", -37.83, 144.98))
    }
}
