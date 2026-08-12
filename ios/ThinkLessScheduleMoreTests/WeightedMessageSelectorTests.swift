// ───────────────────────────────────────────────────────────────────
// WeightedMessageSelectorTests, priority-weighted message picking.
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's WeightedMessageSelectorTest.kt. Pure logic, no
// UserDefaults/UIKit dependency. Reuses the same SeededGenerator
// XCTest already defines in MessageSelectorTests.swift for
// deterministic trials.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class WeightedMessageSelectorTests: XCTestCase {

    func testMessagesWithNoWeightEntryArePickedAsIfWeight1() {
        let pool = ["a", "b", "c"]
        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = WeightedMessageSelector.pick(pool: pool, weights: [:], recentlySent: [], using: &rng)
            XCTAssertTrue(pool.contains(picked))
        }
    }

    func testAMuchHigherWeightDominatesPicksOverManyTrials() {
        let pool = ["favorite", "normal"]
        let weights = ["favorite": 1000, "normal": 1]

        var counts = ["favorite": 0, "normal": 0]
        for seed in (1...200) {
            var rng = SeededGenerator(state: UInt64(seed))
            // Empty recentlySent each trial, isolating the weighting
            // behavior from the anti-repeat exclusion.
            let picked = WeightedMessageSelector.pick(pool: pool, weights: weights, recentlySent: [], using: &rng)
            counts[picked, default: 0] += 1
        }

        XCTAssertTrue(counts["favorite", default: 0] > counts["normal", default: 0],
                       "expected 'favorite' to dominate, got \(counts)")
    }

    func testAntiRepeatExclusionStillAppliesBeforeWeighting() {
        let pool = ["a", "b", "c"]
        let recentlySent = ["a"]
        // Even with "a" weighted enormously, it must still be excluded
        // since it was just sent.
        let weights = ["a": 1000]

        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = WeightedMessageSelector.pick(pool: pool, weights: weights, recentlySent: recentlySent, using: &rng)
            XCTAssertNotEqual(picked, "a", "expected pick not to be 'a' but was '\(picked)'")
        }
    }

    func testFallsBackToTheFullPoolWhenEverythingWouldBeExcluded() {
        let pool = ["only-one"]
        let recentlySent = ["only-one", "only-one"]

        let picked = WeightedMessageSelector.pick(pool: pool, weights: [:], recentlySent: recentlySent)

        XCTAssertEqual(picked, "only-one")
    }

    func testZeroOrNegativeWeightsAreTreatedAsWeight1NotExcluded() {
        let pool = ["a", "b"]
        let weights = ["a": 0, "b": -5]

        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = WeightedMessageSelector.pick(pool: pool, weights: weights, recentlySent: [], using: &rng)
            XCTAssertTrue(picked == "a" || picked == "b")
        }
    }

    func testIsDeterministicGivenASeededGenerator() {
        let pool = ["a", "b", "c"]
        let weights = ["a": 3, "b": 1, "c": 1]
        var rng1 = SeededGenerator(state: 42)
        var rng2 = SeededGenerator(state: 42)

        let picked = WeightedMessageSelector.pick(pool: pool, weights: weights, recentlySent: [], using: &rng1)
        let pickedAgain = WeightedMessageSelector.pick(pool: pool, weights: weights, recentlySent: [], using: &rng2)

        XCTAssertEqual(picked, pickedAgain)
    }
}
