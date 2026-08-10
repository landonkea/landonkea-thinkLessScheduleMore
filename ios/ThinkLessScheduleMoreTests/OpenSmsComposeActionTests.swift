// ───────────────────────────────────────────────────────────────────
// OpenSmsComposeActionTests, parameter validation
// ───────────────────────────────────────────────────────────────────
// Doesn't (and can't, in an XCTest unit test target) verify that
// UIApplication.shared.open() actually switches to Messages, that's
// an OS-level side effect outside what a unit test can observe. What
// IS tested: the id/displayName contract, and that malformed params
// (exactly the risk when Siri/Shortcuts extraction produces empty or
// missing values) are rejected with a clear message before ever
// reaching UIApplication.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class OpenSmsComposeActionTests: XCTestCase {

    private let action = OpenSmsComposeAction()

    func testIdAndDisplayNameAreStable() {
        XCTAssertEqual(action.id, "open_sms_compose")
        XCTAssertFalse(action.displayName.isEmpty)
    }

    func testMissingRecipientFailsWithAClearMessage() {
        let result = action.execute(params: ["message": "hi"])

        XCTAssertFalse(result.success)
        XCTAssertTrue(result.message.contains("recipient"))
    }

    func testMissingMessageFailsWithAClearMessage() {
        let result = action.execute(params: ["recipient": "+15551234567"])

        XCTAssertFalse(result.success)
        XCTAssertTrue(result.message.contains("message"))
    }

    func testEmptyRecipientIsTreatedTheSameAsMissing() {
        let result = action.execute(params: ["recipient": "", "message": "hi"])

        XCTAssertFalse(result.success)
        XCTAssertTrue(result.message.contains("recipient"))
    }

    func testWellFormedParamsReportSuccessButMakeTheAsyncNatureExplicit() {
        let result = action.execute(params: ["recipient": "+15551234567", "message": "hello there"])

        XCTAssertTrue(result.success)
        // "success" here means "the sms: URL was well-formed and an
        // app-switch was requested" -- NOT "a message was sent." iOS
        // has no API for any app to send SMS without a human tapping
        // Send, ever -- see this action's own class comment.
        XCTAssertTrue(result.message.lowercased().contains("opened"))
    }
}
