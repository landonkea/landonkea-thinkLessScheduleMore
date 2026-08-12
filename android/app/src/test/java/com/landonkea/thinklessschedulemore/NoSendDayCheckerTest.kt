// ───────────────────────────────────────────────────────────────────
// NoSendDayCheckerTest, the "no send" day gating logic.
// ───────────────────────────────────────────────────────────────────
// Pure logic, no Android dependencies, so it runs as a plain JVM
// unit test (no Robolectric needed).
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NoSendDayCheckerTest {

    @Test
    fun `not a no-send day when both sets are empty`() {
        assertFalse(
            NoSendDayChecker.isNoSendDay(
                weekday = Calendar.SATURDAY,
                dateKey = "2026-08-15",
                noSendWeekdays = emptySet(),
                noSendDates = emptyList()
            )
        )
    }

    @Test
    fun `blocked when weekday is in the no-send weekday set`() {
        val weekends = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        assertTrue(
            NoSendDayChecker.isNoSendDay(
                weekday = Calendar.SUNDAY,
                dateKey = "2026-08-16",
                noSendWeekdays = weekends,
                noSendDates = emptyList()
            )
        )
    }

    @Test
    fun `blocked when the specific date is in the no-send date list`() {
        assertTrue(
            NoSendDayChecker.isNoSendDay(
                weekday = Calendar.WEDNESDAY,
                dateKey = "2026-12-25",
                noSendWeekdays = emptySet(),
                noSendDates = listOf("2026-12-25")
            )
        )
    }

    @Test
    fun `a weekday not in the set and a date not in the list is not blocked`() {
        assertFalse(
            NoSendDayChecker.isNoSendDay(
                weekday = Calendar.MONDAY,
                dateKey = "2026-08-17",
                noSendWeekdays = setOf(Calendar.SATURDAY, Calendar.SUNDAY),
                noSendDates = listOf("2026-12-25")
            )
        )
    }

    @Test
    fun `both a matching weekday and a matching date still blocks (not exclusive)`() {
        assertTrue(
            NoSendDayChecker.isNoSendDay(
                weekday = Calendar.SATURDAY,
                dateKey = "2026-12-25",
                noSendWeekdays = setOf(Calendar.SATURDAY),
                noSendDates = listOf("2026-12-25")
            )
        )
    }
}
