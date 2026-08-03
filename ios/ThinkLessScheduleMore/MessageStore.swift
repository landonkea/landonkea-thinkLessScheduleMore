// ───────────────────────────────────────────────────────────────────
// MessageStore — stores all app data via UserDefaults
// ───────────────────────────────────────────────────────────────────
// UserDefaults is iOS's equivalent of Android's SharedPreferences.
// It's a simple key-value store that persists between app launches.
//
// This class wraps all read/write access so the rest of the app
// never touches UserDefaults directly.
// ───────────────────────────────────────────────────────────────────

import Foundation
import Combine  // For @Published (auto-refresh UI when data changes)

// ── MessageStore ──────────────────────────────────────────────────
// `ObservableObject` means SwiftUI watches it for changes.
// When any @Published property changes, the UI re-renders.
class MessageStore: ObservableObject {

    // ── Published properties ─────────────────────────────────────
    // These are the app's "source of truth."
    // Changing one of these automatically updates the UI.

    @Published var recipientNumber: String {
        didSet { UserDefaults.standard.set(recipientNumber, forKey: Keys.recipient) }
    }

    // ── Recipient display name (for {name} template substitution) ─
    // Separate from the phone number — a number doesn't tell us how
    // the user wants their partner addressed in a rendered message.
    @Published var recipientName: String {
        didSet { UserDefaults.standard.set(recipientName, forKey: Keys.recipientName) }
    }

    @Published var messages: [String] {
        didSet { saveMessages() }
    }

    @Published var hourStart: Int {
        didSet { UserDefaults.standard.set(hourStart, forKey: Keys.hourStart) }
    }

    @Published var hourEnd: Int {
        didSet { UserDefaults.standard.set(hourEnd, forKey: Keys.hourEnd) }
    }

    @Published var maxPerDay: Int {
        didSet { UserDefaults.standard.set(maxPerDay, forKey: Keys.maxPerDay) }
    }

    @Published var minInterval: Int {
        didSet { UserDefaults.standard.set(minInterval, forKey: Keys.minInterval) }
    }

    @Published var isEnabled: Bool {
        didSet { UserDefaults.standard.set(isEnabled, forKey: Keys.enabled) }
    }

    // ── Send log (persisted, structured — feeds the stats dashboard) ─
    // Previously this was `[String]` and explicitly NOT persisted
    // ("ephemeral for privacy" — reset every app launch). That made a
    // stats-over-time dashboard impossible: there was never more than
    // one session's worth of history to summarize. Persisting it here
    // doesn't change what leaves the device — it's the same on-device
    // UserDefaults storage Android already uses for its sent log — it
    // just stops silently discarding it on relaunch.
    @Published var sentLog: [SentLogEntry] {
        didSet { saveSentLog() }
    }

    // ── Next scheduled send time ─────────────────────────────────
    // Set by SchedulerManager whenever it (re)computes today's sends,
    // so the UI can surface "next message at ..." without duplicating
    // the scheduling math. Ephemeral like sentLog — recomputed each
    // time scheduling runs, not persisted to disk.
    @Published var nextScheduledTime: Date? = nil

    // ── Recently-sent messages (feeds MessageSelector's anti-repeat) ─
    // Persisted so a same-day repeat-open of the app (or the next
    // day's scheduling run) still remembers what just went out, not
    // just what's been picked earlier in the same scheduleToday() call.
    @Published var recentlySent: [String] {
        didSet { UserDefaults.standard.set(recentlySent, forKey: Keys.recentlySent) }
    }

    // ── Keys used in UserDefaults ────────────────────────────────
    private struct Keys {
        static let recipient = "recipient_number"
        static let recipientName = "recipient_name"
        static let messages  = "message_pool"
        static let hourStart = "hour_start"
        static let hourEnd   = "hour_end"
        static let maxPerDay = "max_per_day"
        static let minInterval = "min_interval"
        static let enabled   = "is_enabled"
        static let sentLog   = "sent_log"
        static let recentlySent = "recently_sent"
    }

