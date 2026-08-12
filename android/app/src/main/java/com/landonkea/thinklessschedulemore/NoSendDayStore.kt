// ───────────────────────────────────────────────────────────────────
// NoSendDayStore, days the random pool schedule should skip
// ───────────────────────────────────────────────────────────────────
// Same pattern as RecurringMessageStore: an independent, additive
// SharedPreferences-backed store sharing the "thinkless_prefs" file
// (new keys, no collision with MessageStore's own data), read by
// SchedulerService/NoSendDayChecker to decide whether to skip today's
// random-pool schedule.
//
// Two kinds of entries:
//   - Recurring weekdays (e.g. "no sends on Saturday or Sunday"),
//     stored as java.util.Calendar weekday ints (SUNDAY=1..SATURDAY=7).
//   - One-off specific dates ("yyyy-MM-dd"), for a single day off that
//     isn't a whole weekday (a trip, a holiday that isn't every year).
//
// Only the random pool schedule is gated by this, RecurringMessageStore
// entries (birthdays/anniversaries) are guaranteed sends and ignore
// this store entirely, see SchedulerService.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class NoSendDayStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("thinkless_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NO_SEND_WEEKDAYS = "no_send_weekdays"
        private const val KEY_NO_SEND_DATES = "no_send_dates"
    }

    // ── Recurring weekdays (Calendar.SUNDAY=1 ... Calendar.SATURDAY=7) ─
    fun getNoSendWeekdays(): Set<Int> {
        val raw = prefs.getString(KEY_NO_SEND_WEEKDAYS, "") ?: ""
        if (raw.isEmpty()) return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getInt(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setNoSendWeekdays(weekdays: Set<Int>) {
        val arr = JSONArray()
        weekdays.forEach { arr.put(it) }
        prefs.edit().putString(KEY_NO_SEND_WEEKDAYS, arr.toString()).apply()
    }

    fun toggleNoSendWeekday(weekday: Int, blocked: Boolean) {
        val current = getNoSendWeekdays().toMutableSet()
        if (blocked) current.add(weekday) else current.remove(weekday)
        setNoSendWeekdays(current)
    }

    // ── Specific one-off dates ("yyyy-MM-dd") ──────────────────────
    fun getNoSendDates(): List<String> {
        val raw = prefs.getString(KEY_NO_SEND_DATES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addNoSendDate(dateKey: String) {
        val current = getNoSendDates().toMutableList()
        if (dateKey !in current) {
            current.add(dateKey)
            saveNoSendDates(current)
        }
    }

    fun removeNoSendDate(dateKey: String) {
        val current = getNoSendDates().toMutableList()
        if (current.remove(dateKey)) {
            saveNoSendDates(current)
        }
    }

    private fun saveNoSendDates(dates: List<String>) {
        val arr = JSONArray()
        dates.forEach { arr.put(it) }
        prefs.edit().putString(KEY_NO_SEND_DATES, arr.toString()).apply()
    }
}
