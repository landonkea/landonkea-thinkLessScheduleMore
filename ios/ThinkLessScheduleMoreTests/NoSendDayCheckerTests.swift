// ───────────────────────────────────────────────────────────────────
// NoSendDayCheckerTests, the "no send" day gating logic.
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's NoSendDayCheckerTest.kt. Pure logic, no
// Foundation Calendar dependency in the type under test itself.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class NoSendDayCheckerTests: XCTestCase {

    // Calendar's weekday convention: Sunday=1 ... Saturday=7.
    private let sunday = 1
    private let monday = 2
    private let wednesday = 4
    private let saturday = 7

    func testNotANoSendDayWhenBothSetsAreEmpty() {
        XCTAssertFalse(
            NoSendDayChecker.isNoSendDay(
                weekday: saturday,
                dateKey: "2026-08-15",
                noSendWeekdays: [],
                noSendDates: []
            )
        )
    }

    func testBlockedWhenWeekdayIsInTheNoSendWeekdaySet() {
        let weekends: Set<Int> = [saturday, sunday]
        XCTAssertTrue(
            NoSendDayChecker.isNoSendDay(
                weekday: sunday,
                dateKey: "2026-08-16",
                noSendWeekdays: weekends,
                noSendDates: []
            )
        )
    }

    func testBlockedWhenTheSpecificDateIsInTheNoSendDateList() {
        XCTAssertTrue(
            NoSendDayChecker.isNoSendDay(
                weekday: wednesday,
                dateKey: "2026-12-25",
                noSendWeekdays: [],
                noSendDates: ["2026-12-25"]
            )
        )
    }

    func testAWeekdayNotInTheSetAndADateNotInTheListIsNotBlocked() {
        XCTAssertFalse(
            NoSendDayChecker.isNoSendDay(
                weekday: monday,
                dateKey: "2026-08-17",
                noSendWeekdays: [saturday, sunday],
                noSendDates: ["2026-12-25"]
            )
        )
    }

    func testBothAMatchingWeekdayAndAMatchingDateStillBlocksNotExclusive() {
        XCTAssertTrue(
            NoSendDayChecker.isNoSendDay(
                weekday: saturday,
                dateKey: "2026-12-25",
                noSendWeekdays: [saturday],
                noSendDates: ["2026-12-25"]
            )
        )
    }
}
