package com.ssethhyy.journey.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class FormattersTest {
    private val now = Instant.parse("2026-10-18T03:00:00Z")

    @Test
    fun countdownShowsNowMinutesThenClock() {
        assertEquals("Now", TimeFormatter.countdown(now.plusSeconds(30), now))
        assertEquals("Now", TimeFormatter.countdown(now.minusSeconds(90), now))
        assertEquals("1 min", TimeFormatter.countdown(now.plusSeconds(60), now))
        assertEquals("59 min", TimeFormatter.countdown(now.plusSeconds(59 * 60 + 59), now))
        val later = TimeFormatter.countdown(now.plusSeconds(75 * 60), now, Locale.UK, ZoneOffset.UTC)
        assertEquals("04:15", later)
    }

    @Test
    fun minutesUntilRoundsDown() {
        assertEquals(4L, TimeFormatter.minutesUntil(now.plusSeconds(299), now))
        assertEquals(-1L, TimeFormatter.minutesUntil(now.minusSeconds(1), now))
    }

    @Test
    fun punctuality() {
        assertNull(TimeFormatter.punctuality(null))
        assertEquals("On time", TimeFormatter.punctuality(0))
        assertEquals("3 min late", TimeFormatter.punctuality(3))
        assertEquals("1 min early", TimeFormatter.punctuality(-1))
    }

    @Test
    fun dateRange() {
        val from = Instant.parse("2026-10-18T00:00:00Z")
        val to = Instant.parse("2026-10-19T00:00:00Z")
        val range = TimeFormatter.dateRange(from, to, Locale.UK, ZoneOffset.UTC)!!
        assertTrue(range, range.startsWith("Sun 18 Oct") && "–" in range)
        assertNull(TimeFormatter.dateRange(null, null))
        assertTrue(TimeFormatter.dateRange(from, null, Locale.UK, ZoneOffset.UTC)!!.startsWith("From"))
    }

    @Test
    fun greetingFollowsMelbourneTime() {
        // 03:00 UTC = 14:00 AEDT
        assertEquals("Good afternoon", TimeFormatter.greeting(now))
    }

    @Test
    fun distanceFormatting() {
        Locale.setDefault(Locale.US)
        assertEquals("350 m", DistanceFormatter.distance(347.0))
        assertEquals("1.2 km", DistanceFormatter.distance(1234.0))
        assertEquals(1, DistanceFormatter.walkingMinutes(10.0))
        assertEquals(5, DistanceFormatter.walkingMinutes(400.0))
        assertEquals("400 m · 5 min walk", DistanceFormatter.distanceAndWalk(400.0))
    }

    @Test
    fun haversineFlindersToSouthernCross() {
        val d = GeoUtils.haversineMeters(-37.8183, 144.9671, -37.8184, 144.9525)
        assertTrue("was $d", d in 1200.0..1350.0)
    }

    @Test
    fun viewportRadiusHalvesPerZoomLevel() {
        val z14 = GeoUtils.viewportRadiusMeters(-37.8, 14.0, 400f, 800f)
        val z15 = GeoUtils.viewportRadiusMeters(-37.8, 15.0, 400f, 800f)
        assertEquals(z14 / 2, z15, 0.001)
        assertTrue("was $z14", z14 in 1500.0..3000.0)
    }

    @Test
    fun personalGreetingAndTodayLine() {
        // 03:00 UTC = 2:00 pm AEDT on Sunday 18 October 2026
        assertEquals("Good afternoon, Alex!", TimeFormatter.personalGreeting("  Alex ", now))
        assertEquals("Good afternoon!", TimeFormatter.personalGreeting("", now))
        assertEquals("Good morning!", TimeFormatter.personalGreeting(null, Instant.parse("2026-10-17T21:30:00Z")))
        assertEquals("Good evening!", TimeFormatter.personalGreeting(null, Instant.parse("2026-10-18T12:00:00Z")))
        val line = TimeFormatter.todayLine(now, Locale.UK)
        assertTrue(line, line.startsWith("Today is Sunday 18th October at "))
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map(TimeFormatter::ordinal))
    }
}
