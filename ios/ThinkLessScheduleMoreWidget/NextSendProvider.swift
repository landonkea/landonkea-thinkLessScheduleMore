// NextSendProvider.swift, Supplies timeline entries to NextSendWidget.
//
// HOW WIDGETKIT TIMELINES WORK:
// A widget doesn't run continuously like the main app, the system asks a
// `TimelineProvider` for a `Timeline` (a list of entries, each with a
// date), then renders whichever entry's date has most recently passed, on
// its own schedule.
//
// Unlike a "now playing" widget (which only changes when the app tells
// it to), this widget's headline text becomes stale the moment the next
// send time itself arrives ("Next: 3:45 PM" should flip to "Sending any
// moment" / then eventually a new send time once the app reschedules).
// So on top of the app pushing reloads via
// `WidgetCenter.shared.reloadTimelines(ofKind:)` whenever
// MessageStore.nextScheduledTime changes (see MessageStore.swift), this
// provider ALSO schedules a self-refresh for the moment the currently
// known send time passes, so the widget's text stays accurate even if the
// app never reopens in between.

import WidgetKit

/// One rendered instant of the Next Send widget.
struct NextSendEntry: TimelineEntry {
    let date: Date
    let snapshot: NextSendSnapshot
}

struct NextSendProvider: TimelineProvider {
    /// Shown instantly while the widget is loading its first real entry,
    /// SwiftUI previews and the widget gallery also use this indirectly
    /// via `getSnapshot(in:completion:)`'s `context.isPreview` branch.
    func placeholder(in context: Context) -> NextSendEntry {
        NextSendEntry(date: Date(), snapshot: .placeholderExample)
    }

    /// A quick, representative entry, used by the widget gallery/picker
    /// UI and for transient system snapshots.
    func getSnapshot(in context: Context, completion: @escaping (NextSendEntry) -> Void) {
        if context.isPreview {
            completion(NextSendEntry(date: Date(), snapshot: .placeholderExample))
            return
        }
        completion(NextSendEntry(date: Date(), snapshot: NextSendSnapshotStore.load()))
    }

    /// The real timeline shown on the Home Screen.
    func getTimeline(in context: Context, completion: @escaping (Timeline<NextSendEntry>) -> Void) {
        let snapshot = NextSendSnapshotStore.load()
        let now = Date()
        let entry = NextSendEntry(date: now, snapshot: snapshot)

        // Refresh policy: if there's a future send time, ask WidgetKit to
        // re-invoke getTimeline() right after it passes (so "Next: 3:45 PM"
        // flips to "Sending any moment" without waiting on the app). If
        // there's no scheduled time, `.never`, the app will push a reload
        // via WidgetCenter as soon as scheduling resumes.
        let policy: TimelineReloadPolicy
        if let nextSendDate = snapshot.nextSendDate, nextSendDate > now {
            policy = .after(nextSendDate)
        } else {
            policy = .never
        }

        completion(Timeline(entries: [entry], policy: policy))
    }
}
