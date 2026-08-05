// ───────────────────────────────────────────────────────────────────
// SmsResultMapperTest — resultCode → SendStatus mapping
// ───────────────────────────────────────────────────────────────────
// SmsResultMapper only touches compile-time int constants from the
// Android SDK stub jar (Activity.RESULT_OK, SmsManager.RESULT_ERROR_*),
// same as MessageSelector/StatsCalculator reference other android.*
// framework constants — no Robolectric needed, this runs as a plain
// JVM unit test.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsResultMapperTest {

    @Test
    fun `mapSentResult maps RESULT_OK to SENT`() {
        assertEquals(SendStatus.SENT, SmsResultMapper.mapSentResult(Activity.RESULT_OK))
    }

    @Test
    fun `mapSentResult maps every known error code to FAILED`() {
        for (errorCode in SmsResultMapper.knownErrorCodes) {
            assertEquals(SendStatus.FAILED, SmsResultMapper.mapSentResult(errorCode))
        }
    }

    @Test
    fun `mapDeliveredResult maps RESULT_OK to DELIVERED`() {
        assertEquals(SendStatus.DELIVERED, SmsResultMapper.mapDeliveredResult(Activity.RESULT_OK))
    }

    @Test
    fun `mapDeliveredResult maps every known error code to FAILED`() {
        for (errorCode in SmsResultMapper.knownErrorCodes) {
            assertEquals(SendStatus.FAILED, SmsResultMapper.mapDeliveredResult(errorCode))
        }
    }
}
