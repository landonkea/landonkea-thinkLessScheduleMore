// ───────────────────────────────────────────────────────────────────
// StatsCalculator, turns the raw send log into dashboard numbers
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's StatsCalculator.kt, adapted for iOS's status
// vocabulary (see SentLogEntry): there's no "sent"/"failed" outcome
// here, only "pending" (notification scheduled, not yet tapped) and
// "opened" (user tapped it and Messages opened pre-filled, the
// closest iOS gets to a confirmed send).
//
// Kept as a pure enum (no Foundation UI dependency beyond Date/
// Calendar) so it's trivially unit testable and StatsView can be a
// thin rendering layer, same split as Android's StatsCalculator/
// StatsActivity.
// ───────────────────────────────────────────────────────────────────

import Foundation

enum StatsCalculator {

    struct DailyCount: Identifiable {
        let id = UUID()
        let dayLabel: String
        let count: Int
    }

    struct MessageFrequency: Identifiable {
        let id = UUID()
        let message: String
        let count: Int
    }

    struct Stats {
        let totalOpened: Int
        let totalPending: Int
        // 0.0-100.0. 0 (not NaN) when there's nothing to report yet.
        let engagementRate: Double
        let dailyCounts: [DailyCount]
        let topMessages: [MessageFrequency]
        let currentStreakDays: Int
    }

    // Build the full stats bundle from the raw log.
    //
    // `days` controls how many trailing calendar days the "sends per
    // day" breakdown and streak calculation look back over (from
    // `now`, so tests can pin "today" instead of depending on the
    // real clock).
    static func compute(
        log: [SentLogEntry],
        days: Int = 14,
        topN: Int = 5,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> Stats {
        let opened = log.filter { $0.status == "opened" }
        let pending = log.filter { $0.status == "pending" }
        let total = opened.count + pending.count
        let engagementRate = total == 0 ? 0.0 : (Double(opened.count) * 100.0) / Double(total)

        return Stats(
            totalOpened: opened.count,
            totalPending: pending.count,
            engagementRate: engagementRate,
            dailyCounts: dailyCounts(opened: opened, days: days, now: now, calendar: calendar),
            topMessages: topMessages(opened: opened, topN: topN),
            currentStreakDays: currentStreakDays(opened: opened, now: now, calendar: calendar)
        )
    }

    // ── Opens per day, oldest → newest, for the trailing `days` days ─
    private static func dailyCounts(
        opened: [SentLogEntry], days: Int, now: Date, calendar: Calendar
    ) -> [DailyCount] {
        guard days > 0 else { return [] }

        let labelFormatter = DateFormatter()
        labelFormatter.dateFormat = "MMM d"

        var counts: [DateComponents: Int] = [:]
        for entry in opened {
            let key = calendar.dateComponents([.year, .month, .day], from: entry.timestamp)
            counts[key, default: 0] += 1
        }

        var result: [DailyCount] = []
        for i in stride(from: days - 1, through: 0, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -i, to: now) else { continue }
            let key = calendar.dateComponents([.year, .month, .day], from: day)
            result.append(DailyCount(dayLabel: labelFormatter.string(from: day), count: counts[key] ?? 0))
        }
        return result
    }

    // ── Most-frequently-opened message texts ──────────────────────
    // Ties break by first-seen order (stable, deterministic).
    private static func topMessages(opened: [SentLogEntry], topN: Int) -> [MessageFrequency] {
        guard !opened.isEmpty, topN > 0 else { return [] }

        var counts: [String: Int] = [:]
        var firstSeenOrder: [String] = []
        for entry in opened {
            if counts[entry.message] == nil {
                firstSeenOrder.append(entry.message)
            }
            counts[entry.message, default: 0] += 1
        }

        let ranked = firstSeenOrder.enumerated()
            .map { (index, message) in (message: message, count: counts[message]!, index: index) }
            .sorted { lhs, rhs in
                if lhs.count != rhs.count { return lhs.count > rhs.count }
                return lhs.index < rhs.index
            }

        return ranked.prefix(topN).map { MessageFrequency(message: $0.message, count: $0.count) }
    }

    // ── Current streak of consecutive days with >= 1 "opened" entry ──
    // Counts backward from "today" (relative to `now`). Zero if
    // nothing was opened today.
    private static func currentStreakDays(opened: [SentLogEntry], now: Date, calendar: Calendar) -> Int {
        guard !opened.isEmpty else { return 0 }

        let openedDays = Set(opened.map { calendar.dateComponents([.year, .month, .day], from: $0.timestamp) })

        var streak = 0
        while true {
            guard let day = calendar.date(byAdding: .day, value: -streak, to: now) else { break }
            let key = calendar.dateComponents([.year, .month, .day], from: day)
            if openedDays.contains(key) {
                streak += 1
            } else {
                break
            }
        }
        return streak
    }
}
