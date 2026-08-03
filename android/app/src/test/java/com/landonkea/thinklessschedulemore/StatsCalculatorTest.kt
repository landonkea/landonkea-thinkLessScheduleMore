// ───────────────────────────────────────────────────────────────────
// StatsCalculatorTest — the stats-dashboard aggregation logic.
// ───────────────────────────────────────────────────────────────────
// Pure logic, no Android dependencies, so it runs as a plain JVM
// unit test (no Robolectric needed). `now` is always pinned so day
// bucketing and streak math don't depend on the real clock/timezone
// drifting between test runs.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsCalculatorTest {

    // A fixed "now" — noon on a fixed date — so day-boundary math is
    // stable regardless of when/where the test runs.
    private fun fixedNow(): Long {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JULY, 15, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun daysAgo(now: Long, days: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return cal.timeInMillis
    }

    @Test
    fun `empty log produces zeroed-out stats, not crashes or NaN`() {
        val stats = StatsCalculator.compute(emptyList(), now = fixedNow())

        assertEquals(0, stats.totalSent)
        assertEquals(0, stats.totalFailed)
        assertEquals(0.0, stats.successRate, 0.0001)
        assertEquals(0, stats.currentStreakDays)
        assertTrue(stats.topMessages.isEmpty())
        assertTrue(stats.dailyCounts.all { it.count == 0 })
    }

    @Test
    fun `success rate counts sent versus failed, ignoring other statuses`() {
        val now = fixedNow()
        val log = listOf(
            SentLogEntry(now, "sent", "a"),
            SentLogEntry(now, "sent", "b"),
            SentLogEntry(now, "sent", "c"),
            SentLogEntry(now, "failed", "d")
        )

        val stats = StatsCalculator.compute(log, now = now)

        assertEquals(3, stats.totalSent)
        assertEquals(1, stats.totalFailed)
        assertEquals(75.0, stats.successRate, 0.0001)
    }

    @Test
    fun `top messages are ranked by frequency with ties broken by first-seen order`() {
        val now = fixedNow()
        val log = listOf(
            SentLogEntry(now, "sent", "rare"),
            SentLogEntry(now, "sent", "common"),
            SentLogEntry(now, "sent", "common"),
            SentLogEntry(now, "sent", "tied-first"),
            SentLogEntry(now, "sent", "tied-second"),
            SentLogEntry(now, "failed", "common") // failed sends don't count
        )

        val top = StatsCalculator.compute(log, now = now, topN = 3).topMessages

        assertEquals(3, top.size)
        assertEquals("common", top[0].message)
        assertEquals(2, top[0].count)
        // Among the count-1 ties, "rare" was first-seen (index 0),
        // then "tied-first" (index 2), then "tied-second" (index 3).
        assertEquals("rare", top[1].message)
        assertEquals("tied-first", top[2].message)
    }

    @Test
    fun `daily counts bucket sends by calendar day across the requested window`() {
        val now = fixedNow()
        val log = listOf(
            SentLogEntry(now, "sent", "today-1"),
            SentLogEntry(now, "sent", "today-2"),
            SentLogEntry(daysAgo(now, 1), "sent", "yesterday"),
            SentLogEntry(daysAgo(now, 20), "sent", "too-old-to-appear")
        )

        val daily = StatsCalculator.compute(log, now = now, days = 14).dailyCounts

        assertEquals(14, daily.size)
        assertEquals(2, daily.last().count)   // today
        assertEquals(1, daily[daily.size - 2].count) // yesterday
        // The 20-days-ago entry falls outside the 14-day window, so
        // the total across all buckets should only be 3, not 4.
        assertEquals(3, daily.sumOf { it.count })
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val now = fixedNow()
        val log = listOf(
            SentLogEntry(now, "sent", "today"),
            SentLogEntry(daysAgo(now, 1), "sent", "yesterday"),
            SentLogEntry(daysAgo(now, 2), "sent", "two days ago"),
            // Gap at 3 days ago breaks the streak.
            SentLogEntry(daysAgo(now, 4), "sent", "four days ago")
        )

        val stats = StatsCalculator.compute(log, now = now)

        assertEquals(3, stats.currentStreakDays)
    }

    @Test
    fun `streak is zero when nothing was sent today`() {
        val now = fixedNow()
        val log = listOf(SentLogEntry(daysAgo(now, 1), "sent", "yesterday"))

        val stats = StatsCalculator.compute(log, now = now)

        assertEquals(0, stats.currentStreakDays)
    }
}
