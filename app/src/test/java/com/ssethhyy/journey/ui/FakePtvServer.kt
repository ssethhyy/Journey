package com.ssethhyy.journey.ui

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Serves realistic PTV responses with departure times relative to "now". */
class FakePtvServer {
    val server = MockWebServer()

    private fun t(minutes: Long): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(minutes, ChronoUnit.MINUTES).toString()

    private data class FakeRoute(val id: Int, val type: Int, val name: String, val number: String)
    private data class FakeStop(val id: Int, val type: Int, val name: String, val suburb: String, val lat: Double, val lon: Double, val distance: Double, val routes: List<FakeRoute>)

    private val frankston = FakeRoute(6, 0, "Frankston", "")
    private val sandringham = FakeRoute(12, 0, "Sandringham", "")
    private val pakenham = FakeRoute(11, 0, "Pakenham", "")
    private val tram96 = FakeRoute(1041, 1, "East Brunswick - St Kilda Beach", "96")
    private val tram86 = FakeRoute(1083, 1, "Bundoora RMIT - Waterfront City Docklands", "86")
    private val bus246 = FakeRoute(5332, 2, "Elsternwick - Clifton Hill via St Kilda", "246")
    private val geelong = FakeRoute(1745, 3, "Geelong - Melbourne", "")

    private val stops = listOf(
        FakeStop(1071, 0, "Flinders Street Station", "Melbourne City", -37.8183, 144.9671, 120.0, listOf(frankston, sandringham, pakenham)),
        FakeStop(2258, 1, "Bourke St/Spencer St #1", "Melbourne City", -37.8166, 144.9548, 340.0, listOf(tram96, tram86)),
        FakeStop(18560, 2, "Fitzroy St/Grey St", "St Kilda", -37.8597, 144.9789, 610.0, listOf(bus246)),
        FakeStop(1181, 0, "Southern Cross Station", "Melbourne City", -37.8184, 144.9525, 880.0, listOf(pakenham, frankston)),
        FakeStop(1162, 3, "Southern Cross Station", "Melbourne City", -37.8184, 144.9525, 900.0, listOf(geelong)),
    )

    private fun routeJson(r: FakeRoute) =
        """{"route_type":${r.type},"route_id":${r.id},"route_name":"${r.name}","route_number":"${r.number}","route_gtfs_id":"x","route_service_status":{"description":"Good Service"}}"""

    private fun stopJson(s: FakeStop) =
        """{"stop_distance":${s.distance},"stop_suburb":"${s.suburb}","stop_name":"${s.name}","stop_id":${s.id},"route_type":${s.type},"stop_latitude":${s.lat},"stop_longitude":${s.lon},"stop_sequence":0,"routes":[${s.routes.joinToString(",") { routeJson(it) }}]}"""

    private fun departuresJson(stop: FakeStop): String {
        val destinations = mapOf(
            6 to listOf("Frankston", "Carrum", "Frankston"),
            12 to listOf("Sandringham", "Sandringham"),
            11 to listOf("Pakenham", "Westall"),
            1041 to listOf("St Kilda Beach", "East Brunswick"),
            1083 to listOf("Waterfront City Docklands", "Bundoora RMIT"),
            5332 to listOf("Clifton Hill", "Elsternwick"),
            1745 to listOf("Waurn Ponds", "Geelong"),
        )
        val offsets = listOf(2L, 6L, 11L, 17L, 24L, 38L)
        var i = 0
        val deps = mutableListOf<String>()
        val runs = mutableListOf<String>()
        stop.routes.forEach { route ->
            destinations.getValue(route.id).forEachIndexed { d, dest ->
                val idx = i++
                val runRef = "${route.id}-$idx"
                val scheduled = offsets[idx % offsets.size] + d
                val live = idx % 3 != 2
                val estimated = if (live) "\"${t(scheduled + (if (idx == 1) 2 else 0))}\"" else "null"
                val platform = if (route.type == 0 || route.type == 3) "\"${idx % 6 + 1}\"" else "null"
                val disruptions = if (route.id == 6 && d == 1) "[900001]" else "[]"
                deps += """{"stop_id":${stop.id},"route_id":${route.id},"run_ref":"$runRef","direction_id":$d,"disruption_ids":$disruptions,"scheduled_departure_utc":"${t(scheduled)}","estimated_departure_utc":$estimated,"at_platform":false,"platform_number":$platform,"flags":"","departure_sequence":0}"""
                val status = if (route.id == 12 && d == 1) "cancelled" else "scheduled"
                runs += """"$runRef":{"run_ref":"$runRef","route_id":${route.id},"route_type":${route.type},"destination_name":"$dest","status":"$status","direction_id":$d,"express_stop_count":${if (idx == 0) 3 else 0},"vehicle_descriptor":{"low_floor":${route.type == 1},"air_conditioned":true,"description":"${if (route.type == 1) "E-Class" else "X'Trapolis 100"}"}}"""
            }
        }
        val routes = stop.routes.joinToString(",") { "\"${it.id}\":${routeJson(it)}" }
        val directions = stop.routes.flatMap { r -> destinations.getValue(r.id).take(2).mapIndexed { d, dest -> "\"$d\":{\"direction_id\":$d,\"direction_name\":\"$dest\",\"route_id\":${r.id},\"route_type\":${r.type}}" } }
            .distinctBy { it.substringBefore(":") }
        return """{"departures":[${deps.joinToString(",")}],"stops":{},"routes":{$routes},"runs":{${runs.joinToString(",")}},"directions":{${directions.joinToString(",")}},"disruptions":{"900001":$disruptionFrankston}}"""
    }

