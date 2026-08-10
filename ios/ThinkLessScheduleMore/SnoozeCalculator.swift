// ───────────────────────────────────────────────────────────────────
// SnoozeCalculator, pure fire-date math for notification snoozing
// ───────────────────────────────────────────────────────────────────
// Kept as a standalone, Foundation-only (no UserNotifications/UIKit)
// pure function so it's trivially unit-testable. `Date` is already an
// absolute point in time (seconds since a reference date), so adding
// a TimeInterval to it is correct across day/month/year boundaries
// with no calendar-aware arithmetic needed, the tests exist to prove
// that rather than to work around any subtlety.
// ───────────────────────────────────────────────────────────────────

import Foundation

// How long each snooze option delays the notification by.
enum SnoozeDuration: Equatable {
    case fifteenMinutes
    case oneHour

    var timeInterval: TimeInterval {
        switch self {
        case .fifteenMinutes: return 15 * 60
        case .oneHour: return 60 * 60
        }
    }

    // Notification action identifiers registered on the
    // "SCHEDULED_MESSAGE" category (see NotificationManager).
    static let snooze15ActionIdentifier = "SNOOZE_15"
    static let snooze60ActionIdentifier = "SNOOZE_60"

    // Maps an action identifier from a UNNotificationResponse back to
    // the SnoozeDuration it represents, or nil if it isn't a snooze
    // action at all (e.g. the default tap or dismiss action).
    init?(actionIdentifier: String) {
        switch actionIdentifier {
        case SnoozeDuration.snooze15ActionIdentifier: self = .fifteenMinutes
        case SnoozeDuration.snooze60ActionIdentifier: self = .oneHour
        default: return nil
        }
    }
}

enum SnoozeCalculator {
    // Given a fire date and a snooze duration, compute the new fire
    // date. Pure arithmetic on absolute Date values, day/month/year
    // boundaries "just work" and stacking is just repeated addition.
    static func newFireDate(from date: Date, snoozing duration: TimeInterval) -> Date {
        date.addingTimeInterval(duration)
    }

    // Convenience overload taking the enum directly.
    static func newFireDate(from date: Date, snoozing duration: SnoozeDuration) -> Date {
        newFireDate(from: date, snoozing: duration.timeInterval)
    }
}
