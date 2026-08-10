// ───────────────────────────────────────────────────────────────────
// RecurringMessageMatcherTest, "does this yearly entry fire today?"
// ───────────────────────────────────────────────────────────────────
// Pure logic, no Android dependencies, so it runs as a plain JVM
// unit test (no Robolectric needed).
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringMessageMatcherTest {

    @Test
    fun `matches when month and day are the same as today`() {
        assertTrue(RecurringMessageMatcher.matches(6, 15, todayMonth = 6, todayDay = 15, isLeapYear = false))
    }

    @Test
    fun `does not match a different month or day`() {
        assertFalse(RecurringMessageMatcher.matches(6, 15, todayMonth = 6, todayDay = 16, isLeapYear = false))
        assertFalse(RecurringMessageMatcher.matches(6, 15, todayMonth = 7, todayDay = 15, isLeapYear = false))
    }

    @Test
    fun `Feb 29 entry fires on Feb 29 in a leap year`() {
        assertTrue(RecurringMessageMatcher.matches(2, 29, todayMonth = 2, todayDay = 29, isLeapYear = true))
        // And should NOT fire early on Feb 28 in a leap year, Feb 29 exists this year.
        assertFalse(RecurringMessageMatcher.matches(2, 29, todayMonth = 2, todayDay = 28, isLeapYear = true))
    }

    @Test
    fun `Feb 29 entry fires on Feb 28 in a non-leap year, per documented policy`() {
        assertTrue(RecurringMessageMatcher.matches(2, 29, todayMonth = 2, todayDay = 28, isLeapYear = false))
        // And should not ALSO fire on Feb 29 in a non-leap year (that date doesn't exist,
        // but guard the matcher doesn't accidentally match it anyway).
        assertFalse(RecurringMessageMatcher.matches(2, 29, todayMonth = 2, todayDay = 29, isLeapYear = false))
    }

    @Test
    fun `matchesToday filters a list down to only the entries matching today`() {
        val entries = listOf(
            RecurringMessage(id = "1", month = 6, day = 15, message = "birthday"),
            RecurringMessage(id = "2", month = 6, day = 16, message = "not today"),
            RecurringMessage(id = "3", month = 2, day = 29, message = "leap birthday")
        )

        val matchesOnJune15 = RecurringMessageMatcher.matchesToday(entries, todayMonth = 6, todayDay = 15, isLeapYear = false)
        assertEquals(1, matchesOnJune15.size)
        assertEquals("1", matchesOnJune15[0].id)

        // On Feb 28 of a non-leap year, the Feb 29 entry should also match.
        val matchesOnFeb28NonLeap = RecurringMessageMatcher.matchesToday(entries, todayMonth = 2, todayDay = 28, isLeapYear = false)
        assertEquals(1, matchesOnFeb28NonLeap.size)
        assertEquals("3", matchesOnFeb28NonLeap[0].id)

        val matchesOnNeither = RecurringMessageMatcher.matchesToday(entries, todayMonth = 1, todayDay = 1, isLeapYear = false)
        assertTrue(matchesOnNeither.isEmpty())
    }
}
