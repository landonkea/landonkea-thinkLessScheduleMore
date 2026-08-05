// ───────────────────────────────────────────────────────────────────
// AutomationAction — one thing the app knows how to DO
// ───────────────────────────────────────────────────────────────────
// This is the "action" half of a composable trigger/action automation
// engine (Tasker's own model: any trigger can pair with any action,
// because neither one knows the other exists — they only know about
// this shared contract).
//
// SendSmsAction (see that file) is the first, and today the only,
// implementation — wrapping the exact same SMS-sending logic that
// used to live directly inside SchedulerService. Nothing about THIS
// interface is SMS-specific; a future action (toggle a setting, post
// a notification, whatever) implements the same three members and
// gets picked up by every existing trigger for free — see
// AutomationRegistry for how triggers reach an action without
// knowing its concrete type.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

/**
 * The outcome of one [AutomationAction.execute] call.
 *
 * "success" here means "the action was dispatched without a
 * synchronous failure" — for an inherently asynchronous action like
 * sending an SMS, that's "SmsManager.sendTextMessage didn't throw,"
 * not "the carrier confirmed delivery." Slower, asynchronous
 * confirmation (if any) is the action's own concern to log/surface
 * however makes sense for it — see SmsSender's delivery-receiver
 * handling for exactly that pattern.
 */
data class AutomationResult(
    val success: Boolean,
    val message: String,
)

/**
 * One composable action. Implementations should be side-effect-free
 * to construct (cheap to register at app startup) and should treat
 * [execute] as the only place real work happens.
 */
interface AutomationAction {
    /** Stable, unique identifier — e.g. "send_sms". Never shown to a
     *  user directly; this is what a trigger (a Tasker plugin fire, a
     *  scheduled timer, a future trigger type) uses to look this
     *  action up in [AutomationRegistry], so it must never change
     *  once shipped — a saved Tasker task references this id. */
    val id: String

    /** Human-readable name — e.g. "Send SMS". Shown in Tasker's
     *  action picker and this app's own UI, safe to reword anytime. */
    val displayName: String

    /**
     * The parameter keys this action expects in [execute]'s `params`
     * map, and a short description of each — used to build a real
     * configuration UI (both this app's own and Tasker's plugin edit
     * screen) instead of hardcoding fields per action.
     */
    val paramSchema: Map<String, String>

    /**
     * Run the action for real.
     *
     * @param context Android context — actions that need system
     *   services (SmsManager, NotificationManager, etc.) get it here
     *   rather than storing one at construction time, so a single
     *   registered action instance can safely be reused across many
     *   invocations from different callers/contexts.
     * @param params Values for this action's [paramSchema] keys — a
     *   trigger is responsible for supplying whatever the action
     *   declares it needs. An action should validate its own inputs
     *   defensively (a Tasker-supplied bundle isn't type-checked at
     *   compile time) and return a failed [AutomationResult] with a
     *   clear message rather than throwing.
     */
    fun execute(context: android.content.Context, params: Map<String, String>): AutomationResult
}
