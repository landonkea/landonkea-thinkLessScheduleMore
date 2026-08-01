// ───────────────────────────────────────────────────────────────────
// NotificationManager — handles iOS local notifications
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
import UIKit             // UIApplication — needed to open the sms:// URL
// NOTE: MessageUI is not imported because we use the sms:// URL scheme
// instead of MFMessageComposeViewController (simpler, no in-app compose).

// ── NotificationManager ───────────────────────────────────────────
// Handles scheduling notifications and opening the Messages app.
//
// Also acts as UNUserNotificationCenterDelegate: this is what wires
// "user taps the notification" to "open Messages pre-filled" — the
// last step of the core loop described in shared/ARCHITECTURE.md.
// Without registering as the delegate, iOS still shows the
// notification, but tapping it just opens the app to the main
// screen and does nothing with the recipient/message baked into it.
class NotificationManager: NSObject, UNUserNotificationCenterDelegate {

    // ── Singleton ─────────────────────────────────────────────────
    // There should only be one NotificationManager in the app.
    static let shared = NotificationManager()

    private override init() {
        super.init()
        // Become the delegate so we get notified when the user taps
        // (or a notification arrives while the app is foregrounded).
        UNUserNotificationCenter.current().delegate = self
        // Request notification permission on init.
        requestPermission()
    }

    // ── Request notification permission ───────────────────────────
    // iOS shows a system dialog asking the user to allow notifications.
    // This must be called before scheduling any notifications.
    func requestPermission() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, error in
            if !granted {
                print("Notification permission denied — scheduling won't work.")
            }
        }
    }

    // ── Schedule a notification at a specific time ────────────────
    // iOS will show this notification at the given date.
    // When tapped, the app opens and calls the URL scheme for
    // the Messages app.
    func scheduleNotification(at date: Date, message: String, recipient: String) {
        let center = UNUserNotificationCenter.current()

        // ── Create the notification content ─────────────────────
        let content = UNMutableNotificationContent()
        content.title = "💕 ThinkLessScheduleMore"
        content.body = "Send: \"\(message)\""
        content.sound = .default

        // ── Encode recipient + message in the userInfo ─────────
        // When the user taps the notification, we read this data
        // to open the Messages app pre-filled.
        content.userInfo = [
            "recipient": recipient,
            "message": message
        ]

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
    // Called when the user taps a notification.
    // We use `sms:` URL scheme which opens the Messages app.
    static func openMessages(recipient: String, message: String) {
        // URL-encode the message so special characters work.
        guard let encoded = message.addingPercentEncoding(
            withAllowedCharacters: .urlQueryAllowed
        ) else { return }

        // The `sms:` URL scheme opens the Messages app.
        // Format:  sms://PHONE_NUMBER?body=MESSAGE
        let urlString = "sms://\(recipient)?body=\(encoded)"
        guard let url = URL(string: urlString) else { return }

        // Open the URL (iOS switches to the Messages app).
        DispatchQueue.main.async {
            UIApplication.shared.open(url)
        }
    }

    // ── UNUserNotificationCenterDelegate ────────────────────────────

    // Called when the user taps a delivered notification (app was in
    // background or not running). This is where "wake up" notifications
    // (from scheduleTomorrow) and real send-time notifications diverge:
    // a "wake up" one has no recipient/message in userInfo, so there's
    // nothing to pre-fill — the tap just brings the user into the app,
    // which re-schedules today's sends via ContentView's onAppear/toggle
    // flow. A real send-time notification carries recipient + message,
    // so we open Messages pre-filled with them.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let recipient = userInfo["recipient"] as? String,
           let message = userInfo["message"] as? String,
           !recipient.isEmpty {
            NotificationManager.openMessages(recipient: recipient, message: message)
        }
        completionHandler()
    }

    // Called when a notification would fire while the app is already
    // in the foreground. Without this, foreground notifications are
    // silently swallowed (no banner, no sound) — show them like normal.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
