// NextSendWidget.swift — The widget configuration and per-size views:
// which sizes it supports, what to call it in the widget gallery, and
// what each size draws.

import WidgetKit
import SwiftUI

struct NextSendWidget: Widget {
    // Must exactly match the `ofKind:` string MessageStore passes to
    // `WidgetCenter.shared.reloadTimelines(ofKind:)` — see
    // NextSendSnapshotStore.widgetKind in ../Shared/NextSendSnapshot.swift.
    let kind: String = NextSendSnapshotStore.widgetKind

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: NextSendProvider()) { entry in
            NextSendWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Next Message")
        .description("Shows when the next scheduled message will go out, and to whom.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .accessoryRectangular,
        ])
    }
}

/// Picks the right layout for the widget's current family (size/placement).
struct NextSendWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: NextSendEntry

    var body: some View {
        switch family {
        case .accessoryRectangular:
            LockScreenNextSendView(entry: entry)
        case .systemMedium:
            MediumNextSendView(entry: entry)
        default:
            SmallNextSendView(entry: entry)
        }
    }
}

// MARK: - Home Screen: Small

/// The `.systemSmall` layout — an icon, a compact time/status line, and
/// the recipient name.
struct SmallNextSendView: View {
    let entry: NextSendEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: "envelope.fill")
                .font(.title2)
                .foregroundStyle(.pink)

            Spacer(minLength: 0)

            Text("Next Message")
                .font(.caption2)
                .foregroundStyle(.secondary)

            Text(NextSendFormatter.compactDisplayText(nextSendDate: entry.snapshot.nextSendDate, now: entry.date))
                .font(.headline)
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            if entry.snapshot.nextSendDate != nil, !entry.snapshot.recipientName.isEmpty {
                Text("to \(entry.snapshot.recipientName)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }
}

// MARK: - Home Screen: Medium

/// The `.systemMedium` layout — icon on the left, the full "Next: ... to
/// ..." sentence on the right (wider format, no need to abbreviate).
struct MediumNextSendView: View {
    let entry: NextSendEntry

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "envelope.fill")
                .font(.largeTitle)
                .foregroundStyle(.pink)

            VStack(alignment: .leading, spacing: 4) {
                Text("Next Message")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Text(NextSendFormatter.displayText(
                    nextSendDate: entry.snapshot.nextSendDate,
                    recipientName: entry.snapshot.recipientName,
                    now: entry.date
                ))
                .font(.headline)
                .lineLimit(2)
            }

            Spacer(minLength: 0)
        }
    }
}

// MARK: - Lock Screen: Rectangular

/// The `.accessoryRectangular` Lock Screen layout — text-only (system
/// renders Lock Screen widgets in a single tint color).
struct LockScreenNextSendView: View {
    let entry: NextSendEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Label {
                Text("Next Message")
            } icon: {
                Image(systemName: "envelope.fill")
            }
            .font(.headline)

            Text(NextSendFormatter.displayText(
                nextSendDate: entry.snapshot.nextSendDate,
                recipientName: entry.snapshot.recipientName,
                now: entry.date
            ))
            .font(.caption)
            .lineLimit(1)
        }
    }
}

#Preview(as: .systemSmall) {
    NextSendWidget()
} timeline: {
    NextSendEntry(date: .now, snapshot: .placeholderExample)
    NextSendEntry(date: .now, snapshot: .empty)
}

#Preview(as: .systemMedium) {
    NextSendWidget()
} timeline: {
    NextSendEntry(date: .now, snapshot: .placeholderExample)
}

#Preview(as: .accessoryRectangular) {
    NextSendWidget()
} timeline: {
    NextSendEntry(date: .now, snapshot: .placeholderExample)
}
