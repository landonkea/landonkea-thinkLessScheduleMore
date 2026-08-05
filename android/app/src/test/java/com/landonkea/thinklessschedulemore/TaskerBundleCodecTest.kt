// ───────────────────────────────────────────────────────────────────
// TaskerBundleCodecTest — the encode/decode round trip Tasker relies on
// ───────────────────────────────────────────────────────────────────
// This is the one piece of the Tasker integration where a real bug
// would be invisible until someone actually opened Tasker and tried
// it — TaskerEditActivity encodes, TaskerFireReceiver decodes,
// potentially days apart (Tasker persists the Bundle in the user's
// saved Task), so a round-trip mismatch here is exactly the kind of
// thing worth pinning down with a test instead of just "it compiled."
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskerBundleCodecTest {

    @Test
    fun `encode then decode round-trips the action id and params exactly`() {
        val params = mapOf("recipient" to "+15551234567", "message" to "hello there")

        val bundle = TaskerBundleCodec.encode("send_sms", params)
        val decoded = TaskerBundleCodec.decode(bundle)

        assertEquals("send_sms" to params, decoded)
    }

    @Test
    fun `round-trips an action with no params at all`() {
        val bundle = TaskerBundleCodec.encode("no_params_action", emptyMap())

        val decoded = TaskerBundleCodec.decode(bundle)

        assertEquals("no_params_action" to emptyMap<String, String>(), decoded)
    }

    @Test
    fun `decode returns null for a bundle with no action id`() {
        val bundle = android.os.Bundle().apply { putString("param_recipient", "+1555") }

        assertNull(TaskerBundleCodec.decode(bundle))
    }

    @Test
    fun `decode ignores unrelated keys that don't use the param prefix`() {
        val bundle = TaskerBundleCodec.encode("send_sms", mapOf("recipient" to "+1555"))
        bundle.putString("some_unrelated_key", "should not appear in params")

        val (_, params) = TaskerBundleCodec.decode(bundle)!!

        assertEquals(setOf("recipient"), params.keys)
    }

    @Test
    fun `param values containing special characters survive the round trip`() {
        val params = mapOf("message" to "Hi! 100% done — see you @ 5pm? \"quoted\"")

        val bundle = TaskerBundleCodec.encode("send_sms", params)
        val decoded = TaskerBundleCodec.decode(bundle)

        assertEquals(params, decoded?.second)
    }
}
