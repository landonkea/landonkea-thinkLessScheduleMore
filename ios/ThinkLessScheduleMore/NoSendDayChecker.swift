// ───────────────────────────────────────────────────────────────────
// NoSendDayChecker, "should the random pool schedule skip this day?"
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's NoSendDayChecker.kt. Pure logic, no
// Foundation Calendar dependency in the comparison itself, callers
// (SchedulerManager) compute the weekday int and "yyyy-MM-dd" date
// key however they like and pass in plain values, same split as
// RecurringMessageMatcher.
//
// Two independent ways a day can be blocked: a recurring weekday (e.g.
// "no sends on Saturday or Sunday") or a specific one-off date. Either
// one is enough to skip the day.
//
// Weekday ints use Calendar's convention (Sunday=1 ... Saturday=7),
// same as java.util.Calendar on the Android side, so NoSendDayStore's
// persisted weekday set means the same thing on both platforms.
//
// Scope: this only pauses the random message-pool schedule. Recurring
// (birthday/anniversary) messages from RecurringMessageStore are
// guaranteed sends by design and still fire on a no-send day, see
// SchedulerManager's scheduleRecurringMessages, which isn't gated by
// this check.
// ───────────────────────────────────────────────────────────────────

import Foundation

enum NoSendDayChecker {

    static func isNoSendDay(
        weekday: Int,
        dateKey: String,
        noSendWeekdays: Set<Int>,
        noSendDates: [String]
    ) -> Bool {
        noSendWeekdays.contains(weekday) || noSendDates.contains(dateKey)
    }
}
