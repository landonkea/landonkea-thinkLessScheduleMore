// ───────────────────────────────────────────────────────────────────
// RecurringMessageMatcher, pure date-matching logic for recurring
// (anniversary/birthday-style) messages
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's recurring-message matcher for consistency across
// platforms. Deliberately pure Swift with no Foundation Date/Calendar
// dependency in the comparison itself, so it's trivially unit-testable
//, callers compute `isLeapYear` themselves (e.g. via
// Calendar.current.range(of:in:) or the simple %4/%100/%400 rule).
//
// Feb 29 policy (matches Android): an entry scheduled for Feb 29 fires
// on Feb 28 in non-leap years (rather than never firing, or firing on
// Mar 1) so a "Feb 29 birthday" message still shows up every year.
// ───────────────────────────────────────────────────────────────────

import Foundation

enum RecurringMessageMatcher {

    // Does a single (entryMonth, entryDay) entry fire on
    // (todayMonth, todayDay)?
    static func matches(entryMonth: Int, entryDay: Int, todayMonth: Int, todayDay: Int, isLeapYear: Bool) -> Bool {
        // Feb 29 entries in a non-leap year fire on Feb 28 instead.
        if entryMonth == 2 && entryDay == 29 && !isLeapYear {
            return todayMonth == 2 && todayDay == 28
        }
        return entryMonth == todayMonth && entryDay == todayDay
    }

    // Convenience: given a list of entries, return the ones that fire
    // today. `T` is left generic-ish via a closure-based extraction so
    // this doesn't need to know about RecurringMessage's exact shape,
    // but in practice we just filter RecurringMessageStore's array
    // directly using `matches` above, this exists for callers that
    // want the "which of these fire today" shape without repeating the
    // filter boilerplate.
    static func entriesFiring(
        entries: [RecurringMessage],
        todayMonth: Int,
        todayDay: Int,
        isLeapYear: Bool
    ) -> [RecurringMessage] {
        entries.filter {
            matches(entryMonth: $0.month, entryDay: $0.day, todayMonth: todayMonth, todayDay: todayDay, isLeapYear: isLeapYear)
        }
    }

    // Simple Gregorian leap-year rule, for callers that don't want to
    // reach for Calendar.
    static func isLeapYear(_ year: Int) -> Bool {
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}
