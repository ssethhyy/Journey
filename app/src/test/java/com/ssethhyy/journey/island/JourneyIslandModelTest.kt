package com.ssethhyy.journey.island

import com.sameerasw.island.sdk.IslandPayload
import com.sameerasw.island.sdk.IslandPlacement
import com.sameerasw.island.sdk.IslandTone
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.model.DisruptionSeverity
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.IslandIconStyle
import com.ssethhyy.journey.data.repository.IslandSeverity
import com.ssethhyy.journey.data.repository.IslandStyle
import com.ssethhyy.journey.tracking.TrackingStatus
import com.ssethhyy.journey.tracking.TrackingTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Island payload rules, checked against the limits Essentials' PayloadValidator enforces. */
class JourneyIslandModelTest {
    private val now = Instant.parse("2026-10-20T07:00:00Z")
    private val target = TrackingTarget.Service("955123", TransportMode.TRAIN, 1071, "Westgarth", "Hurstbridge line", "Flinders Street")

    private fun status(chip: String, title: String = "Hurstbridge line in $chip", finished: Boolean = false) =
        TrackingStatus(title, "Westgarth · Platform 1 · to Flinders Street", chip, 0.4f, isLive = true, finished = finished, mode = TransportMode.TRAIN, updatedAt = now)

    private fun disruption(id: Long, type: String, title: String = "Disruption $id") = Disruption(
        id = id, title = title, description = "", url = null, status = "Current", type = type,
        publishedOn = now, fromDate = null, toDate = null, routes = emptyList(), stopNames = emptyList(), mode = TransportMode.TRAIN,
    )

    /** Mirrors com.sameerasw.essentials.island.remote.PayloadValidator so a regression fails here, not silently on device. */
    private fun assertValidForHost(payload: IslandPayload) {
        assertTrue("max 6 items", payload.items.size <= 6)
        assertEquals("unique item keys", payload.items.size, payload.items.map { it.key }.toSet().size)
        val allCellKeys = payload.items.flatMap { it.compact.map { c -> c.key } }
        assertEquals("cell keys unique across items", allCellKeys.size, allCellKeys.toSet().size)
        payload.items.forEach { item ->
            assertTrue(item.key.isNotBlank() && item.key.length <= 64)
            assertTrue("1 or 2 compact cells", item.compact.size in 1..2)
            item.compact.forEach { cell ->
                assertTrue(cell.key.length <= 64)
                assertTrue("cell text <= 32", (cell.text?.length ?: 0) <= 32)
                assertTrue(cell.tone in setOf(IslandTone.ACCENT, IslandTone.ALERT, IslandTone.NONE))
            }
            item.line?.let {
                assertTrue(it.start.length <= 120 && it.end.length <= 120)
                assertTrue(it.tone in setOf(IslandTone.ACCENT, IslandTone.ALERT, IslandTone.NONE))
            }
            item.expanded?.let { assertTrue("height 80..640", it.heightDp in 80..640) }
            item.priorityOverride?.let { assertTrue("override above notifications", it in 11..100) }
            assertTrue(item.priority in 11..100)
        }
    }

    @Test
    fun trackingItemShowsIconAndCountdown() {
        val payload = JourneyIsland.payload(AppSettings(), target, status("4 min"), emptyList())
        assertValidForHost(payload)
        val item = payload.items.single()
        assertEquals(JourneyIsland.ITEM_TRACKING, item.key)
        assertEquals("mode_train_picto", item.compact[0].iconId)
        assertEquals(IslandTone.NONE, item.compact[0].tone)
        assertEquals("4 min", item.compact[1].text)
        assertEquals("Hurstbridge line", item.line?.start)
        assertEquals("4 min · Westgarth", item.line?.end)
        assertNull(item.priorityOverride)
        assertEquals(IslandPlacement.DYNAMIC, item.placement)
    }

    @Test
    fun trackingBecomesUrgentAndRespectsIconAndPinSettings() {
        val settings = AppSettings(islandIconStyle = IslandIconStyle.MONOCHROME, islandPinned = true)
        val item = JourneyIsland.payload(settings, target, status("2 min"), emptyList()).items.single()
        assertEquals(JourneyIsland.PRIORITY_TRACKING_URGENT, item.priorityOverride)
        assertEquals("mode_train_mono", item.compact[0].iconId)
        assertEquals(IslandTone.ACCENT, item.compact[0].tone)
        assertEquals(IslandPlacement.PINNED, item.placement)
    }

