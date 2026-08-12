// ───────────────────────────────────────────────────────────────────
// WeightedMessageSelector, picks the next message biased by priority
// (iOS)
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's WeightedMessageSelector.kt. Builds on top of
// MessageSelector's anti-repeat exclusion (same "never exclude the
// whole pool" guarantee) instead of duplicating it, then does a
// weighted random pick over whatever candidates are left, so a
// message the user marked as a favorite (higher weight) shows up more
// often without ever fully starving the rest of the pool.
//
// Weights come from MessagePriorityStore, keyed by exact message text.
// Any message missing a weight (never set, or <= 0) is treated as
// weight 1, "normal."
//
// Kept as a pure, standalone type (no Foundation/UIKit dependency
// beyond what's needed for RandomNumberGenerator) so it's trivially
// unit-testable and deterministic under a seeded generator, same as
// MessageSelector.
// ───────────────────────────────────────────────────────────────────

import Foundation

enum WeightedMessageSelector {

    // Pick the next message from `pool`, first applying
    // MessageSelector's anti-repeat exclusion, then a weighted random
    // choice over what's left. `weights` only needs entries for
    // messages the user actually set a priority on, anything absent
    // (or <= 0) counts as weight 1.
    static func pick<G: RandomNumberGenerator>(
        pool: [String],
        weights: [String: Int],
        recentlySent: [String],
        using generator: inout G
    ) -> String {
        precondition(!pool.isEmpty, "Cannot pick a message from an empty pool")

        let maxExclusions = pool.count - 1
        let toExclude: Set<String> = maxExclusions <= 0
            ? []
            : Set(recentlySent.suffix(maxExclusions))

        let candidates = pool.filter { !toExclude.contains($0) }
        let finalCandidates = candidates.isEmpty ? pool : candidates

        let weighted = finalCandidates.map { message -> (String, Int) in
            let raw = weights[message] ?? 1
            return (message, raw > 0 ? raw : 1)
        }
        let totalWeight = weighted.reduce(0) { $0 + $1.1 }

        // Defensive fallback, every weight is coerced to >= 1 above so
        // totalWeight should always be positive, but never risk a
        // divide-by-zero / empty-range crash on a bad input.
        guard totalWeight > 0 else {
            let index = Int(generator.next(upperBound: UInt(finalCandidates.count)))
            return finalCandidates[index]
        }

        var roll = Int(generator.next(upperBound: UInt(totalWeight)))
        for (message, weight) in weighted {
            if roll < weight { return message }
            roll -= weight
        }

        // Unreachable given the loop above covers [0, totalWeight), kept
        // as a safe fallback rather than a force-unwrap.
        return weighted.last?.0 ?? finalCandidates[0]
    }

    // Convenience overload using the system random generator.
    static func pick(pool: [String], weights: [String: Int], recentlySent: [String]) -> String {
        var rng = SystemRandomNumberGenerator()
        return pick(pool: pool, weights: weights, recentlySent: recentlySent, using: &rng)
    }
}

// Small helper so `pick` can request a bounded random index without
// pulling in Int.random(in:using:)'s empty-range edge cases directly
// at every call site. Duplicated from MessageSelector.swift's private
// extension (file-private in Swift, can't be shared across files
// without making it non-private there), kept identical on purpose.
private extension RandomNumberGenerator {
    mutating func next(upperBound: UInt) -> UInt {
        guard upperBound > 0 else { return 0 }
        return UInt.random(in: 0..<upperBound, using: &self)
    }
}
