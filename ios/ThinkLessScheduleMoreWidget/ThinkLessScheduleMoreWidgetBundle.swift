// ThinkLessScheduleMoreWidgetBundle.swift, Entry point for the widget
// extension process.
//
// A WidgetKit extension is its own tiny app-like process (separate from
// ThinkLessScheduleMore) launched by the system to render one or more
// widgets. This `@main` struct is where the OS starts, analogous to
// ThinkLessScheduleMoreApp.swift's `@main App` for the main app, but for
// widget kinds instead of scenes.
//
// Currently there's a single widget kind (`NextSendWidget`). `WidgetBundle`
// is written as a list so adding a second widget later is just another
// line in `body`.

import WidgetKit
import SwiftUI

@main
struct ThinkLessScheduleMoreWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextSendWidget()
    }
}
