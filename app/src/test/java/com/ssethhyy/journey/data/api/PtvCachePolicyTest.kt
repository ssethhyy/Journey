package com.ssethhyy.journey.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PtvCachePolicyTest {
    private val day = 24 * 60 * 60L

    @Test
    fun slowChangingAnswersAreKept() {
        assertEquals(7 * day, PtvCachePolicy.maxAgeSeconds("/v3/routes/6"))
        assertEquals(7 * day, PtvCachePolicy.maxAgeSeconds("/v3/directions/route/6"))
        assertEquals(7 * day, PtvCachePolicy.maxAgeSeconds("/v3/stops/route/6/route_type/0"))
        assertEquals(day, PtvCachePolicy.maxAgeSeconds("/v3/stops/location/-37.81,144.96"))
        assertEquals(day, PtvCachePolicy.maxAgeSeconds("/v3/stops/1071/route_type/0"))
    }

    @Test
    fun liveAnswersAreNeverKept() {
        assertNull(PtvCachePolicy.maxAgeSeconds("/v3/departures/route_type/0/stop/1071"))
        assertNull(PtvCachePolicy.maxAgeSeconds("/v3/pattern/run/1-ABC/route_type/0"))
        assertNull(PtvCachePolicy.maxAgeSeconds("/v3/disruptions"))
        assertNull(PtvCachePolicy.maxAgeSeconds("/v3/search/flinders"))
    }
}
