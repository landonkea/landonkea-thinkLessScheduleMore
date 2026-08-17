// ───────────────────────────────────────────────────────────────────
// SchedulerService, the background engine
// ───────────────────────────────────────────────────────────────────
// This is a "Foreground Service", Android keeps it running even
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
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
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
    private lateinit var noSendDayStore: NoSendDayStore
    private lateinit var priorityStore: MessagePriorityStore

    // ── SMS sending ──────────────────────────────────────────────
    // This service is a TRIGGER (a timer, plus the recurring-date
    // check below), it no longer knows how to send an SMS itself.
    // sendSms() now just calls AutomationRegistry.execute("send_sms",
    // ...), the exact same call a Tasker-fired trigger would make
    // (see SendSmsAction/AutomationRegistry). All the actual
    // PendingIntent/delivery-confirmation plumbing that used to live
    // here moved to SmsSender, which owns its own short-lived
    // receiver per send instead of this service holding one open for
    // its whole lifetime.

    // ── Service lifecycle ─────────────────────────────────────────

    // Called when the service starts (via Intent).
    // NOTE: onStartCommand can run more than once for the same running
    // service instance, e.g. if startForegroundService() is called again
    // while the service is already alive, or Android redelivers the
    // start after a process restart. Without a guard, each call would
    // stack an additional independent scheduleNext() chain on top of
    // whatever is already pending, causing duplicate/overlapping SMS
    // sends. Clearing pending callbacks first makes this method safe
    // to call repeatedly, there is always at most one active chain.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        store = MessageStore(this)
        recurringStore = RecurringMessageStore(this)
        noSendDayStore = NoSendDayStore(this)
        priorityStore = MessagePriorityStore(this)

        // Show the persistent notification (required for foreground).
        startForeground(NOTIFICATION_ID, createNotification())

        // Cancel any timer already in flight before starting a new chain.
        handler.removeCallbacksAndMessages(null)

        // Check today's recurring (birthday/anniversary-style) messages
        // once per onStartCommand, this runs whenever the service (re)starts
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
                // Already sent today, the guard against duplicate sends
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
            stopSelf()  // Kill the service, nothing to do.
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

        // ── "No send" day gate ────────────────────────────────────
        // Skips the random pool schedule entirely for today if it's a
        // blocked weekday or a one-off blocked date (see NoSendDayChecker).
        // Recurring (birthday/anniversary) messages are unaffected, they're
        // handled separately above by sendDueRecurringMessages, which
        // isn't gated by this check, guaranteed sends stay guaranteed.
        val todayCal = java.util.Calendar.getInstance()
        val todayWeekday = todayCal.get(java.util.Calendar.DAY_OF_WEEK)
        val todayKey = TODAY_KEY_FORMAT.format(todayCal.time)
        if (NoSendDayChecker.isNoSendDay(
                weekday = todayWeekday,
                dateKey = todayKey,
                noSendWeekdays = noSendDayStore.getNoSendWeekdays(),
                noSendDates = noSendDayStore.getNoSendDates()
            )
        ) {
            scheduleTomorrow()
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
            // recently (see MessageSelector, no more back-to-back
            // repeats from a small pool) and biased by whatever
            // priority weight the user set per message (see
            // WeightedMessageSelector/MessagePriorityStore, a message
            // with no weight set is treated as normal priority).
            val template = WeightedMessageSelector.pick(
                messages,
                priorityStore.getWeights(),
                store.getRecentlySent()
            )
            store.addRecentlySent(template)

            // Render {name}/{time-of-day} placeholders against the
            // actual send time, so one template produces variety.
            val sendHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val rendered = MessageTemplate.render(template, store.getRecipientName(), sendHour)

            // Send the SMS (this also logs it, see sendSms).
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
    @VisibleForTesting
    fun calculateRandomDelay(now: Long, startHour: Int, endHour: Int): Long {
        // Window bounds in the DEVICE'S LOCAL time, not UTC: startHour/
        // endHour come from a plain 0-23 SeekBar the user reads as their
        // own wall-clock hour, so "today" has to mean local-midnight,
        // not the most recent UTC midnight (now % 86400000L was doing
        // the latter, offsetting the whole window by the device's UTC
        // offset for anyone not literally in UTC).
        val windowStartMs = localHourToday(now, startHour)  // e.g. 9 AM
        val windowEndMs = localHourToday(now, endHour)    // e.g. 9 PM

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
        val startHour = store.getHourStart()
        val windowStartMs = localHourToday(now, startHour, addDays = 1)
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

    // ── Resolve a wall-clock hour to an epoch millis timestamp ────
    // `hour` is local time (0-23, as set on the SeekBar in
    // MainActivity), so this anchors to the device's local calendar
    // day, not a UTC day, and lets Calendar's own arithmetic (rather
    // than raw millis math) absorb any DST transition when addDays
    // crosses one.
    @VisibleForTesting
    fun localHourToday(now: Long, hour: Int, addDays: Int = 0): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        if (addDays != 0) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, addDays)
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ── Send the actual SMS ───────────────────────────────────────
    // This service's ONLY job as a trigger is deciding WHEN to send,
    // the how (logging, SmsManager, delivery confirmation) lives in
    // SmsSender, reached through AutomationRegistry exactly like a
    // Tasker-fired trigger would reach it. Ignoring the returned
    // AutomationResult here is intentional: a synchronous dispatch
    // failure is already logged as FAILED by SmsSender itself (see
    // that class), so there's nothing further for a fire-and-forget
    // timer trigger to do with it.
    private fun sendSms(recipient: String, message: String) {
        AutomationRegistry.execute(
            this,
            "send_sms",
            mapOf("recipient" to recipient, "message" to message)
        )
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
        store.clearNextSendTime()
    }

    // ── Constants ─────────────────────────────────────────────────
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_scheduler_channel"

        private val TODAY_KEY_FORMAT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    }
}
