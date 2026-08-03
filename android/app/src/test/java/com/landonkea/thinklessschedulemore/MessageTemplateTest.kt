// ───────────────────────────────────────────────────────────────────
// MessageTemplateTest — {name}/{time-of-day} placeholder rendering.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTemplateTest {

    @Test
    fun `substitutes both placeholders`() {
        val result = MessageTemplate.render("Good {time-of-day}, {name}!", "Sam", hour = 8)
        assertEquals("Good morning, Sam!", result)
    }

    @Test
    fun `falls back to 'there' when name is blank`() {
        val result = MessageTemplate.render("Hey {name}", "", hour = 8)
        assertEquals("Hey there", result)
    }

    @Test
    fun `falls back to 'there' when name is whitespace only`() {
        val result = MessageTemplate.render("Hey {name}", "   ", hour = 8)
        assertEquals("Hey there", result)
    }

    @Test
    fun `template with no placeholders passes through unchanged`() {
        val result = MessageTemplate.render("Thinking of you", "Sam", hour = 8)
        assertEquals("Thinking of you", result)
    }

    @Test
    fun `repeated placeholders are all substituted`() {
        val result = MessageTemplate.render("{name} {name} {name}", "Sam", hour = 8)
        assertEquals("Sam Sam Sam", result)
    }

    @Test
    fun `time-of-day buckets - morning`() {
        assertEquals("morning", MessageTemplate.timeOfDay(5))
        assertEquals("morning", MessageTemplate.timeOfDay(11))
    }

    @Test
    fun `time-of-day buckets - afternoon`() {
        assertEquals("afternoon", MessageTemplate.timeOfDay(12))
        assertEquals("afternoon", MessageTemplate.timeOfDay(16))
    }

    @Test
    fun `time-of-day buckets - evening`() {
        assertEquals("evening", MessageTemplate.timeOfDay(17))
        assertEquals("evening", MessageTemplate.timeOfDay(21))
    }

    @Test
    fun `time-of-day buckets - night wraps past midnight`() {
        assertEquals("night", MessageTemplate.timeOfDay(22))
        assertEquals("night", MessageTemplate.timeOfDay(0))
        assertEquals("night", MessageTemplate.timeOfDay(4))
    }
}
