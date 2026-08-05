// ───────────────────────────────────────────────────────────────────
// NextSendFormatter — turns "next scheduled send" data into the
// display string shown on the Home Screen widget.
// ───────────────────────────────────────────────────────────────────
// Mirrors iOS's NextSendFormatter.swift. Kept as a pure function (no
// System.currentTimeMillis(), no Context, no AppWidgetManager) so it's
// directly unit-testable on the JVM without Robolectric — mirrors
// MessageTemplate.kt's pure `render`/`timeOfDay` functions.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NextSendFormatter {

    /// Builds the widget's headline text.
    ///
    /// @param nextSendTimeMs the next scheduled send time (epoch millis),
    ///   or null/0 if nothing is scheduled (matches MessageStore's
    ///   convention of 0L meaning "none").
    /// @param recipientName the recipient's display name; blank falls
    ///   back to "your partner" (same spirit as MessageTemplate's
    ///   {name} fallback to "there", phrased for third-person widget text).
    /// @param nowMs injected so this stays pure/testable instead of
    ///   calling System.currentTimeMillis() internally.
    /// @param timeZone injected for the same reason (day-boundary checks
    ///   otherwise implicitly depend on the device's current timezone).
    @JvmStatic
    @JvmOverloads
    fun displayText(
        nextSendTimeMs: Long?,
        recipientName: String,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (nextSendTimeMs == null || nextSendTimeMs <= 0L) {
            return "No messages scheduled"
        }

        val recipient = if (recipientName.isBlank()) "your partner" else recipientName

        if (nextSendTimeMs <= nowMs) {
            return "Sending to $recipient any moment"
        }

        val time = formatTime(nextSendTimeMs, timeZone)

        return when (dayOffset(nextSendTimeMs, nowMs, timeZone)) {
            0 -> "Next: $time to $recipient"
            1 -> "Next: tomorrow $time to $recipient"
            else -> "Next: ${formatWeekdayTime(nextSendTimeMs, timeZone)} to $recipient"
        }
    }

    /// A shorter variant for space-constrained layouts — just the
    /// time/status, no recipient name.
    @JvmStatic
    @JvmOverloads
    fun compactDisplayText(
        nextSendTimeMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (nextSendTimeMs == null || nextSendTimeMs <= 0L) {
            return "Nothing scheduled"
        }

        if (nextSendTimeMs <= nowMs) {
            return "Sending now"
        }

        val time = formatTime(nextSendTimeMs, timeZone)

        return when (dayOffset(nextSendTimeMs, nowMs, timeZone)) {
            0 -> time
            1 -> "Tomorrow $time"
            else -> formatWeekdayTime(nextSendTimeMs, timeZone)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /// 0 = same calendar day as `nowMs`, 1 = the day after, else -1
    /// (used only to fall through to the "further out" branch above).
    private fun dayOffset(targetMs: Long, nowMs: Long, timeZone: TimeZone): Int {
        val today = startOfDay(nowMs, timeZone)
        val target = startOfDay(targetMs, timeZone)
        val diffDays = (target - today) / (24L * 60 * 60 * 1000)
        return when (diffDays) {
            0L -> 0
            1L -> 1
            else -> -1
        }
    }

    private fun startOfDay(ms: Long, timeZone: TimeZone): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatTime(ms: Long, timeZone: TimeZone): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.US)
        formatter.timeZone = timeZone
        return formatter.format(Date(ms))
    }

    private fun formatWeekdayTime(ms: Long, timeZone: TimeZone): String {
        val formatter = SimpleDateFormat("EEE h:mm a", Locale.US)
        formatter.timeZone = timeZone
        return formatter.format(Date(ms))
    }
}
