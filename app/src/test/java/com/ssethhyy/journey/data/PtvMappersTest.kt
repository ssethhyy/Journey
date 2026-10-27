package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.api.DeparturesResponse
import com.ssethhyy.journey.data.api.DisruptionDto
import com.ssethhyy.journey.data.api.DisruptionRouteDto
import com.ssethhyy.journey.data.api.DisruptionsResponse
import com.ssethhyy.journey.data.api.PtvMappers
import com.ssethhyy.journey.data.api.StopAccessibilityDto
import com.ssethhyy.journey.data.api.StopAmenitiesDto
import com.ssethhyy.journey.data.api.StopDetailsDto
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.FacilityKind
import com.ssethhyy.journey.data.model.TransportMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PtvMappersTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }

    private fun departures(): DeparturesResponse =
        json.decodeFromString(DeparturesResponse.serializer(), javaClass.classLoader!!.getResource("ptv/departures.json")!!.readText())

    @Test
    fun departureBoardIsSortedByBestTime() {
        val board = PtvMappers.departureBoard(departures())
        val times = board.departures.map { it.bestTime }
        assertEquals(times.sortedBy { it }, times)
        assertEquals("Carrum", board.departures.first().destination)
    }

    @Test
    fun liveDepartureHasDelayExpressAndVehicle() {
        val dep = PtvMappers.departureBoard(departures()).departures.first { it.runRef == "955123" }
        assertEquals(2L, dep.delayMinutes)
        assertTrue(dep.isLive)
        assertTrue(dep.isExpress)
        assertEquals("4", dep.platform)
        assertEquals("Frankston", dep.route?.name)
        assertEquals("Frankston", dep.route?.badgeLabel)
        assertEquals(true, dep.vehicle?.airConditioned)
    }

    @Test
    fun cancelledRunIsFlagged() {
        val dep = PtvMappers.departureBoard(departures()).departures.first { it.runRef == "955124" }
        assertTrue(dep.isCancelled)
        assertFalse(dep.isLive)
        assertNull(dep.delayMinutes)
        assertEquals(listOf(123456L), dep.disruptionIds)
    }

    @Test
    fun boardIncludesDisruptionsAndRoutes() {
        val board = PtvMappers.departureBoard(departures(), now = Instant.EPOCH)
        assertEquals(setOf(123456L), board.disruptions.keys)
        assertEquals(DisruptionSeverity.PLANNED, board.disruptions.getValue(123456).severity)
        assertEquals(listOf("Frankston", "Pakenham"), board.routes.map { it.name })
        assertEquals(Instant.EPOCH, board.fetchedAt)
    }

    @Test
    fun directionNameFallsBackForHeadsign() {
        val dep = PtvMappers.departureBoard(departures()).departures.first { it.runRef == "955125" }
        assertEquals("Pakenham", dep.directionName)
        assertEquals("Pakenham", dep.headsign)
    }

    @Test
    fun disruptionsAreFlattenedDedupedAndAssignedModes() {
        val severe = DisruptionDto(id = 1, title = "Suspended", type = "Service Suspended", routes = listOf(DisruptionRouteDto(routeType = 1, routeId = 3)))
        val planned = DisruptionDto(id = 2, title = "Works", type = "Planned Works")
        val response = DisruptionsResponse(
            mapOf(
                "metro_train" to listOf(planned),
                "metro_tram" to listOf(severe),
                "general" to listOf(planned),
            ),
        )
        val list = PtvMappers.disruptions(response)
        assertEquals(listOf(1L, 2L), list.map { it.id })
        assertEquals(TransportMode.TRAM, list.first().mode)
        assertEquals(DisruptionSeverity.SEVERE, list.first().severity)
        assertEquals(TransportMode.TRAIN, list[1].mode)
    }

    @Test
    fun geopathParsesSpaceAndCommaSeparatedPairs() {
        assertEquals(listOf(-37.81 to 144.96, -37.82 to 144.97), PtvMappers.parseGeopath("-37.81,144.96 -37.82,144.97"))
        assertEquals(listOf(-37.81 to 144.96, -37.82 to 144.97), PtvMappers.parseGeopath("-37.81,144.96,-37.82,144.97"))
        assertTrue(PtvMappers.parseGeopath("garbage").isEmpty())
        assertTrue(PtvMappers.parseGeopath("-37.81,144.96 -37.82").isEmpty())
    }

    @Test
    fun stopDetailsMapsFacilities() {
        val dto = StopDetailsDto(
            id = 1071,
            name = "Flinders Street Station",
            routeType = 0,
            amenities = StopAmenitiesDto(toilet = true, taxiRank = true, carParking = "0", cctv = false),
            accessibility = StopAccessibilityDto(lift = true, stopAccessible = true, hearingLoop = false),
        )
        val details = PtvMappers.stopDetails(dto, fallback = null)
        val kinds = details.facilities.map { it.kind }
        assertEquals(listOf(FacilityKind.TOILET, FacilityKind.TAXI_RANK, FacilityKind.WHEELCHAIR, FacilityKind.LIFT), kinds)
        assertEquals(TransportMode.TRAIN, details.stop.mode)
    }

    @Test
    fun invalidInstantIsNull() {
        assertNull(PtvMappers.parseInstant("not a date"))
        assertNull(PtvMappers.parseInstant(""))
        assertEquals(Instant.parse("2026-10-18T03:30:00Z"), PtvMappers.parseInstant("2026-10-18T03:30:00Z"))
    }
}
