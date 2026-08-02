// ───────────────────────────────────────────────────────────────────
// NotificationManagerTests — verifies the notification-tap-to-open-
// Messages wiring that was previously dead code (delegate was never
// registered with UNUserNotificationCenter).
// ───────────────────────────────────────────────────────────────────
//
// NOTE on scope: constructing a real UNNotificationResponse/UNNotification
// to drive userNotificationCenter(_:didReceive:withCompletionHandler:)
// end-to-end was attempted (subclassing UNNotification/UNNotificationResponse
// and overriding their read-only `request`/`notification`/`actionIdentifier`
// properties) but does not compile: Apple marks `init()` on both types as
// unavailable outside the framework, and there is no other public
// initializer or factory. That was confirmed by actually attempting the
// build here, not assumed. So this test suite verifies what's genuinely
// reachable from outside the framework:
//   1. Delegate conformance is in place (the actual root cause of the
//      original bug — the class existed but was never registered/didn't
//      conform, so taps silently did nothing).
//   2. `NotificationManager.shared` is correctly wired as the current
//      UNUserNotificationCenter's delegate after init.

import XCTest
import UserNotifications
@testable import ThinkLessScheduleMore

final class NotificationManagerTests: XCTestCase {

    func testConformsToDelegateProtocol() {
        XCTAssertTrue(
            NotificationManager.shared is UNUserNotificationCenterDelegate,
            "NotificationManager must conform to UNUserNotificationCenterDelegate for notification taps to be handled"
        )
    }

    func testSharedInstanceIsRegisteredAsCurrentDelegate() {
        // The original bug: NotificationManager existed with the right
        // logic but was never assigned as UNUserNotificationCenter's
        // delegate, so `didReceive`/`willPresent` were never called by
        // iOS. NotificationManager.init() registers itself — accessing
        // `.shared` triggers that init, so this checks the registration
        // actually stuck.
        _ = NotificationManager.shared
        let delegate = UNUserNotificationCenter.current().delegate
        XCTAssertNotNil(delegate, "UNUserNotificationCenter.current().delegate must be set")
        XCTAssertTrue(delegate === NotificationManager.shared,
                      "The registered delegate must be the NotificationManager singleton")
    }
}
