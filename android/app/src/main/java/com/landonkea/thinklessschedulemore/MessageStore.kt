// ───────────────────────────────────────────────────────────────────
// MessageStore — stores all app data in SharedPreferences
// ───────────────────────────────────────────────────────────────────
// SharedPreferences is Android's simplest key-value storage.
// It's like a JSON file the phone manages for you.
// We use it instead of Room (database) because we only store:
//   1. A phone number (one string)
//   2. A list of messages (a small structured collection)
//   3. A few number settings (hour start, hour end, etc.)
//
// No complex queries needed.  SharedPreferences is perfect.
//
// NOTE on storage format: message pool + send log used to be stored
// as one big string joined with a "|||" / "|" delimiter. That broke
// if a message ever contained those exact characters (log parsing
// would silently corrupt). Both are now stored as JSON (via the
// built-in org.json — no new dependency) so arbitrary message text,
// including pipes, is safe.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// ── A single send-log entry ─────────────────────────────────────────
data class SentLogEntry(
    val timestamp: Long,
    val status: String,   // "sent", "failed", or "pending"
    val message: String,
    val error: String? = null
)

// ── MessageStore ──────────────────────────────────────────────────
// This class wraps all SharedPreferences access.
// The rest of the app calls messageStore.getMessages() etc.
// and never touches SharedPreferences directly.
class MessageStore(context: Context) {

    // ── SharedPreferences instance ───────────────────────────────
    // `Context.MODE_PRIVATE` means only this app can read it.
    private val prefs: SharedPreferences =
        context.getSharedPreferences("thinkless_prefs", Context.MODE_PRIVATE)

    // ── Key constants ────────────────────────────────────────────
    // These are the "column names" in our key-value store.
    companion object {
        private const val KEY_RECIPIENT = "recipient_number"
        private const val KEY_MESSAGES = "message_pool"
        private const val KEY_HOUR_START = "hour_start"
        private const val KEY_HOUR_END = "hour_end"
        private const val KEY_MAX_PER_DAY = "max_per_day"
        private const val KEY_INTERVAL_MIN = "interval_minutes"
        private const val KEY_ENABLED = "is_enabled"
        private const val KEY_SENT_LOG = "sent_log"
        private const val KEY_NEXT_SEND = "next_send_time"
        private const val LEGACY_DELIMITER = "|||"
    }

    // ── Recipient ────────────────────────────────────────────────
    fun getRecipient(): String = prefs.getString(KEY_RECIPIENT, "") ?: ""
    fun saveRecipient(number: String) {
        prefs.edit().putString(KEY_RECIPIENT, number).apply()
    }

    // ── Message pool (stored as a JSON array) ─────────────────────
    fun getMessages(): List<String> {
        val raw = prefs.getString(KEY_MESSAGES, "") ?: ""
        if (raw.isEmpty()) return emptyList()

        // Try JSON first (current format).
        try {
            val arr = JSONArray(raw)
            return List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            // Fall back to the legacy "|||"-delimited format so
            // existing installs don't lose their message pool.
            return raw.split(LEGACY_DELIMITER)
        }
    }

    // Save an entire list of messages.
    fun saveMessages(messages: List<String>) {
        val arr = JSONArray()
        messages.forEach { arr.put(it) }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
    }

    // Add one message to the pool.
    fun addMessage(text: String) {
        val current = getMessages().toMutableList()
        current.add(text)
        saveMessages(current)
    }

    // Update the text of an existing message by index (edit, not delete+add).
    fun updateMessage(index: Int, text: String) {
        val current = getMessages().toMutableList()
        if (index in current.indices) {
            current[index] = text
            saveMessages(current)
        }
    }

    // Remove one message by index.
    fun removeMessage(index: Int) {
        val current = getMessages().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveMessages(current)
        }
    }

    // ── Time window ──────────────────────────────────────────────
    fun getHourStart(): Int = prefs.getInt(KEY_HOUR_START, 9)     // Default 9 AM
    fun getHourEnd(): Int = prefs.getInt(KEY_HOUR_END, 21)        // Default 9 PM
    fun saveHourStart(hour: Int) = prefs.edit().putInt(KEY_HOUR_START, hour).apply()
    fun saveHourEnd(hour: Int) = prefs.edit().putInt(KEY_HOUR_END, hour).apply()

    // ── Limits ───────────────────────────────────────────────────
    fun getMaxPerDay(): Int = prefs.getInt(KEY_MAX_PER_DAY, 3)     // Default 3 messages/day
    fun getMinInterval(): Int = prefs.getInt(KEY_INTERVAL_MIN, 60) // Default 60 min between messages
    fun saveMaxPerDay(max: Int) = prefs.edit().putInt(KEY_MAX_PER_DAY, max).apply()
    fun saveMinInterval(min: Int) = prefs.edit().putInt(KEY_INTERVAL_MIN, min).apply()

    // ── Master switch ────────────────────────────────────────────
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ── Next scheduled send time (epoch millis, 0 = none scheduled) ─
    // Written by SchedulerService each time it arms a new timer, so
    // the UI can surface "next message at ..." without duplicating
    // the scheduling math.
    fun getNextSendTime(): Long = prefs.getLong(KEY_NEXT_SEND, 0L)
    fun saveNextSendTime(timestampMs: Long) {
        prefs.edit().putLong(KEY_NEXT_SEND, timestampMs).apply()
    }
    fun clearNextSendTime() = saveNextSendTime(0L)

    // ── Send log (stored as a JSON array of objects) ──────────────
    // Stored as a rolling log of the last 50 sends.
    fun getSentLog(): List<SentLogEntry> {
        val raw = prefs.getString(KEY_SENT_LOG, "") ?: ""
        if (raw.isEmpty()) return emptyList()

        try {
            val arr = JSONArray(raw)
            return List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                SentLogEntry(
                    timestamp = obj.getLong("timestamp"),
                    status = obj.getString("status"),
                    message = obj.getString("message"),
                    error = if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null
                )
            }
        } catch (e: Exception) {
            // Legacy "|||"-delimited, "timestamp|status|message[|error]" format.
            return raw.split(LEGACY_DELIMITER).mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 3) {
                    SentLogEntry(
                        timestamp = parts[0].toLongOrNull() ?: 0L,
                        status = parts[1],
                        message = parts[2],
                        error = parts.getOrNull(3)
                    )
                } else null
            }
        }
    }

    fun addToSentLog(timestamp: Long, status: String, message: String, error: String? = null) {
        val current = getSentLog().toMutableList()
        current.add(0, SentLogEntry(timestamp, status, message, error))  // Newest first
        if (current.size > 50) {
            current.removeAt(current.lastIndex)
        }
        val arr = JSONArray()
        current.forEach { entry ->
            val obj = JSONObject()
            obj.put("timestamp", entry.timestamp)
            obj.put("status", entry.status)
            obj.put("message", entry.message)
            if (entry.error != null) obj.put("error", entry.error)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SENT_LOG, arr.toString()).apply()
    }
}
