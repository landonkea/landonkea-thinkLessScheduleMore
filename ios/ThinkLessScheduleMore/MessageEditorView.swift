// ───────────────────────────────────────────────────────────────────
// MessageEditorView — shared Add/Edit message sheet
// ───────────────────────────────────────────────────────────────────
// SwiftUI's `.alert` TextField can't show a live character counter,
// so Add and Edit both use this small sheet instead. It shows the
// live "N/160" counter against the standard single-segment SMS
// length (messages can still be longer — this is just the design
// guideline both platforms use).
// ───────────────────────────────────────────────────────────────────

import SwiftUI

let smsMaxLength = 160

struct MessageEditorView: View {
    let title: String
    let confirmLabel: String
    @State var text: String
    let onSave: (String) -> Void

    @Environment(\.dismiss) private var dismiss

    private var overLimit: Bool { text.count > smsMaxLength }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    TextEditor(text: $text)
                        .frame(minHeight: 120)
                }

                Section {
                    HStack {
                        Spacer()
                        Text("\(text.count)/\(smsMaxLength)")
                            .font(.caption)
                            .foregroundColor(overLimit ? .red : .secondary)
                    }
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(confirmLabel) {
                        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            onSave(trimmed)
                        }
                        dismiss()
                    }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
