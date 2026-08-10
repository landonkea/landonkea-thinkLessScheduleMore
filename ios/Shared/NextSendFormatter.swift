// NextSendFormatter.swift, Turns a NextSendSnapshot into the display
// string shown on the Home Screen widget (and reusable anywhere else in
// the app that wants the same "next message" summary).
//
// Kept as a pure function (no Date(), no UserDefaults, no WidgetKit
// imports) so it's directly unit-testable, mirrors MessageTemplate's
// pure `render`/`timeOfDay` functions in the app target.

import Foundation

enum NextSendFormatter {

    /// Builds the widget's headline text.
    ///
    /// - Parameters:
    ///   - nextSendDate: the next scheduled send time, or `nil` if
    ///     nothing is scheduled.
    ///   - recipientName: the recipient's display name; empty falls back
    ///     to "your partner" (same spirit as MessageTemplate's {name}
    ///     fallback to "there", just phrased for third-person widget text).
    ///   - now: injected so this stays pure/testable instead of calling
    ///     `Date()` internally.
    ///   - calendar: injected for the same reason (isDateInToday/Tomorrow
    ///     otherwise implicitly depend on the current calendar/timezone).
    static func displayText(
        nextSendDate: Date?,
        recipientName: String,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        guard let nextSendDate else {
            return "No messages scheduled"
        }

        let recipient = recipientName.isEmpty ? "your partner" : recipientName

        if nextSendDate <= now {
            return "Sending to \(recipient) any moment"
        }

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mm a"
        let time = timeFormatter.string(from: nextSendDate)

        if calendar.isDate(nextSendDate, inSameDayAs: now) {
            return "Next: \(time) to \(recipient)"
        }

        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now),
           calendar.isDate(nextSendDate, inSameDayAs: tomorrow) {
            return "Next: tomorrow \(time) to \(recipient)"
        }

        let dayFormatter = DateFormatter()
        dayFormatter.dateFormat = "EEE h:mm a"
        return "Next: \(dayFormatter.string(from: nextSendDate)) to \(recipient)"
    }

    /// A shorter variant for space-constrained layouts (e.g. the small
    /// widget size, or a lock-screen-style accessory view), just the
    /// time/status, no recipient name.
    static func compactDisplayText(
        nextSendDate: Date?,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        guard let nextSendDate else {
            return "Nothing scheduled"
        }

        if nextSendDate <= now {
            return "Sending now"
        }

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mm a"
        let time = timeFormatter.string(from: nextSendDate)

        if calendar.isDate(nextSendDate, inSameDayAs: now) {
            return time
        }

        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now),
           calendar.isDate(nextSendDate, inSameDayAs: tomorrow) {
            return "Tomorrow \(time)"
        }

        let dayFormatter = DateFormatter()
        dayFormatter.dateFormat = "EEE h:mm a"
        return dayFormatter.string(from: nextSendDate)
    }
}
