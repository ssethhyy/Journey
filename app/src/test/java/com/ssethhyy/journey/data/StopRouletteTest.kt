package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.game.StopRoulette
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StopRouletteTest {
    private val here = -37.8183 to 144.9671 // Flinders Street
    private fun stop(id: Int, name: String, lat: Double, lon: Double) = Stop(id, TransportMode.TRAIN, name, null, lat, lon)

    private val stops = listOf(
        stop(1, "Flinders Street", -37.8183, 144.9671), // 0 km
        stop(2, "Richmond", -37.8240, 144.9900), // ~2 km
        stop(3, "Caulfield", -37.8774, 145.0426), // ~9 km
        stop(4, "Frankston", -38.1428, 145.1260), // ~39 km
        stop(2, "Richmond", -37.8240, 144.9900), // duplicate
    )

    @Test
    fun radiusAndWalkingDistanceAreRespected() {
        val within10 = StopRoulette.eligible(stops, here.first, here.second, 10_000, skipNearby = true).map { it.first.name }
        assertEquals(listOf("Richmond", "Caulfield"), within10)
        val includingNearby = StopRoulette.eligible(stops, here.first, here.second, 10_000, skipNearby = false).map { it.first.name }
        assertEquals(listOf("Flinders Street", "Richmond", "Caulfield"), includingNearby)
    }

    @Test
    fun rollPicksACandidateAndAChallengeWhenAsked() {
        val candidates = StopRoulette.eligible(stops, here.first, here.second, 50_000, skipNearby = true)
        repeat(20) { seed ->
            val result = StopRoulette.roll(candidates, withChallenge = true, random = Random(seed))!!
            assertTrue(result.stop.name in setOf("Richmond", "Caulfield", "Frankston"))
            assertNotNull(result.challenge)
            assertTrue(result.distanceMeters > StopRoulette.NEARBY_METERS)
        }
        assertNull(StopRoulette.roll(candidates, withChallenge = false)!!.challenge)
        assertNull(StopRoulette.roll(emptyList(), withChallenge = true))
    }
}
