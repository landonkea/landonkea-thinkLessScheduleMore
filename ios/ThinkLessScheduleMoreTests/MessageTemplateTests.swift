// ───────────────────────────────────────────────────────────────────
// MessageTemplateTests — {name}/{time-of-day} placeholder rendering.
// ───────────────────────────────────────────────────────────────────
// Mirrors Android's MessageTemplateTest.kt.
// ───────────────────────────────────────────────────────────────────

import XCTest
@testable import ThinkLessScheduleMore

final class MessageTemplateTests: XCTestCase {

    func testSubstitutesBothPlaceholders() {
        let result = MessageTemplate.render("Good {time-of-day}, {name}!", name: "Sam", hour: 8)
        XCTAssertEqual(result, "Good morning, Sam!")
    }

    func testFallsBackToThereWhenNameIsBlank() {
        let result = MessageTemplate.render("Hey {name}", name: "", hour: 8)
        XCTAssertEqual(result, "Hey there")
    }

    func testFallsBackToThereWhenNameIsWhitespaceOnly() {
        let result = MessageTemplate.render("Hey {name}", name: "   ", hour: 8)
        XCTAssertEqual(result, "Hey there")
    }

    func testTemplateWithNoPlaceholdersPassesThroughUnchanged() {
        let result = MessageTemplate.render("Thinking of you", name: "Sam", hour: 8)
        XCTAssertEqual(result, "Thinking of you")
    }

    func testRepeatedPlaceholdersAreAllSubstituted() {
        let result = MessageTemplate.render("{name} {name} {name}", name: "Sam", hour: 8)
        XCTAssertEqual(result, "Sam Sam Sam")
    }

    func testTimeOfDayBucketsMorning() {
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 5), "morning")
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 11), "morning")
    }

    func testTimeOfDayBucketsAfternoon() {
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 12), "afternoon")
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 16), "afternoon")
    }

    func testTimeOfDayBucketsEvening() {
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 17), "evening")
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 21), "evening")
    }

    func testTimeOfDayBucketsNightWrapsPastMidnight() {
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 22), "night")
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 0), "night")
        XCTAssertEqual(MessageTemplate.timeOfDay(hour: 4), "night")
    }
}
