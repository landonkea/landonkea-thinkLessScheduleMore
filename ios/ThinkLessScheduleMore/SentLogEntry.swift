// ───────────────────────────────────────────────────────────────────
// SentLogEntry — a single entry in the persisted send log
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's SentLogEntry (MessageStore.kt), with one
// necessary difference in `status` semantics: iOS cannot send SMS
// silently (see NotificationManager.swift), so there's no "sent" /
// "failed" outcome to report. Instead:
//   "pending" — a local notification was scheduled for this message;
//               the user hasn't tapped it yet.
//   "opened"  — the user tapped the notification and the Messages
//               app was opened pre-filled with this message. This is
//               the closest iOS gets to a confirmed send: iOS has no
//               API to know whether the user actually tapped "Send"
//               inside Messages afterward.
//
// `id` lets NotificationManager's tap handler find the matching log
// entry (via the notification's userInfo) and flip it from "pending"
// to "opened" without guessing by message text/time.
// ───────────────────────────────────────────────────────────────────

import Foundation

struct SentLogEntry: Codable, Identifiable, Equatable {
    let id: UUID
    let timestamp: Date
    var status: String   // "pending" or "opened"
    let message: String
}
