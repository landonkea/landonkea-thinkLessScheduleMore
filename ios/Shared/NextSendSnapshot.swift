// NextSendSnapshot.swift, Shared "when's the next message going out"
// state, crossing the process boundary between the main app and the
// Home Screen widget extension.
//
// WHY THIS FILE LIVES IN ITS OWN Shared FOLDER (mirrors the pattern used
// by landonkea-ytmusic-ios's Sources/YTMusicShared):
// The widget extension (ios/ThinkLessScheduleMoreWidget) runs in a
// separate OS process from the main app (ios/ThinkLessScheduleMore), a
// distinct extension target with its own sandbox. It cannot read
// MessageStore's @Published properties directly. Both targets compile
// this same file (see project.yml's `sources:` for each target), so they
// share one definition of the data that crosses the process boundary
// instead of two independently-maintained copies that could drift.
//
// HOW DATA CROSSES THE PROCESS BOUNDARY:
// The app and the widget extension both belong to the same "App Group",
// a sandboxed container both processes are allowed to read/write
// (declared in ThinkLessScheduleMore.entitlements and
// ThinkLessScheduleMoreWidget.entitlements). We use UserDefaults(suiteName:)
// scoped to that App Group as a lightweight key-value store.

import Foundation

/// A snapshot of "when's the next scheduled message," written by the main
/// app (MessageStore.nextScheduledTime's didSet) and read by the widget
/// extension's TimelineProvider.
struct NextSendSnapshot: Codable, Equatable {
    /// The next scheduled send time, or `nil` if nothing is currently
    /// scheduled (scheduling disabled, no recipient, or no messages).
    let nextSendDate: Date?
    /// The recipient's display name (falls back to "there" in the UI,
    /// same convention as MessageTemplate's {name} placeholder).
    let recipientName: String
    /// When this snapshot was written.
    let updatedAt: Date

    /// The "nothing scheduled" state, shown on fresh installs (before the
    /// app has ever written a snapshot) and whenever scheduling is off.
    static let empty = NextSendSnapshot(nextSendDate: nil, recipientName: "", updatedAt: Date())

    /// Sample data for widget gallery / SwiftUI previews.
    static let placeholderExample = NextSendSnapshot(
        nextSendDate: Date().addingTimeInterval(3 * 3600),
        recipientName: "Sam",
        updatedAt: Date()
    )
}

/// Reads/writes `NextSendSnapshot` through the App Group shared container
/// so the main app and the widget extension can exchange "next send" state
/// across the process boundary.
enum NextSendSnapshotStore {
    /// Must exactly match the App Group string configured in both targets'
    /// entitlements files (see project.yml → CODE_SIGN_ENTITLEMENTS).
    static let appGroupID = "group.com.landonkea.thinklessschedulemore"

    /// The WidgetKit "kind" identifier shared between the widget's
    /// `StaticConfiguration(kind:)` and the app's
    /// `WidgetCenter.reloadTimelines(ofKind:)` call, both must agree on
    /// this string or the reload silently targets nothing.
    static let widgetKind = "NextSendWidget"

    private static let storageKey = "nextSendSnapshot"

    private static var defaults: UserDefaults? {
        UserDefaults(suiteName: appGroupID)
    }

    /// Persist the current snapshot. Safe to call on every scheduling
    /// recompute, it's a tiny JSON blob written to UserDefaults.
    ///
    /// If the App Group is unavailable (e.g. a local build with no
    /// code-signing team configured, so the shared container can't be
    /// resolved), `UserDefaults(suiteName:)` returns `nil` and this
    /// silently no-ops, the main app's own scheduling is unaffected;
    /// only the widget would show stale/empty data until a real
    /// provisioning profile with this App Group capability is set up.
    static func save(_ snapshot: NextSendSnapshot) {
        guard let defaults, let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: storageKey)
    }

    /// Read the last-saved snapshot, or `.empty` if nothing has ever been
    /// written, the App Group is unavailable, or the stored data is
    /// somehow corrupt/undecodable, the widget always has *something*
    /// safe to draw.
    static func load() -> NextSendSnapshot {
        guard let defaults, let data = defaults.data(forKey: storageKey) else {
            return .empty
        }
        return (try? JSONDecoder().decode(NextSendSnapshot.self, from: data)) ?? .empty
    }
}
