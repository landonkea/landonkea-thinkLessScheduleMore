// ───────────────────────────────────────────────────────────────────
// MessageStore — stores all app data in SharedPreferences
// ───────────────────────────────────────────────────────────────────
// SharedPreferences is Android's simplest key-value storage.
// It's like a JSON file the phone manages for you.
// We use it instead of Room (database) because we only store:
//   1. A phone number (one string)
//   2. A list of messages (strings separated by a delimiter)
//   3. A few number settings (hour start, hour end, etc.)
//
// No complex queries needed.  SharedPreferences is perfect.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Context
import android.content.SharedPreferences

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
        private const val MESSAGE_DELIMITER = "|||"  // Unique separator unlikely to appear in messages
    }

    // ── Recipient ────────────────────────────────────────────────
    fun getRecipient(): String = prefs.getString(KEY_RECIPIENT, "") ?: ""
    fun saveRecipient(number: String) {
        prefs.edit().putString(KEY_RECIPIENT, number).apply()
    }

    // ── Message pool (stored as one big string, delimited) ───────
    fun getMessages(): List<String> {
        val raw = prefs.getString(KEY_MESSAGES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(MESSAGE_DELIMITER)
    }

    // Save an entire list of messages.
    fun saveMessages(messages: List<String>) {
        val raw = messages.joinToString(MESSAGE_DELIMITER)
        prefs.edit().putString(KEY_MESSAGES, raw).apply()
    }

    // Add one message to the pool.
    fun addMessage(text: String) {
        val current = getMessages().toMutableList()
        current.add(text)
        saveMessages(current)
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

    // ── Send log (JSON-like format, simple) ──────────────────────
    // Stored as a rolling log of the last 50 sends.
    // Each entry: "timestamp|status|message"
    fun getSentLog(): List<String> {
        val raw = prefs.getString(KEY_SENT_LOG, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(MESSAGE_DELIMITER)
    }

    fun addToSentLog(entry: String) {
        val current = getSentLog().toMutableList()
        current.add(0, entry)  // Newest first
        if (current.size > 50) {
            // Keep only the last 50 entries.
            current.removeAt(current.lastIndex)
        }
        val raw = current.joinToString(MESSAGE_DELIMITER)
        prefs.edit().putString(KEY_SENT_LOG, raw).apply()
    }
}
