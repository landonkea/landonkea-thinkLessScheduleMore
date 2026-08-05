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
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

class SchedulerService : Service() {

    // ── Handler for timing ────────────────────────────────────────
    // Handler runs code on a specific thread after a delay.
    // We use the main Looper (UI thread) for simplicity.
    private val handler = Handler(Looper.getMainLooper())

    // The Runnable that triggers the next SMS send.
    private lateinit var sendRunnable: Runnable

    // Our data stores.
    private lateinit var store: MessageStore
    private lateinit var recurringStore: RecurringMessageStore

    // Whether the delivery-confirmation receiver (see below) is currently
    // registered, so onDestroy doesn't try to unregister twice (or a
    // never-registered receiver) if onStartCommand never got that far.
    private var receiverRegistered = false

    // ── Delivery-confirmation BroadcastReceiver ─────────────────────
    // sendSms() passes PendingIntents (carrying custom actions below) as
    // SmsManager's sentIntent/deliveryIntent. When Android fires those
    // intents back at us, this receiver reads the system-assigned
    // resultCode (RESULT_OK or one of SmsManager's RESULT_ERROR_* codes),
    // maps it to a SendStatus via SmsResultMapper, and updates the
    // matching log entry (found by the id we stashed as an intent extra).
    private val smsResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_LOG_ID) ?: return
            when (intent.action) {
                ACTION_SMS_SENT -> {
                    val status = SmsResultMapper.mapSentResult(resultCode)
                    val error = if (status == SendStatus.FAILED) "SMS send failed (resultCode=$resultCode)" else null
                    store.updateLogEntryStatus(id, status, error)
                }
                ACTION_SMS_DELIVERED -> {
                    val status = SmsResultMapper.mapDeliveredResult(resultCode)
                    // Only DELIVERED is meaningful here — some carriers never
                    // send a delivery report at all, which isn't a failure,
                    // it just means the entry stays at whatever mapSentResult
                    // already set it to (SENT). Don't downgrade a confirmed
                    // SENT to FAILED just because delivery wasn't confirmed.
                    if (status == SendStatus.DELIVERED) {
                        store.updateLogEntryStatus(id, SendStatus.DELIVERED)
                    }
                }
            }
        }
    }

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
        recurringStore = RecurringMessageStore(this)

        // Show the persistent notification (required for foreground).
        startForeground(NOTIFICATION_ID, createNotification())

        registerSmsResultReceiver()

        // Cancel any timer already in flight before starting a new chain.
        handler.removeCallbacksAndMessages(null)

        // Check today's recurring (birthday/anniversary-style) messages
        // once per onStartCommand — this runs whenever the service (re)starts
        // (enabled from the UI, boot, process restart). The per-id
        // last-fired-date guard in RecurringMessageStore makes repeated
        // calls on the same day safe, so it's fine that this can run more
        // than once in a given day.
        sendDueRecurringMessages()

        // Start the scheduling loop.
        scheduleNext()

        // If Android kills the service, restart it automatically.
        return START_STICKY
    }

    // ── Register the dynamic BroadcastReceiver for SMS callbacks ────
    private fun registerSmsResultReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
        }
        // Android 13+ (API 33) requires an explicit exported flag when
        // dynamically registering a receiver. This receiver only reacts to
        // our own PendingIntent callbacks, so RECEIVER_NOT_EXPORTED is the
        // correct/safe choice (nothing outside this app should trigger it).
        ContextCompat.registerReceiver(this, smsResultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    // ── Recurring (yearly date-based) messages ───────────────────────
    // Additive to the normal random-pool schedule: guaranteed to send on
    // their date regardless of whether/when the pool schedule fires today.
    private fun sendDueRecurringMessages() {
        val entries = recurringStore.getRecurringMessages()
        if (entries.isEmpty()) return

        val cal = java.util.Calendar.getInstance()
        val todayMonth = cal.get(java.util.Calendar.MONTH) + 1  // Calendar.MONTH is 0-based
        val todayDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val isLeapYear = (cal as java.util.GregorianCalendar).isLeapYear(cal.get(java.util.Calendar.YEAR))
        val todayKey = TODAY_KEY_FORMAT.format(cal.time)

        val dueToday = RecurringMessageMatcher.matchesToday(entries, todayMonth, todayDay, isLeapYear)
        for (entry in dueToday) {
            if (recurringStore.getLastFiredDateKey(entry.id) == todayKey) {
                // Already sent today — the guard against duplicate sends
                // when onStartCommand runs more than once in a day.
                continue
            }

            val recipient = store.getRecipient()
            if (recipient.isEmpty()) continue

            val sendHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val rendered = MessageTemplate.render(entry.message, store.getRecipientName(), sendHour)
            sendSms(recipient, rendered)
            recurringStore.setLastFiredDateKey(entry.id, todayKey)
        }
    }

    // ── The scheduling loop ───────────────────────────────────────
    private fun scheduleNext() {
        // Stop if scheduling is disabled or no recipient.
        if (!store.isEnabled() || store.getRecipient().isEmpty()) {
            store.clearNextSendTime()
            stopSelf()  // Kill the service — nothing to do.
            return
        }

        // Get the message pool.
        val messages = store.getMessages()
        if (messages.isEmpty()) {
            // No messages in the pool.  Stop the service.
            // User will restart it after adding messages.
            store.clearNextSendTime()
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

        // Surface the next send time to the UI (see MessageStore.getNextSendTime).
        store.saveNextSendTime(now + delayMs)

        // ── Create the Runnable (what happens when timer fires) ─
        sendRunnable = Runnable {
            // Pick a message, avoiding whatever we've sent most
            // recently (see MessageSelector — no more back-to-back
            // repeats from a small pool).
            val template = MessageSelector.pick(messages, store.getRecentlySent())
            store.addRecentlySent(template)

            // Render {name}/{time-of-day} placeholders against the
            // actual send time, so one template produces variety.
            val sendHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val rendered = MessageTemplate.render(template, store.getRecipientName(), sendHour)

            // Send the SMS (this also logs it — see sendSms).
            sendSms(store.getRecipient(), rendered)

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
            store.clearNextSendTime()
            stopSelf()
            return
        }

        // Surface tomorrow's window-open time as "next" until scheduleNext()
        // picks a more specific random time inside the window.
        store.saveNextSendTime(windowStartMs)

        handler.postDelayed({
            // Tomorrow's window has started.  Begin the loop.
            scheduleNext()
        }, delayMs)
    }

    // ── Send the actual SMS ───────────────────────────────────────
    // Logs a PENDING entry first (we don't yet know if it actually sent),
    // then wires SmsManager's sentIntent/deliveryIntent PendingIntents to
    // smsResultReceiver (see above) so the entry gets flipped to
    // SENT/FAILED/DELIVERED once Android calls back. If sendTextMessage
    // throws synchronously (e.g. missing permission), we flip the same
    // entry straight to FAILED rather than logging a second entry.
    private fun sendSms(recipient: String, message: String) {
        val logId = store.addToSentLog(System.currentTimeMillis(), SendStatus.PENDING, message)
        try {
            val smsManager = getSystemService(SmsManager::class.java)

            val sentIntent = PendingIntent.getBroadcast(
                this,
                logId.hashCode(),
                Intent(ACTION_SMS_SENT).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_LOG_ID, logId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val deliveryIntent = PendingIntent.getBroadcast(
                this,
                // Distinct request code from sentIntent so the two
                // PendingIntents don't collide/overwrite each other.
                (logId + "_delivered").hashCode(),
                Intent(ACTION_SMS_DELIVERED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_LOG_ID, logId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            smsManager.sendTextMessage(
                recipient,     // To: the recipient's number
                null,          // From: null = use default SIM
                message,       // The message text
                sentIntent,    // sentIntent: fires when the SMS leaves the device
                deliveryIntent // deliveryIntent: fires on carrier delivery confirmation (if supported)
            )
        } catch (e: Exception) {
            // Synchronous failure (e.g. SecurityException from a missing
            // permission) — flip the entry we already logged rather than
            // creating a duplicate one.
            store.updateLogEntryStatus(logId, SendStatus.FAILED, e.message)
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
        if (receiverRegistered) {
            unregisterReceiver(smsResultReceiver)
            receiverRegistered = false
        }
        store.clearNextSendTime()
    }

    // ── Constants ─────────────────────────────────────────────────
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_scheduler_channel"

        // Custom broadcast actions for the sentIntent/deliveryIntent
        // PendingIntents passed to SmsManager.sendTextMessage.
        private const val ACTION_SMS_SENT = "com.landonkea.thinklessschedulemore.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.landonkea.thinklessschedulemore.SMS_DELIVERED"
        private const val EXTRA_LOG_ID = "log_id"

        private val TODAY_KEY_FORMAT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    }
}
