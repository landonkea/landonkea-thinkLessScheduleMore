// ───────────────────────────────────────────────────────────────────
// RecurringMessageStore — date-based recurring messages (iOS)
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's recurring-message design: a second, independent
// store that's additive to the random-pool schedule, not competing
// with it. Follows MessageStore.swift's exact pattern (Keys struct,
// @Published array persisted via didSet, UserDefaults-backed).
//
// A RecurringMessage fires once per calendar year on (month, day) —
// see RecurringMessageMatcher for the actual date-matching logic
// (including the Feb 29 policy) and SchedulerManager for how this
// store is consulted each time scheduleToday() runs.
// ───────────────────────────────────────────────────────────────────

import Foundation
import Combine

struct RecurringMessage: Identifiable, Codable, Equatable {
    let id: UUID
    var month: Int   // 1...12
    var day: Int     // 1...31 (validity vs. the specific month isn't enforced here)
    var message: String

    init(id: UUID = UUID(), month: Int, day: Int, message: String) {
        self.id = id
        self.month = month
        self.day = day
        self.message = message
    }
}

class RecurringMessageStore: ObservableObject {

    @Published var entries: [RecurringMessage] {
        didSet { saveEntries() }
    }

    // ── Last-fired-date guard ──────────────────────────────────────
    // Keyed by entry id -> "yyyy-MM-dd" string for the calendar day it
    // last fired on. Prevents scheduleToday() re-firing the same
    // recurring entry twice if it runs more than once on the same day
    // (e.g. app reopened). Kept as a simple persisted dictionary —
    // deliberately not trying to be clever about cleanup, entries are
    // few and strings are tiny.
    @Published private(set) var lastFiredDates: [String: String] {
        didSet { UserDefaults.standard.set(lastFiredDates, forKey: Keys.lastFiredDates) }
    }

    private struct Keys {
        static let entries = "recurring_messages"
        static let lastFiredDates = "recurring_messages_last_fired"
    }

    init() {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: Keys.entries),
           let decoded = try? JSONDecoder().decode([RecurringMessage].self, from: data) {
            entries = decoded
        } else {
            entries = []
        }
        lastFiredDates = defaults.dictionary(forKey: Keys.lastFiredDates) as? [String: String] ?? [:]
    }

    private func saveEntries() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        UserDefaults.standard.set(data, forKey: Keys.entries)
    }

    // ── Add/update/remove — analogous to MessageStore's message-pool
    // functions ──────────────────────────────────────────────────────
    func addEntry(month: Int, day: Int, message: String) {
        entries.append(RecurringMessage(month: month, day: day, message: message))
    }

    func updateEntry(id: UUID, month: Int, day: Int, message: String) {
        guard let index = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[index].month = month
        entries[index].day = day
        entries[index].message = message
    }

    func removeEntry(id: UUID) {
        entries.removeAll { $0.id == id }
    }

    func removeEntry(at index: Int) {
        guard entries.indices.contains(index) else { return }
        entries.remove(at: index)
    }

    // ── Guard against firing the same entry twice on the same day ───
    // `dayKey` is a caller-supplied "yyyy-MM-dd"-style string for
    // today (kept as a plain string, not Date, so this stays testable
    // without Calendar/timezone concerns leaking in).
    func hasFired(id: UUID, onDayKey dayKey: String) -> Bool {
        lastFiredDates[id.uuidString] == dayKey
    }

    func markFired(id: UUID, onDayKey dayKey: String) {
        lastFiredDates[id.uuidString] = dayKey
    }
}
