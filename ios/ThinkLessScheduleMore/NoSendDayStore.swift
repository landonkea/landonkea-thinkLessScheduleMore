// ───────────────────────────────────────────────────────────────────
// NoSendDayStore, days the random pool schedule should skip (iOS)
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's NoSendDayStore.kt and follows MessageStore.swift's
// own pattern (Keys struct, @Published property persisted via didSet,
// UserDefaults-backed).
//
// Two kinds of entries:
//   - Recurring weekdays (e.g. "no sends on Saturday or Sunday"),
//     stored as Calendar weekday ints (Sunday=1...Saturday=7, same
//     convention java.util.Calendar uses on Android).
//   - One-off specific dates ("yyyy-MM-dd"), for a single day off that
//     isn't a whole weekday (a trip, a holiday that isn't every year).
//
// Only the random pool schedule is gated by this, RecurringMessageStore
// entries (birthdays/anniversaries) are guaranteed sends and ignore
// this store entirely, see SchedulerManager.
// ───────────────────────────────────────────────────────────────────

import Foundation
import Combine

class NoSendDayStore: ObservableObject {

    @Published var noSendWeekdays: Set<Int> {
        didSet { UserDefaults.standard.set(Array(noSendWeekdays), forKey: Keys.weekdays) }
    }

    @Published var noSendDates: [String] {
        didSet { UserDefaults.standard.set(noSendDates, forKey: Keys.dates) }
    }

    private struct Keys {
        static let weekdays = "no_send_weekdays"
        static let dates = "no_send_dates"
    }

    init() {
        let defaults = UserDefaults.standard
        let savedWeekdays = defaults.array(forKey: Keys.weekdays) as? [Int] ?? []
        noSendWeekdays = Set(savedWeekdays)
        noSendDates = defaults.array(forKey: Keys.dates) as? [String] ?? []
    }

    func toggleWeekday(_ weekday: Int, blocked: Bool) {
        if blocked {
            noSendWeekdays.insert(weekday)
        } else {
            noSendWeekdays.remove(weekday)
        }
    }

    func addDate(_ dateKey: String) {
        guard !noSendDates.contains(dateKey) else { return }
        noSendDates.append(dateKey)
    }

    func removeDate(_ dateKey: String) {
        noSendDates.removeAll { $0 == dateKey }
    }

    func removeDate(at index: Int) {
        guard noSendDates.indices.contains(index) else { return }
        noSendDates.remove(at: index)
    }
}
