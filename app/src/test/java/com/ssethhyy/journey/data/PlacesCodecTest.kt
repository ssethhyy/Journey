package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.repository.PlaceIcon
import com.ssethhyy.journey.data.repository.PlaceKind
import com.ssethhyy.journey.data.repository.PlacesCodec
import com.ssethhyy.journey.data.repository.RecentTrip
import com.ssethhyy.journey.data.repository.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesCodecTest {
    private fun place(id: String, name: String = id) = SavedPlace(
        id = id,
        kind = PlaceKind.CUSTOM,
        name = name,
        address = "Addr $id",
        latitude = -37.8,
        longitude = 144.9,
        icon = PlaceIcon.STAR,
    )

    @Test
    fun customPlacesUpsertById() {
        val gym = place("g", "Gym")
        val uni = place("u", "Uni")
        val renamed = place("g", "Pool")
        val list: List<SavedPlace> = PlacesCodec.upsert(listOf(gym, uni), renamed)
        assertEquals(listOf("Pool", "Uni"), list.map { it.name })
        assertEquals(3, PlacesCodec.upsert(list, place("x")).size)
    }

    @Test
    fun roundTripAndGarbage() {
        val list: List<SavedPlace> = listOf(place("h", "Home"), place("c", "Custom"))
        assertEquals(list, PlacesCodec.decodePlaces(PlacesCodec.encodePlaces(list)))
        assertTrue(PlacesCodec.decodePlaces("nope").isEmpty())
        assertTrue(PlacesCodec.decodeRecents(null).isEmpty())
    }

    @Test
    fun recentsDedupeAndCap() {
        fun trip(to: String, lat: Double) = RecentTrip("Current location", 0.0, 0.0, true, to, lat, 145.0)
        var list = emptyList<RecentTrip>()
        list = PlacesCodec.addRecent(list, trip("A", -37.1))
        list = PlacesCodec.addRecent(list, trip("B", -37.2))
        list = PlacesCodec.addRecent(list, trip("A again", -37.1))
        assertEquals(listOf("A again", "B"), list.map { it.toName })
        repeat(10) { list = PlacesCodec.addRecent(list, trip("T$it", -36.0 - it)) }
        assertEquals(6, list.size)
    }
}
