package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.api.DeparturesResponse
import com.ssethhyy.journey.data.api.PtvMappers
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: /v3/pattern returns `disruptions` as an array (departures return an object). */
class PatternParsingTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }

    private val pattern = """
        {"disruptions":[{"disruption_id":372550,"title":"Southern Cross Station: pedestrian access changes","disruption_type":"Planned Works","routes":[]}],
         "departures":[
           {"skipped_stops":[],"stop_id":1155,"route_id":8,"run_ref":"32005","direction_id":7,"disruption_ids":[372550],"scheduled_departure_utc":"2026-10-18T06:56:00Z","estimated_departure_utc":null,"platform_number":"","flags":"S_WCA","departure_sequence":1},
           {"skipped_stops":[],"stop_id":1071,"route_id":8,"run_ref":"32005","direction_id":7,"disruption_ids":[],"scheduled_departure_utc":"2026-10-18T07:00:00Z","platform_number":"3","departure_sequence":2}],
         "stops":{"1155":{"stop_id":1155,"stop_name":"Parliament","stop_suburb":"Melbourne City","route_type":0,"stop_latitude":-37.81,"stop_longitude":144.97,"stop_sequence":0,"stop_ticket":{"zone":"Zone 1"}},
                  "1071":{"stop_id":1071,"stop_name":"Flinders Street","route_type":0}},
         "routes":{"8":{"route_type":0,"route_id":8,"route_name":"Hurstbridge","route_number":"","geopath":[]}},
         "runs":{"32005":{"run_ref":"32005","route_id":8,"route_type":0,"destination_name":"Macleod","status":"scheduled","direction_id":7,"express_stop_count":3,"vehicle_position":null,"vehicle_descriptor":null}},
         "directions":{"7":{"direction_id":7,"direction_name":"Hurstbridge","route_id":8,"route_type":0}},
         "status":{"version":"3.0","health":1}}
    """.trimIndent()

    @Test
    fun parsesPatternWithDisruptionArray() {
        val response = json.decodeFromString(DeparturesResponse.serializer(), pattern)
        assertEquals(setOf("372550"), response.disruptions.keys)
        val parsed = PtvMappers.stoppingPattern(response)
        assertEquals("Macleod", parsed.destination)
        assertEquals(listOf("Parliament", "Flinders Street"), parsed.stops.map { it.stop.name })
        assertEquals(1, parsed.disruptions.size)
        assertEquals("3", parsed.stops[1].platform)
    }

    @Test
    fun stillParsesDisruptionObjectFromDepartures() {
        val response = json.decodeFromString(
            DeparturesResponse.serializer(),
            """{"departures":[],"disruptions":{"9":{"disruption_id":9,"title":"x"}}}""",
        )
        assertEquals(setOf("9"), response.disruptions.keys)
    }
}
