package com.ssethhyy.journey.data

import com.ssethhyy.journey.data.repository.LivePositionRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Transport Victoria's GTFS Realtime position wins whenever it's live; PTV's is only the backup. */
class LivePositionRuleTest {
    private val now = Instant.parse("2026-11-28T05:00:00Z")

    @Test
    fun aRecentFeedPositionIsUsedEvenIfPtvsIsNewer() {
        assertTrue(LivePositionRule.feedIsLive(now.minusSeconds(40), now))
        assertTrue(LivePositionRule.feedIsLive(now.minusSeconds(300), now))
    }

    @Test
    fun aStaleFeedPositionFallsBackToPtv() {
        assertFalse(LivePositionRule.feedIsLive(now.minusSeconds(301), now))
    }

    @Test
    fun anUndatedFeedPositionCountsAsLive() {
        assertTrue(LivePositionRule.feedIsLive(null, now))
    }
}
