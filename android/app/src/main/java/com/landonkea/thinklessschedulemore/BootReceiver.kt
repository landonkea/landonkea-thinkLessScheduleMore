// ───────────────────────────────────────────────────────────────────
// BootReceiver — restarts scheduling after a device reboot
// ───────────────────────────────────────────────────────────────────
// Without this, SchedulerService (a plain Foreground Service, not
// WorkManager) simply doesn't exist anymore after a reboot — Android
// doesn't restart services on its own. The user would see "Enabled"
// still checked in the UI, believe messages are being sent, and
// nothing would actually go out until they manually reopened the app
// and re-toggled the switch. That's a silent, total feature outage.
//
// We listen for BOOT_COMPLETED (and MY_PACKAGE_REPLACED, which fires
// after the app itself is updated/reinstalled — the service is torn
// down then too) and, if the user had scheduling enabled with a
// recipient set, restart SchedulerService. SchedulerService's own
// scheduleNext() re-validates messages/recipient/enabled state, so
// this receiver only needs to decide "should we even try."
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val store = MessageStore(context)

                // Only restart if the user actually had scheduling on.
                // SchedulerService.scheduleNext() double-checks this too,
                // but there's no point starting a foreground service at
                // all (and showing its notification) if it'll just stop
                // itself immediately.
                if (store.isEnabled() && store.getRecipient().isNotEmpty()) {
                    val serviceIntent = Intent(context, SchedulerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
