// ───────────────────────────────────────────────────────────────────
// WeightedMessageSelector, picks the next message biased by priority
// ───────────────────────────────────────────────────────────────────
// Builds on top of MessageSelector's anti-repeat exclusion (same
// "never exclude the whole pool" guarantee) instead of duplicating it,
// then does a weighted random pick over whatever candidates are left,
// so a message the user marked as a favorite (higher weight) shows up
// more often without ever fully starving the rest of the pool.
//
// Weights come from MessagePriorityStore, keyed by exact message text
// (see that class for why: a separate, additive store, same pattern
// as RecurringMessageStore). Any message missing a weight (never set,
// or PRIORITY_DEFAULT) is treated as weight 1, "normal."
//
// Kept as a pure, standalone object (no Android/Context dependency)
// so it's trivially unit-testable on the JVM, same as MessageSelector.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import kotlin.random.Random

object WeightedMessageSelector {

    // Pick the next message from `pool`, first applying MessageSelector's
    // anti-repeat exclusion, then a weighted random choice over what's
    // left. `weights` only needs entries for messages the user actually
    // set a priority on, anything absent (or <= 0) counts as weight 1.
    fun pick(
        pool: List<String>,
        weights: Map<String, Int>,
        recentlySent: List<String>,
        random: Random = Random.Default
    ): String {
        require(pool.isNotEmpty()) { "Cannot pick a message from an empty pool" }

        val maxExclusions = pool.size - 1
        val toExclude = if (maxExclusions <= 0) emptySet() else recentlySent.takeLast(maxExclusions).toSet()
        val candidates = pool.filter { it !in toExclude }
        val finalCandidates = candidates.ifEmpty { pool }

        val weighted = finalCandidates.map { it to (weights[it]?.takeIf { w -> w > 0 } ?: 1) }
        val totalWeight = weighted.sumOf { it.second }

        // Defensive fallback, every weight is coerced to >= 1 above so
        // totalWeight should always be positive, but never risk a
        // divide-by-zero / empty-range crash on a bad input.
        if (totalWeight <= 0) return finalCandidates[random.nextInt(finalCandidates.size)]

        var roll = random.nextInt(totalWeight)
        for ((message, weight) in weighted) {
            if (roll < weight) return message
            roll -= weight
        }

        // Unreachable given the loop above covers [0, totalWeight), kept
        // as a safe fallback rather than an assertion.
        return weighted.last().first
    }
}
