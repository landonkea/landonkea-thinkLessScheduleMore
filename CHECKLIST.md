# landonkea-thinkLessScheduleMore checklist

Status + explicit next-agenda items. Delete this file once everything below is checked off.

## What this is
Sends "thinking of you" texts to a partner at random times during the day, from your real phone number, no bot/third-party service. Two platforms (Android + iOS), also split into standalone repos with preserved history.

## Next up

This list used to just mirror README's "Known gaps / ideas" section verbatim,
which had gone stale: widgets and delivery reports were both actually built
(`b995ddf`, `4608812`) but stayed listed here as not-done. Re-verified against
the actual source tree during this pass, corrected below.

- [x] "No send" days, implemented on both Android and iOS (NoSendDayChecker/NoSendDayStore, wired into the scheduler and settings UI on each platform)
- [x] Weighted message selection, implemented on both Android and iOS (WeightedMessageSelector/MessagePriorityStore, wired into the scheduler and settings UI on each platform)
- [x] Widgets, Home Screen widgets on both platforms (NextSendWidgetProvider on Android, ThinkLessScheduleMoreWidgetExtension on iOS), see `PROJECT_CHECKLIST.md`
- [x] Delivery reports (Android only, iOS has no delivery API to report on), SendStatus/SmsResultMapper/SmsSender track SENT/DELIVERED/FAILED via `sentIntent`/`deliveryIntent`
- [x] Android/iOS build channels (this pass): a `beta` Android build type, matching `Beta`/`Release` iOS configurations, and three GitHub Actions workflows (`build-debug.yml`, `build-beta.yml`, `build-release.yml`) building both platforms per channel, see `BUILD_LOG.md`
- [ ] Multiple recipients
- [ ] Scheduled one-off messages (birthdays/anniversaries), Android has full UI + backend, iOS only has the backend wired (`RecurringMessageStore`/`RecurringMessageMatcher`), no settings UI to add/edit one, see "Also noticed in passing" below
- [ ] Localization

None of the no-send-days/weighted-selection Kotlin/Swift code (`de7e691`) has
been compiled or run on a real device or simulator yet, that session had no
Xcode/Android Studio/emulator/device available. This pass did verify the
newly-added build-channel changes compile: Android's `assembleDebug` and
`assembleBeta` both produced real APKs, and iOS's `Debug`/`Beta`/`Release`
Xcode configurations all built clean via `xcodebuild`, see `BUILD_LOG.md` for
the one thing that didn't get verified (`assembleRelease`'s lint step, blocked
on this machine only having JDK 26, not the 17 the build actually needs).
Multiple recipients, the iOS recurring-messages UI, localization, plus CSV
import and dark mode from `PROJECT_CHECKLIST.md`'s Phase 2/3, remain entirely
unbuilt.

Also noticed in passing (not part of this pass's scope): iOS has no UI for
recurring (birthday/anniversary) messages, even though
`RecurringMessageStore`/`RecurringMessageMatcher`/`SchedulerManager` wiring
for it already exists there. Android has this UI, iOS doesn't, worth a
follow-up.

See `shared/ARCHITECTURE.md` for the core design these would build on top of,
`BUILD_LOG.md` for how the repo got here and how to rebuild it from scratch,
and `FEATURE_IDEAS.md` for a working list of ideas beyond this checklist.
