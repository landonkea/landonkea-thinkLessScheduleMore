// ───────────────────────────────────────────────────────────────────
// StatsCalculator — turns the raw send log into dashboard numbers
// ───────────────────────────────────────────────────────────────────
// MessageStore already keeps a rolling log of the last 50 sends
// (SentLogEntry: timestamp + status + message [+ error]). Nothing
// previously summarized it beyond "show the last 10 lines" in
// MainActivity. This pulls that summarization into a pure, testable
// module so StatsActivity can be a thin rendering layer.
//
// Kept as a pure object (no Android/Context dependency) — same shape
// as MessageSelector/MessageTemplate — so it's trivially unit
// testable on the JVM and mirrors iOS's StatsCalculator.swift.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object StatsCalculator {

    // One day's worth of sends, for the "sends per day" bar list.
    data class DailyCount(val dayLabel: String, val count: Int)

    // One entry in the "most-sent messages" leaderboard.
    data class MessageFrequency(val message: String, val count: Int)

    // The full bundle of numbers a dashboard screen needs.
    data class Stats(
        val totalSent: Int,
        val totalFailed: Int,
        // 0.0-100.0. 0 (not NaN) when there's nothing to report yet,
        // so callers can display "0%" instead of special-casing.
        val successRate: Double,
        val dailyCounts: List<DailyCount>,
        val topMessages: List<MessageFrequency>,
        val currentStreakDays: Int
    )

    private val dayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Build the full stats bundle from the raw log.
    //
    // `days` controls how many trailing calendar days the "sends per
    // day" breakdown and streak calculation look back over (from
    // `now`, so tests can pin "today" instead of depending on the
    // real clock).
    fun compute(
        log: List<SentLogEntry>,
        days: Int = 14,
        topN: Int = 5,
        now: Long = System.currentTimeMillis()
    ): Stats {
        val sent = log.filter { it.status == "sent" }
        val failed = log.filter { it.status == "failed" }
        val total = sent.size + failed.size
        val successRate = if (total == 0) 0.0 else (sent.size * 100.0) / total

        return Stats(
            totalSent = sent.size,
            totalFailed = failed.size,
            successRate = successRate,
            dailyCounts = dailyCounts(sent, days, now),
            topMessages = topMessages(sent, topN),
            currentStreakDays = currentStreakDays(sent, now)
        )
    }

    // ── Sends per day, oldest → newest, for the trailing `days` days ─
    private fun dailyCounts(sent: List<SentLogEntry>, days: Int, now: Long): List<DailyCount> {
        if (days <= 0) return emptyList()

        // Bucket sent entries by calendar day key ("yyyy-MM-dd").
        val counts = HashMap<String, Int>()
        for (entry in sent) {
            val key = dayKeyFormat.format(java.util.Date(entry.timestamp))
            counts[key] = (counts[key] ?: 0) + 1
        }

        val result = mutableListOf<DailyCount>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        for (i in (days - 1) downTo 0) {
            val dayCal = cal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, -i)
            val key = dayKeyFormat.format(dayCal.time)
            val label = dayFormat.format(dayCal.time)
            result.add(DailyCount(label, counts[key] ?: 0))
        }
        return result
    }

    // ── Most-frequently-sent message texts ────────────────────────
    // Ties break by first-seen order (stable, deterministic) rather
    // than message text, so the leaderboard doesn't reshuffle
    // alphabetically every time two counts happen to match.
    private fun topMessages(sent: List<SentLogEntry>, topN: Int): List<MessageFrequency> {
        if (sent.isEmpty() || topN <= 0) return emptyList()

        val counts = LinkedHashMap<String, Int>()
        for (entry in sent) {
            counts[entry.message] = (counts[entry.message] ?: 0) + 1
        }

        return counts.entries
            .mapIndexed { index, e -> Triple(e.key, e.value, index) }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .take(topN)
            .map { MessageFrequency(it.first, it.second) }
    }

    // ── Current streak of consecutive days with >= 1 successful send ─
    // Counts backward from "today" (relative to `now`). If nothing
    // was sent today, the streak is 0 — it doesn't count "yesterday"
    // as an active streak once today has passed without a send.
    private fun currentStreakDays(sent: List<SentLogEntry>, now: Long): Int {
        if (sent.isEmpty()) return 0

        val sentDays = sent.map { dayKeyFormat.format(java.util.Date(it.timestamp)) }.toSet()

        var streak = 0
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        while (true) {
            val dayCal = cal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, -streak)
            val key = dayKeyFormat.format(dayCal.time)
            if (key in sentDays) {
                streak++
            } else {
                break
            }
        }
        return streak
    }
}
