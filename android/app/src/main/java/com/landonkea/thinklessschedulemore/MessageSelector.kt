// ───────────────────────────────────────────────────────────────────
// MessageSelector, picks the next message to send, avoiding recent
// repeats
// ───────────────────────────────────────────────────────────────────
// Previously SchedulerService picked `messages[Random.nextInt(size)]`
// with no memory at all, it was entirely possible (and, with a small
// pool, common) to send the exact same message twice or three times
// in a row. This tracks the last few messages actually sent and
// excludes them from the candidate pool for the next pick.
//
// Kept as a pure, standalone object (no Android/Context dependency)
// so it's trivially unit-testable on the JVM.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import kotlin.random.Random

object MessageSelector {

    // How many of the most-recently-sent messages to remember/avoid.
    // Kept small: with short pools we still need candidates left over.
    const val HISTORY_SIZE = 5

    // Pick the next message from `pool`, preferring one that isn't in
    // `recentlySent` (newest-last order; only the tail is consulted).
    //
    // Never excludes the entire pool: if every message would be
    // excluded (e.g. pool size 1, or recentlySent covers everything),
    // we fall back to the full pool rather than returning nothing.
    fun pick(pool: List<String>, recentlySent: List<String>, random: Random = Random.Default): String {
        require(pool.isNotEmpty()) { "Cannot pick a message from an empty pool" }

        // Never exclude more than (pool.size - 1) entries, always
        // leave at least one candidate standing.
        val maxExclusions = pool.size - 1
        val toExclude = if (maxExclusions <= 0) emptySet() else recentlySent.takeLast(maxExclusions).toSet()

        val candidates = pool.filter { it !in toExclude }
        val finalCandidates = candidates.ifEmpty { pool }

        return finalCandidates[random.nextInt(finalCandidates.size)]
    }
}
