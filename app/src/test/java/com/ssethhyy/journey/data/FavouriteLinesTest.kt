package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteLine
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.FavouritesCodec
import com.ssethhyy.journey.ui.screens.favourites.FavouriteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FavouriteLinesTest {
    private val frankston = Route(6, TransportMode.TRAIN, "Frankston", "")
    private val sandringham = Route(12, TransportMode.TRAIN, "Sandringham", "")
    private fun dep(route: Route, direction: Int, name: String) = Departure(
        1071, route, "r$direction", direction, name, name, Instant.EPOCH, null, null, false, null, emptyList(), false, false, null,
    )

    private val departures = listOf(
        dep(frankston, 1, "City"), dep(frankston, 5, "Frankston"), dep(frankston, 1, "City"),
        dep(sandringham, 1, "City"),
    )

    @Test
    fun choicesGroupDirectionsUnderEachLine() {
        val choices = FavouriteChoices.from(departures, listOf(frankston, sandringham))
        assertEquals(listOf("Frankston", "Sandringham"), choices.map { it.route.badgeLabel })
        assertEquals(listOf("City", "Frankston"), choices[0].directions.map { it.directionName })
        assertFalse(FavouriteChoices.isSingle(choices))
        assertTrue(FavouriteChoices.isSingle(FavouriteChoices.from(listOf(dep(frankston, 1, "City")), listOf(frankston))))
    }

    @Test
    fun aFavouriteWithoutLinesShowsEverythingAndOneWithLinesFilters() {
        val all = FavouriteStop(1071, 0, "Flinders Street")
        assertEquals(4, all.filter(departures).size)
        assertNull(all.linesSummary)
        val cityBound = all.copy(lines = listOf(FavouriteLine(6, "Frankston", 1, "City")))
        assertEquals(2, cityBound.filter(departures).size)
        assertEquals("Frankston to City", cityBound.linesSummary)
        val wholeLine = all.copy(lines = listOf(FavouriteLine(12, "Sandringham")))
        assertEquals(1, wholeLine.filter(departures).size)
    }

    @Test
    fun linesCanBeChangedAndOldFavouritesStillDecode() {
        val list = listOf(FavouriteStop(1071, 0, "Flinders Street"))
        val updated = FavouritesCodec.setLines(list, list[0].key, listOf(FavouriteLine(6, "Frankston", 1, "City"), FavouriteLine(6, "Frankston", 1, "City")))
        assertEquals(1, updated[0].lines.size)
        val old = FavouritesCodec.decode("""[{"stopId":1071,"routeType":0,"name":"Flinders Street"}]""")
        assertTrue(old[0].showsEverything)
    }
}
