package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteLine
import com.ssethhyy.journey.data.repository.FavouritesCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitFavouritesTest {
    private val flinders = Stop(1071, TransportMode.TRAIN, "Flinders Street Station", "Melbourne", -37.8, 144.9)
    private val other = Stop(1020, TransportMode.TRAIN, "Clifton Hill Station", "Clifton Hill", -37.7, 144.9)
    private val toCity = FavouriteLine(6, "Frankston", 1, "City")
    private val toFrankston = FavouriteLine(6, "Frankston", 2, "Frankston")

    @Test
    fun eachDirectionBecomesItsOwnFavourite() {
        val list = FavouritesCodec.addStop(emptyList(), flinders, listOf(toCity, toFrankston), separately = true, now = 0)
        assertEquals(2, list.size)
        assertEquals(listOf(listOf(toCity), listOf(toFrankston)), list.map { it.lines })
        assertEquals(2, list.map { it.key }.toSet().size)
        assertEquals(setOf(flinders.key), list.map { it.stopKey }.toSet())
    }

    @Test
    fun togetherKeepsOneFavouriteAndReplacesSplitOnesInPlace() {
        val start = FavouritesCodec.addStop(emptyList(), flinders, listOf(toCity, toFrankston), separately = true, now = 0)
            .let { FavouritesCodec.addStop(it, other, emptyList(), separately = false, now = 0) }
        val merged = FavouritesCodec.addStop(start, flinders, listOf(toCity, toFrankston), separately = false, now = 0)
        assertEquals(listOf(flinders.key, other.key), merged.map { it.key })
        assertEquals(2, merged.first().lines.size)
    }

    @Test
    fun oldFavouritesKeepTheirKeys() {
        val single = FavouritesCodec.addStop(emptyList(), flinders, emptyList(), separately = true, now = 0).single()
        assertEquals(flinders.key, single.key)
    }
}
