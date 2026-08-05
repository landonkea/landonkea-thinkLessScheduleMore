// ───────────────────────────────────────────────────────────────────
// ContactPickerView — SwiftUI wrapper around CNContactPickerViewController
// ───────────────────────────────────────────────────────────────────
// Lets the user pick a phone number from their address book instead of
// typing it in by hand. `CNContactPickerViewController` runs
// out-of-process (like a share sheet or UIImagePickerController) — the
// system UI does the browsing/searching in its own sandboxed process,
// and the app only receives the single CNContact the user explicitly
// selected. Because of that, picking a single contact this way does
// NOT require NSContactsUsageDescription / CNContactStore authorization
// the way directly fetching/searching the address book via CNContactStore
// would.
//
// We still check CNContactStore.authorizationStatus(for:) defensively
// before presenting the picker (some iOS versions/entitlement
// configurations can still gate presentation on it), and if access is
// denied/restricted we simply don't present the picker — the manual
// TextField in ContentView is always there regardless, so "falling
// back to manual entry" doesn't require any special-case UI here, just
// not dead-ending or crashing.
// ───────────────────────────────────────────────────────────────────

import SwiftUI
import Contacts
import ContactsUI

struct ContactPickerView: UIViewControllerRepresentable {

    // Called with (phoneNumber, name?) when the user selects a contact
    // with at least one phone number. Never called if they cancel.
    var onPick: (String, String?) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let status = CNContactStore.authorizationStatus(for: .contacts)

        switch status {
        case .denied, .restricted:
            // Can't/shouldn't present the picker — hand back an empty
            // view controller. ContentView's sheet just dismisses
            // itself; the manual TextField remains the fallback.
            return UIViewController()
        default:
            // .notDetermined, .authorized, and (iOS 18+) .limited all
            // proceed — CNContactPickerViewController itself handles
            // any system prompting it needs for out-of-process picking.
            let picker = CNContactPickerViewController()
            picker.delegate = context.coordinator
            // Only offer contacts that actually have a phone number —
            // there'd be nothing to populate recipientNumber with
            // otherwise.
            picker.predicateForEnablingContact = NSPredicate(format: "phoneNumbers.@count > 0")
            picker.displayedPropertyKeys = [CNContactPhoneNumbersKey, CNContactGivenNameKey, CNContactFamilyNameKey]
            return picker
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    final class Coordinator: NSObject, CNContactPickerDelegate {
        let onPick: (String, String?) -> Void

        init(onPick: @escaping (String, String?) -> Void) {
            self.onPick = onPick
        }

        func contactPicker(_ picker: CNContactPickerViewController, didSelect contact: CNContact) {
            guard let firstPhoneNumber = contact.phoneNumbers.first else { return }
            let phoneNumber = firstPhoneNumber.value.stringValue

            let formatter = CNContactFormatter()
            formatter.style = .fullName
            let name = formatter.string(from: contact)

            onPick(phoneNumber, name)
        }

        func contactPickerDidCancel(_ picker: CNContactPickerViewController) {
            // No-op — user backed out, nothing to populate.
        }
    }
}
