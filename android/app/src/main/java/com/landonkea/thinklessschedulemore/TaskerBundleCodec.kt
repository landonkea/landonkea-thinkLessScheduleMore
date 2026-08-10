// ───────────────────────────────────────────────────────────────────
// TaskerBundleCodec, packs/unpacks an action id + params into a
// Bundle, the shape Tasker stores and hands back at fire time
// ───────────────────────────────────────────────────────────────────
// Tasker's plugin contract (see TaskerEditActivity/TaskerFireReceiver)
// only ever moves a single android.os.Bundle across the boundary
// between "user configured this in Tasker's task editor" and "Tasker
// is now firing it", Tasker persists that Bundle itself and hands it
// straight back later, verbatim. This class is the one place that
// knows how an AutomationAction's (id, params) pair maps into and out
// of that Bundle's string keys, so TaskerEditActivity (writes it) and
// TaskerFireReceiver (reads it) can't drift out of sync with each
// other about the format.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.os.Bundle

object TaskerBundleCodec {

    private const val KEY_ACTION_ID = "action_id"
    private const val KEY_PARAM_PREFIX = "param_"

    fun encode(actionId: String, params: Map<String, String>): Bundle {
        val bundle = Bundle()
        bundle.putString(KEY_ACTION_ID, actionId)
        for ((key, value) in params) {
            bundle.putString(KEY_PARAM_PREFIX + key, value)
        }
        return bundle
    }

    /** Returns null if [bundle] has no action id at all, a Bundle from
     *  some other, unrelated Locale-plugin-compatible app, or genuinely
     *  corrupted data, rather than something this app itself produced. */
    fun decode(bundle: Bundle): Pair<String, Map<String, String>>? {
        val actionId = bundle.getString(KEY_ACTION_ID) ?: return null
        val params = bundle.keySet()
            .filter { it.startsWith(KEY_PARAM_PREFIX) }
            .associate { key -> key.removePrefix(KEY_PARAM_PREFIX) to (bundle.getString(key) ?: "") }
        return actionId to params
    }
}
