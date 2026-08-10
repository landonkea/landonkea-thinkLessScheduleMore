// ───────────────────────────────────────────────────────────────────
// SendStatus, the lifecycle of one SMS send attempt
// ───────────────────────────────────────────────────────────────────
// Replaces the old ad-hoc "sent"/"failed"/"pending" free strings with
// a real enum, now that SchedulerService wires up SmsManager's
// sentIntent/deliveryIntent callbacks (see SmsResultMapper) and needs
// a fourth state, DELIVERED, that the old vocabulary had no room
// for.
//
// `raw` is the exact string persisted to JSON in MessageStore. Kept
// as simple lowercase words (not enum.name) so old installs' JSON on
// disk ("sent" / "failed" / "pending") still parses correctly via
// fromRaw, no migration step needed.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

enum class SendStatus(val raw: String) {
    PENDING("pending"),     // Log entry created, SmsManager call not yet confirmed.
    SENT("sent"),           // SmsManager confirmed the SMS left the device.
    DELIVERED("delivered"), // Carrier confirmed the SMS reached the recipient's device.
    FAILED("failed");       // SmsManager reported an error (or we caught an exception).

    companion object {
        // Tolerant of unknown/legacy raw strings, falls back to PENDING
        // rather than throwing, so a corrupted or future-versioned entry
        // never crashes the whole log read.
        fun fromRaw(raw: String): SendStatus = values().firstOrNull { it.raw == raw } ?: PENDING
    }
}
