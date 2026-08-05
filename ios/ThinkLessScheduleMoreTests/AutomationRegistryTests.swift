// ───────────────────────────────────────────────────────────────────
// AutomationRegistryTests — proves the trigger/action decoupling is
// real on iOS too
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's AutomationRegistryTest.kt: a fake action, never
// OpenSmsComposeAction, so these tests can't accidentally pass just
// because SMS-compose logic happens to work.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

private final class RecordingAction: AutomationAction {
    let id: String
    let displayName = "Recording Action"
    private let resultToReturn: AutomationResult
    var lastParams: [String: String]?

    init(id: String, result: AutomationResult = AutomationResult(success: true, message: "ok")) {
        self.id = id
        self.resultToReturn = result
    }

    func execute(params: [String: String]) -> AutomationResult {
        lastParams = params
        return resultToReturn
    }
}

final class AutomationRegistryTests: XCTestCase {

    override func setUp() {
        super.setUp()
        AutomationRegistry.shared.clearForTesting()
    }

    func testExecuteDispatchesToTheActionRegisteredUnderThatId() {
        let action = RecordingAction(id: "greet")
        AutomationRegistry.shared.register(action)

        let result = AutomationRegistry.shared.execute(actionId: "greet", params: ["name": "World"])

        XCTAssertTrue(result.success)
        XCTAssertEqual(action.lastParams, ["name": "World"])
    }

    func testExecuteOnAnUnregisteredIdFailsWithoutTouchingAnyAction() {
        let action = RecordingAction(id: "greet")
        AutomationRegistry.shared.register(action)

        let result = AutomationRegistry.shared.execute(actionId: "does_not_exist", params: [:])

        XCTAssertFalse(result.success)
        XCTAssertTrue(result.message.contains("does_not_exist"))
        XCTAssertNil(action.lastParams)
    }

    func testTwoDifferentActionsAreIndependentlyReachableById() {
        let greet = RecordingAction(id: "greet")
        let farewell = RecordingAction(id: "farewell")
        AutomationRegistry.shared.register(greet)
        AutomationRegistry.shared.register(farewell)

        _ = AutomationRegistry.shared.execute(actionId: "farewell", params: ["x": "1"])

        XCTAssertNil(greet.lastParams)
        XCTAssertEqual(farewell.lastParams, ["x": "1"])
    }

    func testRegisteringASecondActionUnderTheSameIdReplacesTheFirst() {
        let original = RecordingAction(id: "greet", result: AutomationResult(success: true, message: "v1"))
        let replacement = RecordingAction(id: "greet", result: AutomationResult(success: true, message: "v2"))
        AutomationRegistry.shared.register(original)
        AutomationRegistry.shared.register(replacement)

        let result = AutomationRegistry.shared.execute(actionId: "greet", params: [:])

        XCTAssertEqual(result.message, "v2")
        XCTAssertNil(original.lastParams)
    }
}
