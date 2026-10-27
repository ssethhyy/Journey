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
    private fun place(id: String, kind: PlaceKind, name: String = id) = SavedPlace(id, kind, name, "Addr $id", -37.8, 144.9, PlaceIcon.STAR)

    @Test
    fun homeAndWorkAreUnique() {
        val home1 = place("h1", PlaceKind.HOME)
        val gym = place("g", PlaceKind.CUSTOM, "Gym")
        val home2 = place("h2", PlaceKind.HOME)
        val list = PlacesCodec.upsert(PlacesCodec.upsert(listOf(home1), gym), home2)
        assertEquals(listOf("h2", "g"), list.map { it.id })
    }

    @Test
    fun customPlacesUpsertById() {
        val gym = place("g", PlaceKind.CUSTOM, "Gym")
        val uni = place("u", PlaceKind.CUSTOM, "Uni")
        val renamed = gym.copy(name = "Pool")
        val list = PlacesCodec.upsert(listOf(gym, uni), renamed)
        assertEquals(listOf("Pool", "Uni"), list.map { it.label })
        assertEquals(3, PlacesCodec.upsert(list, place("x", PlaceKind.CUSTOM)).size)
    }

    @Test
    fun changingKindOfExistingPlaceReplacesPreviousHome() {
        val home = place("h", PlaceKind.HOME)
        val gym = place("g", PlaceKind.CUSTOM, "Gym")
        val list = PlacesCodec.upsert(listOf(home, gym), gym.copy(kind = PlaceKind.HOME))
        assertEquals(listOf("g"), list.map { it.id })
        assertEquals("Home", list.single().label)
    }

    @Test
    fun sortedPutsHomeThenWorkFirst() {
        val sorted = PlacesCodec.sorted(listOf(place("c", PlaceKind.CUSTOM), place("w", PlaceKind.WORK), place("h", PlaceKind.HOME)))
        assertEquals(listOf("h", "w", "c"), sorted.map { it.id })
    }

    @Test
    fun roundTripAndGarbage() {
        val list = listOf(place("h", PlaceKind.HOME), place("c", PlaceKind.CUSTOM))
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
