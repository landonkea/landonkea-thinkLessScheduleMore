// ───────────────────────────────────────────────────────────────────
// ContentView — the app's only screen (iOS)
// ───────────────────────────────────────────────────────────────────
// This is the iOS equivalent of Android's MainActivity.
// It shows all settings in one scrollable view:
//   - Recipient number
//   - On/off toggle
//   - Time window sliders
//   - Limits
//   - Message pool (add/remove)
//   - Send history
//
// Tapping a notification calls NotificationManager.openMessages()
// which opens the Messages app pre-filled.
// ───────────────────────────────────────────────────────────────────

import SwiftUI

struct ContentView: View {

    // ── Access the shared MessageStore ───────────────────────────
    // `@EnvironmentObject` reads the store passed in by the App struct.
    @EnvironmentObject var store: MessageStore

    // ── State for the "add message" dialog ───────────────────────
    @State private var showAddMessage = false
    @State private var newMessageText = ""

    // ── The scheduler (re-created when store changes) ────────────
    // `@State` because it's owned by this view.
    @State private var scheduler: SchedulerManager? = nil

    var body: some View {
        NavigationView {
            Form {
                // ── Section: Recipient ─────────────────────────────
                Section(header: Text("👤 Recipient")) {
                    TextField("Phone number (e.g. +14155551234)",
                              text: $store.recipientNumber)
                        .keyboardType(.phonePad)
                }

                // ── Section: Master Switch ─────────────────────────
                Section(header: Text("⚡ Scheduling")) {
                    Toggle("Enabled", isOn: $store.isEnabled)
                        .onChange(of: store.isEnabled) { _, enabled in
                            if enabled {
                                startScheduling()
                            } else {
                                stopScheduling()
                            }
                        }
                }

                // ── Section: Time Window ───────────────────────────
                Section(header: Text("⏰ Time Window")) {
                    VStack {
                        Text("Start: \(store.hourStart):00")
                        Slider(value: Binding(
                            get: { Double(store.hourStart) },
                            set: { store.hourStart = Int($0) }
                        ), in: 0...23, step: 1)
                    }

                    VStack {
                        Text("End: \(store.hourEnd):00")
                        Slider(value: Binding(
                            get: { Double(store.hourEnd) },
                            set: { store.hourEnd = Int($0) }
                        ), in: 0...23, step: 1)
                    }
                }

                // ── Section: Limits ────────────────────────────────
                Section(header: Text("📊 Limits")) {
                    VStack {
                        Text("Max per day: \(store.maxPerDay)")
                        Slider(value: Binding(
                            get: { Double(store.maxPerDay) },
                            set: { store.maxPerDay = Int($0) }
                        ), in: 1...10, step: 1)
                    }

                    VStack {
                        Text("Min interval: \(store.minInterval) min")
                        Slider(value: Binding(
                            get: { Double(store.minInterval) },
                            set: { store.minInterval = Int($0) }
                        ), in: 15...240, step: 15)
                    }
                }

                // ── Section: Message Pool ──────────────────────────
                Section(header: Text("💬 Message Pool")) {
                    if store.messages.isEmpty {
                        Text("No messages yet — tap Add below")
                            .foregroundColor(.secondary)
                    }

                    ForEach(store.messages.indices, id: \.self) { index in
                        HStack {
                            Text("\(index + 1).")
                                .foregroundColor(.secondary)
                            Text("❝\(store.messages[index])❞")
                            Spacer()
                            // Swipe to delete.
                            Button(role: .destructive) {
                                store.removeMessage(at: index)
                            } label: {
                                Image(systemName: "trash")
                            }
                        }
                    }

                    Button("➕ Add Message") {
                        showAddMessage = true
                    }
                }

                // ── Section: Send History ──────────────────────────
                Section(header: Text("📋 Send History")) {
                    if store.sentLog.isEmpty {
                        Text("No messages sent yet")
                            .foregroundColor(.secondary)
                    }

                    ForEach(store.sentLog.prefix(10), id: \.self) { entry in
                        Text(entry)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("ThinkLess💕ScheduleMore")
            .onAppear {
                // Initialize the scheduler when the view appears.
                if scheduler == nil {
                    scheduler = SchedulerManager(store: store)
                }
            }
            // ── Alert dialog for adding a message ──────────────
            .alert("New Message", isPresented: $showAddMessage) {
                TextField("Type your message...", text: $newMessageText)
                Button("Add") {
                    let text = newMessageText.trimmingCharacters(
                        in: .whitespacesAndNewlines
                    )
                    if !text.isEmpty {
                        store.addMessage(text)
                        newMessageText = ""
                    }
                }
                Button("Cancel", role: .cancel) {
                    newMessageText = ""
                }
            } message: {
                Text("What would you like to send?")
            }
        }
    }

    // ── Start the scheduling engine ───────────────────────────────
    private func startScheduling() {
        scheduler?.scheduleToday()
    }

    // ── Stop the scheduling engine ────────────────────────────────
    private func stopScheduling() {
        scheduler?.cancelAll()
    }
}
