// ───────────────────────────────────────────────────────────────────
// MessageTemplate, renders {name}/{time-of-day} placeholders
// ───────────────────────────────────────────────────────────────────
// Lets one message pool entry generate variety instead of every send
// being verbatim. Supports two placeholders:
//   {name}         → the recipient's display name (falls back to
//                    "there" if the user never set one)
//   {time-of-day}  → morning / afternoon / evening / night, derived
//                    from the hour the message is actually sent
//
// Plain string replacement, no need for a templating library for
// two placeholders. Kept as a pure, standalone object so it's
// trivially unit-testable and shared conceptually with the iOS
// MessageTemplate.swift (same rendering rules on both platforms).
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

object MessageTemplate {

    // Bucket boundaries chosen to match common intuition:
    //   5–11  morning
    //   12–16 afternoon
    //   17–21 evening
    //   22–4  night
    fun timeOfDay(hour: Int): String = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }

    // Render a template string against the recipient's name and the
    // hour of day the message will actually be sent.
    fun render(template: String, name: String, hour: Int): String {
        val resolvedName = name.trim().ifEmpty { "there" }
        return template
            .replace("{name}", resolvedName)
            .replace("{time-of-day}", timeOfDay(hour))
    }
}
