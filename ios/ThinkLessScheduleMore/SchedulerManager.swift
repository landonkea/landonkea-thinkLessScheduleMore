// ───────────────────────────────────────────────────────────────────
// SchedulerManager — the iOS scheduling engine
// ───────────────────────────────────────────────────────────────────
// This is the iOS equivalent of Android's SchedulerService.
// It:
//   1. Picks random times within the user's window
//   2. Schedules local notifications at those times
//   3. Runs in-process (iOS doesn't allow true background services,
//      so the scheduling only works while the app is in memory)
//
// Difference from Android: iOS kills background apps aggressively.
// This scheduler's notifications WILL fire (iOS handles that),
// but to keep the scheduling loop running, the app needs to be
// opened periodically.  In practice: the user opens the app once,
// sets it up, and iOS notifications handle the rest.
// ───────────────────────────────────────────────────────────────────

import Foundation
import UIKit

class SchedulerManager {

    // ── The message store (reads settings) ───────────────────────
    private let store: MessageStore

    // ── Notification manager (sends local notifications) ─────────
    private let notifier = NotificationManager.shared

    // ── Init ─────────────────────────────────────────────────────
    init(store: MessageStore) {
        self.store = store
    }

    // ── Schedule today's messages ─────────────────────────────────
    // Called when the user enables scheduling.
    // Picks random times and creates local notifications.
    func scheduleToday() {
        // Cancel any previously scheduled notifications.
        notifier.cancelAll()

        guard store.isEnabled else {
            store.nextScheduledTime = nil
            return
        }
        guard !store.recipientNumber.isEmpty else {
            store.nextScheduledTime = nil
            return
        }
        guard !store.messages.isEmpty else {
            store.nextScheduledTime = nil
            return
        }

        let now = Date()
        let calendar = Calendar.current

        // ── Calculate today's window boundaries ─────────────────
        var startComponents = calendar.dateComponents([.year, .month, .day], from: now)
        startComponents.hour = store.hourStart
        startComponents.minute = 0
        guard let windowStart = calendar.date(from: startComponents) else { return }

        var endComponents = startComponents
        endComponents.hour = store.hourEnd
        guard let windowEnd = calendar.date(from: endComponents) else { return }

        // ── If window is already closed, schedule tomorrow ──────
        if now > windowEnd {
            scheduleTomorrow()
            store.nextScheduledTime = tomorrowWindowStart()
            return
        }

        // ── Calculate available time and pick random slots ─────
        let availableSeconds = windowEnd.timeIntervalSince(max(now, windowStart))
        guard availableSeconds > 0 else { return }

        let maxMessages = min(store.maxPerDay, store.messages.count)
        let minIntervalSeconds = Double(store.minInterval * 60)

        // Divide the window into segments and pick one random time
        // within each segment (same strategy as Android).
        let segmentSeconds = availableSeconds / Double(maxMessages)

        // Track the earliest send time so it can be surfaced in the UI
        // (see MessageStore.nextScheduledTime).
        var earliestSendTime: Date? = nil

        for i in 0..<maxMessages {
            // Pick a random time within this segment.
            let segmentStart = segmentSeconds * Double(i)
            let segmentEnd = segmentSeconds * Double(i + 1) - minIntervalSeconds
            guard segmentEnd > segmentStart else { continue }

            let randomOffset = Double.random(in: segmentStart...segmentEnd)
            let sendTime = max(now, windowStart).addingTimeInterval(randomOffset)

            // Pick a message, avoiding whatever we've picked most
            // recently (see MessageSelector — no more back-to-back
            // repeats from a small pool).
            let template = MessageSelector.pick(pool: store.messages, recentlySent: store.recentlySent)
            store.addRecentlySent(template)

            // Render {name}/{time-of-day} placeholders against the
            // hour this message is actually scheduled to go out.
            let hour = calendar.component(.hour, from: sendTime)
            let message = MessageTemplate.render(template, name: store.recipientName, hour: hour)

            // Log it first so the id exists before the notification can
            // possibly be tapped (schedule → id → notify → log would
            // leave a window where a tap arrives before the log entry
            // exists; this ordering avoids that race).
            let id = UUID()
            store.addToLog(id: id, timestamp: sendTime, status: "pending", message: message)

            // Schedule the notification, tagged with the same id so a
            // tap can flip this log entry to "opened".
            notifier.scheduleNotification(
                at: sendTime,
                message: message,
                recipient: store.recipientNumber,
                id: id
            )

            if earliestSendTime == nil || sendTime < earliestSendTime! {
                earliestSendTime = sendTime
            }
        }

        store.nextScheduledTime = earliestSendTime

        // Schedule tomorrow's check (so we keep repeating).
        scheduleTomorrow()
    }

    // ── Schedule tomorrow's first notification window ────────────
    // Uses a local notification that fires at windowStart tomorrow.
    // When it fires, the user opens the app, which re-schedules.
    private func scheduleTomorrow() {
        guard let startTime = tomorrowWindowStart() else { return }

        // Schedule a "wake up" notification.
        notifier.scheduleNotification(
            at: startTime,
            message: "Tap to schedule today's messages",
            recipient: store.recipientNumber
        )
    }

    // ── Compute tomorrow's window-start Date ──────────────────────
    private func tomorrowWindowStart() -> Date? {
        let calendar = Calendar.current
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date())!

        var startComponents = calendar.dateComponents([.year, .month, .day], from: tomorrow)
        startComponents.hour = store.hourStart
        startComponents.minute = 0

        return calendar.date(from: startComponents)
    }

    // ── Cancel all scheduled messages ─────────────────────────────
    func cancelAll() {
        notifier.cancelAll()
        store.nextScheduledTime = nil
    }
}
