// ───────────────────────────────────────────────────────────────────
// MessageTemplate, renders {name}/{time-of-day} placeholders
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's MessageTemplate.kt. Lets one message pool entry
// generate variety instead of every send being verbatim. Supports:
//   {name}         → the recipient's display name (falls back to
//                    "there" if the user never set one)
//   {time-of-day}  → morning / afternoon / evening / night, derived
//                    from the hour the message is actually scheduled
//                    to be sent
// ───────────────────────────────────────────────────────────────────

import Foundation

enum MessageTemplate {

    // Bucket boundaries chosen to match common intuition:
    //   5–11  morning
    //   12–16 afternoon
    //   17–21 evening
    //   22–4  night
    static func timeOfDay(hour: Int) -> String {
        switch hour {
        case 5...11: return "morning"
        case 12...16: return "afternoon"
        case 17...21: return "evening"
        default: return "night"
        }
    }

    // Render a template string against the recipient's name and the
    // hour of day the message is scheduled to be sent.
    static func render(_ template: String, name: String, hour: Int) -> String {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = trimmedName.isEmpty ? "there" : trimmedName
        return template
            .replacingOccurrences(of: "{name}", with: resolvedName)
            .replacingOccurrences(of: "{time-of-day}", with: timeOfDay(hour: hour))
    }
}
