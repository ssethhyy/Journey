package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.repository.PlaceIcon
import com.ssethhyy.journey.data.repository.PlacesCodec
import com.ssethhyy.journey.data.repository.RecentTrip
import com.ssethhyy.journey.data.repository.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesCodecTest {
    private fun place(
        id: String,
        icon: PlaceIcon = PlaceIcon.STAR,
        name: String = id,
    ) = SavedPlace(
        id = id,
        name = name,
        address = "Addr $id",
        latitude = -37.8,
        longitude = 144.9,
        icon = icon,
    )

    @Test
    fun placesUpsertById() {
        val gym = place("g", name = "Gym")
        val uni = place("u", name = "Uni")
        val renamed = gym.copy(name = "Pool")

        val list = PlacesCodec.upsert(listOf(gym, uni), renamed)
        assertEquals(listOf("Pool", "Uni"), list.map { it.name })

        // A brand-new id appends rather than replaces.
        assertEquals(3, PlacesCodec.upsert(list, place("x")).size)
    }

    @Test
    fun savingASecondPlaceWithHomeIconKeepsBoth() {
        // Icons are cosmetic now: two places may both use the Home icon.
        val home1 = place("h1", icon = PlaceIcon.HOME, name = "Home")
        val home2 = place("h2", icon = PlaceIcon.HOME, name = "Mum's")
        val list = PlacesCodec.upsert(PlacesCodec.upsert(emptyList(), home1), home2)
        assertEquals(listOf("h1", "h2"), list.map { it.id })
    }

    @Test
    fun roundTripAndGarbage() {
        val list = listOf(
            place("h", icon = PlaceIcon.HOME, name = "Home"),
            place("c", name = "Gym"),
        )
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
