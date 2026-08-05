// ───────────────────────────────────────────────────────────────────
// RecurringMessageMatcherTests — verifies the pure date-matching logic
// used by SchedulerManager's recurring (birthday/anniversary) message
// wiring, including the Feb 29 -> Feb 28 non-leap-year policy.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class RecurringMessageMatcherTests: XCTestCase {

    // ── matches: normal month/day cases ─────────────────────────────

    func testMatchesReturnsTrueOnExactMonthDayMatch() {
        XCTAssertTrue(
            RecurringMessageMatcher.matches(entryMonth: 7, entryDay: 4, todayMonth: 7, todayDay: 4, isLeapYear: false)
        )
    }

    func testMatchesReturnsFalseWhenDayDiffers() {
        XCTAssertFalse(
            RecurringMessageMatcher.matches(entryMonth: 7, entryDay: 4, todayMonth: 7, todayDay: 5, isLeapYear: false)
        )
    }

    func testMatchesReturnsFalseWhenMonthDiffers() {
        XCTAssertFalse(
            RecurringMessageMatcher.matches(entryMonth: 7, entryDay: 4, todayMonth: 8, todayDay: 4, isLeapYear: false)
        )
    }

    // ── Feb 29 policy ────────────────────────────────────────────────

    func testFeb29EntryFiresOnFeb29InLeapYear() {
        XCTAssertTrue(
            RecurringMessageMatcher.matches(entryMonth: 2, entryDay: 29, todayMonth: 2, todayDay: 29, isLeapYear: true)
        )
    }

    func testFeb29EntryDoesNotFireOnFeb28InLeapYear() {
        // In a leap year, Feb 29 exists, so the entry should fire on
        // Feb 29 itself, not "early" on Feb 28.
        XCTAssertFalse(
            RecurringMessageMatcher.matches(entryMonth: 2, entryDay: 29, todayMonth: 2, todayDay: 28, isLeapYear: true)
        )
    }

    func testFeb29EntryFiresOnFeb28InNonLeapYear() {
        // Feb 29 doesn't exist in a non-leap year — policy says fire
        // on Feb 28 instead so the message still shows up every year.
        XCTAssertTrue(
            RecurringMessageMatcher.matches(entryMonth: 2, entryDay: 29, todayMonth: 2, todayDay: 28, isLeapYear: false)
        )
    }

    func testFeb29EntryDoesNotFireOnMar1InNonLeapYear() {
        XCTAssertFalse(
            RecurringMessageMatcher.matches(entryMonth: 2, entryDay: 29, todayMonth: 3, todayDay: 1, isLeapYear: false)
        )
    }

    // ── entriesFiring: list filtering ───────────────────────────────

    func testEntriesFiringFiltersToOnlyMatchingEntries() {
        let entries = [
            RecurringMessage(month: 7, day: 4, message: "Happy 4th!"),
            RecurringMessage(month: 12, day: 25, message: "Merry Christmas!"),
            RecurringMessage(month: 7, day: 4, message: "Second July 4th message"),
        ]

        let firing = RecurringMessageMatcher.entriesFiring(
            entries: entries,
            todayMonth: 7,
            todayDay: 4,
            isLeapYear: false
        )

        XCTAssertEqual(firing.count, 2)
        XCTAssertTrue(firing.allSatisfy { $0.month == 7 && $0.day == 4 })
    }

    func testEntriesFiringReturnsEmptyWhenNothingMatches() {
        let entries = [
            RecurringMessage(month: 7, day: 4, message: "Happy 4th!"),
        ]

        let firing = RecurringMessageMatcher.entriesFiring(
            entries: entries,
            todayMonth: 1,
            todayDay: 1,
            isLeapYear: false
        )

        XCTAssertTrue(firing.isEmpty)
    }

    func testEntriesFiringAppliesFeb29PolicyAcrossTheList() {
        let entries = [
            RecurringMessage(month: 2, day: 29, message: "Leap birthday"),
            RecurringMessage(month: 2, day: 28, message: "Regular Feb 28 entry"),
        ]

        // Non-leap year, today is Feb 28 — both entries should fire:
        // the Feb 29 entry via the policy, and the Feb 28 entry directly.
        let firing = RecurringMessageMatcher.entriesFiring(
            entries: entries,
            todayMonth: 2,
            todayDay: 28,
            isLeapYear: false
        )

        XCTAssertEqual(firing.count, 2)
    }

    // ── isLeapYear ───────────────────────────────────────────────────

    func testIsLeapYearTrueWhenDivisibleBy4NotBy100() {
        XCTAssertTrue(RecurringMessageMatcher.isLeapYear(2024))
    }

    func testIsLeapYearFalseWhenDivisibleBy100NotBy400() {
        XCTAssertFalse(RecurringMessageMatcher.isLeapYear(1900))
    }

    func testIsLeapYearTrueWhenDivisibleBy400() {
        XCTAssertTrue(RecurringMessageMatcher.isLeapYear(2000))
    }

    func testIsLeapYearFalseForOrdinaryNonLeapYear() {
        XCTAssertFalse(RecurringMessageMatcher.isLeapYear(2023))
    }
}