    @Test
    fun disruptionsItemCountsAndColoursBySeverity() {
        val list = listOf(disruption(1, "Planned Works"), disruption(2, "Minor Delays"), disruption(3, "Major Delays", "Hurstbridge line: major delays"))
        val payload = JourneyIsland.payload(AppSettings(islandMinSeverity = IslandSeverity.ALL), null, null, list)
        assertValidForHost(payload)
        val item = payload.items.single()
        assertEquals("alert_major", item.compact[0].iconId)
        assertEquals(IslandTone.NONE, item.compact[0].tone)
        assertEquals("3", item.compact[1].text)
        assertEquals("3 service updates", item.line?.start)
        assertEquals("Hurstbridge line: major delays", item.line?.end)
        assertEquals(JourneyIsland.PRIORITY_DISRUPTIONS_URGENT, item.priorityOverride)

        // Default minimum (Delays+) drops planned works; Major-only keeps just the major one.
        assertEquals("2", JourneyIsland.payload(AppSettings(), null, null, list).items.single().compact[1].text)
        assertEquals("1", JourneyIsland.payload(AppSettings(islandMinSeverity = IslandSeverity.MAJOR), null, null, list).items.single().compact[1].text)
        // Only planned works with the default filter: nothing to show.
        assertTrue(JourneyIsland.payload(AppSettings(), null, null, listOf(disruption(9, "Planned Works"))).items.isEmpty())
    }

    @Test
    fun severityColoursAreDistinct() {
        val colours = DisruptionSeverity.entries.map(JourneyIsland::severityColor)
        assertEquals(colours.size, colours.toSet().size)
        assertEquals("alert_severe", JourneyIsland.Icons.alert(DisruptionSeverity.SEVERE))
    }

    @Test
    fun settingsTurnItemsOff() {
        val list = listOf(disruption(3, "Major Delays"))
        assertTrue(JourneyIsland.payload(AppSettings(islandEnabled = false), target, status("4 min"), list).items.isEmpty())
        assertEquals(listOf(JourneyIsland.ITEM_DISRUPTIONS), JourneyIsland.payload(AppSettings(islandTracking = false), target, status("4 min"), list).items.map { it.key })
        assertEquals(listOf(JourneyIsland.ITEM_TRACKING), JourneyIsland.payload(AppSettings(islandDisruptions = false), target, status("4 min"), list).items.map { it.key })
    }

    @Test
    fun longTextIsClippedToHostLimits() {
        val long = TrackingTarget.Trip("j", "Here", "A".repeat(200))
        val payload = JourneyIsland.payload(AppSettings(), long, status("12 min"), listOf(disruption(1, "Suspended", "B".repeat(300))))
        assertValidForHost(payload)
        assertTrue(payload.items.first().line!!.start.endsWith("…"))
    }

    @Test
    fun peekPolicyFiresAtMilestonesOnlyInLineStyle() {
        val line = IslandStyle.LINE
        assertTrue("first status peeks", TrackingPeekPolicy.shouldPeekTracking(null, status("9 min"), line))
        assertFalse(TrackingPeekPolicy.shouldPeekTracking(status("9 min"), status("8 min"), line))
        assertTrue("crosses 5", TrackingPeekPolicy.shouldPeekTracking(status("6 min"), status("5 min"), line))
        assertFalse(TrackingPeekPolicy.shouldPeekTracking(status("5 min"), status("4 min"), line))
        assertTrue("crosses 2", TrackingPeekPolicy.shouldPeekTracking(status("3 min"), status("1 min"), line))
        assertTrue("departing", TrackingPeekPolicy.shouldPeekTracking(status("1 min"), status("Now", "Hurstbridge line is departing now"), line))
        assertTrue("departed", TrackingPeekPolicy.shouldPeekTracking(status("Now"), status("Gone", "Hurstbridge line has departed", finished = true), line))
        assertTrue("journey phase change", TrackingPeekPolicy.shouldPeekTracking(status("4 min", "Hurstbridge in 4 min"), status("12 min", "On Hurstbridge · get off at Flinders Street"), line))
        assertFalse("compact style never peeks", TrackingPeekPolicy.shouldPeekTracking(null, status("1 min"), IslandStyle.COMPACT))
    }

    @Test
    fun disruptionPeeksOnlyForNewMajorOnes() {
        val major = disruption(5, "Major Delays")
        val minor = disruption(6, "Minor Delays")
        assertTrue(TrackingPeekPolicy.shouldPeekDisruptions(emptySet(), listOf(major), alertsEnabled = true))
        assertFalse(TrackingPeekPolicy.shouldPeekDisruptions(setOf(5L), listOf(major), alertsEnabled = true))
        assertFalse(TrackingPeekPolicy.shouldPeekDisruptions(emptySet(), listOf(minor), alertsEnabled = true))
        assertFalse(TrackingPeekPolicy.shouldPeekDisruptions(emptySet(), listOf(major), alertsEnabled = false))
    }

    @Test
    fun chipParsing() {
        assertEquals(0L, TrackingPeekPolicy.minutesFromChip("Now"))
        assertEquals(12L, TrackingPeekPolicy.minutesFromChip("12 min"))
        assertNull(TrackingPeekPolicy.minutesFromChip("1 hr 5 min"))
        assertNull(TrackingPeekPolicy.minutesFromChip("Gone"))
    }
}
