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

    @Published var sentLog: [String] {
        didSet { saveSentLog() }
    }

    // ── Keys used in UserDefaults ────────────────────────────────
    private struct Keys {
        static let recipient = "recipient_number"
        static let messages  = "message_pool"
        static let hourStart = "hour_start"
        static let hourEnd   = "hour_end"
        static let maxPerDay = "max_per_day"
        static let minInterval = "min_interval"
        static let enabled   = "is_enabled"
        static let sentLog   = "sent_log"
    }

    // ── Init: load saved data or use defaults ────────────────────
    init() {
        let defaults = UserDefaults.standard

        // Load each value, falling back to a sensible default.
        recipientNumber = defaults.string(forKey: Keys.recipient) ?? ""
        hourStart = defaults.object(forKey: Keys.hourStart) as? Int ?? 9    // 9 AM
        hourEnd   = defaults.object(forKey: Keys.hourEnd) as? Int ?? 21     // 9 PM
        maxPerDay = defaults.object(forKey: Keys.maxPerDay) as? Int ?? 3
        minInterval = defaults.object(forKey: Keys.minInterval) as? Int ?? 60
        isEnabled = defaults.bool(forKey: Keys.enabled)
        sentLog = []

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

    // ── Sent log (rolling log of last 50 sends) ──────────────────
    func addToLog(_ entry: String) {
        sentLog.insert(entry, at: 0)  // Newest first
        if sentLog.count > 50 {
            sentLog = Array(sentLog.prefix(50))
        }
    }

    private func saveSentLog() {
        // Sent log is ephemeral — we don't persist it to disk
        // for privacy reasons.  It resets when the app restarts.
        // This is intentional: the log is "what happened this session."
    }
}