    private val disruptionFrankston =
        """{"disruption_id":900001,"title":"Frankston line: Buses replace trains between Caulfield and Moorabbin from 8:30pm","url":"https://www.ptv.vic.gov.au","description":"Buses replace trains between Caulfield and Moorabbin from 8:30pm to last service each night due to level crossing removal works.","disruption_status":"Current","disruption_type":"Planned Works","published_on":"${t(-600)}","from_date":"${t(-60)}","to_date":"${t(2000)}","routes":[${routeJson(frankston)}],"stops":[]}"""

    private val disruptionTram =
        """{"disruption_id":900002,"title":"Route 96: Major delays due to a collision","url":"https://www.ptv.vic.gov.au","description":"Trams may be delayed by up to 20 minutes.","disruption_status":"Current","disruption_type":"Major Delays","published_on":"${t(-20)}","routes":[${routeJson(tram96)}],"stops":[]}"""

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty().substringBefore("?")
            if (path.startsWith("/opendata/public-transport/gtfs/realtime/")) {
                // A four-car train: car 3 is the fullest.
                val feed = com.ssethhyy.journey.data.crowding.feed(
                    com.ssethhyy.journey.data.crowding.train("1380T", -37.8403f, 144.9968f, 3, listOf(1, 2, 5, 3)),
                )
                return MockResponse().setBody(okio.Buffer().write(feed)).setHeader("Content-Type", "application/x-protobuf")
            }
            val body = when {
                path.startsWith("/v3/departures/route_type/") -> {
                    val parts = path.split("/")
                    val stopId = parts[6].toInt()
                    val type = parts[4].toInt()
                    departuresJson(stops.firstOrNull { it.id == stopId && it.type == type } ?: stops.first())
                }
                path.startsWith("/v3/stops/location/") -> """{"stops":[${stops.joinToString(",") { stopJson(it) }}]}"""
                path.startsWith("/v3/disruptions/stop/") -> """{"disruptions":{"metro_train":[$disruptionFrankston]}}"""
                path.startsWith("/v3/disruptions") -> """{"disruptions":{"metro_train":[$disruptionFrankston],"metro_tram":[$disruptionTram],"metro_bus":[]}}"""
                path.startsWith("/v3/search/") -> """{"stops":[${stops.joinToString(",") { stopJson(it) }}],"routes":[${listOf(frankston, tram96, bus246).joinToString(",") { routeJson(it) }}]}"""
                path.startsWith("/v3/stops/route/") -> """{"stops":[${stops.filter { it.type == 0 }.mapIndexed { i, s -> stopJson(s.copy(distance = 0.0)).replace("\"stop_sequence\":0", "\"stop_sequence\":$i") }.joinToString(",")}],"geopath":[]}"""
                path.matches(Regex("/v3/stops/\\d+/route_type/\\d+")) ->
                    """{"stop":{"stop_id":1071,"stop_name":"Flinders Street Station","route_type":0,"stop_location":{"gps":{"latitude":-37.8183,"longitude":144.9671},"suburb":"Melbourne City"},"stop_amenities":{"toilet":true,"taxi_rank":true,"car_parking":"0","cctv":true},"stop_accessibility":{"lift":true,"escalator":true,"stop_accessible":true,"hearing_loop":true,"tactile_ground_surface_indicator":true,"waiting_room":true,"lighting":true,"audio_customer_information":true}}}"""
                path.startsWith("/v3/routes/") -> """{"route":${routeJson(frankston).dropLast(1)},"geopath":[]}}"""
                path.startsWith("/v3/directions/route/") -> """{"directions":[{"direction_id":1,"direction_name":"City (Flinders Street)","route_id":6,"route_type":0},{"direction_id":5,"direction_name":"Frankston","route_id":6,"route_type":0}]}"""
                path.startsWith("/v3/pattern/run/") -> patternJson()
                path.startsWith("/api/v1/plan") -> planJson()
                path.startsWith("/api/v1/geocode") -> """[{"type":"PLACE","name":"Melbourne Central","id":"w1","lat":-37.8097,"lon":144.9637,"areas":[{"name":"Melbourne","adminLevel":9.0,"default":true}]},{"type":"ADDRESS","name":"Smith Street","street":"Smith Street","houseNumber":"12","id":"a1","lat":-37.80,"lon":144.98,"areas":[{"name":"Fitzroy","adminLevel":9.0,"default":true}]}]"""
                else -> return MockResponse().setResponseCode(404).setBody("""{"message":"Not found: $path"}""")
            }
            return MockResponse().setBody(body).setHeader("Content-Type", "application/json")
        }
    }

    private fun planPlace(name: String, dep: Long?, arr: Long?, track: String? = null) =
        """{"name":"$name","lat":-37.8,"lon":144.9${dep?.let { ",\"departure\":\"${t(it)}\"" } ?: ""}${arr?.let { ",\"arrival\":\"${t(it)}\"" } ?: ""}${track?.let { ",\"track\":\"$it\"" } ?: ""}}"""

    private fun walkLeg(from: String, to: String, start: Long, end: Long, meters: Int) =
        """{"mode":"WALK","from":${planPlace(from, start, null)},"to":${planPlace(to, null, end)},"startTime":"${t(start)}","endTime":"${t(end)}","distance":$meters}"""

    private fun transitLeg(mode: String, route: String, headsign: String, from: String, to: String, start: Long, end: Long, track: String?, stops: List<String>, delay: Long = 0) =
        """{"mode":"$mode","from":${planPlace(from, start, null, track)},"to":${planPlace(to, null, end)},"startTime":"${t(start)}","endTime":"${t(end)}","scheduledStartTime":"${t(start - delay)}","realTime":true,"headsign":"$headsign","routeShortName":"$route","intermediateStops":[${stops.joinToString(",") { planPlace(it, null, null) }}]}"""

    private fun planJson(): String {
        val trainTrip = """{"startTime":"${t(1)}","endTime":"${t(26)}","transfers":0,"legs":[${walkLeg("START", "Flinders Street Railway Station", 1, 4, 220)},${transitLeg("SUBWAY", "Frankston", "Frankston", "Flinders Street Railway Station", "Caulfield Railway Station", 5, 21, "6", listOf("Richmond Railway Station", "South Yarra Railway Station", "Hawksburn Railway Station", "Toorak Railway Station", "Armadale Railway Station", "Malvern Railway Station"), delay = 2)},${walkLeg("Caulfield Railway Station", "END", 21, 26, 380)}]}"""
        val tramBus = """{"startTime":"${t(3)}","endTime":"${t(41)}","transfers":1,"legs":[${walkLeg("START", "Bourke St/Spencer St #1", 3, 6, 260)},${transitLeg("TRAM", "96", "St Kilda Beach", "Bourke St/Spencer St #1", "Fitzroy St/Grey St", 7, 27, null, listOf("Southern Cross Station", "Albert Park"))},${transitLeg("BUS", "246", "Elsternwick", "Fitzroy St/Grey St", "Glen Huntly Rd", 30, 37, null, listOf("Balaclava"))},${walkLeg("Glen Huntly Rd", "END", 37, 41, 300)}]}"""
        return """{"itineraries":[$trainTrip,$tramBus],"direct":[],"nextPageCursor":"page2"}"""
    }

    private fun patternJson(): String {
        // A Frankston-line run with realistic coordinates; the vehicle is between South Yarra and Hawksburn,
        // and every stop from South Yarra on is predicted 2 minutes late.
        val names = listOf("Flinders Street", "Richmond", "South Yarra", "Hawksburn", "Toorak", "Armadale", "Malvern", "Caulfield")
        val coords = listOf(-37.8183 to 144.9671, -37.8240 to 144.9900, -37.8386 to 144.9926, -37.8421 to 145.0010, -37.8420 to 145.0123, -37.8559 to 145.0194, -37.8617 to 145.0292, -37.8774 to 145.0426)
        val deps = names.indices.joinToString(",") { i ->
            val late = if (i >= 2) 2L else 0L
            """{"stop_id":${100 + i},"route_id":6,"run_ref":"6-0","direction_id":1,"disruption_ids":[],"scheduled_departure_utc":"${t(2L + i * 3)}","estimated_departure_utc":"${t(2L + i * 3 + late)}","at_platform":false,"platform_number":"${i % 4 + 1}","departure_sequence":$i}"""
        }
        val stopMap = names.mapIndexed { i, n -> """"${100 + i}":{"stop_id":${100 + i},"stop_name":"$n Station","stop_suburb":"Melbourne","route_type":0,"stop_latitude":${coords[i].first},"stop_longitude":${coords[i].second},"stop_sequence":$i}""" }.joinToString(",")
        val vehicle = """{"latitude":-37.8403,"longitude":144.9968,"bearing":140.0,"datetime_utc":"${t(0)}","expiry_time":"${t(5)}"}"""
        return """{"departures":[$deps],"stops":{$stopMap},"routes":{"6":${routeJson(frankston)}},"runs":{"6-0":{"run_ref":"6-0","route_id":6,"route_type":0,"destination_name":"Frankston","status":"scheduled","direction_id":1,"express_stop_count":0,"vehicle_descriptor":{"id":"1380T-1433T-159M","air_conditioned":true,"description":"X'Trapolis 100"},"vehicle_position":$vehicle}},"directions":{},"disruptions":{}}"""
    }

    fun start(): String {
        server.dispatcher = dispatcher
        server.start()
        return server.url("/").toString()
    }

    fun shutdown() = server.shutdown()
}
