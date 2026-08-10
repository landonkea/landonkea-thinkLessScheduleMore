// ───────────────────────────────────────────────────────────────────
// SmsSender, sends one SMS and logs its lifecycle, on its own
// ───────────────────────────────────────────────────────────────────
// Extracted out of SchedulerService, which used to own a single
// long-lived BroadcastReceiver (registered for the whole time the
// service ran) to catch SmsManager's sentIntent/deliveryIntent
// callbacks. That tied SMS-sending to "a SchedulerService instance
// happens to be alive right now", fine for the timer-driven
// schedule, but wrong for a Tasker-fired one-off send (see
// SendSmsAction/TaskerPluginReceiver), which can happen with no
// SchedulerService running at all.
//
// This class instead registers its own SHORT-LIVED receiver per
// send, scoped to that specific log entry, and unregisters itself
// once both callbacks have arrived (or after a timeout, in case a
// carrier never sends a delivery report, see TIMEOUT_MS). Any
// caller, SchedulerService's timer loop, the recurring-date check,
// or a future Tasker-fired action, gets identical behavior by going
// through this one class instead of duplicating the PendingIntent
// wiring.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class SmsSender(private val context: Context) {

    private val store = MessageStore(context)
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Send [message] to [recipient], logging a PENDING entry immediately
     * and updating it to SENT/DELIVERED/FAILED as SmsManager's async
     * callbacks arrive (or FAILED right away if the synchronous call
     * itself throws).
     *
     * Returns as soon as the log entry is created and the send is
     * dispatched, this does NOT wait for delivery confirmation, which
     * (per SmsResultMapper's docs) some carriers never send at all.
     * "success" in the returned AutomationResult means "dispatched
     * without a synchronous exception," matching AutomationAction's
     * documented contract.
     */
    fun send(recipient: String, message: String): AutomationResult {
        val logId = store.addToSentLog(System.currentTimeMillis(), SendStatus.PENDING, message)
        val receiver = registerResultReceiver(logId)

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)

            val sentIntent = PendingIntent.getBroadcast(
                context,
                logId.hashCode(),
                Intent(sentAction(logId)).apply { setPackage(context.packageName) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val deliveryIntent = PendingIntent.getBroadcast(
                context,
                (logId + "_delivered").hashCode(),
                Intent(deliveredAction(logId)).apply { setPackage(context.packageName) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            smsManager.sendTextMessage(recipient, null, message, sentIntent, deliveryIntent)

            // Belt-and-suspenders cleanup: unregister this send's
            // receiver after a timeout regardless of whether both
            // callbacks arrived, so a carrier that never fires a
            // delivery report doesn't leak a registered receiver
            // forever. Safe to call twice (see unregisterSafely).
            handler.postDelayed({ unregisterSafely(receiver) }, TIMEOUT_MS)

            AutomationResult(success = true, message = "SMS dispatched to $recipient")
        } catch (e: Exception) {
            store.updateLogEntryStatus(logId, SendStatus.FAILED, e.message)
            unregisterSafely(receiver)
            AutomationResult(success = false, message = "SMS send failed: ${e.message}")
        }
    }

    // ── Per-send receiver, scoped to one logId ──────────────────────
    // Uses per-logId action strings (rather than one shared action
    // string + an extra, as SchedulerService's old version did) so
    // this receiver only ever hears broadcasts for ITS OWN send,
    // simpler than filtering by extra inside onReceive, and means two
    // concurrent sends (e.g. a Tasker-fired one arriving mid-scheduled-
    // send) can never cross-deliver results to each other's receiver.
    private fun registerResultReceiver(logId: String): BroadcastReceiver {
        var deliveredSeen = false
        var sentSeen = false
        lateinit var receiver: BroadcastReceiver
        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    sentAction(logId) -> {
                        sentSeen = true
                        val status = SmsResultMapper.mapSentResult(resultCode)
                        val error = if (status == SendStatus.FAILED) "SMS send failed (resultCode=$resultCode)" else null
                        store.updateLogEntryStatus(logId, status, error)
                    }
                    deliveredAction(logId) -> {
                        deliveredSeen = true
                        // Only DELIVERED is meaningful, see SmsResultMapper's
                        // docs on carriers that never send a delivery report;
                        // don't downgrade a confirmed SENT to FAILED for that.
                        val status = SmsResultMapper.mapDeliveredResult(resultCode)
                        if (status == SendStatus.DELIVERED) {
                            store.updateLogEntryStatus(logId, SendStatus.DELIVERED)
                        }
                    }
                }
                if (sentSeen && deliveredSeen) unregisterSafely(receiver)
            }
        }
        val filter = IntentFilter().apply {
            addAction(sentAction(logId))
            addAction(deliveredAction(logId))
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return receiver
    }

    private fun unregisterSafely(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered (e.g. both callbacks arrived before the
            // timeout fired), not an error, just a race we don't need to
            // prevent, only handle gracefully.
        }
    }

    private fun sentAction(logId: String) = "com.landonkea.thinklessschedulemore.SMS_SENT.$logId"
    private fun deliveredAction(logId: String) = "com.landonkea.thinklessschedulemore.SMS_DELIVERED.$logId"

    companion object {
        private const val TIMEOUT_MS = 60_000L
    }
}
