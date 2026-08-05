// ───────────────────────────────────────────────────────────────────
// TaskerFireReceiver — Tasker calls this when a configured task runs
// ───────────────────────────────────────────────────────────────────
// This is the "FIRE_SETTING" half of the Locale/Tasker plugin
// contract (the public, stable intent-based API originally defined by
// the Locale app, which Tasker also implements — any app that
// implements this contract is automatically usable from BOTH Tasker
// and Locale, and from any other app that speaks the same protocol).
// Tasker previously called TaskerEditActivity to let the user
// configure one action + its params, got back a Bundle (via
// TaskerBundleCodec), and now stores that Bundle as part of the
// user's saved Task. Every time that Task runs, Tasker re-sends this
// exact Bundle inside a FIRE_SETTING broadcast — this receiver's only
// job is decoding it and handing it to AutomationRegistry, the EXACT
// same call SchedulerService's timer trigger makes. Neither this
// receiver nor SchedulerService knows or cares what action actually
// runs — that's the whole point of the registry.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TaskerFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_SETTING) return

        val bundle = intent.getBundleExtra(EXTRA_BUNDLE)
        if (bundle == null) {
            Log.w(TAG, "FIRE_SETTING received with no bundle — ignoring")
            return
        }

        val decoded = TaskerBundleCodec.decode(bundle)
        if (decoded == null) {
            Log.w(TAG, "FIRE_SETTING bundle had no action_id — ignoring")
            return
        }
        val (actionId, params) = decoded

        val result = AutomationRegistry.execute(context, actionId, params)
        if (!result.success) {
            Log.w(TAG, "Tasker-fired action \"$actionId\" failed: ${result.message}")
        }
    }

    companion object {
        private const val TAG = "TaskerFireReceiver"

        // Standard Locale/Tasker plugin intent constants — these exact
        // strings are the public contract, not something this app
        // invented; changing them would silently stop working with
        // real Tasker.
        const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    }
}
