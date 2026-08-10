// ───────────────────────────────────────────────────────────────────
// AutomationAction, one thing this app knows how to DO (iOS side)
// ───────────────────────────────────────────────────────────────────
// The Swift/iOS twin of Android's AutomationAction.kt, same shape,
// same reasoning: a trigger (a notification tap, an App Intent fired
// from Siri/Shortcuts) reaches an action only by its string id, never
// by concrete type, via AutomationRegistry. See that file's comment
// for why that's what actually makes triggers and actions composable
// instead of hardwired.
//
// IMPORTANT PLATFORM DIFFERENCE FROM ANDROID: iOS never lets any app
// send SMS silently, for any reason, there's no API for it, by
// Apple's own design. OpenSmsComposeAction (the one implementation
// today) can only open the Messages app pre-filled; a human still has
// to tap Send. That's not a shortcoming of this abstraction, it's a
// real platform ceiling, see OpenSmsComposeAction's own comment.
// ───────────────────────────────────────────────────────────────────

import Foundation

struct AutomationResult {
    let success: Bool
    let message: String
}

protocol AutomationAction {
    /// Stable, unique identifier, e.g. "send_sms". A saved Shortcut
    /// or Siri phrase references this indirectly through the AppIntent
    /// that wraps this action, so it should never change once shipped.
    var id: String { get }

    /// Human-readable name, shown in this app's own UI.
    var displayName: String { get }

    func execute(params: [String: String]) -> AutomationResult
}
