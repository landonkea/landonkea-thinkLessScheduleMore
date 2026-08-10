// ───────────────────────────────────────────────────────────────────
// StatsCalculatorTests, the stats-dashboard aggregation logic.
// ───────────────────────────────────────────────────────────────────
// Pure logic, no UIKit dependency, so these run fast without a
// simulator's app lifecycle involved. `now`/`calendar` are always
// pinned so day bucketing and streak math don't depend on the real
// clock/timezone drifting between test runs.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class StatsCalculatorTests: XCTestCase {

    private var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }

    private func fixedNow() -> Date {
        var components = DateComponents()
        components.year = 2026
        components.month = 7
        components.day = 15
        components.hour = 12
        return calendar.date(from: components)!
    }

    private func daysAgo(_ now: Date, _ days: Int) -> Date {
        calendar.date(byAdding: .day, value: -days, to: now)!
    }

    func testEmptyLogProducesZeroedOutStats() {
        let stats = StatsCalculator.compute(log: [], now: fixedNow(), calendar: calendar)

        XCTAssertEqual(stats.totalOpened, 0)
        XCTAssertEqual(stats.totalPending, 0)
        XCTAssertEqual(stats.engagementRate, 0.0, accuracy: 0.0001)
        XCTAssertEqual(stats.currentStreakDays, 0)
        XCTAssertTrue(stats.topMessages.isEmpty)
        XCTAssertTrue(stats.dailyCounts.allSatisfy { $0.count == 0 })
    }

    func testEngagementRateCountsOpenedVersusPending() {
        let now = fixedNow()
        let log = [
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "a"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "b"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "c"),
            SentLogEntry(id: UUID(), timestamp: now, status: "pending", message: "d")
        ]

        let stats = StatsCalculator.compute(log: log, now: now, calendar: calendar)

        XCTAssertEqual(stats.totalOpened, 3)
        XCTAssertEqual(stats.totalPending, 1)
        XCTAssertEqual(stats.engagementRate, 75.0, accuracy: 0.0001)
    }

    func testTopMessagesRankedByFrequencyTiesBrokenByFirstSeen() {
        let now = fixedNow()
        let log = [
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "rare"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "common"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "common"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "tied-first"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "tied-second"),
            SentLogEntry(id: UUID(), timestamp: now, status: "pending", message: "common") // pending doesn't count
        ]

        let top = StatsCalculator.compute(log: log, topN: 3, now: now, calendar: calendar).topMessages

        XCTAssertEqual(top.count, 3)
        XCTAssertEqual(top[0].message, "common")
        XCTAssertEqual(top[0].count, 2)
        // Among the count-1 ties, "rare" was first-seen, then "tied-first".
        XCTAssertEqual(top[1].message, "rare")
        XCTAssertEqual(top[2].message, "tied-first")
    }

    func testDailyCountsBucketOpensByCalendarDayAcrossWindow() {
        let now = fixedNow()
        let log = [
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "today-1"),
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "today-2"),
            SentLogEntry(id: UUID(), timestamp: daysAgo(now, 1), status: "opened", message: "yesterday"),
            SentLogEntry(id: UUID(), timestamp: daysAgo(now, 20), status: "opened", message: "too-old-to-appear")
        ]

        let daily = StatsCalculator.compute(log: log, days: 14, now: now, calendar: calendar).dailyCounts

        XCTAssertEqual(daily.count, 14)
        XCTAssertEqual(daily.last?.count, 2) // today
        XCTAssertEqual(daily[daily.count - 2].count, 1) // yesterday
        // The 20-days-ago entry falls outside the 14-day window.
        XCTAssertEqual(daily.reduce(0) { $0 + $1.count }, 3)
    }

    func testStreakCountsConsecutiveDaysEndingToday() {
        let now = fixedNow()
        let log = [
            SentLogEntry(id: UUID(), timestamp: now, status: "opened", message: "today"),
            SentLogEntry(id: UUID(), timestamp: daysAgo(now, 1), status: "opened", message: "yesterday"),
            SentLogEntry(id: UUID(), timestamp: daysAgo(now, 2), status: "opened", message: "two days ago"),
            // Gap at 3 days ago breaks the streak.
            SentLogEntry(id: UUID(), timestamp: daysAgo(now, 4), status: "opened", message: "four days ago")
        ]

        let stats = StatsCalculator.compute(log: log, now: now, calendar: calendar)

        XCTAssertEqual(stats.currentStreakDays, 3)
    }

    func testStreakIsZeroWhenNothingOpenedToday() {
        let now = fixedNow()
        let log = [SentLogEntry(id: UUID(), timestamp: daysAgo(now, 1), status: "opened", message: "yesterday")]

        let stats = StatsCalculator.compute(log: log, now: now, calendar: calendar)

        XCTAssertEqual(stats.currentStreakDays, 0)
    }
}
