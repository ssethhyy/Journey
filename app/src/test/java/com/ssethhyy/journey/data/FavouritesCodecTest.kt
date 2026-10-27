package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.data.repository.FavouritesCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesCodecTest {
    private fun fav(id: Int, mode: TransportMode, name: String = "Stop $id") =
        FavouriteStop.from(Stop(id, mode, name, "Suburb", -37.8, 144.9), now = 1L)

    private val train1 = fav(1, TransportMode.TRAIN)
    private val tram1 = fav(2, TransportMode.TRAM)
    private val train2 = fav(3, TransportMode.TRAIN)
    private val train3 = fav(4, TransportMode.TRAIN)

    @Test
    fun addIgnoresDuplicatesByModeAndId() {
        val list = FavouritesCodec.add(listOf(train1), fav(1, TransportMode.TRAIN, "Renamed"))
        assertEquals(1, list.size)
        // Same stop id in a different mode is a different favourite.
        assertEquals(2, FavouritesCodec.add(list, fav(1, TransportMode.BUS)).size)
    }

    @Test
    fun removeAndRename() {
        val list = listOf(train1, tram1)
        assertEquals(listOf(tram1), FavouritesCodec.remove(list, train1.key))
        val renamed = FavouritesCodec.rename(list, tram1.key, "  Work  ")
        assertEquals("Work", renamed[1].displayName)
        assertEquals(tram1.name, FavouritesCodec.rename(renamed, tram1.key, " ")[1].displayName)
    }

    @Test
    fun moveStaysWithinModeGroup() {
        val list = listOf(train1, tram1, train2, train3)
        // Moving train3 up one within trains swaps it with train2; the tram stays put.
        assertEquals(listOf(train1, tram1, train3, train2), FavouritesCodec.move(list, train3.key, -1))
        // Moving train2 up past the tram lands it before train1's position.
        assertEquals(listOf(train2, train1, tram1, train3), FavouritesCodec.move(list, train2.key, -1))
        // Out-of-range moves are no-ops.
        assertEquals(list, FavouritesCodec.move(list, train1.key, -1))
        assertEquals(list, FavouritesCodec.move(list, tram1.key, 1))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val list = listOf(train1, FavouritesCodec.rename(listOf(tram1), tram1.key, "Home").first())
        assertEquals(list, FavouritesCodec.decode(FavouritesCodec.encode(list)))
        assertEquals(list, FavouritesCodec.decode(FavouritesCodec.encode(list, pretty = true)))
    }

    @Test
    fun decodeToleratesGarbageAndUnknownFields() {
        assertTrue(FavouritesCodec.decode(null).isEmpty())
        assertTrue(FavouritesCodec.decode("not json").isEmpty())
        val decoded = FavouritesCodec.decode("""[{"stopId":5,"routeType":1,"name":"X","future":true}]""")
        assertEquals(TransportMode.TRAM, decoded.single().mode)
    }

    @Test
    fun mergeKeepsExistingOrderAndAppendsNew() {
        val merged = FavouritesCodec.merge(listOf(train1, tram1), listOf(tram1, train2))
        assertEquals(listOf(train1, tram1, train2), merged)
    }
}
