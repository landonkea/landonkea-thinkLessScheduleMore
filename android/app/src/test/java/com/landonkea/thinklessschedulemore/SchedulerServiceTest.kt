// ───────────────────────────────────────────────────────────────────
// SchedulerServiceTest, proves the send window is resolved in local
// time, not UTC
// ───────────────────────────────────────────────────────────────────
// calculateRandomDelay/scheduleTomorrow used to derive "midnight" via
// now - (now % 86400000L), which is the most recent UTC midnight
// since System.currentTimeMillis() is a UTC epoch, while startHour/
// endHour are a local wall-clock hour read straight off a SeekBar in
// MainActivity. For anyone not literally in UTC, the configured send
// window landed at the wrong local hour. These tests pin the JVM's
// default timezone to a non-UTC zone and check the resolved window
// bound lands on the expected local wall-clock hour instead.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchedulerServiceTest {

    private val service = SchedulerService()
    private lateinit var originalDefault: TimeZone

    @Before
    fun setUp() {
        originalDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefault)
    }

    @Test
    fun `localHourToday resolves to the local wall-clock hour, not UTC`() {
        // 2026-01-15 10:30 local (Pacific is UTC-8 in January, no DST).
        val now = localTime(2026, Calendar.JANUARY, 15, 10, 30)

        val windowStartMs = service.localHourToday(now, 9)

        val result = Calendar.getInstance()
        result.timeInMillis = windowStartMs
        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, result.get(Calendar.MONTH))
        assertEquals(15, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))

        // The old bug resolved "9 AM" against UTC midnight instead,
        // landing 8 hours off in January; confirm we're not there.
        val utcMidnight = now - (now % 86400000L)
        assertNotEquals(utcMidnight + 9 * 3600000L, windowStartMs)
    }

    @Test
    fun `localHourToday with addDays advances one local day across a DST jump`() {
        // 2026-03-07 23:00 local, the night before the US spring-forward
        // (2026-03-08). A raw +86400000ms step would land on the wrong
        // wall-clock hour once the clocks jump; Calendar.add must not.
        val now = localTime(2026, Calendar.MARCH, 7, 23, 0)

        val tomorrowStartMs = service.localHourToday(now, 9, addDays = 1)

        val result = Calendar.getInstance()
        result.timeInMillis = tomorrowStartMs
        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, result.get(Calendar.MONTH))
        assertEquals(8, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }

    private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
