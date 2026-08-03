// ───────────────────────────────────────────────────────────────────
// MessageSelectorTests — the anti-repeat message-picking logic.
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's MessageSelectorTest.kt. Pure logic, no
// UserDefaults/UIKit dependency.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

// A deterministic generator for reproducible tests.
struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        // xorshift64* — good enough determinism for tests, not for security.
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}

final class MessageSelectorTests: XCTestCase {

    func testExcludesTheMostRecentlySentMessageWhenPoolIsLargeEnough() {
        let pool = ["a", "b", "c"]
        let recentlySent = ["a"]

        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = MessageSelector.pick(pool: pool, recentlySent: recentlySent, using: &rng)
            XCTAssertNotEqual(picked, "a", "expected pick not to be 'a' but was '\(picked)'")
        }
    }

    func testFallsBackToFullPoolWhenEverythingWouldBeExcluded() {
        let pool = ["only-one"]
        let recentlySent = ["only-one", "only-one"]

        let picked = MessageSelector.pick(pool: pool, recentlySent: recentlySent)

        XCTAssertEqual(picked, "only-one")
    }

    func testNeverExcludesMoreThanPoolSizeMinusOne() {
        let pool = ["a", "b"]
        let recentlySent = ["a", "b"]

        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = MessageSelector.pick(pool: pool, recentlySent: recentlySent, using: &rng)
            XCTAssertTrue(picked == "a" || picked == "b")
        }
    }

    func testEmptyRecentlySentExcludesNothing() {
        let pool = ["a", "b", "c"]

        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = MessageSelector.pick(pool: pool, recentlySent: [], using: &rng)
            XCTAssertTrue(pool.contains(picked))
        }
    }

    func testOnlyTheTailOfRecentlySentHistoryIsConsulted() {
        let pool = ["a", "b", "c"]
        // Only the last two entries ("b", "c") should be excluded,
        // leaving "a" as the only candidate.
        let recentlySent = ["a", "b", "c"]

        for seed in (1...50) {
            var rng = SeededGenerator(state: UInt64(seed))
            let picked = MessageSelector.pick(pool: pool, recentlySent: recentlySent, using: &rng)
            XCTAssertEqual(picked, "a")
        }
    }

    func testIsDeterministicGivenASeededGenerator() {
        let pool = ["a", "b", "c"]
        var rng1 = SeededGenerator(state: 42)
        var rng2 = SeededGenerator(state: 42)

        let picked = MessageSelector.pick(pool: pool, recentlySent: [], using: &rng1)
        let pickedAgain = MessageSelector.pick(pool: pool, recentlySent: [], using: &rng2)

        XCTAssertEqual(picked, pickedAgain)
    }
}
