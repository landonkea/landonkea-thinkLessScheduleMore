// ───────────────────────────────────────────────────────────────────
// MessageSelector, picks the next message to send, avoiding recent
// repeats
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's MessageSelector.kt: previously SchedulerManager
// picked `store.messages.randomElement()` with no memory at all, so
// a small pool could send the same message twice (or more) in a row
// within the same day's batch. This tracks the last few messages
// picked and excludes them from the candidate pool for the next pick.
//
// Kept as a pure, standalone type (no Foundation/UIKit dependency
// beyond what's needed for RandomNumberGenerator) so it's trivially
// unit-testable and deterministic under a seeded generator.
// ───────────────────────────────────────────────────────────────────

import Foundation

enum MessageSelector {

    // How many of the most-recently-sent messages to remember/avoid.
    // Kept small: with short pools we still need candidates left over.
    static let historySize = 5

    // Pick the next message from `pool`, preferring one that isn't in
    // `recentlySent` (oldest-first order; only the tail is consulted).
    //
    // Never excludes the entire pool: if every message would be
    // excluded (e.g. pool size 1, or recentlySent covers everything),
    // we fall back to the full pool rather than returning nothing.
    static func pick<G: RandomNumberGenerator>(
        pool: [String],
        recentlySent: [String],
        using generator: inout G
    ) -> String {
        precondition(!pool.isEmpty, "Cannot pick a message from an empty pool")

        // Never exclude more than (pool.count - 1) entries, always
        // leave at least one candidate standing.
        let maxExclusions = pool.count - 1
        let toExclude: Set<String> = maxExclusions <= 0
            ? []
            : Set(recentlySent.suffix(maxExclusions))

        let candidates = pool.filter { !toExclude.contains($0) }
        let finalCandidates = candidates.isEmpty ? pool : candidates

        let index = Int(generator.next(upperBound: UInt(finalCandidates.count)))
        return finalCandidates[index]
    }

    // Convenience overload using the system random generator.
    static func pick(pool: [String], recentlySent: [String]) -> String {
        var rng = SystemRandomNumberGenerator()
        return pick(pool: pool, recentlySent: recentlySent, using: &rng)
    }
}

// Small helper so `pick` can request a bounded random index without
// pulling in Int.random(in:using:)'s empty-range edge cases directly
// at every call site.
private extension RandomNumberGenerator {
    mutating func next(upperBound: UInt) -> UInt {
        guard upperBound > 0 else { return 0 }
        return UInt.random(in: 0..<upperBound, using: &self)
    }
}
