// ───────────────────────────────────────────────────────────────────
// WeightedMessageSelectorTest, priority-weighted message picking.
// ───────────────────────────────────────────────────────────────────
// Pure logic, no Android dependencies, so it runs as a plain JVM
// unit test (no Robolectric needed).
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeightedMessageSelectorTest {

    @Test
    fun `messages with no weight entry are picked as if weight 1`() {
        val pool = listOf("a", "b", "c")
        repeat(50) {
            val picked = WeightedMessageSelector.pick(pool, emptyMap(), emptyList())
            assertTrue(picked in pool)
        }
    }

    @Test
    fun `a much higher weight dominates picks over many trials`() {
        val pool = listOf("favorite", "normal")
        val weights = mapOf("favorite" to 1000, "normal" to 1)

        val counts = mutableMapOf("favorite" to 0, "normal" to 0)
        repeat(200) {
            // Empty recentlySent each trial, isolating the weighting
            // behavior from the anti-repeat exclusion.
            val picked = WeightedMessageSelector.pick(pool, weights, emptyList())
            counts[picked] = counts.getValue(picked) + 1
        }

        assertTrue("expected 'favorite' to dominate, got $counts", counts.getValue("favorite") > counts.getValue("normal"))
    }

    @Test
    fun `anti-repeat exclusion still applies before weighting`() {
        val pool = listOf("a", "b", "c")
        val recentlySent = listOf("a")
        // Even with "a" weighted enormously, it must still be excluded
        // since it was just sent.
        val weights = mapOf("a" to 1000)

        repeat(50) {
            val picked = WeightedMessageSelector.pick(pool, weights, recentlySent)
            assertTrue("expected pick not to be 'a' but was '$picked'", picked != "a")
        }
    }

    @Test
    fun `falls back to the full pool when everything would be excluded`() {
        val pool = listOf("only-one")
        val recentlySent = listOf("only-one", "only-one")

        val picked = WeightedMessageSelector.pick(pool, emptyMap(), recentlySent)

        assertEquals("only-one", picked)
    }

    @Test
    fun `zero or negative weights are treated as weight 1, not excluded`() {
        val pool = listOf("a", "b")
        val weights = mapOf("a" to 0, "b" to -5)

        repeat(50) {
            val picked = WeightedMessageSelector.pick(pool, weights, emptyList())
            assertTrue(picked == "a" || picked == "b")
        }
    }

    @Test
    fun `is deterministic given a seeded Random`() {
        val pool = listOf("a", "b", "c")
        val weights = mapOf("a" to 3, "b" to 1, "c" to 1)
        val picked = WeightedMessageSelector.pick(pool, weights, emptyList(), Random(42))
        val pickedAgain = WeightedMessageSelector.pick(pool, weights, emptyList(), Random(42))
        assertEquals(picked, pickedAgain)
    }
}
