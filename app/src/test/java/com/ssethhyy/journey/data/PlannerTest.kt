package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.GeocodeAreaDto
import com.ssethhyy.journey.data.planner.GeocodeMatchDto
import com.ssethhyy.journey.data.planner.ItineraryDto
import com.ssethhyy.journey.data.planner.JourneyMapper
import com.ssethhyy.journey.data.planner.LegDto
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.data.planner.PlanResponseDto
import com.ssethhyy.journey.data.planner.PlannerApi
import com.ssethhyy.journey.data.planner.PlannerPlaceDto
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlannerTest {
    @Test
    fun decodesGooglePolylineReferenceExample() {
        // Reference example from Google's encoded polyline documentation (precision 5).
        val points = JourneyMapper.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 1e-6)
        assertEquals(-120.2, points[0].longitude, 1e-6)
        assertEquals(43.252, points[2].latitude, 1e-6)
        assertEquals(-126.453, points[2].longitude, 1e-6)
    }

    @Test
    fun decodeToleratesTruncatedInput() {
        assertTrue(JourneyMapper.decodePolyline("").isEmpty())
        assertEquals(1, JourneyMapper.decodePolyline("_p~iF~ps|U_ul", precision = 5).size)
    }

    @Test
    fun mapsMotisModesToVictorianModes() {
        assertEquals(TransportMode.TRAIN, JourneyMapper.transportMode("SUBWAY"))
        assertEquals(TransportMode.VLINE, JourneyMapper.transportMode("REGIONAL_RAIL"))
        assertEquals(TransportMode.VLINE, JourneyMapper.transportMode("COACH"))
        assertEquals(TransportMode.TRAM, JourneyMapper.transportMode("tram"))
        assertEquals(TransportMode.BUS, JourneyMapper.transportMode("BUS"))
        assertNull(JourneyMapper.transportMode("WALK"))
    }

    private fun place(name: String, dep: String? = null, arr: String? = null, track: String? = null) =
        PlannerPlaceDto(name = name, departure = dep, arrival = arr, track = track)

    private fun itinerary(start: String, end: String, transfers: Int, vararg legs: LegDto) =
        ItineraryDto(duration = 0, startTime = start, endTime = end, transfers = transfers, legs = legs.toList())

    private val walk = LegDto(mode = "WALK", from = place("START"), to = place("Flinders Street Railway Station"), startTime = "2026-10-18T06:25:00Z", endTime = "2026-10-18T06:27:00Z", distance = 120.0)
    private val train = LegDto(
        mode = "SUBWAY",
        from = place("Flinders Street Railway Station", dep = "2026-10-18T06:28:00Z", track = "6"),
        to = place("Caulfield Railway Station", arr = "2026-10-18T06:43:00Z", track = "2"),
        startTime = "2026-10-18T06:28:00Z",
        endTime = "2026-10-18T06:43:00Z",
        scheduledStartTime = "2026-10-18T06:26:00Z",
        realTime = true,
        headsign = "Frankston",
        routeShortName = "Frankston",
        routeColor = "028430",
        intermediateStops = listOf(place("Richmond Railway Station"), place("South Yarra Railway Station")),
    )

    @Test
    fun mapsLegsWithRealtimePlatformsAndCleanNames() {
        val leg = JourneyMapper.leg(train)
        assertEquals(LegKind.TRANSIT, leg.kind)
        assertEquals(TransportMode.TRAIN, leg.mode)
        assertEquals("Flinders Street Station", leg.fromName)
        assertEquals("6", leg.platform)
        assertEquals("2", leg.arrivalPlatform)
        assertEquals(2L, leg.departureDelayMinutes)
        assertEquals(15L, leg.durationMinutes)
        assertEquals("Frankston", leg.label)
        assertEquals(0xFF028430, leg.routeColor)
        assertEquals(listOf("Richmond Station", "South Yarra Station"), leg.intermediateStops)
        assertEquals("", JourneyMapper.leg(walk).fromName)
    }

    @Test
    fun resultsAreSortedByArrivalAndIncludeShortWalkOnlyOption() {
        val later = itinerary("2026-10-18T06:40:00Z", "2026-10-18T07:05:00Z", 0, walk, train)
        val sooner = itinerary("2026-10-18T06:25:00Z", "2026-10-18T06:47:00Z", 0, walk, train)
        val longWalk = itinerary("2026-10-18T06:25:00Z", "2026-10-18T08:00:00Z", 0, walk)
        val shortWalk = itinerary("2026-10-18T06:25:00Z", "2026-10-18T06:50:00Z", 0, walk)
        val results = JourneyMapper.results(PlanResponseDto(itineraries = listOf(later, sooner), direct = listOf(longWalk), nextPageCursor = "abc"))
        assertEquals(listOf(Instant.parse("2026-10-18T06:47:00Z"), Instant.parse("2026-10-18T07:05:00Z")), results.journeys.map { it.end })
        assertEquals("abc", results.nextPageCursor)
        // A 95 minute walk is not offered; a 25 minute walk is.
        val withWalk = JourneyMapper.results(PlanResponseDto(itineraries = listOf(sooner), direct = listOf(shortWalk)))
        assertEquals(2, withWalk.journeys.size)
        assertTrue(withWalk.journeys.any { it.isWalkOnly })
    }

    @Test
    fun journeySummaryValues() {
        val journey = JourneyMapper.journey(itinerary("2026-10-18T06:25:00Z", "2026-10-18T06:47:00Z", 1, walk, train), 0)
        assertEquals(22L, journey.durationMinutes)
        assertEquals(120.0, journey.walkMeters, 0.01)
        assertEquals("Frankston", journey.firstTransit?.label)
        assertEquals(1, journey.transfers)
    }

    @Test
    fun geocodeFormatsAddressesWithLocality() {
        val address = JourneyMapper.geocode(
            GeocodeMatchDto(
                type = "ADDRESS",
                name = "Smith Street",
                street = "Smith Street",
                houseNumber = "12",
                lat = -37.8,
                lon = 144.98,
                areas = listOf(GeocodeAreaDto("Victoria", 4.0), GeocodeAreaDto("Fitzroy", 9.0, isDefault = true)),
            ),
        )
        assertEquals("12 Smith Street", address.name)
        assertEquals("Fitzroy", address.subtitle)
    }

    @Test
    fun planRequestSendsCoordinatesTimeAndUserAgent() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("""{"itineraries":[],"direct":[],"unknownField":1}"""))
        PlannerApi(baseUrl = server.url("/").toString()).plan(-37.81, 144.96, -37.87, 145.04, time = Instant.parse("2026-10-18T06:00:00Z"), arriveBy = true)
        val request = server.takeRequest()
        val path = request.path!!
        assertTrue(path, path.startsWith("/api/v1/plan?fromPlace=-37.81%2C144.96&toPlace=-37.87%2C145.04"))
        assertTrue(path, "arriveBy=true" in path)
        assertTrue(path, "time=2026-10-18T06%3A00%3A00Z" in path)
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Journey-Android"))
        server.shutdown()
    }
}