    // ── Init: load saved data or use defaults ────────────────────
    init() {
        let defaults = UserDefaults.standard

        // Load each value, falling back to a sensible default.
        recipientNumber = defaults.string(forKey: Keys.recipient) ?? ""
        recipientName = defaults.string(forKey: Keys.recipientName) ?? ""
        hourStart = defaults.object(forKey: Keys.hourStart) as? Int ?? 9    // 9 AM
        hourEnd   = defaults.object(forKey: Keys.hourEnd) as? Int ?? 21     // 9 PM
        maxPerDay = defaults.object(forKey: Keys.maxPerDay) as? Int ?? 3
        minInterval = defaults.object(forKey: Keys.minInterval) as? Int ?? 60
        isEnabled = defaults.bool(forKey: Keys.enabled)
        if let data = defaults.data(forKey: Keys.sentLog),
           let decoded = try? JSONDecoder().decode([SentLogEntry].self, from: data) {
            sentLog = decoded
        } else {
            sentLog = []
        }
        recentlySent = defaults.array(forKey: Keys.recentlySent) as? [String] ?? []

        // Load messages from the saved array.
        if let saved = defaults.array(forKey: Keys.messages) as? [String] {
            messages = saved
        } else {
            // Default messages to get the user started.
            messages = [
                "Thinking of you ❤️",
                "Hope you're having a great day!",
                "You're amazing, don't forget that ✨",
                "Just wanted to say I love you 💕",
                "Can't wait to see you later!"
            ]
        }
    }

    // ── Persist messages array ───────────────────────────────────
    private func saveMessages() {
        UserDefaults.standard.set(messages, forKey: Keys.messages)
    }

    // ── Add one message ──────────────────────────────────────────
    func addMessage(_ text: String) {
        messages.append(text)
    }

    // ── Delete a message by index ────────────────────────────────
    func removeMessage(at index: Int) {
        guard messages.indices.contains(index) else { return }
        messages.remove(at: index)
    }

    // ── Edit the text of an existing message (not delete + re-add) ──
    func updateMessage(at index: Int, text: String) {
        guard messages.indices.contains(index) else { return }
        messages[index] = text
    }

    // ── Loose phone-number validation ────────────────────────────
    // Same permissive E.164-ish check as Android: optional leading
    // "+", 8-15 digits. A guard against fat-fingering, not a full
    // carrier-grade validator.
    static func isValidPhoneNumber(_ number: String) -> Bool {
        let regex = try! NSRegularExpression(pattern: "^\\+?[0-9]{8,15}$")
        let range = NSRange(number.startIndex..<number.endIndex, in: number)
        return regex.firstMatch(in: number, range: range) != nil
    }

    // ── Sent log (rolling log of last 50 entries, persisted) ──────
    func addToLog(id: UUID, timestamp: Date, status: String, message: String) {
        sentLog.insert(SentLogEntry(id: id, timestamp: timestamp, status: status, message: message), at: 0)  // Newest first
        if sentLog.count > 50 {
            sentLog = Array(sentLog.prefix(50))
        }
    }

    // ── Mark a pending entry as opened ────────────────────────────
    // Called when the user taps a scheduled-message notification
    // (see NotificationManager's `onOpen` callback). No-op if the id
    // isn't found (e.g. log was trimmed past 50 entries, or this is
    // a "wake up" notification with no associated log entry).
    func markOpened(_ id: UUID) {
        guard let index = sentLog.firstIndex(where: { $0.id == id }) else { return }
        sentLog[index].status = "opened"
    }

    private func saveSentLog() {
        guard let data = try? JSONEncoder().encode(sentLog) else { return }
        UserDefaults.standard.set(data, forKey: Keys.sentLog)
    }

    // ── Recently-sent history (feeds MessageSelector's anti-repeat) ─
    // Separate from sentLog (display/history oriented, session-only).
    // This is just the last few message *texts*, capped to
    // MessageSelector.historySize, purely to avoid back-to-back
    // repeats when picking the next message to send.
    func addRecentlySent(_ message: String) {
        var updated = recentlySent
        updated.append(message)  // Oldest first — MessageSelector reads the tail.
        if updated.count > MessageSelector.historySize {
            updated.removeFirst(updated.count - MessageSelector.historySize)
        }
        recentlySent = updated
    }
}
