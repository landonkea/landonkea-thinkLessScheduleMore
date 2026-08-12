// ───────────────────────────────────────────────────────────────────
// NoSendDayChecker, "should the random pool schedule skip this day?"
// ───────────────────────────────────────────────────────────────────
// Pure logic, no android.icu.util.Calendar/Context dependency, callers
// (SchedulerService) compute the weekday int and "yyyy-MM-dd" date key
// however they like and pass in plain values, same split as
// RecurringMessageMatcher.
//
// Two independent ways a day can be blocked: a recurring weekday (e.g.
// "no sends on Saturday or Sunday") or a specific one-off date. Either
// one is enough to skip the day.
//
// Weekday ints use java.util.Calendar's convention (SUNDAY=1 ...
// SATURDAY=7) since that's what SchedulerService already computes
// everything else from, no new convention to remember.
//
// Scope: this only pauses the random message-pool schedule. Recurring
// (birthday/anniversary) messages from RecurringMessageStore are
// guaranteed sends by design and still fire on a no-send day, see
// SchedulerService's sendDueRecurringMessages, which isn't gated by
// this check.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

object NoSendDayChecker {

    fun isNoSendDay(
        weekday: Int,
        dateKey: String,
        noSendWeekdays: Set<Int>,
        noSendDates: List<String>
    ): Boolean {
        return weekday in noSendWeekdays || dateKey in noSendDates
    }
}
