// ───────────────────────────────────────────────────────────────────
// SchedulerService — the background engine
// ───────────────────────────────────────────────────────────────────
// This is a "Foreground Service" — Android keeps it running even
// if the app is in the background.  It works like this:
//
//   1. When the user enables scheduling, we start this service.
//   2. It picks a random time within the user's window.
//   3. It uses a Handler (timer) to wait until that time.
//   4. When the timer fires, it sends the SMS.
//   5. Then it picks the NEXT random time and waits again.
//   6. At the end of the day, it stops and waits for tomorrow.
//
// Why a Foreground Service instead of WorkManager?
//   - WorkManager is designed for "fire and forget" tasks.
//   - We need a PERSISTENT timer (pick random time, wait, send,
//     pick next time, wait...).  A Service is the right tool.
//   - The notification is required by Android for foreground services.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class SchedulerService : Service() {

    // ── Handler for timing ────────────────────────────────────────
    // Handler runs code on a specific thread after a delay.
    // We use the main Looper (UI thread) for simplicity.
    private val handler = Handler(Looper.getMainLooper())

    // The Runnable that triggers the next SMS send.
    private lateinit var sendRunnable: Runnable

    // Our data store.
    private lateinit var store: MessageStore

    // ── Service lifecycle ─────────────────────────────────────────

    // Called when the service starts (via Intent).
    // NOTE: onStartCommand can run more than once for the same running
    // service instance — e.g. if startForegroundService() is called again
    // while the service is already alive, or Android redelivers the
    // start after a process restart. Without a guard, each call would
    // stack an additional independent scheduleNext() chain on top of
    // whatever is already pending, causing duplicate/overlapping SMS
    // sends. Clearing pending callbacks first makes this method safe
    // to call repeatedly — there is always at most one active chain.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        store = MessageStore(this)

        // Show the persistent notification (required for foreground).
        startForeground(NOTIFICATION_ID, createNotification())

        // Cancel any timer already in flight before starting a new chain.
        handler.removeCallbacksAndMessages(null)

        // Start the scheduling loop.
        scheduleNext()

        // If Android kills the service, restart it automatically.
        return START_STICKY
    }

    // ── The scheduling loop ───────────────────────────────────────
    private fun scheduleNext() {
        // Stop if scheduling is disabled or no recipient.
        if (!store.isEnabled() || store.getRecipient().isEmpty()) {
            stopSelf()  // Kill the service — nothing to do.
            return
        }

        // Get the message pool.
        val messages = store.getMessages()
        if (messages.isEmpty()) {
            // No messages in the pool.  Stop the service.
            // User will restart it after adding messages.
            stopSelf()
            return
        }

        // ── Pick a random delay within the time window ───────────
        val now = System.currentTimeMillis()
        val startHour = store.getHourStart()
        val endHour = store.getHourEnd()

        // Calculate the delay in milliseconds.
        val delayMs = calculateRandomDelay(now, startHour, endHour)

        if (delayMs <= 0) {
            // Time window has passed for today.  Schedule tomorrow.
            scheduleTomorrow()
            return
        }

        // ── Create the Runnable (what happens when timer fires) ─
        sendRunnable = Runnable {
            // Pick a random message from the pool.
            val randomMessage = messages[Random.nextInt(messages.size)]

            // Send the SMS.
            sendSms(store.getRecipient(), randomMessage)

            // Log it.
            store.addToSentLog("${System.currentTimeMillis()}|sent|$randomMessage")

            // Pick the NEXT random time and wait again.
            // This creates the loop: send → wait → send → wait...
            scheduleNext()
        }

        // Start the timer.
        handler.postDelayed(sendRunnable, delayMs)
    }

    // ── Calculate a random delay within the time window ───────────
    // Returns milliseconds to wait.
    private fun calculateRandomDelay(now: Long, startHour: Int, endHour: Int): Long {
        // Get today's start and end in milliseconds.
        val dayStartMs = now - (now % 86400000L)         // Midnight today
        val windowStartMs = dayStartMs + (startHour * 3600000L)  // e.g. 9 AM
        val windowEndMs = dayStartMs + (endHour * 3600000L)    // e.g. 9 PM

        // If the window hasn't started yet, schedule for window start.
        if (now < windowStartMs) {
            return windowStartMs - now
        }

        // If the window has already ended, schedule for tomorrow.
        if (now >= windowEndMs) {
            return -1  // Signal "schedule tomorrow"
        }

        // Window is active.  Pick a random time between now and window end.
        val availableMs = windowEndMs - now
        val minIntervalMs = store.getMinInterval() * 60_000L

        // Make sure we have enough time for at least minInterval.
        if (availableMs < minIntervalMs) {
            return -1  // Not enough time left today
        }

        // Pick random delay between minInterval and available time.
        return Random.nextLong(minIntervalMs, availableMs + 1)
    }

    // ── Schedule tomorrow's first send ───────────────────────────
    // Called when today's window is over.  Schedules a single
    // timer that fires at the START of tomorrow's window.
    private fun scheduleTomorrow() {
        val now = System.currentTimeMillis()
        val tomorrowStart = now - (now % 86400000L) + 86400000L
        val startHour = store.getHourStart()
        val windowStartMs = tomorrowStart + (startHour * 3600000L)
        val delayMs = windowStartMs - now

        if (delayMs <= 0) {
            // Tomorrow already started?  Just stop.
            stopSelf()
            return
        }

        handler.postDelayed({
            // Tomorrow's window has started.  Begin the loop.
            scheduleNext()
        }, delayMs)
    }

    // ── Send the actual SMS ───────────────────────────────────────
    private fun sendSms(recipient: String, message: String) {
        try {
            val smsManager = getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(
                recipient,   // To: the recipient's number
                null,        // From: null = use default SIM
                message,     // The message text
                null,        // sentIntent: null = no callback
                null         // deliveryIntent: null = no callback
            )
        } catch (e: Exception) {
            // Log the failure but don't crash the service.
            store.addToSentLog("${System.currentTimeMillis()}|failed|$message|${e.message}")
        }
    }

    // ── Notification (required by Android for foreground services) ─
    private fun createNotification(): Notification {
        // Create the notification channel (required on Android 8+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Scheduler",
                NotificationManager.IMPORTANCE_LOW  // Low = no sound
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ThinkLessScheduleMore")
            .setContentText("Scheduling sweet messages 💕")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Required override (we don't bind, so return null) ─────────
    override fun onBind(intent: Intent?): IBinder? = null

    // ── Clean up when service stops ───────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)  // Cancel all pending timers
    }

    // ── Constants ─────────────────────────────────────────────────
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_scheduler_channel"
    }
}
