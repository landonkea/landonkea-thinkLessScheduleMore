// ───────────────────────────────────────────────────────────────────
// NotificationManager, handles iOS local notifications
// ───────────────────────────────────────────────────────────────────
// iOS does NOT allow apps to send SMS silently.
// The best we can do is:
//   1. Schedule a local notification at a random time.
//   2. When the user taps it, open the Messages app with the
//      recipient and message pre-filled.
//   3. User taps "Send" once.
//
// This is the iOS equivalent of Android's SmsManager.
// ───────────────────────────────────────────────────────────────────

import Foundation
import UserNotifications  // iOS notification framework
// NOTE: MessageUI is not imported because OpenSmsComposeAction uses the
// sms:// URL scheme instead of MFMessageComposeViewController (simpler,
// no in-app compose) -- see that file for the actual URL-opening logic.

// ── NotificationManager ───────────────────────────────────────────
// Handles scheduling notifications and opening the Messages app.
//
// Also acts as UNUserNotificationCenterDelegate: this is what wires
// "user taps the notification" to "open Messages pre-filled", the
// last step of the core loop described in shared/ARCHITECTURE.md.
// Without registering as the delegate, iOS still shows the
// notification, but tapping it just opens the app to the main
// screen and does nothing with the recipient/message baked into it.
class NotificationManager: NSObject, UNUserNotificationCenterDelegate {

    // ── Singleton ─────────────────────────────────────────────────
    // There should only be one NotificationManager in the app.
    static let shared = NotificationManager()

    // Category identifier for real send-time notifications, the
    // "wake up" (tomorrow re-schedule trigger) notifications are NOT
    // tagged with this, since snoozing a re-schedule trigger wouldn't
    // mean anything (there's no recipient/message to delay).
    static let scheduledMessageCategory = "SCHEDULED_MESSAGE"

    private override init() {
        super.init()
        // Become the delegate so we get notified when the user taps
        // (or a notification arrives while the app is foregrounded).
        UNUserNotificationCenter.current().delegate = self
        // Register the snooze actions once so every notification
        // tagged with scheduledMessageCategory shows them.
        registerNotificationCategories()
        // Request notification permission on init.
        requestPermission()
    }

    // ── Register notification categories/actions ──────────────────
    // "Snooze 15 min" / "Snooze 1 hour" appear on any notification
    // whose content.categoryIdentifier is scheduledMessageCategory
    // (see scheduleNotification below).
    private func registerNotificationCategories() {
        let snooze15 = UNNotificationAction(
            identifier: SnoozeDuration.snooze15ActionIdentifier,
            title: "Snooze 15 min",
            options: []
        )
        let snooze60 = UNNotificationAction(
            identifier: SnoozeDuration.snooze60ActionIdentifier,
            title: "Snooze 1 hour",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: NotificationManager.scheduledMessageCategory,
            actions: [snooze15, snooze60],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    // ── Tap callback (feeds the send-log "opened" status) ─────────
    // Set by ContentView (which owns the MessageStore) so this class
    // stays store-agnostic. Called with the log entry's id whenever
    // the user taps a real send-time notification (not a "wake up"
    // one, those carry no id).
    var onOpen: ((UUID) -> Void)?

    // ── Request notification permission ───────────────────────────
    // iOS shows a system dialog asking the user to allow notifications.
    // This must be called before scheduling any notifications.
    func requestPermission() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, error in
            if !granted {
                print("Notification permission denied, scheduling won't work.")
            }
        }
    }

    // ── Schedule a notification at a specific time ────────────────
    // iOS will show this notification at the given date.
    // When tapped, the app opens and calls the URL scheme for
    // the Messages app.
    func scheduleNotification(at date: Date, message: String, recipient: String, id: UUID? = nil) {
        let center = UNUserNotificationCenter.current()

        // ── Create the notification content ─────────────────────
        let content = UNMutableNotificationContent()
        content.title = "💕 ThinkLessScheduleMore"
        content.body = "Send: \"\(message)\""
        content.sound = .default

        // ── Encode recipient + message in the userInfo ─────────
        // When the user taps the notification, we read this data
        // to open the Messages app pre-filled. `id`, when present,
        // ties this notification back to its SentLogEntry so the tap
        // can flip that entry's status to "opened" (see `onOpen`).
        var userInfo: [String: String] = [
            "recipient": recipient,
            "message": message
        ]
        if let id = id {
            userInfo["id"] = id.uuidString
            // Only real send-time notifications (the ones with an id
            // tying them to a SentLogEntry) get snooze actions, the
            // "wake up" re-schedule trigger has nothing to delay.
            content.categoryIdentifier = NotificationManager.scheduledMessageCategory
        }
        content.userInfo = userInfo

        // ── Create the trigger (specific date/time) ────────────
        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: date
        )
        let trigger = UNCalendarNotificationTrigger(
            dateMatching: components,
            repeats: false
        )

