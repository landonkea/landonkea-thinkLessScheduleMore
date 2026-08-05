// ───────────────────────────────────────────────────────────────────
// SmsSenderTest — the real send path, via Robolectric's shadow SmsManager
// ───────────────────────────────────────────────────────────────────
// Robolectric intercepts SmsManager.sendTextMessage instead of
// touching real telephony hardware, so this runs on the plain JVM
// exactly like every other test in this project. Covers what moved
// out of SchedulerService: logging a PENDING entry, calling
// SmsManager with the right recipient/message, and returning a
// dispatched-successfully AutomationResult.
//
// NOT covered here: the async SENT/DELIVERED broadcast callbacks
// firing after dispatch (that would mean manually invoking the
// PendingIntents Robolectric captured, simulating the Android
// broadcast system end to end) — SmsResultMapperTest already covers
// the resultCode -> SendStatus mapping logic those callbacks feed
// into, and this refactor is a straight extraction of
// SchedulerService's pre-existing, already-shipped receiver-wiring
// code (see SmsSender's class comment), not new behavior.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSmsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsSenderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: MessageStore
    private lateinit var sender: SmsSender

    @Before
    fun setUp() {
        store = MessageStore(context)
        sender = SmsSender(context)
    }

    @Test
    fun `send dispatches through SmsManager with the given recipient and message`() {
        val result = sender.send("+15551234567", "hello there")

        assertTrue(result.success)

        val shadowSmsManager: ShadowSmsManager = shadowOf(context.getSystemService(android.telephony.SmsManager::class.java))
        val lastParams = shadowSmsManager.lastSentTextMessageParams
        assertEquals("+15551234567", lastParams.destinationAddress)
        assertEquals("hello there", lastParams.text)
    }

    @Test
    fun `send logs a PENDING entry immediately`() {
        sender.send("+15551234567", "hello there")

        val log = store.getSentLog()
        assertEquals(1, log.size)
        assertEquals(SendStatus.PENDING, log[0].status)
        assertEquals("hello there", log[0].message)
    }

    @Test
    fun `two sends produce two independent log entries`() {
        sender.send("+15551234567", "first")
        sender.send("+15557654321", "second")

        val log = store.getSentLog()
        assertEquals(2, log.size)
    }
}
