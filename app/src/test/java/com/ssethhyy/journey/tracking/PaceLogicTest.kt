package com.ssethhyy.journey.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PaceLogicTest {
    private val now = Instant.parse("2026-11-28T08:00:00Z")

    @Test
    fun atTheStopThereIsNoPace() {
        assertNull(PaceLogic.pace(50.0, now.plusSeconds(600), now))
    }

    @Test
    fun paceRisesAsTimeRunsOut() {
        // 500 m straight is about 650 m on foot.
        assertEquals(Pace.LEISURELY, PaceLogic.pace(500.0, now.plusSeconds(1_200), now))
        assertEquals(Pace.WALK, PaceLogic.pace(500.0, now.plusSeconds(600), now))
        assertEquals(Pace.BRISK, PaceLogic.pace(500.0, now.plusSeconds(420), now))
        assertEquals(Pace.JOG, PaceLogic.pace(500.0, now.plusSeconds(300), now))
        assertEquals(Pace.RUN, PaceLogic.pace(500.0, now.plusSeconds(200), now))
        assertEquals(Pace.TOO_LATE, PaceLogic.pace(500.0, now.plusSeconds(100), now))
        assertEquals(Pace.TOO_LATE, PaceLogic.pace(500.0, now.minusSeconds(10), now))
    }

    @Test
    fun leaveAtAllowsAnOrdinaryWalkAndAMinuteSpare() {
        val departure = now.plusSeconds(1_800)
        // 540 m straight -> 702 m walking -> 520 s at 1.35 m/s, plus 60 s spare.
        assertEquals(departure.minusSeconds((702 / 1.35).toLong() + 60), PaceLogic.leaveAt(540.0, departure))
        assertEquals(9L, PaceLogic.walkMinutes(540.0))
        assertEquals(1L, PaceLogic.walkMinutes(10.0))
    }
}
