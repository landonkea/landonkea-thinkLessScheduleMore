// ───────────────────────────────────────────────────────────────────
// MessagePriorityStore, per-message weight for WeightedMessageSelector
// ───────────────────────────────────────────────────────────────────
// Entries are keyed by the exact message text, the same key
// MessageSelector/WeightedMessageSelector already compare against for
// the anti-repeat check, no separate id needed. Independent, additive
// SharedPreferences-backed store sharing the "thinkless_prefs" file,
// same pattern as NoSendDayStore/RecurringMessageStore.
//
// Keying by text means editing a message (MessageStore.updateMessage)
// orphans its old weight entry, that's an accepted tradeoff, not a
// bug, WeightedMessageSelector already treats any message missing a
// weight as PRIORITY_DEFAULT, so an orphaned entry is just harmless
// dead weight in the JSON blob, not a crash or a wrong pick.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class MessagePriorityStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("thinkless_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRIORITIES = "message_priorities"
        const val PRIORITY_MIN = 1
        const val PRIORITY_MAX = 10
        const val PRIORITY_DEFAULT = 1
    }

    // ── All weights, keyed by exact message text ────────────────────
    // Returned map is what WeightedMessageSelector.pick expects directly.
    fun getWeights(): Map<String, Int> {
        val raw = prefs.getString(KEY_PRIORITIES, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getInt(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getWeight(message: String): Int =
        getWeights()[message]?.takeIf { it > 0 } ?: PRIORITY_DEFAULT

    fun setWeight(message: String, weight: Int) {
        val current = getWeights().toMutableMap()
        current[message] = weight.coerceIn(PRIORITY_MIN, PRIORITY_MAX)
        saveWeights(current)
    }

    private fun saveWeights(weights: Map<String, Int>) {
        val obj = JSONObject()
        weights.forEach { (message, weight) -> obj.put(message, weight) }
        prefs.edit().putString(KEY_PRIORITIES, obj.toString()).apply()
    }
}
