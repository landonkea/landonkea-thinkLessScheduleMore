// ───────────────────────────────────────────────────────────────────
// SmsResultMapper — turns an Android broadcast resultCode into a
// SendStatus
// ───────────────────────────────────────────────────────────────────
// SmsManager.sendTextMessage's sentIntent/deliveryIntent PendingIntents
// fire as broadcasts whose BroadcastReceiver.getResultCode() is set
// by the system to Activity.RESULT_OK on success, or one of
// SmsManager's RESULT_ERROR_* constants on failure.
//
// Kept as a pure object with no Context dependency — it only touches
// compile-time int constants from the Android SDK stub jar (same as
// how other files in this codebase reference android.* framework
// constants), so it's trivially unit-testable on the plain JVM, same
// as MessageSelector/StatsCalculator.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.app.Activity
import android.telephony.SmsManager

object SmsResultMapper {

    // resultCode from the "sent" PendingIntent — did the SMS leave the device?
    fun mapSentResult(resultCode: Int): SendStatus = when (resultCode) {
        Activity.RESULT_OK -> SendStatus.SENT
        else -> SendStatus.FAILED
    }

    // resultCode from the "delivered" PendingIntent — did the carrier confirm
    // the recipient's device received it? Only fires if the carrier supports
    // delivery reports; some carriers/regions never call this back at all,
    // in which case the entry just stays at SENT — that's fine, not a bug.
    fun mapDeliveredResult(resultCode: Int): SendStatus = when (resultCode) {
        Activity.RESULT_OK -> SendStatus.DELIVERED
        else -> SendStatus.FAILED
    }

    // Exposed mainly so tests can enumerate "every error code we know about"
    // without hardcoding the SmsManager constant values themselves.
    val knownErrorCodes: List<Int> = listOf(
        SmsManager.RESULT_ERROR_GENERIC_FAILURE,
        SmsManager.RESULT_ERROR_NO_SERVICE,
        SmsManager.RESULT_ERROR_NULL_PDU,
        SmsManager.RESULT_ERROR_RADIO_OFF,
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED,
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE
    )
}
