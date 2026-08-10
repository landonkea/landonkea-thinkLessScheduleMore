// ───────────────────────────────────────────────────────────────────
// SendSmsAction, the first AutomationAction implementation
// ───────────────────────────────────────────────────────────────────
// A thin adapter: all the real work is SmsSender's. This class exists
// so "send an SMS" is reachable by id ("send_sms") from
// AutomationRegistry, the same way any future action would be,
// instead of being something only SchedulerService's timer knows how
// to do.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Context

class SendSmsAction : AutomationAction {

    override val id = "send_sms"
    override val displayName = "Send SMS"
    override val paramSchema = mapOf(
        "recipient" to "Phone number to send to, e.g. +15551234567",
        "message" to "Message text to send",
    )

    override fun execute(context: Context, params: Map<String, String>): AutomationResult {
        val recipient = params["recipient"]
        val message = params["message"]

        if (recipient.isNullOrBlank()) {
            return AutomationResult(success = false, message = "Missing required param \"recipient\"")
        }
        if (message.isNullOrBlank()) {
            return AutomationResult(success = false, message = "Missing required param \"message\"")
        }

        return SmsSender(context).send(recipient, message)
    }
}
