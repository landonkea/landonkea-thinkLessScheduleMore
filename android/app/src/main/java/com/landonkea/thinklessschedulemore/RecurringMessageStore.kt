// ───────────────────────────────────────────────────────────────────
// RecurringMessageStore — yearly date-based messages (birthdays,
// anniversaries, etc.)
// ───────────────────────────────────────────────────────────────────
// Independent of MessageStore's random message pool: entries here
// GUARANTEE a send on their matching date (month + day, since it
// repeats every year — no year field), additive to whatever the
// normal random-pool schedule already sends that day. See
// RecurringMessageMatcher for the "does this fire today" logic and
// SchedulerService for where it's hooked into the daily loop.
//
// Same persistence style as MessageStore: SharedPreferences-backed,
// JSON array via org.json, sharing the same "thinkless_prefs" file
// (new keys, so it doesn't collide with the message pool).
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ── A single recurring (yearly) message ─────────────────────────────
data class RecurringMessage(
    val id: String,
    val month: Int,  // 1-12
    val day: Int,    // 1-31 (or 29 for a Feb 29 leap-day entry)
    val message: String
)

class RecurringMessageStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("thinkless_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RECURRING_MESSAGES = "recurring_messages"
        // Maps entry id -> the "yyyy-MM-dd" date it last actually fired on,
        // so SchedulerService doesn't re-send the same entry twice if it
        // re-evaluates the schedule more than once on the same day (e.g.
        // service restart, or scheduleNext() looping after a pool send).
        private const val KEY_LAST_FIRED = "recurring_last_fired"
    }

    // ── Recurring message list ──────────────────────────────────────
    fun getRecurringMessages(): List<RecurringMessage> {
        val raw = prefs.getString(KEY_RECURRING_MESSAGES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                RecurringMessage(
                    id = obj.getString("id"),
                    month = obj.getInt("month"),
                    day = obj.getInt("day"),
                    message = obj.getString("message")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRecurringMessages(entries: List<RecurringMessage>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("month", entry.month)
            obj.put("day", entry.day)
            obj.put("message", entry.message)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_RECURRING_MESSAGES, arr.toString()).apply()
    }

    // Adds a new entry with a freshly-generated id and returns it.
    fun addRecurringMessage(month: Int, day: Int, message: String): RecurringMessage {
        val entry = RecurringMessage(UUID.randomUUID().toString(), month, day, message)
        val current = getRecurringMessages().toMutableList()
        current.add(entry)
        saveRecurringMessages(current)
        return entry
    }

    fun updateRecurringMessage(id: String, month: Int, day: Int, message: String) {
        val current = getRecurringMessages().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return
        current[index] = RecurringMessage(id, month, day, message)
        saveRecurringMessages(current)
    }

    fun removeRecurringMessage(id: String) {
        val current = getRecurringMessages().toMutableList()
        current.removeAll { it.id == id }
        saveRecurringMessages(current)
    }

    // ── Last-fired tracking (per entry id) ───────────────────────────
    fun getLastFiredDateKey(id: String): String? {
        val raw = prefs.getString(KEY_LAST_FIRED, "") ?: ""
        if (raw.isEmpty()) return null
        return try {
            JSONObject(raw).optString(id, null.toString()).let { if (it == "null") null else it }
        } catch (e: Exception) {
            null
        }
    }

    fun setLastFiredDateKey(id: String, dateKey: String) {
        val raw = prefs.getString(KEY_LAST_FIRED, "") ?: ""
        val obj = try {
            if (raw.isEmpty()) JSONObject() else JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
        obj.put(id, dateKey)
        prefs.edit().putString(KEY_LAST_FIRED, obj.toString()).apply()
    }
}
