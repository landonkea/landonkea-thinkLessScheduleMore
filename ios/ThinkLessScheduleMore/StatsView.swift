// ───────────────────────────────────────────────────────────────────
// StatsView — the stats/history dashboard screen (iOS)
// ───────────────────────────────────────────────────────────────────
// Reached via a "📊 Stats Dashboard" NavigationLink from ContentView's
// Send History section. Summarizes StatsCalculator's output:
//   - Overview: total opened, engagement rate, current streak
//   - Opens per day: a simple bar chart for the last 14 days
//   - Top messages: the most-frequently-opened message texts
//
// Built with plain SwiftUI shapes (no Charts framework dependency)
// to match the rest of the app's minimal style and keep the iOS
// deployment target unconstrained by chart-library availability.
// ───────────────────────────────────────────────────────────────────

import SwiftUI

struct StatsView: View {
    let stats: StatsCalculator.Stats

    var body: some View {
        Form {
            Section(header: Text("Overview")) {
                LabeledContent("✅ Opened", value: "\(stats.totalOpened)")
                LabeledContent("🕓 Pending", value: "\(stats.totalPending)")
                LabeledContent("📈 Engagement rate", value: formattedRate)
                LabeledContent("🔥 Current streak", value: "\(stats.currentStreakDays) day\(stats.currentStreakDays == 1 ? "" : "s")")
            }

            Section(header: Text("Opens per day (last 14 days)")) {
                if stats.dailyCounts.allSatisfy({ $0.count == 0 }) {
                    Text("(No messages opened yet)")
                        .foregroundColor(.secondary)
                } else {
                    dailyChart
                        .frame(height: 120)
                        .padding(.vertical, 4)
                }
            }

            Section(header: Text("Most-opened messages")) {
                if stats.topMessages.isEmpty {
                    Text("(No messages opened yet)")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(stats.topMessages) { entry in
                        HStack {
                            Text("\(entry.count)×")
                                .foregroundColor(.secondary)
                                .frame(width: 32, alignment: .leading)
                            Text("❝\(entry.message)❞")
                                .font(.caption)
                        }
                    }
                }
            }
        }
        .navigationTitle("📊 Stats Dashboard")
    }

    private var formattedRate: String {
        "\(Int(stats.engagementRate.rounded()))%"
    }

    // A minimal bar chart: one bar per day, height proportional to
    // that day's count relative to the busiest day in the window.
    private var dailyChart: some View {
        let maxCount = max(stats.dailyCounts.map(\.count).max() ?? 1, 1)
        return GeometryReader { geo in
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(stats.dailyCounts) { day in
                    VStack(spacing: 2) {
                        Text(day.count > 0 ? "\(day.count)" : "")
                            .font(.system(size: 8))
                            .foregroundColor(.secondary)
                        RoundedRectangle(cornerRadius: 2)
                            .fill(day.count > 0 ? Color.accentColor : Color.secondary.opacity(0.15))
                            .frame(height: max(CGFloat(day.count) / CGFloat(maxCount) * (geo.size.height - 30), 2))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

#Preview {
    NavigationView {
        StatsView(stats: StatsCalculator.compute(log: [
            SentLogEntry(id: UUID(), timestamp: Date(), status: "opened", message: "Thinking of you ❤️"),
            SentLogEntry(id: UUID(), timestamp: Date(), status: "opened", message: "Thinking of you ❤️"),
            SentLogEntry(id: UUID(), timestamp: Date(), status: "pending", message: "Hope you're having a great day!")
        ]))
    }
}
