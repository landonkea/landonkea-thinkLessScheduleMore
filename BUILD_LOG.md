# Build log: how this repo came to be, and how to rebuild it from nothing

`CHECKLIST.md` and `PROJECT_CHECKLIST.md` track what's done and what's next. This file looks
backward instead: the actual sequence of decisions that got the repo from an empty directory to
its current state (24 commits, five weeks, two platforms), and a literal path to reproduce that
state on a machine that has nothing on it yet. If you, or an agent with no memory of any of
this, ever need to stand the app up from scratch, start here.

## How this actually happened, condensed from git history

**1. Both platforms, minimal, on day one (`976f45b`).** The very first commit is already both
apps: an Android foreground service that sends SMS from the real SIM via `SmsManager`, and an
iOS app that schedules local notifications and opens Messages pre-filled when tapped (Apple
doesn't let any app send SMS silently, full stop, so the iOS half of this project has been a
"remind and pre-fill" app since before there was a git history to look back on). Both platforms
store settings in the platform's plainest key-value store, `SharedPreferences` on Android,
`UserDefaults` on iOS. No database on either side, still true today.

**2. A cluster of same-day and next-day fixes (`c6e45be`, `7c23745`, `3b628d7`, `7bd46e3`).**
Runtime permission requests, a remove-message dialog, iOS notification cleanup, a
`foregroundServiceType` fix (Android 14 requires a real value; `specialUse` was the honest one
since scheduled SMS doesn't match any built-in service type), the Xcode project generated via
`xcodegen` for the first time, then a fix so the hand-written `Info.plist` survived
regeneration. Fast iteration on a skeleton that was mostly right but not quite installable yet.

**3. Build types, README, and CI, together (`fd59708`, `313d294`, `ccbc3fd`).** The Android
`debug`/`release` build types (release gets R8 + resource shrinking, no signing config since
there's no keystore in the repo) landed in the same commit as the first root `README.md`. CI
followed immediately: `compileDebugKotlin`/`compileReleaseKotlin` on JDK 17, an iOS simulator
build, and the first real XCTest, verifying `NotificationManager` actually registers itself as
`UNUserNotificationCenterDelegate` (before this, tapping a notification did nothing; the tap
handler existed but nothing ever wired it up). The simulator-selection fix in `ccbc3fd` exists
because `macos-latest` runners don't always ship the same simulator lineup, so CI greps for
whatever iPhone 14+ simulator is actually installed instead of hardcoding a device name.

**4. A hardening pass, and a real bug fix (`b4ac843`).** Live 160-char counters, in-place message
editing, confirm-before-delete, the next-scheduled-time surfaced in the UI, loose phone-number
validation. Buried in the same commit: the send log had been stored as
`"timestamp|status|message"`, which broke the moment a message itself contained a `|`. Android's
pool and log moved to JSON (`org.json`, no new dependency) with a fallback parser for existing
installs. "No send" days and a preview/test-send button were explicitly scoped out of this pass
as too risky to bundle with everything else, both showed up later, one already, one still in
`FEATURE_IDEAS.md`.

**5. Boot survival, variety, and the first split (`11a0c8c`, `12b9234`, `d67d677`, `67c48ec`).**
`BootReceiver` re-arms `SchedulerService` after a restart, since before this a reboot silently
killed scheduling with no recovery short of reopening the app. `MessageSelector` started avoiding
the last five sent messages so back-to-back repeats stopped happening. `MessageTemplate` added
`{name}`/`{time-of-day}` placeholders so one pool entry can produce variety. Each platform was
also split out into its own standalone repo (`landonkea-thinklessschedulemore-android`,
`landonkea-thinklessschedulemore-ios`) via `git-filter-repo`, preserving full history on each
side, while this monorepo stayed canonical. A stats/history dashboard (`StatsCalculator` +
`StatsActivity`/`StatsView`) followed the same day.

**6. Home Screen widgets (`b995ddf`).** iOS got a real WidgetKit extension reading a
`NextSendSnapshot` that `MessageStore` writes to an App Group container, the same pattern already
used in `landonkea-ytmusic-ios`'s `NowPlayingWidget`. Android got a plain `AppWidgetProvider` +
`RemoteViews` widget, no Glance, since this project has no Compose dependency. Both sides share
one pure, unit-tested `NextSendFormatter` that turns a send time and recipient into display text,
independent of `Date()`/`System.currentTimeMillis()`, so every branch (today, tomorrow, further
out, sending now, nothing scheduled) is deterministically testable without a clock.

**7. The automation-engine milestone, four commits in one day (`0da4a6c`, `5625bc9`, `4608812`,
`d94fedb`, all `2026-08-05`).** `0da4a6c` pulled SMS-sending out from under `SchedulerService`'s
timer loop into `AutomationAction`/`AutomationRegistry`, a pool a trigger reaches only by a
string id, never a concrete type. `SchedulerService` became purely a trigger deciding *when*, no
longer *what*. `5625bc9` then implemented the actual Locale/Tasker plugin contract on top of that
registry (`TaskerEditActivity`, `TaskerFireReceiver`, `TaskerBundleCodec`), proving the decoupling
was real by making a Tasker-fired send call the exact same `AutomationRegistry.execute()` path
the scheduler's own timer already used. `d94fedb` mirrored the whole thing on iOS with
`SendSmsIntent`/`ThinkLessScheduleMoreShortcuts`, deliberately not named `SendSmsAction` the way
Android's is, since iOS still can't send anything without a human tapping Send; the honest iOS
action is `OpenSmsComposeAction`, extracted out of `NotificationManager` so a notification tap
became just one more trigger sharing the pool rather than special-cased code. `4608812` filled in
snooze actions, recurring yearly messages (with Feb 29 falling back to Feb 28 in non-leap years),
real Android SMS delivery confirmation via `sentIntent`/`deliveryIntent`, and a contact picker on
both platforms.

**8. Repo hygiene (`a739d7d`, `2f95303`, `e78ab74`, both `2026-08-07`).** A CI workflow blocking
AI attribution in commit authorship and messages, then widened the same day to also check
committer fields, not just author fields.

**9. Docs (`5a0b6fd`, `9c0d9af`).** `docs/DESIGN.md` added with the Mermaid diagrams still in
that file today, then a pass removing em dashes from README and markdown files repo-wide, before
the writing-style convention this file also follows existed as a written checklist.

**10. No-send days and weighted selection (`de7e691`, `2026-08-12`).** `NoSendDayChecker`/
`NoSendDayStore` and `WeightedMessageSelector`/`MessagePriorityStore`, wired into the scheduler
and settings UI on both platforms. Per `CHECKLIST.md`, none of it had been compiled or run yet at
that point, no Xcode/Android Studio/emulator/device was available in that session.

**11. This pass: build channels, plus this file and `FEATURE_IDEAS.md`.** A `beta` Android build
type (`android/app/build.gradle.kts`) sits between `debug` and `release`: R8 shrinking on like
release, but with its own `applicationIdSuffix` so it installs side by side with both. Three
GitHub Actions workflows now back the three channels the gradle comments already referenced:
`build-debug.yml` (push to a `dev-*` branch), `build-beta.yml` (a pre-release tag like
`v1.2.0-beta.1`), `build-release.yml` (a stable tag like `v1.2.0`). iOS got the closest honest
equivalent: a `Beta` Xcode configuration in `project.yml`, release-shaped like Android's beta
build type, but without a separate bundle identifier, because TestFlight and the App Store
install as the same app either way, distinguished by build number and distribution channel, not
by identity. All three workflows build both platforms; the iOS side is a simulator build in every
case, since no Apple signing identity lives in this repo (same reasoning that's kept Android's
`release` build type unsigned since `fd59708`).

## Decisions worth knowing the reasoning behind

- **No Room, no WorkManager, no ViewModel/Repository pattern.** `PROJECT_CHECKLIST.md` states
  this outright: `SharedPreferences` is simpler and enough for a phone number and a message list,
  a `Service` with `Handler` and a random delay is cleaner than `WorkManager` for this job, and a
  two-screen app doesn't need a repository layer standing between the UI and storage.
- **Android sends silently, iOS opens Messages pre-filled, and that's permanent, not a gap.**
  This isn't an unfinished feature on iOS. Apple doesn't expose any API for a third-party app to
  send SMS without a human tapping Send, and the app's entire iOS design (notification, tap,
  pre-filled compose screen) exists because of that constraint, not in spite of not having found
  a workaround yet.
- **The send log is JSON, not pipe-delimited, because pipe-delimited actually broke.** `b4ac843`
  wasn't a style preference, a message containing `|` corrupted the log format. The fix kept a
  fallback parser for logs written before the change.
- **`AutomationAction`/`AutomationRegistry` exist so Tasker and App Intents didn't need their own
  parallel send logic.** A trigger (the scheduler's timer, a Tasker task, a boot receiver) reaches
  an action only by a string id. `SendSmsAction` on Android and `OpenSmsComposeAction` on iOS are
  each the one and only implementation right now, but the scheduler, Tasker, and (eventually)
  anything else all call the same code path instead of three copies of it.
- **Release builds are intentionally unsigned in this repo.** True since `fd59708`, still true
  after this pass's beta channel. Signing requires a real keystore (Android) or Developer Program
  membership and certificates (iOS), neither of which is available in this environment, and
  neither belongs committed into source control regardless. `build-release.yml` and
  `build-beta.yml` produce unsigned artifacts for someone to sign by hand later, not a publish
  pipeline.
- **iOS's beta channel doesn't get a distinct bundle ID the way Android's does.** Android can
  install `debug`/`beta`/`release` side by side on one device because `applicationIdSuffix` makes
  them three different apps as far as the OS is concerned. iOS has no equivalent: a TestFlight
  beta and an App Store release are the same app with the same bundle identifier, just different
  build numbers and distribution channels. Giving iOS's `Beta` configuration its own bundle ID
  would also have required re-suffixing the embedded widget extension's ID to keep the
  container/extension prefix match Xcode requires, solvable, but a real increase in surface area
  for a channel that can't be tested end-to-end here anyway (no device, no signing).
- **Both platforms split into standalone repos, but this monorepo stays canonical.** `d67d677`:
  `git-filter-repo` extracted each platform with full history preserved into its own repo,
  useful for anyone who only cares about one platform's history, but every change here since then
  has continued to land in the monorepo first.

## Rebuilding from zero: exact steps

This assumes a machine with nothing app-specific on it yet.

### 0. Prerequisites (manual, one-time, can't be scripted)

- **JDK 17+** for Android. `java -version` to check; the project's CI pins Temurin 17 via
  `actions/setup-java`, and a couple of comments in `android/app/build.gradle.kts` explain why
  the *test* task specifically stays pinned to 17 even if a newer JDK runs the rest of the build
  (Robolectric 4.13's bundled ASM can't parse class files from very new JDKs). This repo was
  audited under a JDK 26-only machine during this pass; `assembleDebug` and `assembleBeta`
  compiled and produced APKs fine, but `assembleRelease`'s `lintVitalAnalyzeRelease` task failed
  specifically under JDK 26, a real local-environment gap, not something exercised or fixed by
  this pass. Get an actual JDK 17 on the build machine (or trust CI, which already pins one) if
  you need `assembleRelease` to work locally.
- **Android SDK, compileSdk 36 / minSdk 26.** Android Studio installs this automatically on first
  open; `android/local.properties` (the local SDK path) is git-ignored and created for you then.
- **Xcode 15+** (iOS 17 deployment target) for iOS. Verified working in this environment via
  `xcodebuild -version`.
- **[xcodegen](https://github.com/yonaskolb/XcodeGen)**, `brew install xcodegen`. `ios/project.yml`
  is the source of truth; the checked-in `.xcodeproj` exists so the project opens without
  xcodegen installed, but drifts out of sync with `project.yml` if you skip regenerating it after
  a source-file or build-setting change.
- **Python 3**, used only by `scripts/gen_test_report.py` for the combined test-report step.

### 1. Clone

```bash
git clone git@github.com:landonkea/landonkea-thinkLessScheduleMore.git
cd landonkea-thinkLessScheduleMore
```

### 2. Android

```bash
cd android
./gradlew compileDebugKotlin compileReleaseKotlin   # compile-only sanity check
./gradlew assembleDebug                              # debug APK
./gradlew testDebugUnitTest                          # Robolectric-backed unit tests
```

Open the `android/` folder directly in Android Studio to run on a device or emulator; it creates
`local.properties` for you on first open. Three build types exist:
`debug` (`.debug` applicationId suffix, unminified), `beta` (release-shaped, R8 on, `.beta`
suffix, `-beta` version suffix), and `release` (R8 + resource shrinking, unsigned). Runtime
permissions requested on first launch: `SEND_SMS` and, on Android 13+, `POST_NOTIFICATIONS`;
either can be denied without crashing the app, sends just get logged as `failed` or the
foreground-service notification silently doesn't show.

### 3. iOS

```bash
cd ios
xcodegen generate
open ThinkLessScheduleMore.xcodeproj
```

Or from the command line:

```bash
xcodebuild -scheme ThinkLessScheduleMore -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build
```

Three Xcode build configurations exist: `Debug` (the only one with
`SWIFT_ACTIVE_COMPILATION_CONDITIONS: DEBUG` set), `Beta`, and `Release` (both release-shaped,
same bundle ID). Pass `-configuration Beta` or `-configuration Release` to target the other two.
Re-run `xcodegen generate` any time a source file is added/removed or `project.yml` changes,
that regeneration was verified working during this pass, all three configurations built clean.

### 4. Run the test suites

```bash
scripts/run_all_tests.sh          # both platforms, writes test-results/latest.md
scripts/run_all_tests.sh android  # Android only
scripts/run_all_tests.sh ios      # iOS only
```

`test-results/` is git-ignored, regenerated on every run. CI runs the same underlying test
commands directly, then calls this script with `--parse-only` per job since the results are
already on disk by that point.

### 5. What CI actually builds, and when

- **`.github/workflows/ci.yml`**: compiles and tests both platforms on every push/PR to `main`.
- **`.github/workflows/ai-attribution-check.yml`**: blocks commits with AI author/committer
  fields or AI co-author trailers, on push/PR to `main`, `master`, `dev`, `staging`.
- **`.github/workflows/build-debug.yml`**: push to a `dev-*` branch builds and uploads an Android
  debug APK and an iOS Debug-configuration simulator build.
- **`.github/workflows/build-beta.yml`**: a pre-release tag (`v1.2.0-beta.1`, matched by the
  `v*.*.*-*` glob, anything with a hyphen after the version numbers) builds and uploads an Android
  beta APK and an iOS Beta-configuration simulator build.
- **`.github/workflows/build-release.yml`**: a stable tag (`v1.2.0`) builds and uploads an Android
  release APK + AAB and an iOS Release-configuration simulator build. Since GitHub Actions' tag
  globbing can't cleanly express "no hyphen anywhere," this workflow triggers on the same `v*.*.*`
  shape the beta pattern is a superset of, then each job double-checks with an explicit
  `!contains(github.ref_name, '-')` condition before doing anything.

None of these three publish anywhere. They produce unsigned build artifacts (`actions/upload-
artifact`) for someone with real signing credentials to pick up and sign by hand.

### What a script genuinely cannot do for you

- **Install Xcode, Android Studio, or Homebrew themselves.** Chicken-and-egg, same as any repo.
- **Provision real Apple Developer / Google Play signing credentials.** Neither exists in this
  repo by design (see "Decisions" above), and setting either up is an account-and-payment step,
  not a coding task.
- **Verify an actual SMS send.** `SmsManager` needs a real SIM in a real device; nothing in CI or
  the simulator exercises that path. Per `CHECKLIST.md`, the most recently added Kotlin/Swift
  (no-send days, weighted selection, and everything from this pass) is unverified on real
  hardware for exactly this reason.
- **Test the Tasker plugin or Siri Shortcuts flows end to end.** Both need Tasker actually
  installed (Android) or a real device (iOS Shortcuts app integration is unreliable on
  Simulator), neither of which is available here.
