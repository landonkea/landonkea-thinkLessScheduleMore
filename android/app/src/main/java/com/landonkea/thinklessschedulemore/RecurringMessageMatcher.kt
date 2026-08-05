// ───────────────────────────────────────────────────────────────────
// RecurringMessageMatcher — "does this yearly entry fire today?"
// ───────────────────────────────────────────────────────────────────
// Pure logic, no java.time / android.icu.util.Calendar dependency —
// callers compute (month, day, isLeapYear) however they like (see
// SchedulerService, which uses java.util.Calendar) and pass in plain
// ints/booleans, so this is trivially unit-testable on the JVM.
//
// Policy: a Feb 29 recurring entry fires on Feb 28 in non-leap years,
// so it's never skipped entirely — the alternative (silently doing
// nothing for 3 out of every 4 years) would be a worse surprise for a
// birthday/anniversary reminder than firing a day early.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

object RecurringMessageMatcher {

    // Does a single (entryMonth, entryDay) entry fire on (todayMonth, todayDay)?
    fun matches(entryMonth: Int, entryDay: Int, todayMonth: Int, todayDay: Int, isLeapYear: Boolean): Boolean {
        if (entryMonth == 2 && entryDay == 29 && !isLeapYear) {
            // Policy: a Feb 29 recurring entry fires on Feb 28 in non-leap
            // years, so it's never skipped entirely.
            return todayMonth == 2 && todayDay == 28
        }
        return entryMonth == todayMonth && entryDay == todayDay
    }

    // Convenience for filtering a whole list of stored entries at once.
    fun matchesToday(
        entries: List<RecurringMessage>,
        todayMonth: Int,
        todayDay: Int,
        isLeapYear: Boolean
    ): List<RecurringMessage> =
        entries.filter { matches(it.month, it.day, todayMonth, todayDay, isLeapYear) }
}
