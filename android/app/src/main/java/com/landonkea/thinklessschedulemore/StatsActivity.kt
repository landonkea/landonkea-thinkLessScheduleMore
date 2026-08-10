// ───────────────────────────────────────────────────────────────────
// StatsActivity, the stats/history dashboard screen
// ───────────────────────────────────────────────────────────────────
// A second screen (launched from MainActivity's "📊 Stats" button)
// that summarizes the send log via StatsCalculator:
//   - Overview: total sent, success rate, current streak
//   - Sends per day: a simple text bar chart for the last 14 days
//   - Top messages: the most-frequently-sent message texts
//
// Built the same way as MainActivity, a single ScrollView of
// programmatically-created views, no XML layout or charting library.
// A text-block bar chart keeps this dependency-free and consistent
// with the rest of the app's minimal style.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatsActivity : AppCompatActivity() {

    companion object {
        // Widest bar (in block characters) for the daily-sends chart.
        private const val MAX_BAR_WIDTH = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = MessageStore(this)
        val stats = StatsCalculator.compute(store.getSentLog())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scrollView = ScrollView(this)
        scrollView.addView(root)
        setContentView(scrollView)

        root.addView(sectionHeader("📊 Stats Dashboard", size = 22f))

        // ── Overview ─────────────────────────────────────────────
        root.addView(sectionHeader("Overview"))
        root.addView(TextView(this).apply {
            text = buildString {
                append("✅ Sent: ${stats.totalSent}\n")
                append("❌ Failed: ${stats.totalFailed}\n")
                append("📈 Success rate: ${formatRate(stats.successRate)}\n")
                append("🔥 Current streak: ${stats.currentStreakDays} day${if (stats.currentStreakDays == 1) "" else "s"}")
            }
        })

        // ── Sends per day ────────────────────────────────────────
        root.addView(sectionHeader("\nSends per day (last 14 days)"))
        root.addView(TextView(this).apply {
            text = if (stats.dailyCounts.all { it.count == 0 }) {
                "(No sends yet)"
            } else {
                formatDailyChart(stats.dailyCounts)
            }
            typeface = android.graphics.Typeface.MONOSPACE
        })

        // ── Top messages ─────────────────────────────────────────
        root.addView(sectionHeader("\nMost-sent messages"))
        root.addView(TextView(this).apply {
            text = if (stats.topMessages.isEmpty()) {
                "(No messages sent yet)"
            } else {
                stats.topMessages.joinToString("\n") { entry ->
                    "${entry.count}× ❝${entry.message.take(50)}${if (entry.message.length > 50) "…" else ""}❞"
                }
            }
        })
    }

    private fun sectionHeader(text: String, size: Float = 18f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
        }

    private fun formatRate(rate: Double): String {
        // Whole percent is plenty of precision for a summary screen.
        return "${Math.round(rate)}%"
    }

    private fun formatDailyChart(counts: List<StatsCalculator.DailyCount>): String {
        val maxCount = counts.maxOf { it.count }.coerceAtLeast(1)
        return counts.joinToString("\n") { day ->
            val barLength = if (day.count == 0) 0 else {
                ((day.count.toDouble() / maxCount) * MAX_BAR_WIDTH).toInt().coerceAtLeast(1)
            }
            val bar = "█".repeat(barLength)
            "${day.dayLabel.padEnd(6)} $bar ${day.count}"
        }
    }
}
