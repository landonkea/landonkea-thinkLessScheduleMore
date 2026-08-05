// ───────────────────────────────────────────────────────────────────
// SendSmsIntent — the iOS twin of Android's Tasker plugin
// ───────────────────────────────────────────────────────────────────
// This is what makes "open_sms_compose" reachable from Siri and the
// Shortcuts app — Apple's own automation framework (App Intents,
// iOS 16+), the direct iOS equivalent of the Locale/Tasker plugin
// contract implemented on Android (see TaskerEditActivity/
// TaskerFireReceiver over there). @Parameter-annotated properties are
// the iOS analogue of Android's paramSchema-driven edit screen — the
// Shortcuts app builds a configuration UI FROM these annotations
// automatically, no custom screen needed on this side at all.
//
// perform() calls AutomationRegistry.execute() -- the exact same call
// a notification tap makes (see NotificationManager.openMessages) --
// proving this is really one more trigger sharing the same action
// pool, not a special case bolted on for Siri.
// ───────────────────────────────────────────────────────────────────

import AppIntents

struct SendSmsIntent: AppIntent {
    static var title: LocalizedStringResource = "Open SMS Compose"
    static var description: IntentDescription = """
        Opens Messages pre-filled with a recipient and message. iOS never lets any app \
        send SMS without a human tapping Send — this gets you to the compose screen ready to go.
        """

    // Ensures the app process is alive and on the main thread before
    // perform() runs UIApplication.shared.open() inside
    // OpenSmsComposeAction — required for a side effect like this one,
    // not just a "return some data" intent.
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Recipient", description: "Phone number to open Messages with, e.g. +15551234567")
    var recipient: String

    @Parameter(title: "Message", description: "Message text to pre-fill")
    var message: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = AutomationRegistry.shared.execute(
            actionId: "open_sms_compose",
            params: ["recipient": recipient, "message": message]
        )
        return .result(dialog: IntentDialog(stringLiteral: result.message))
    }
}

// ── Discoverability: what Siri phrases map to this intent ─────────
// AppShortcutsProvider is how an app tells iOS "here are the phrases
// that should work with Siri out of the box," without the user
// having to manually build a Shortcut first — the direct equivalent
// of a Tasker user picking this app's action from Tasker's own
// action list, except discoverable by voice immediately after install.
struct ThinkLessScheduleMoreShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SendSmsIntent(),
            phrases: [
                "Open message compose in \(.applicationName)",
                "Compose a text with \(.applicationName)",
            ],
            shortTitle: "Open SMS Compose",
            systemImageName: "message"
        )
    }
}
