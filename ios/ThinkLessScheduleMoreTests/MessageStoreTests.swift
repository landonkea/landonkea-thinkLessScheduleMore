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

    // ── Recently-sent history (feeds MessageSelector) ─────────────

    func testRecentlySentTracksAdditionsInOrderOldestFirst() {
        let store = MessageStore()
        store.recentlySent = []

        store.addRecentlySent("first")
        store.addRecentlySent("second")

        XCTAssertEqual(store.recentlySent, ["first", "second"])
    }

    func testRecentlySentCapsAtHistorySizeDroppingTheOldest() {
        let store = MessageStore()
        store.recentlySent = []

        for i in 0..<(MessageSelector.historySize + 3) {
            store.addRecentlySent("msg-\(i)")
        }

        XCTAssertEqual(store.recentlySent.count, MessageSelector.historySize)
        XCTAssertEqual(store.recentlySent.first, "msg-3")
        XCTAssertEqual(store.recentlySent.last, "msg-\(MessageSelector.historySize + 2)")
    }

    // ── Send log (persisted, structured — feeds StatsCalculator) ──

    func testAddToLogInsertsNewestFirst() {
        let store = MessageStore()
        store.sentLog = []

        store.addToLog(id: UUID(), timestamp: Date(timeIntervalSince1970: 1), status: "pending", message: "older")
        store.addToLog(id: UUID(), timestamp: Date(timeIntervalSince1970: 2), status: "pending", message: "newer")

        XCTAssertEqual(store.sentLog.map(\.message), ["newer", "older"])
    }

    func testAddToLogCapsAt50DroppingOldest() {
        let store = MessageStore()
        store.sentLog = []

        for i in 0..<55 {
            store.addToLog(id: UUID(), timestamp: Date(), status: "pending", message: "msg-\(i)")
        }

        XCTAssertEqual(store.sentLog.count, 50)
        // Newest-first: the most recently added (msg-54) should be at the front.
        XCTAssertEqual(store.sentLog.first?.message, "msg-54")
    }

    func testMarkOpenedFlipsMatchingEntryStatus() {
        let store = MessageStore()
        store.sentLog = []
        let id = UUID()
        store.addToLog(id: id, timestamp: Date(), status: "pending", message: "hi")

        store.markOpened(id)

        XCTAssertEqual(store.sentLog.first?.status, "opened")
    }

    func testMarkOpenedWithUnknownIdIsNoOp() {
        let store = MessageStore()
        store.sentLog = []
        store.addToLog(id: UUID(), timestamp: Date(), status: "pending", message: "hi")

        store.markOpened(UUID()) // unrelated id

        XCTAssertEqual(store.sentLog.first?.status, "pending")
    }
}
