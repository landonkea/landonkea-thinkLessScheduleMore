// ───────────────────────────────────────────────────────────────────
// TaskerFireReceiverTest — Tasker's broadcast actually reaches the
// registry
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingAction(override val id: String) : AutomationAction {
    override val displayName = "Recording Action"
    override val paramSchema = emptyMap<String, String>()
    var lastParams: Map<String, String>? = null

    override fun execute(context: android.content.Context, params: Map<String, String>): AutomationResult {
        lastParams = params
        return AutomationResult(success = true, message = "ok")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskerFireReceiverTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val receiver = TaskerFireReceiver()

    @Before
    fun setUp() {
        AutomationRegistry.clearForTesting()
    }

    @Test
    fun `a real FIRE_SETTING intent dispatches to the encoded action`() {
        val action = RecordingAction("send_sms")
        AutomationRegistry.register(action)

        val bundle = TaskerBundleCodec.encode("send_sms", mapOf("recipient" to "+1555", "message" to "hi"))
        val intent = Intent(TaskerFireReceiver.ACTION_FIRE_SETTING)
            .putExtra(TaskerFireReceiver.EXTRA_BUNDLE, bundle)

        receiver.onReceive(context, intent)

        assertEquals(mapOf("recipient" to "+1555", "message" to "hi"), action.lastParams)
    }

    @Test
    fun `an intent with the wrong action string is ignored`() {
        val action = RecordingAction("send_sms")
        AutomationRegistry.register(action)

        val bundle = TaskerBundleCodec.encode("send_sms", mapOf("recipient" to "+1555"))
        val intent = Intent("some.other.action").putExtra(TaskerFireReceiver.EXTRA_BUNDLE, bundle)

        receiver.onReceive(context, intent)

        assertNull(action.lastParams)
    }

    @Test
    fun `a FIRE_SETTING intent with no bundle at all doesn't crash`() {
        val action = RecordingAction("send_sms")
        AutomationRegistry.register(action)

        val intent = Intent(TaskerFireReceiver.ACTION_FIRE_SETTING)

        receiver.onReceive(context, intent)  // must not throw

        assertNull(action.lastParams)
    }

    @Test
    fun `a bundle encoding an unregistered action id is a no-op, not a crash`() {
        val bundle = TaskerBundleCodec.encode("nonexistent_action", emptyMap())
        val intent = Intent(TaskerFireReceiver.ACTION_FIRE_SETTING)
            .putExtra(TaskerFireReceiver.EXTRA_BUNDLE, bundle)

        receiver.onReceive(context, intent)  // must not throw
    }
}