        // ── Create the request ─────────────────────────────────
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,  // Unique ID per notification
            content: content,
            trigger: trigger
        )

        // ── Schedule it ────────────────────────────────────────
        center.add(request) { error in
            if let error = error {
                print("Failed to schedule notification: \(error)")
            }
        }
    }

    // ── Cancel ALL scheduled notifications ───────────────────────
    // Called when the user pauses scheduling.
    func cancelAll() {
        UNUserNotificationCenter.current()
            .removeAllPendingNotificationRequests()
    }

    // ── Open Messages app with pre-filled message ─────────────────
    // Called when the user taps a notification. The real work moved
    // to OpenSmsComposeAction, reached through AutomationRegistry
    // exactly the way a future Siri/Shortcuts-triggered AppIntent
    // will reach it too, a notification tap is just one more
    // trigger sharing the same action, not special-cased logic.
    static func openMessages(recipient: String, message: String) {
        _ = AutomationRegistry.shared.execute(
            actionId: "open_sms_compose",
            params: ["recipient": recipient, "message": message]
        )
    }

    // ── UNUserNotificationCenterDelegate ────────────────────────────

    // Called when the user taps a delivered notification (app was in
    // background or not running). This is where "wake up" notifications
    // (from scheduleTomorrow) and real send-time notifications diverge:
    // a "wake up" one has no recipient/message in userInfo, so there's
    // nothing to pre-fill, the tap just brings the user into the app,
    // which re-schedules today's sends via ContentView's onAppear/toggle
    // flow. A real send-time notification carries recipient + message,
    // so we open Messages pre-filled with them.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // ── Snooze actions ("Snooze 15 min" / "Snooze 1 hour") ──────
        // These reschedule the notification instead of opening
        // Messages, the underlying SentLogEntry stays "pending", so
        // this genuinely delays the send (see SnoozeCalculator.swift
        // and Feature A notes in the task for the full rationale).
        if let duration = SnoozeDuration(actionIdentifier: response.actionIdentifier) {
            handleSnooze(response: response, duration: duration)
            completionHandler()
            return
        }

        let userInfo = response.notification.request.content.userInfo
        if let recipient = userInfo["recipient"] as? String,
           let message = userInfo["message"] as? String,
           !recipient.isEmpty {
            NotificationManager.openMessages(recipient: recipient, message: message)

            if let idString = userInfo["id"] as? String, let id = UUID(uuidString: idString) {
                onOpen?(id)
            }
        }
        completionHandler()
    }

    // ── Handle a snooze action ─────────────────────────────────────
    // Reads the original notification's delivery date + userInfo,
    // computes the new fire date, removes the now-delivered
    // notification, and schedules a fresh one at the new time with
    // the same recipient/message/id (so it re-flips the same log
    // entry to "opened" whenever the user finally taps it, and can be
    // snoozed again since it's tagged with the same category).
    private func handleSnooze(response: UNNotificationResponse, duration: SnoozeDuration) {
        let request = response.notification.request
        let userInfo = request.content.userInfo
        guard let recipient = userInfo["recipient"] as? String,
              let message = userInfo["message"] as? String else { return }

        let id: UUID? = (userInfo["id"] as? String).flatMap { UUID(uuidString: $0) }

        // `response.notification.date` is the actual delivery
        // (fire) date of the notification that was just acted on.
        let originalFireDate = response.notification.date
        let newFireDate = SnoozeCalculator.newFireDate(from: originalFireDate, snoozing: duration)

        // The delivered notification is gone once acted on, but
        // removing it from the delivered list keeps Notification
        // Center tidy.
        UNUserNotificationCenter.current()
            .removeDeliveredNotifications(withIdentifiers: [request.identifier])

        scheduleNotification(at: newFireDate, message: message, recipient: recipient, id: id)
    }

    // Called when a notification would fire while the app is already
    // in the foreground. Without this, foreground notifications are
    // silently swallowed (no banner, no sound), show them like normal.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
