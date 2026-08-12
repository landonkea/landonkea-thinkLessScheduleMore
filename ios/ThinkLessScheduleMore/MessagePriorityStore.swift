// ───────────────────────────────────────────────────────────────────
// MessagePriorityStore, per-message weight for WeightedMessageSelector
// (iOS)
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's MessagePriorityStore.kt. Entries are keyed by the
// exact message text, the same key MessageSelector/
// WeightedMessageSelector already compare against for the anti-repeat
// check, no separate id needed. Follows MessageStore.swift's own
// pattern (@Published property persisted via didSet, UserDefaults-
// backed).
//
// Keying by text means editing a message (MessageStore.updateMessage)
// orphans its old weight entry, that's an accepted tradeoff, not a
// bug, WeightedMessageSelector already treats any message missing a
// weight as priorityDefault, so an orphaned entry is just harmless
// dead weight in the dictionary, not a crash or a wrong pick.
// ───────────────────────────────────────────────────────────────────

import Foundation
import Combine

class MessagePriorityStore: ObservableObject {

    static let priorityMin = 1
    static let priorityMax = 10
    static let priorityDefault = 1

    @Published var weights: [String: Int] {
        didSet { UserDefaults.standard.set(weights, forKey: Keys.weights) }
    }

    private struct Keys {
        static let weights = "message_priorities"
    }

    init() {
        weights = UserDefaults.standard.dictionary(forKey: Keys.weights) as? [String: Int] ?? [:]
    }

    func weight(for message: String) -> Int {
        let stored = weights[message] ?? Self.priorityDefault
        return stored > 0 ? stored : Self.priorityDefault
    }

    func setWeight(_ weight: Int, for message: String) {
        weights[message] = min(max(weight, Self.priorityMin), Self.priorityMax)
    }
}
