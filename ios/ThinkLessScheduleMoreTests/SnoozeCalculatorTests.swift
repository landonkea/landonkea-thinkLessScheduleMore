// ───────────────────────────────────────────────────────────────────
// SnoozeCalculatorTests — verifies the pure fire-date math used by
// NotificationManager's "Snooze 15 min" / "Snooze 1 hour" actions.
// ───────────────────────────────────────────────────────────────────

import XCTest
import UserNotifications
@testable import ThinkLessScheduleMore

final class SnoozeCalculatorTests: XCTestCase {

    // ── Fixed-duration correctness ──────────────────────────────────

    func testFifteenMinuteSnoozeAddsExactly900Seconds() {
        let start = Date(timeIntervalSince1970: 0)
        let result = SnoozeCalculator.newFireDate(from: start, snoozing: SnoozeDuration.fifteenMinutes.timeInterval)
        XCTAssertEqual(result.timeIntervalSince1970, 900)
    }

    func testOneHourSnoozeAddsExactly3600Seconds() {
        let start = Date(timeIntervalSince1970: 0)
        let result = SnoozeCalculator.newFireDate(from: start, snoozing: SnoozeDuration.oneHour.timeInterval)
        XCTAssertEqual(result.timeIntervalSince1970, 3600)
    }

    func testEnumOverloadMatchesRawTimeIntervalOverload() {
        let start = Date(timeIntervalSince1970: 12345)
        let viaEnum = SnoozeCalculator.newFireDate(from: start, snoozing: .fifteenMinutes)
        let viaInterval = SnoozeCalculator.newFireDate(from: start, snoozing: 15 * 60)
        XCTAssertEqual(viaEnum, viaInterval)
    }

    // ── Day-boundary crossing ────────────────────────────────────────

    func testFifteenMinuteSnoozeCrossesMidnightCorrectly() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!

        let elevenFifty = calendar.date(from: DateComponents(year: 2026, month: 3, day: 15, hour: 23, minute: 50))!
        let result = SnoozeCalculator.newFireDate(from: elevenFifty, snoozing: .fifteenMinutes)

        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: result)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 3)
        XCTAssertEqual(components.day, 16)
        XCTAssertEqual(components.hour, 0)
        XCTAssertEqual(components.minute, 5)
    }

    func testOneHourSnoozeCrossesMidnightCorrectly() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!

        let elevenThirty = calendar.date(from: DateComponents(year: 2026, month: 3, day: 15, hour: 23, minute: 30))!
        let result = SnoozeCalculator.newFireDate(from: elevenThirty, snoozing: .oneHour)

        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: result)
        XCTAssertEqual(components.day, 16)
        XCTAssertEqual(components.hour, 0)
        XCTAssertEqual(components.minute, 30)
    }

    // ── Month/year boundary ──────────────────────────────────────────

    func testSnoozeCrossesMonthBoundary() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!

        let endOfMonth = calendar.date(from: DateComponents(year: 2026, month: 1, day: 31, hour: 23, minute: 50))!
        let result = SnoozeCalculator.newFireDate(from: endOfMonth, snoozing: .fifteenMinutes)

        let components = calendar.dateComponents([.year, .month, .day], from: result)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 2)
        XCTAssertEqual(components.day, 1)
    }

    func testSnoozeCrossesYearBoundary() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!

        let newYearsEve = calendar.date(from: DateComponents(year: 2026, month: 12, day: 31, hour: 23, minute: 55))!
        let result = SnoozeCalculator.newFireDate(from: newYearsEve, snoozing: .oneHour)

        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: result)
        XCTAssertEqual(components.year, 2027)
        XCTAssertEqual(components.month, 1)
        XCTAssertEqual(components.day, 1)
        XCTAssertEqual(components.hour, 0)
        XCTAssertEqual(components.minute, 55)
    }

    // ── Stacking (applying snooze twice) ──────────────────────────────

    func testStackedFifteenMinuteSnoozesEqualDoubleOffset() {
        let start = Date(timeIntervalSince1970: 1000)
        let once = SnoozeCalculator.newFireDate(from: start, snoozing: .fifteenMinutes)
        let twice = SnoozeCalculator.newFireDate(from: once, snoozing: .fifteenMinutes)

        XCTAssertEqual(twice.timeIntervalSince(start), 2 * SnoozeDuration.fifteenMinutes.timeInterval)
    }

    func testStackedMixedSnoozesSumTheirDurations() {
        let start = Date(timeIntervalSince1970: 1000)
        let afterFifteen = SnoozeCalculator.newFireDate(from: start, snoozing: .fifteenMinutes)
        let afterHour = SnoozeCalculator.newFireDate(from: afterFifteen, snoozing: .oneHour)

        let expectedOffset = SnoozeDuration.fifteenMinutes.timeInterval + SnoozeDuration.oneHour.timeInterval
        XCTAssertEqual(afterHour.timeIntervalSince(start), expectedOffset)
    }

    // ── Action identifier mapping ─────────────────────────────────────

    func testSnoozeDurationFromActionIdentifier() {
        XCTAssertEqual(SnoozeDuration(actionIdentifier: "SNOOZE_15"), .fifteenMinutes)
        XCTAssertEqual(SnoozeDuration(actionIdentifier: "SNOOZE_60"), .oneHour)
        XCTAssertNil(SnoozeDuration(actionIdentifier: "UNKNOWN"))
        XCTAssertNil(SnoozeDuration(actionIdentifier: UNNotificationDefaultActionIdentifier))
    }
}
