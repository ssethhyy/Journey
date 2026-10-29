package com.ssethhyy.journey.data.crowding

import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.model.VehicleInfo
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdingTest {
    @Test fun parsesVehiclesAndCarriages() {
        val v = GtfsRealtime.parseVehicles(feed(train("1380T", -37.8f, 144.9f, 3, listOf(1, 2, 5, 3)), train("9999X", -37.9f, 145.0f, null, emptyList())))
        assertEquals(2, v.size)
        assertEquals(Occupancy.STANDING_ONLY, v[0].occupancy)
        assertEquals(4, v[0].carriages.size)
        assertEquals(Occupancy.FULL, v[0].carriages[2].occupancy)
        assertEquals(-37.8, v[0].latitude!!, 0.001)
        assertEquals(Occupancy.UNKNOWN, v[1].occupancy)
    }

    @Test fun damagedFeedKeepsWhatWasRead() {
        val good = feed(train("1380T", -37.8f, 144.9f, 2, emptyList()))
        assertEquals(1, GtfsRealtime.parseVehicles(good + byteArrayOf(0x12, 0x7F)).size)
        assertTrue(GtfsRealtime.parseVehicles(ByteArray(0)).isEmpty())
        assertTrue(GtfsRealtime.parseVehicles(byteArrayOf(1, 2, 3)).isEmpty())
    }

    @Test fun matchesByCarriageNumberThenByPosition() {
        val vehicles = GtfsRealtime.parseVehicles(feed(train("1380T-1433T", -37.8f, 144.9f, 1, emptyList()), train("5555T", -37.81f, 144.91f, 4, emptyList())))
        assertEquals("5555T", CrowdingMatcher.match(vehicles, "1234M-5555T-77M", null)?.id)
        assertEquals("1380T-1433T", CrowdingMatcher.match(vehicles, null, -37.8001 to 144.9001)?.id)
        assertNull(CrowdingMatcher.match(vehicles, "nothing", -38.5 to 145.5))
    }

    @Test fun carriagesAreOrderedAndNamed() {
        val v = GtfsRealtime.parseVehicles(feed(train("1380T", -37.8f, 144.9f, null, listOf(0, 4, 4, 1))))
        val c = CrowdingMatcher.toCrowding(v[0])
        assertEquals(Occupancy.CRUSHED, c.overall)
        assertTrue(c.hasCarriageDetail)
        assertEquals(listOf(2, 3), c.busiestCarriages)
        assertEquals("Very crowded. Cars 2 and 3 are fullest.", CrowdingAlertPolicy.message(c))
    }

    @Test fun alertFiresOnceAndRearms() {
        val t = Occupancy.STANDING_ONLY
        var d = CrowdingAlertPolicy.next(null, Occupancy.FEW_SEATS, t)
        assertFalse(d.alert)
        d = CrowdingAlertPolicy.next(d.lastAlerted, Occupancy.STANDING_ONLY, t)
        assertTrue(d.alert)
        d = CrowdingAlertPolicy.next(d.lastAlerted, Occupancy.STANDING_ONLY, t)
        assertFalse(d.alert)
        d = CrowdingAlertPolicy.next(d.lastAlerted, Occupancy.UNKNOWN, t)
        assertFalse(d.alert)
        d = CrowdingAlertPolicy.next(d.lastAlerted, Occupancy.CRUSHED, t)
        assertTrue(d.alert)
        d = CrowdingAlertPolicy.next(d.lastAlerted, Occupancy.MANY_SEATS, t)
        assertNull(d.lastAlerted)
        assertTrue(CrowdingAlertPolicy.next(d.lastAlerted, Occupancy.STANDING_ONLY, t).alert)
    }

    @Test fun airConditioning() {
        val fault = Disruption(id = 1, title = "Air conditioning fault", description = "", url = null, status = "Current", type = "Minor Delays", publishedOn = null, fromDate = null, toDate = null, routes = emptyList(), stopNames = emptyList(), mode = null)
        val fitted = VehicleInfo("X", null, true)
        assertEquals(AirConditioning.Available, AirConditioningCheck.check(fitted, emptyList()))
        assertEquals(AirConditioning.NotFitted, AirConditioningCheck.check(VehicleInfo("X", null, false), emptyList()))
        assertTrue(AirConditioningCheck.check(fitted, listOf(fault)) is AirConditioning.FaultReported)
        assertEquals(AirConditioning.Unknown, AirConditioningCheck.check(null, emptyList()))
    }

    @Test fun repositoryFetchesWithKeyAndCaches() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(feed(train("1380T", -37.8f, 144.9f, 3, listOf(1, 3))))))
        server.start()
        try {
            val repo = CrowdingRepository(CrowdingApi({ "abc" }, server.url("/").toString().trimEnd('/')))
            val r = repo.crowdingFor(TransportMode.TRAIN, "1380T", null)
            assertTrue(r is CrowdingResult.Found)
            val request = server.takeRequest()
            assertEquals("abc", request.getHeader("KeyID"))
            assertTrue(request.path!!.endsWith("/metro/vehicle-positions"))
            assertNotNull((repo.crowdingFor(TransportMode.TRAIN, "nothing", null)) as? CrowdingResult.NotFound)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun repositoryReportsMissingAndRejectedKeys() = runBlocking {
        assertEquals(CrowdingResult.NotConfigured, CrowdingRepository(CrowdingApi({ "" })).crowdingFor(TransportMode.TRAM, "1", null))
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        try {
            val repo = CrowdingRepository(CrowdingApi({ "bad" }, server.url("/").toString().trimEnd('/')))
            val r = repo.crowdingFor(TransportMode.TRAM, "1", null)
            assertTrue(r is CrowdingResult.Failed && r.badKey)
        } finally {
            server.shutdown()
        }
    }
}
