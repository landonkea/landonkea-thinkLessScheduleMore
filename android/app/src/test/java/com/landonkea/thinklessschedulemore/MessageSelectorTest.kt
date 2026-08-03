// ───────────────────────────────────────────────────────────────────
// MessageSelectorTest — the anti-repeat message-picking logic.
// ───────────────────────────────────────────────────────────────────
// Pure logic, no Android dependencies, so it runs as a plain JVM
// unit test (no Robolectric needed).
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MessageSelectorTest {

    @Test
    fun `excludes the single most recently sent message when pool is large enough`() {
        val pool = listOf("a", "b", "c")
        val recentlySent = listOf("a")

        // Run many times to make sure "a" never comes back while it's
        // excluded — a flaky single-shot random assertion would miss bugs.
        repeat(50) {
            val picked = MessageSelector.pick(pool, recentlySent)
            assertTrue("expected pick not to be 'a' but was '$picked'", picked != "a")
        }
    }

    @Test
    fun `falls back to the full pool when everything would be excluded`() {
        val pool = listOf("only-one")
        val recentlySent = listOf("only-one", "only-one")

        val picked = MessageSelector.pick(pool, recentlySent)

        assertEquals("only-one", picked)
    }

    @Test
    fun `never excludes more than pool size minus one, leaving at least one candidate`() {
        val pool = listOf("a", "b")
        // Both messages recently sent — with a pool of 2 we must still
        // leave at least one standing (the most recent exclusion wins,
        // since takeLast(1) keeps only the tail).
        val recentlySent = listOf("a", "b")

        repeat(50) {
            val picked = MessageSelector.pick(pool, recentlySent)
            assertTrue(picked == "a" || picked == "b")
        }
    }

    @Test
    fun `empty recently-sent history excludes nothing`() {
        val pool = listOf("a", "b", "c")

        repeat(50) {
            val picked = MessageSelector.pick(pool, emptyList())
            assertTrue(picked in pool)
        }
    }

    @Test
    fun `only the tail of recently-sent history (up to pool size minus one) is consulted`() {
        val pool = listOf("a", "b", "c")
        // History has more entries than we can exclude; only the last
        // two ("b", "c") should be excluded, leaving "a" as the only
        // candidate.
        val recentlySent = listOf("a", "b", "c")

        repeat(50) {
            assertEquals("a", MessageSelector.pick(pool, recentlySent))
        }
    }

    @Test
    fun `is deterministic given a seeded Random`() {
        val pool = listOf("a", "b", "c")
        val picked = MessageSelector.pick(pool, emptyList(), Random(42))
        val pickedAgain = MessageSelector.pick(pool, emptyList(), Random(42))
        assertEquals(picked, pickedAgain)
    }
}
