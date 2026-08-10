// ───────────────────────────────────────────────────────────────────
// NextSendFormatterTest, the Home Screen widget's display-text logic.
// ───────────────────────────────────────────────────────────────────
// NextSendFormatter is a pure function (no System.currentTimeMillis(),
// no Context, no AppWidgetManager) so every branch can be exercised
// deterministically here with injected `nowMs`/`timeZone` values,
// mirrors iOS's NextSendFormatterTests.swift.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NextSendFormatterTest {

    private val zone = TimeZone.getTimeZone("America/Los_Angeles")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(zone)
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, 0)
        return cal.timeInMillis
    }

    // ── displayText ────────────────────────────────────────────────

    @Test
    fun `no scheduled time shows nothing scheduled`() {
        val result = NextSendFormatter.displayText(null, "Sam")
        assertEquals("No messages scheduled", result)
    }

    @Test
    fun `zero next send time shows nothing scheduled`() {
        val result = NextSendFormatter.displayText(0L, "Sam")
        assertEquals("No messages scheduled", result)
    }

    @Test
    fun `future time today shows time and recipient`() {
        val now = millis(2026, 8, 4, 9, 0)
        val sendTime = millis(2026, 8, 4, 15, 30)
        val result = NextSendFormatter.displayText(sendTime, "Sam", now, zone)
        assertEquals("Next: 3:30 PM to Sam", result)
    }

    @Test
    fun `blank recipient name falls back to your partner`() {
        val now = millis(2026, 8, 4, 9, 0)
        val sendTime = millis(2026, 8, 4, 15, 30)
        val result = NextSendFormatter.displayText(sendTime, "", now, zone)
        assertEquals("Next: 3:30 PM to your partner", result)
    }

    @Test
    fun `time in past or present shows sending any moment`() {
        val now = millis(2026, 8, 4, 15, 30)
        val result = NextSendFormatter.displayText(now, "Sam", now, zone)
        assertEquals("Sending to Sam any moment", result)
    }

    @Test
    fun `tomorrow shows tomorrow prefix`() {
        val now = millis(2026, 8, 4, 22, 0)
        val sendTime = millis(2026, 8, 5, 9, 0)
        val result = NextSendFormatter.displayText(sendTime, "Sam", now, zone)
        assertEquals("Next: tomorrow 9:00 AM to Sam", result)
    }

    @Test
    fun `further out shows weekday and time`() {
        // now = Tuesday Aug 4 2026, send time = Friday Aug 7 2026.
        val now = millis(2026, 8, 4, 9, 0)
        val sendTime = millis(2026, 8, 7, 9, 0)
        val result = NextSendFormatter.displayText(sendTime, "Sam", now, zone)
        assertEquals("Next: Fri 9:00 AM to Sam", result)
    }

    // ── compactDisplayText ───────────────────────────────────────────

    @Test
    fun `compact no scheduled time`() {
        val result = NextSendFormatter.compactDisplayText(null)
        assertEquals("Nothing scheduled", result)
    }

    @Test
    fun `compact sending now`() {
        val now = millis(2026, 8, 4, 15, 30)
        val result = NextSendFormatter.compactDisplayText(now, now, zone)
        assertEquals("Sending now", result)
    }

    @Test
    fun `compact today shows just time`() {
        val now = millis(2026, 8, 4, 9, 0)
        val sendTime = millis(2026, 8, 4, 15, 30)
        val result = NextSendFormatter.compactDisplayText(sendTime, now, zone)
        assertEquals("3:30 PM", result)
    }

    @Test
    fun `compact tomorrow shows tomorrow prefix`() {
        val now = millis(2026, 8, 4, 22, 0)
        val sendTime = millis(2026, 8, 5, 9, 0)
        val result = NextSendFormatter.compactDisplayText(sendTime, now, zone)
        assertEquals("Tomorrow 9:00 AM", result)
    }
}
