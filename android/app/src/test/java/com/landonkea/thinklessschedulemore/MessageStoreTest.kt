// ───────────────────────────────────────────────────────────────────
// MessageStoreTest — first tests in this project.
// ───────────────────────────────────────────────────────────────────
// Runs under Robolectric so MessageStore can use real
// SharedPreferences + org.json on the JVM (no emulator needed).
//
// Focus: the JSON-backed storage migration (fixes the pipe-delimiter
// corruption bug — see MessageStore's class comment) and the new
// edit-message / next-send-time behavior added alongside it.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned to SDK 34: Robolectric 4.13's newest supported framework jar is
// API 34, while the app's targetSdk is 36 (Robolectric can't shadow a
// higher API than it ships). SharedPreferences/org.json behavior is
// unaffected by this gap.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageStoreTest {

    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        store = MessageStore(ApplicationProvider.getApplicationContext())
    }

    // ── Message pool ────────────────────────────────────────────

    @Test
    fun `messages containing the old pipe delimiters round-trip intact`() {
        // This is the exact scenario that used to corrupt the store:
        // a message containing the delimiter characters themselves.
        val tricky = "Meet at 5|||6pm | don't be late!"
        store.addMessage(tricky)

        assertEquals(listOf(tricky), store.getMessages())
    }

    @Test
    fun `addMessage appends without disturbing existing messages`() {
        store.addMessage("first")
        store.addMessage("second")

        assertEquals(listOf("first", "second"), store.getMessages())
    }

    @Test
    fun `updateMessage edits text in place, preserving order`() {
        store.saveMessages(listOf("a", "b", "c"))

        store.updateMessage(1, "b-edited")

        assertEquals(listOf("a", "b-edited", "c"), store.getMessages())
    }

    @Test
    fun `updateMessage on an out-of-range index is a no-op`() {
        store.saveMessages(listOf("a", "b"))

        store.updateMessage(5, "should not apply")

        assertEquals(listOf("a", "b"), store.getMessages())
    }

    @Test
    fun `removeMessage deletes only the target index`() {
        store.saveMessages(listOf("a", "b", "c"))

        store.removeMessage(1)

        assertEquals(listOf("a", "c"), store.getMessages())
    }

    @Test
    fun `getMessages on empty store returns empty list`() {
        assertEquals(emptyList<String>(), store.getMessages())
    }

    // ── Send log ─────────────────────────────────────────────────

    @Test
    fun `sent log entries containing pipes round-trip intact`() {
        store.addToSentLog(1000L, "sent", "Call me | text me ||| whichever")

        val log = store.getSentLog()
        assertEquals(1, log.size)
        assertEquals("Call me | text me ||| whichever", log[0].message)
        assertEquals("sent", log[0].status)
        assertEquals(1000L, log[0].timestamp)
    }

    @Test
    fun `sent log keeps newest entries first`() {
        store.addToSentLog(1000L, "sent", "older")
        store.addToSentLog(2000L, "sent", "newer")

        val log = store.getSentLog()
        assertEquals("newer", log[0].message)
        assertEquals("older", log[1].message)
    }

    @Test
    fun `failed entries preserve the error message`() {
        store.addToSentLog(1000L, "failed", "hi", "SecurityException: no SMS permission")

        assertEquals("SecurityException: no SMS permission", store.getSentLog()[0].error)
    }

    // ── Next send time ───────────────────────────────────────────

    @Test
    fun `next send time defaults to unset`() {
        assertEquals(0L, store.getNextSendTime())
    }

    @Test
    fun `next send time persists and can be cleared`() {
        store.saveNextSendTime(123456L)
        assertEquals(123456L, store.getNextSendTime())

        store.clearNextSendTime()
        assertEquals(0L, store.getNextSendTime())
    }
}
