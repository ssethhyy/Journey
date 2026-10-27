package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.DepartureBoard
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.Route
import com.ssethhyy.journey.data.model.SearchResults
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.SearchRanking
import com.ssethhyy.journey.data.repository.TtlCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RepositoryLogicTest {
    @Test
    fun ttlCacheExpiresEntries() {
        var time = 0L
        val cache = TtlCache<String, Int>(ttlMs = 100, clock = { time })
        cache.put("a", 1)
        time = 100
        assertEquals(1, cache.get("a"))
        time = 101
        assertNull(cache.get("a"))
    }

    @Test
    fun ttlCacheEvictsOldestWhenFull() {
        var time = 0L
        val cache = TtlCache<String, Int>(ttlMs = 1_000, maxEntries = 2, clock = { time })
        cache.put("a", 1)
        time = 1
        cache.put("b", 2)
        time = 2
        cache.put("c", 3)
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun getOrLoadUsesCacheUnlessForced() = runTest {
        val cache = TtlCache<String, Int>(ttlMs = 10_000)
        var calls = 0
        repeat(3) { cache.getOrLoad("k") { ++calls } }
        assertEquals(1, calls)
        assertEquals(2, cache.getOrLoad("k", force = true) { ++calls })
    }

    @Test
    fun searchRankingPrefersExactThenPrefixThenWordPrefix() {
        assertEquals(0, SearchRanking.score("flinders street", "Flinders Street"))
        assertEquals(1, SearchRanking.score("flin", "Flinders Street Station"))
        assertEquals(2, SearchRanking.score("street", "Flinders Street Station"))
        assertEquals(3, SearchRanking.score("nder", "Flinders Street"))
        assertEquals(4, SearchRanking.score("xyz", "Flinders Street"))
    }

    @Test
    fun searchRankingSortsStopsAndRoutes() {
        fun stop(id: Int, name: String, mode: TransportMode) = Stop(id, mode, name, null, 0.0, 0.0)
        val results = SearchResults(
            stops = listOf(
                stop(1, "Richmond Station/Swan St", TransportMode.TRAM),
                stop(2, "Richmond Station", TransportMode.TRAIN),
                stop(3, "North Richmond", TransportMode.TRAIN),
            ),
            routes = listOf(
                Route(1, TransportMode.BUS, "Richmond - Box Hill", "246"),
                Route(2, TransportMode.TRAM, "Richmond", "48"),
            ),
        )
        val ranked = SearchRanking.rank("richmond", results)
        assertEquals(listOf(2, 1, 3), ranked.stops.map { it.id })
        assertEquals(listOf(2, 1), ranked.routes.map { it.id })
    }

    @Test
    fun transportModeMapping() {
        assertEquals(TransportMode.TRAIN, TransportMode.fromRouteType(0))
        assertEquals(TransportMode.NIGHT_BUS, TransportMode.fromRouteType(4))
        assertEquals(TransportMode.BUS, TransportMode.fromRouteType(99))
        assertEquals(TransportMode.VLINE, TransportMode.fromDisruptionKey("regional_coach"))
        assertNull(TransportMode.fromDisruptionKey("general"))
    }

    @Test
    fun disruptionSeverityFromType() {
        assertEquals(DisruptionSeverity.SEVERE, Disruption.severityFor("Service Suspended"))
        assertEquals(DisruptionSeverity.SEVERE, Disruption.severityFor("Part Closure"))
        assertEquals(DisruptionSeverity.PLANNED, Disruption.severityFor("Planned Closure"))
        assertEquals(DisruptionSeverity.MAJOR, Disruption.severityFor("Major Delays"))
        assertEquals(DisruptionSeverity.MINOR, Disruption.severityFor("Minor Delays"))
        assertEquals(DisruptionSeverity.PLANNED, Disruption.severityFor("Planned Works"))
        assertEquals(DisruptionSeverity.INFO, Disruption.severityFor("Service Information"))
        assertEquals(DisruptionSeverity.INFO, Disruption.severityFor(null))
    }

    @Test
    fun stopDisruptionsIncludeLineDisruptionsReferencedByDepartures() {
        fun disruption(id: Long, type: String, status: String = "Current") = Disruption(
            id, "d$id", "", null, status, type, Instant.ofEpochSecond(id), null, null, emptyList(), emptyList(), TransportMode.TRAIN,
        )
        fun departure(vararg ids: Long) = Departure(
            1209, null, null, 1, null, null, null, null, null, false, null, ids.toList(), false, false, null,
        )
        val stopLevel = disruption(1, "Service Information")
        val lineWorks = disruption(2, "Planned Works")
        val suspension = disruption(3, "Service Suspended")
        val unreferenced = disruption(4, "Major Delays")
        val finished = disruption(5, "Minor Delays", status = "Completed")
        val board = DepartureBoard(
            departures = listOf(departure(2, 3), departure(2, 5)),
            disruptions = listOf(lineWorks, suspension, unreferenced, finished).associateBy { it.id },
            routes = emptyList(),
            fetchedAt = Instant.EPOCH,
        )

        val merged = Disruption.forStop(listOf(stopLevel, lineWorks), board)

        assertEquals(listOf(3L, 2L, 1L), merged.map { it.id })
        assertEquals(listOf(1L), Disruption.forStop(listOf(stopLevel), null).map { it.id })
    }

    @Test
    fun routeLabels() {
        val train = Route(6, TransportMode.TRAIN, "Frankston", "")
        val tram = Route(1, TransportMode.TRAM, "East Coburg - South Melbourne Beach", "1")
        assertEquals("Frankston", train.badgeLabel)
        assertEquals("Frankston", train.displayName)
        assertEquals("1", tram.badgeLabel)
        assertEquals("1 East Coburg - South Melbourne Beach", tram.displayName)
        val combined = Route(250, TransportMode.BUS, "City - La Trobe University", "250-251 combined")
        assertEquals("250-251", combined.badgeLabel)
        val smartBus = Route(901, TransportMode.BUS, "Frankston - Melbourne Airport", "901")
        assertEquals("901", smartBus.badgeLabel)
    }
}
