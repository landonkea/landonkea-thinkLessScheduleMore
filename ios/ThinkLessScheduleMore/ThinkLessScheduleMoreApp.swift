// ───────────────────────────────────────────────────────────────────
// ThinkLessScheduleMoreApp — iOS app entry point
// ───────────────────────────────────────────────────────────────────
// This is the SwiftUI app entry point.  It's equivalent to
// Android's AndroidManifest.xml + onCreate().
//
// SwiftUI apps start here and render whatever `WindowGroup`
// contains (in our case, ContentView — the main screen).
// ───────────────────────────────────────────────────────────────────

import SwiftUI

// ── @main marks this as the app's entry point ────────────────────
// When the user taps the app icon, iOS launches this struct.
// Only one file in the app can have @main.
@main
struct ThinkLessScheduleMoreApp: App {

    // ── State that lives for the entire app lifetime ───────────
    // `@StateObject` means "create this once and keep it alive
    // even if the view re-renders."  Our MessageStore is the
    // single source of truth for all data.
    @StateObject private var store = MessageStore()

    // Second, independent store for date-based recurring messages
    // (birthdays/anniversaries) — additive to `store`'s random-pool
    // schedule, not a replacement. See RecurringMessageStore.swift.
    @StateObject private var recurringStore = RecurringMessageStore()

    var body: some Scene {
        WindowGroup {
            // The main screen.  We pass the store as an
            // environment object so every child view can access it.
            ContentView()
                .environmentObject(store)
                .environmentObject(recurringStore)
        }
    }
}
