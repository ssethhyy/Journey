package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.foryou.DisruptionLevel
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.ServiceUpdateSplit
import com.ssethhyy.journey.ui.screens.map.MapViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StopUpdatesAndMapTest {
    private val line = Route(id = 8, mode = TransportMode.TRAIN, name = "Hurstbridge", number = "")

    private fun disruption(id: Long, title: String, type: String) = Disruption(
        id = id, title = title, description = "", url = null, status = "Current", type = type,
        publishedOn = Instant.EPOCH, fromDate = null, toDate = null, routes = listOf(line), stopNames = emptyList(), mode = TransportMode.TRAIN,
    )

    private val closure = disruption(1, "Buses replace trains between Clifton Hill and Eltham", "Planned Works")
    private val lift = disruption(2, "Lift out of service", "Service Information")
    private val delays = disruption(3, "Minor delays", "Minor Delays")

    @Test
    fun onlyMajorAlertsBringUpTheCardButAllAreKept() {
        val split = ServiceUpdateSplit.of(listOf(lift, closure, delays), DisruptionLevel.MAJOR)
        assertEquals(listOf(1L), split.important.map { it.id })
        assertEquals(setOf(2L, 3L), split.others.map { it.id }.toSet())
    }

    @Test
    fun facilityClosuresAreNotMajor() {
        val carPark = disruption(4, "Hurstbridge Line stations: Temporary car park closures and pedestrian access changes", "Planned Closure")
        val lifts = disruption(5, "Lift closed at Flinders Street", "Service Information")
        val replaced = disruption(6, "Buses replace trains; station car park closed", "Planned Works")
        assertTrue(ServiceUpdateSplit.of(listOf(carPark, lifts), DisruptionLevel.MAJOR).important.isEmpty())
        assertEquals(listOf(6L), ServiceUpdateSplit.of(listOf(carPark, replaced), DisruptionLevel.MAJOR).important.map { it.id })
    }

    @Test
    fun withNoMajorAlertsThereIsNothingImportant() {
        assertTrue(ServiceUpdateSplit.of(listOf(lift, delays), DisruptionLevel.MAJOR).important.isEmpty())
        assertEquals(2, ServiceUpdateSplit.of(listOf(lift, delays), DisruptionLevel.ALL).important.size)
    }

    @Test
    fun modesAppearAsYouZoomIn() {
        val all = TransportMode.entries.toSet()
        assertEquals(setOf(TransportMode.VLINE), MapViewModel.visibleModes(6.0, all))
        assertEquals(setOf(TransportMode.VLINE, TransportMode.TRAIN), MapViewModel.visibleModes(10.0, all))
        assertEquals(setOf(TransportMode.VLINE, TransportMode.TRAIN, TransportMode.TRAM), MapViewModel.visibleModes(12.5, all))
        assertEquals(all, MapViewModel.visibleModes(15.0, all))
        // Turned-off modes stay off.
        assertEquals(setOf(TransportMode.TRAM), MapViewModel.visibleModes(15.0, setOf(TransportMode.TRAM)))
    }
}
