// ───────────────────────────────────────────────────────────────────
// NextSendFormatterTests, the Home Screen widget's display-text logic.
// ───────────────────────────────────────────────────────────────────
// NextSendFormatter is a pure function (no Date(), no UserDefaults, no
// WidgetKit) so every branch can be exercised deterministically here
// with injected `now`/`calendar` values.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class NextSendFormatterTests: XCTestCase {

    private let calendar = Calendar(identifier: .gregorian)

    private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int) -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        return calendar.date(from: components)!
    }

    // ── displayText ────────────────────────────────────────────────

    func testNoScheduledTimeShowsNothingScheduled() {
        let result = NextSendFormatter.displayText(nextSendDate: nil, recipientName: "Sam")
        XCTAssertEqual(result, "No messages scheduled")
    }

    func testFutureTimeTodayShowsTimeAndRecipient() {
        let now = date(2026, 8, 4, 9, 0)
        let sendTime = date(2026, 8, 4, 15, 30)
        let result = NextSendFormatter.displayText(
            nextSendDate: sendTime, recipientName: "Sam", now: now, calendar: calendar
        )
        XCTAssertEqual(result, "Next: 3:30 PM to Sam")
    }

    func testEmptyRecipientNameFallsBackToYourPartner() {
        let now = date(2026, 8, 4, 9, 0)
        let sendTime = date(2026, 8, 4, 15, 30)
        let result = NextSendFormatter.displayText(
            nextSendDate: sendTime, recipientName: "", now: now, calendar: calendar
        )
        XCTAssertEqual(result, "Next: 3:30 PM to your partner")
    }

    func testTimeInPastOrPresentShowsSendingAnyMoment() {
        let now = date(2026, 8, 4, 15, 30)
        let sendTime = date(2026, 8, 4, 15, 30)
        let result = NextSendFormatter.displayText(
            nextSendDate: sendTime, recipientName: "Sam", now: now, calendar: calendar
        )
        XCTAssertEqual(result, "Sending to Sam any moment")
    }

    func testTomorrowShowsTomorrowPrefix() {
        let now = date(2026, 8, 4, 22, 0)
        let sendTime = date(2026, 8, 5, 9, 0)
        let result = NextSendFormatter.displayText(
            nextSendDate: sendTime, recipientName: "Sam", now: now, calendar: calendar
        )
        XCTAssertEqual(result, "Next: tomorrow 9:00 AM to Sam")
    }

    func testFurtherOutShowsWeekdayAndTime() {
        // now = Tuesday Aug 4 2026, send time = Friday Aug 7 2026.
        let now = date(2026, 8, 4, 9, 0)
        let sendTime = date(2026, 8, 7, 9, 0)
        let result = NextSendFormatter.displayText(
            nextSendDate: sendTime, recipientName: "Sam", now: now, calendar: calendar
        )
        XCTAssertEqual(result, "Next: Fri 9:00 AM to Sam")
    }

    // ── compactDisplayText ───────────────────────────────────────────

    func testCompactNoScheduledTime() {
        let result = NextSendFormatter.compactDisplayText(nextSendDate: nil)
        XCTAssertEqual(result, "Nothing scheduled")
    }

    func testCompactSendingNow() {
        let now = date(2026, 8, 4, 15, 30)
        let result = NextSendFormatter.compactDisplayText(nextSendDate: now, now: now, calendar: calendar)
        XCTAssertEqual(result, "Sending now")
    }

    func testCompactTodayShowsJustTime() {
        let now = date(2026, 8, 4, 9, 0)
        let sendTime = date(2026, 8, 4, 15, 30)
        let result = NextSendFormatter.compactDisplayText(nextSendDate: sendTime, now: now, calendar: calendar)
        XCTAssertEqual(result, "3:30 PM")
    }

    func testCompactTomorrowShowsTomorrowPrefix() {
        let now = date(2026, 8, 4, 22, 0)
        let sendTime = date(2026, 8, 5, 9, 0)
        let result = NextSendFormatter.compactDisplayText(nextSendDate: sendTime, now: now, calendar: calendar)
        XCTAssertEqual(result, "Tomorrow 9:00 AM")
    }
}
