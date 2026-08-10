// ───────────────────────────────────────────────────────────────────
// AutomationRegistryTest, proves the trigger/action decoupling is real
// ───────────────────────────────────────────────────────────────────
// Uses a fake action (not SendSmsAction) specifically so these tests
// can't accidentally pass just because SMS-sending logic happens to
// work, they're testing that ANY registered action is reachable by
// id, with no special-casing anywhere in AutomationRegistry, which is
// the actual architectural property this refactor exists to deliver.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeAction(
    override val id: String,
    private val result: AutomationResult = AutomationResult(success = true, message = "ok"),
) : AutomationAction {
    override val displayName = "Fake Action ($id)"
    override val paramSchema = emptyMap<String, String>()
    var lastParams: Map<String, String>? = null

    override fun execute(context: android.content.Context, params: Map<String, String>): AutomationResult {
        lastParams = params
        return result
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRegistryTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        AutomationRegistry.clearForTesting()
    }

    @Test
    fun `execute dispatches to the action registered under that id`() {
        val action = FakeAction("greet")
        AutomationRegistry.register(action)

        val result = AutomationRegistry.execute(context, "greet", mapOf("name" to "World"))

        assertTrue(result.success)
        assertEquals(mapOf("name" to "World"), action.lastParams)
    }

    @Test
    fun `execute on an unregistered id fails without touching any action`() {
        val action = FakeAction("greet")
        AutomationRegistry.register(action)

        val result = AutomationRegistry.execute(context, "does_not_exist", emptyMap())

        assertFalse(result.success)
        assertTrue(result.message.contains("does_not_exist"))
        assertEquals(null, action.lastParams)  // the wrong action was never called
    }

    @Test
    fun `two different actions are independently reachable by id`() {
        val greet = FakeAction("greet")
        val farewell = FakeAction("farewell")
        AutomationRegistry.register(greet)
        AutomationRegistry.register(farewell)

        AutomationRegistry.execute(context, "farewell", mapOf("x" to "1"))

        assertEquals(null, greet.lastParams)
        assertEquals(mapOf("x" to "1"), farewell.lastParams)
    }

    @Test
    fun `registering a second action under the same id replaces the first`() {
        val original = FakeAction("greet", AutomationResult(success = true, message = "v1"))
        val replacement = FakeAction("greet", AutomationResult(success = true, message = "v2"))
        AutomationRegistry.register(original)
        AutomationRegistry.register(replacement)

        val result = AutomationRegistry.execute(context, "greet", emptyMap())

        assertEquals("v2", result.message)
        assertEquals(null, original.lastParams)
    }

    @Test
    fun `allActions reflects every registered action`() {
        AutomationRegistry.register(FakeAction("a"))
        AutomationRegistry.register(FakeAction("b"))

        val ids = AutomationRegistry.allActions().map { it.id }.toSet()

        assertEquals(setOf("a", "b"), ids)
    }

    @Test
    fun `find returns null for an unregistered id`() {
        assertEquals(null, AutomationRegistry.find("nope"))
    }
}
