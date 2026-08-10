// ───────────────────────────────────────────────────────────────────
// SendSmsActionTest, parameter validation
// ───────────────────────────────────────────────────────────────────
// Deliberately does NOT test the real send path here (that's
// SmsSender's job, exercised via Robolectric's shadow SmsManager in
// SmsSenderTest), this just proves a malformed params map (exactly
// what an untyped Tasker-supplied Bundle risks producing) is rejected
// with a clear message *before* ever reaching SmsSender, matching
// AutomationAction's documented "validate defensively" contract.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendSmsActionTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val action = SendSmsAction()

    @Test
    fun `id and displayName are stable`() {
        assertTrue(action.id == "send_sms")
        assertTrue(action.displayName.isNotBlank())
    }

    @Test
    fun `paramSchema declares recipient and message`() {
        assertTrue(action.paramSchema.containsKey("recipient"))
        assertTrue(action.paramSchema.containsKey("message"))
    }

    @Test
    fun `missing recipient fails with a clear message, before any send is attempted`() {
        val result = action.execute(context, mapOf("message" to "hi"))

        assertFalse(result.success)
        assertTrue(result.message.contains("recipient"))
    }

    @Test
    fun `missing message fails with a clear message`() {
        val result = action.execute(context, mapOf("recipient" to "+15551234567"))

        assertFalse(result.success)
        assertTrue(result.message.contains("message"))
    }

    @Test
    fun `blank recipient is treated the same as missing`() {
        val result = action.execute(context, mapOf("recipient" to "   ", "message" to "hi"))

        assertFalse(result.success)
        assertTrue(result.message.contains("recipient"))
    }
}
