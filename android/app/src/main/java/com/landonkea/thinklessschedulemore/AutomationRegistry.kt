// ───────────────────────────────────────────────────────────────────
// AutomationRegistry, the pool every trigger and action shares
// ───────────────────────────────────────────────────────────────────
// This is the piece that actually makes triggers and actions
// composable instead of hardwired: a trigger (SchedulerService's
// timer today, a Tasker plugin fire once TaskerPluginReceiver lands)
// never imports or references a concrete action class like
// SendSmsAction, it only knows an action's string id, looked up
// here. Adding a second action later means implementing
// AutomationAction and calling register() once at startup; every
// existing trigger can reach it immediately, with zero changes to
// SchedulerService or the Tasker plugin wiring.
//
// A plain Kotlin `object` (process-wide singleton) rather than
// something requiring DI/a framework, this app has no dependency-
// injection setup elsewhere (MessageStore/RecurringMessageStore are
// constructed directly with a Context), so this matches the existing
// style rather than introducing a new pattern for one file.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import androidx.annotation.VisibleForTesting

object AutomationRegistry {

    private val actions = mutableMapOf<String, AutomationAction>()

    /** Register (or replace) an action under its own [AutomationAction.id]. */
    fun register(action: AutomationAction) {
        actions[action.id] = action
    }

    /** All currently-registered actions, in registration order, used to
     *  build both this app's own "pick an action" UI and Tasker's plugin
     *  edit-activity action list, so neither hardcodes the current set. */
    fun allActions(): List<AutomationAction> = actions.values.toList()

    fun find(actionId: String): AutomationAction? = actions[actionId]

    /**
     * Look up [actionId] and run it, or return a failure result if no
     * action is registered under that id, callers (a trigger) never
     * need their own null-check branch, keeping every trigger's
     * dispatch code identical regardless of which action fires.
     */
    fun execute(
        context: android.content.Context,
        actionId: String,
        params: Map<String, String>,
    ): AutomationResult {
        val action = actions[actionId]
            ?: return AutomationResult(success = false, message = "No action registered with id \"$actionId\"")
        return action.execute(context, params)
    }

    /** Test-only: drop everything so each test starts from a clean
     *  registry instead of accumulating registrations across tests
     *  that share this process-wide singleton. */
    @VisibleForTesting
    fun clearForTesting() {
        actions.clear()
    }
}
