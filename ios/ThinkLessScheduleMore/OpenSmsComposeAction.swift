// ───────────────────────────────────────────────────────────────────
// OpenSmsComposeAction, the first AutomationAction implementation (iOS)
// ───────────────────────────────────────────────────────────────────
// NOT called "SendSmsAction" on purpose, unlike Android's SendSmsAction
//, iOS genuinely cannot send an SMS without a human tapping Send in
// Messages, for any app, ever (no public API exists for it; this is
// an intentional Apple restriction, not a gap in this codebase). This
// action's honest job is opening Messages pre-filled via the `sms:`
// URL scheme, the exact mechanism NotificationManager already used
// for a notification tap, extracted here so both a notification tap
// AND a future Siri/Shortcuts-triggered AppIntent go through the same
// code, instead of two copies of the same URL-building logic.
//
// "success" from execute() means "the sms: URL was well-formed and
// UIApplication.open was asked to open it", NOT "a message was
// sent." That distinction is real and should stay visible in how
// this is named and documented, not smoothed over.
// ───────────────────────────────────────────────────────────────────

import Foundation
import UIKit

struct OpenSmsComposeAction: AutomationAction {
    let id = "open_sms_compose"
    let displayName = "Open SMS Compose"

    func execute(params: [String: String]) -> AutomationResult {
        guard let recipient = params["recipient"], !recipient.isEmpty else {
            return AutomationResult(success: false, message: "Missing required param \"recipient\"")
        }
        guard let message = params["message"], !message.isEmpty else {
            return AutomationResult(success: false, message: "Missing required param \"message\"")
        }

        guard let encoded = message.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return AutomationResult(success: false, message: "Could not URL-encode message")
        }
        guard let url = URL(string: "sms://\(recipient)?body=\(encoded)") else {
            return AutomationResult(success: false, message: "Could not build a valid sms: URL")
        }

        DispatchQueue.main.async {
            UIApplication.shared.open(url)
        }

        return AutomationResult(success: true, message: "Opened Messages, pre-filled, user still has to tap Send")
    }
}
