# landonkea-thinkLessScheduleMore checklist

Status + explicit next-agenda items. Delete this file once everything below is checked off.

## What this is
Sends "thinking of you" texts to a partner at random times during the day, from your real phone number, no bot/third-party service. Two platforms (Android + iOS), also split into standalone repos with preserved history.

## Next up (from README's own "Known gaps / ideas" section)
- [x] "No send" days, implemented on both Android and iOS (NoSendDayChecker/NoSendDayStore, wired into the scheduler and settings UI on each platform)
- [x] Weighted message selection, implemented on both Android and iOS (WeightedMessageSelector/MessagePriorityStore, wired into the scheduler and settings UI on each platform)
- [ ] Multiple recipients
- [ ] Scheduled one-off messages (birthdays/anniversaries)
- [ ] Widgets
- [ ] Delivery reports
- [ ] Localization

None of the new Kotlin/Swift code above has been compiled or run, this
session had no Xcode/Android Studio/emulator/device available. Before
relying on it, build + test both apps on a real device or simulator.
Everything else in this list (multiple recipients, scheduled one-off
messages, widgets, delivery reports, localization, plus CSV import and
dark mode from PROJECT_CHECKLIST.md's Phase 2/3) is entirely unbuilt.

Also noticed in passing (not part of this pass's scope): iOS has no UI for
recurring (birthday/anniversary) messages, even though
`RecurringMessageStore`/`RecurringMessageMatcher`/`SchedulerManager` wiring
for it already exists there. Android has this UI, iOS doesn't, worth a
follow-up.

See `shared/ARCHITECTURE.md` for the core design these would build on top of.
