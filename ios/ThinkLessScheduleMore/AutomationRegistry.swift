// ───────────────────────────────────────────────────────────────────
// AutomationRegistry — the pool every trigger and action shares (iOS)
// ───────────────────────────────────────────────────────────────────
// The Swift twin of Android's AutomationRegistry.kt. A plain
// singleton class (not an actor/framework-managed object) matching
// this app's existing style — MessageStore/RecurringMessageStore are
// both constructed and used directly, no DI framework anywhere else
// in this codebase either.
// ───────────────────────────────────────────────────────────────────

import Foundation

final class AutomationRegistry {
    static let shared = AutomationRegistry()

    private var actions: [String: AutomationAction] = [:]

    private init() {}

    func register(_ action: AutomationAction) {
        actions[action.id] = action
    }

    func execute(actionId: String, params: [String: String]) -> AutomationResult {
        guard let action = actions[actionId] else {
            return AutomationResult(success: false, message: "No action registered with id \"\(actionId)\"")
        }
        return action.execute(params: params)
    }

    /// Test-only: reset to a clean slate instead of accumulating
    /// registrations across tests that share this singleton.
    func clearForTesting() {
        actions.removeAll()
    }
}
