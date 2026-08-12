// ───────────────────────────────────────────────────────────────────
// ContentView, the app's only screen (iOS)
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

    // ── Date-based recurring messages (birthdays/anniversaries) ────
    // Additive to `store`'s pool schedule, see RecurringMessageStore.
    @EnvironmentObject var recurringStore: RecurringMessageStore

    // ── Days the random pool schedule should skip ───────────────────
    // See NoSendDayStore/NoSendDayChecker.
    @EnvironmentObject var noSendDayStore: NoSendDayStore

    // ── Per-message priority weights ─────────────────────────────────
    // See MessagePriorityStore/WeightedMessageSelector.
    @EnvironmentObject var priorityStore: MessagePriorityStore

    // ── State for the "add a one-off no-send date" picker ───────────
    @State private var newNoSendDate = Date()

    // ── State for the contact picker sheet ─────────────────────────
    @State private var showContactPicker = false

    // ── State for the add/edit message sheet ──────────────────────
    // `editingIndex == nil` means "adding a new message"; otherwise
    // it's the index of the message being edited.
    @State private var showMessageEditor = false
    @State private var editingIndex: Int? = nil

    // ── State for the "confirm delete" alert ───────────────────────
    @State private var pendingDeleteIndex: Int? = nil

    // ── The scheduler (re-created when store changes) ────────────
    // `@State` because it's owned by this view.
    @State private var scheduler: SchedulerManager? = nil

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM d h:mm a"
        return f
    }()

    // "yyyy-MM-dd", matches NoSendDayStore's documented date-key format
    // and SchedulerManager's own dayKeyFormatter.
    private static let dayKeyFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = Calendar.current.timeZone
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    var body: some View {
        NavigationView {
            Form {
                // ── Section: Recipient ─────────────────────────────
                Section(header: Text("👤 Recipient")) {
                    TextField("Phone number (e.g. +14155551234)",
                              text: $store.recipientNumber)
                        .keyboardType(.phonePad)

                    // Alternative to typing the number by hand, picks a
                    // single contact via the system contact picker. Manual
                    // entry above always stays available as a fallback
                    // (e.g. Contacts access denied/restricted).
                    Button {
                        showContactPicker = true
                    } label: {
                        Label("Pick Contact", systemImage: "person.crop.circle.badge.plus")
                    }

                    if !store.recipientNumber.isEmpty &&
                        !MessageStore.isValidPhoneNumber(store.recipientNumber) {
                        Text("That doesn't look like a valid phone number (e.g. +14155551234)")
                            .font(.caption)
                            .foregroundColor(.red)
                    }

                    // Feeds the {name} template placeholder, purely
                    // cosmetic, the phone number is what's actually texted.
                    TextField("Name (for {name} in messages)",
                              text: $store.recipientName)
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

                    if let next = store.nextScheduledTime {
                        Text("Next message: \(Self.dateFormatter.string(from: next))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text("Next message: not scheduled")
                            .font(.caption)
                            .foregroundColor(.secondary)
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
                Section(header: Text("💬 Message Pool"),
                        footer: Text("Tip: use {name} and {time-of-day} for variety, e.g. \"Good {time-of-day}, {name}!\"")) {
                    if store.messages.isEmpty {
                        Text("No messages yet, tap Add below")
                            .foregroundColor(.secondary)
                    }

                    ForEach(store.messages.indices, id: \.self) { index in
                        HStack {
                            Text("\(index + 1).")
                                .foregroundColor(.secondary)
                            Text("❝\(store.messages[index])❞")
                            Spacer()
                            // Edit this message's text.
                            Button {
                                editingIndex = index
                                showMessageEditor = true
                            } label: {
                                Image(systemName: "pencil")
                            }
                            .buttonStyle(.borderless)
                            // Delete (with confirmation, no undo once it's gone).
                            Button(role: .destructive) {
                                pendingDeleteIndex = index
                            } label: {
                                Image(systemName: "trash")
                            }
                            .buttonStyle(.borderless)
                        }
                    }

                    Button("➕ Add Message") {
                        editingIndex = nil
                        showMessageEditor = true
                    }
                }

                // ── Section: No-Send Days ──────────────────────────
                // Pauses the random pool schedule (only, recurring
                // birthday/anniversary messages above still fire), see
                // NoSendDayChecker's doc comment.
                Section(header: Text("🚫 No-Send Days"),
                        footer: Text("Pauses random messages (recurring ones above still send).")) {
                    ForEach(Calendar.current.weekdaySymbols.indices, id: \.self) { i in
                        let weekday = i + 1  // Calendar weekday: Sunday=1...Saturday=7
                        Toggle(Calendar.current.weekdaySymbols[i], isOn: Binding(
                            get: { noSendDayStore.noSendWeekdays.contains(weekday) },
                            set: { noSendDayStore.toggleWeekday(weekday, blocked: $0) }
                        ))
                    }

                    DatePicker("One-off date", selection: $newNoSendDate, displayedComponents: .date)
                    Button("➕ Add No-Send Date") {
                        noSendDayStore.addDate(Self.dayKeyFormatter.string(from: newNoSendDate))
                    }

                    ForEach(noSendDayStore.noSendDates.indices, id: \.self) { index in
                        HStack {
                            Text(noSendDayStore.noSendDates[index])
                            Spacer()
                            Button(role: .destructive) {
                                noSendDayStore.removeDate(at: index)
                            } label: {
                                Image(systemName: "trash")
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                }

                // ── Section: Message Priority ──────────────────────
                // Weights a message's odds of being picked next by the
                // random pool schedule (see WeightedMessageSelector).
                Section(header: Text("⭐ Message Priority"),
                        footer: Text("Higher priority messages send more often (1=normal, 10=favorite).")) {
                    if store.messages.isEmpty {
                        Text("No messages yet, add one above")
                            .foregroundColor(.secondary)
                    }

                    ForEach(store.messages.indices, id: \.self) { index in
                        let message = store.messages[index]
                        Stepper(
                            "Priority \(priorityStore.weight(for: message)), ❝\(message.prefix(40))\(message.count > 40 ? "…" : "")❞",
                            value: Binding(
                                get: { priorityStore.weight(for: message) },
                                set: { priorityStore.setWeight($0, for: message) }
                            ),
                            in: MessagePriorityStore.priorityMin...MessagePriorityStore.priorityMax
                        )
                        .font(.caption)
                    }
                }

                // ── Section: Send History ──────────────────────────
                Section(header: Text("📋 Send History")) {
                    if store.sentLog.isEmpty {
                        Text("No messages sent yet")
                            .foregroundColor(.secondary)
                    }

                    ForEach(store.sentLog.prefix(10)) { entry in
                        HStack(alignment: .top) {
                            Text(entry.status == "opened" ? "✅" : "🕓")
                            VStack(alignment: .leading) {
                                Text(Self.dateFormatter.string(from: entry.timestamp))
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                                Text(entry.message)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }

                    NavigationLink("📊 Stats Dashboard") {
                        StatsView(stats: StatsCalculator.compute(log: store.sentLog))
                    }
                }
            }
            .navigationTitle("ThinkLess💕ScheduleMore")
            .onAppear {
                // Wire notification taps to the send log: tapping a
                // scheduled-message notification opens Messages (handled
                // inside NotificationManager itself) AND flips that
                // entry from "pending" to "opened" here.
                NotificationManager.shared.onOpen = { id in
                    store.markOpened(id)
                }

                // Initialize the scheduler when the view appears.
                if scheduler == nil {
                    scheduler = SchedulerManager(
                        store: store,
                        recurringStore: recurringStore,
                        noSendDayStore: noSendDayStore,
                        priorityStore: priorityStore
                    )
                    // If scheduling was already enabled from a previous
                    // session, re-run it now so "Next message" reflects
                    // reality instead of showing stale/empty state.
                    if store.isEnabled {
                        scheduler?.scheduleToday()
                    }
                }
            }
            // ── Sheet for adding/editing a message (has a live char counter) ──
            .sheet(isPresented: $showMessageEditor) {
                if let index = editingIndex {
                    MessageEditorView(
                        title: "Edit Message",
                        confirmLabel: "Save",
                        text: store.messages[index]
                    ) { newText in
                        store.updateMessage(at: index, text: newText)
                    }
                } else {
                    MessageEditorView(
                        title: "New Message",
                        confirmLabel: "Add",
                        text: ""
                    ) { newText in
                        store.addMessage(newText)
                    }
                }
            }
            // ── Sheet for picking a contact (falls back to manual entry, see ContactPickerView.swift) ──
            .sheet(isPresented: $showContactPicker) {
                ContactPickerView { phoneNumber, name in
                    store.recipientNumber = phoneNumber
                    if let name = name, !name.isEmpty {
                        store.recipientName = name
                    }
                }
            }
            // ── Confirmation before deleting a message, no undo ────
            .alert(
                "Delete this message?",
                isPresented: Binding(
                    get: { pendingDeleteIndex != nil },
                    set: { if !$0 { pendingDeleteIndex = nil } }
                )
            ) {
                Button("Delete", role: .destructive) {
                    if let index = pendingDeleteIndex {
                        store.removeMessage(at: index)
                    }
                    pendingDeleteIndex = nil
                }
                Button("Cancel", role: .cancel) {
                    pendingDeleteIndex = nil
                }
            } message: {
                if let index = pendingDeleteIndex, store.messages.indices.contains(index) {
                    Text("❝\(store.messages[index])❞\n\nThis can't be undone.")
                }
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
