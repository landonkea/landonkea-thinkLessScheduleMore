// ───────────────────────────────────────────────────────────────────
// MessageStoreTests — covers the message-editing and phone-number
// validation behavior added alongside the QUICK-tier feature pass.
// ───────────────────────────────────────────────────────────────────
// NOTE: MessageStore reads/writes UserDefaults.standard directly (no
// injectable suite), so these tests explicitly set `messages` before
// asserting rather than relying on isolation from prior app state.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class MessageStoreTests: XCTestCase {

    // ── updateMessage ────────────────────────────────────────────

    func testUpdateMessageEditsInPlacePreservingOrder() {
        let store = MessageStore()
        store.messages = ["a", "b", "c"]

        store.updateMessage(at: 1, text: "b-edited")

        XCTAssertEqual(store.messages, ["a", "b-edited", "c"])
    }

    func testUpdateMessageOutOfRangeIsNoOp() {
        let store = MessageStore()
        store.messages = ["a", "b"]

        store.updateMessage(at: 5, text: "should not apply")

        XCTAssertEqual(store.messages, ["a", "b"])
    }

    func testRemoveMessageDeletesOnlyTargetIndex() {
        let store = MessageStore()
        store.messages = ["a", "b", "c"]

        store.removeMessage(at: 1)

        XCTAssertEqual(store.messages, ["a", "c"])
    }

    // ── Phone number validation ─────────────────────────────────

    func testValidPhoneNumbers() {
        XCTAssertTrue(MessageStore.isValidPhoneNumber("+14155551234"))
        XCTAssertTrue(MessageStore.isValidPhoneNumber("14155551234"))
        XCTAssertTrue(MessageStore.isValidPhoneNumber("+442071838750"))
    }

    func testInvalidPhoneNumbers() {
        XCTAssertFalse(MessageStore.isValidPhoneNumber(""))
        XCTAssertFalse(MessageStore.isValidPhoneNumber("abc"))
        XCTAssertFalse(MessageStore.isValidPhoneNumber("123"))
        XCTAssertFalse(MessageStore.isValidPhoneNumber("+1 415 555 1234"))
    }
}
