package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.RouteStopOrder
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStopOrderTest {
    private fun stop(id: Int, name: String, lat: Double, sequence: Int = 0) =
        Stop(id, TransportMode.BUS, name, null, lat, 145.0, sequence = sequence)

    // A straight route running north from the city.
    private val north = listOf(-37.82 to 145.0, -37.80 to 145.0, -37.78 to 145.0, -37.76 to 145.0)
    private val stops = listOf(stop(3, "Alpha St", -37.77), stop(1, "City Terminus", -37.82), stop(2, "Middle Rd", -37.79))

    @Test
    fun unnumberedStopsFollowTheDirectionsPath() {
        val ordered = RouteStopOrder.order(stops, mapOf(5 to listOf(north), 6 to listOf(north.reversed())), 5, "Northland")
        assertEquals(listOf(1, 2, 3), ordered.map { it.id })
        val back = RouteStopOrder.order(stops, mapOf(5 to listOf(north), 6 to listOf(north.reversed())), 6, "City")
        assertEquals(listOf(3, 2, 1), back.map { it.id })
    }

    @Test
    fun aSharedPathIsTurnedRoundToEndWhereTheDirectionSays() {
        val ordered = RouteStopOrder.order(stops, mapOf(0 to listOf(north)), 6, "City")
        assertEquals(listOf(3, 2, 1), ordered.map { it.id })
    }

    @Test
    fun numberedStopsKeepTheirOrderAndTheOtherDirectionsAreLeftOut() {
        val list = listOf(stop(9, "Bourke St", -37.81), stop(2, "B", -37.82, 2), stop(1, "A", -37.77, 1))
        assertEquals(listOf(1, 2), RouteStopOrder.order(list, mapOf(5 to listOf(north)), 5, null).map { it.id })
    }
}
