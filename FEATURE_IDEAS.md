# Feature ideas

Not a roadmap, nothing here is scheduled. A working list of things that would genuinely fit this
app specifically (a message pool with weighted priority and anti-repeat selection, no-send days,
recurring dates, a composable `AutomationAction`/`AutomationRegistry` pool already wired to
Tasker on Android and App Intents on iOS, Home Screen widgets reading a shared snapshot), rather
than generic app features that happen to apply. Move anything here into `CHECKLIST.md` or
`PROJECT_CHECKLIST.md` when it's actually being worked on.

## Already flagged once and worth resurfacing

1. **A preview / test-send button.** Explicitly scoped out of `b4ac843`'s hardening pass as
   "meaningfully riskier" to bundle with everything else in that commit. Still hasn't happened.
   Given the SMS sends silently on Android with no confirmation dialog, a "send this exact message
   to yourself right now" button would catch a bad phone number or a garbled `{name}`/
   `{time-of-day}` template substitution before it goes to a real recipient.

2. **iOS UI for recurring messages.** `CHECKLIST.md` already notes this directly:
   `RecurringMessageStore`/`RecurringMessageMatcher`/`SchedulerManager` wiring exists on iOS, but
   there's no settings screen to add or edit a birthday/anniversary entry, only Android has that
   UI today.

## Around the automation engine (Tasker / App Intents)

3. **A read-only "when's the next message" query action.** Every action registered in
   `AutomationRegistry` today (`SendSmsAction`, `OpenSmsComposeAction`) is a command: send
   something. Nothing lets Tasker or Siri *ask* the app anything. A query action that returns the
   next scheduled time and recipient (the same data the Home Screen widget already reads from
   `NextSendSnapshot`) would let a Tasker task or Shortcut say "next message goes out at 3:40pm"
   out loud, without opening the app.

4. **A "send now, off-schedule" action distinct from the scheduled trigger.** Right now the only
   way to get an unscheduled send is to wait for `SchedulerService`'s timer. A second registered
   action, reachable from Tasker/Shortcuts and from a button on the widget itself, that picks a
   message and sends immediately without disturbing the next scheduled time, would cover the
   "I just want to send one right now" case the composable action pool was explicitly built to
   support.

## Making the message pool smarter

5. **Retire a message after it's been sent N times.** `WeightedMessageSelector` already tracks
   priority; nothing currently ages a message out. A soft cap (configurable, maybe defaulting to
   off) that flags a message as "getting stale" after, say, 20 sends would nudge toward refreshing
   the pool instead of the same handful of favorites dominating forever.

6. **Mood/occasion tags on top of the existing priority weighting.** A message pool entry already
   carries a priority weight; adding a lightweight tag (sweet, funny, reassuring) and letting the
   weighted selector optionally bias toward a tag for a stretch (say, extra "reassuring" weight
   during a week flagged as stressful) would layer on top of `MessagePriorityStore` rather than
   replace it.

7. **A minimum gap after a recurring message.** A birthday/anniversary message fires from
   `RecurringMessageMatcher` independent of the random pool schedule. Nothing stops a random pool
   message from landing four minutes later the same day, which dilutes the occasion. A short
   enforced gap (skip or reschedule the next random pick if a recurring message already fired
   today) would keep the two systems from stepping on each other.

## Scheduling nuance

8. **Recipient-timezone-aware windows for long-distance use.** The send-time window (e.g. 9am to
   9pm) is presumably evaluated against the sending device's local time. For a couple in different
   timezones, "9am" on the sender's phone might be 3am for the recipient. An explicit recipient
   UTC offset, stored alongside the recipient's contact info from the existing contact picker,
   would let the window target the recipient's actual day.

9. **An occasion override that ignores no-send days.** `NoSendDayChecker` treats every configured
   blackout day equally. A specific date (Valentine's Day, an anniversary already tracked by
   `RecurringMessageStore`) landing on a configured no-send day currently means nothing goes out.
   Letting recurring, date-specific messages bypass the no-send check they'd otherwise be blocked
   by would fix an edge case that's easy to hit by accident (a weekly no-send day overlapping a
   yearly date).

10. **A geofenced pause.** If the sender and recipient are both carrying phones that report
    location (opt-in, off by default), suppressing sends while the sender is physically at the
    recipient's saved location, they're already together, so a "thinking of you" text at that
    moment reads as odd rather than sweet.

11. **A dry-run week simulator.** Before turning the real scheduler on, generate a full
    hypothetical week of send times and which pool message each one would have picked, shown as a
    preview list, no notifications, no SMS. Useful for sanity-checking that the pool has enough
    variety and the window feels right before committing to it live.

## Small UX gaps sitting next to code that already exists

12. **One-tap pause/resume from outside the app.** The master on/off toggle only lives in the main
    screen today. An Android Quick Settings tile and an iOS Lock Screen widget (the Home Screen
    widget infrastructure and `NextSendSnapshot` App Group plumbing already exist for the
    read-only display) that toggles the scheduler without opening the app would match how people
    actually want to pause something briefly.

13. **Surface *why* a day was skipped, not just that it was.** `NoSendDayChecker` can suppress a
    send silently. The stats/history dashboard (`StatsCalculator`) shows sends and failures, but a
    day with nothing scheduled because of a no-send rule looks identical to a day where something
    just didn't fire. A distinct history entry type ("skipped: no-send day") would remove the
    "did this actually work today" uncertainty that comes with looking at an empty history.

14. **Export and re-import the message pool as JSON.** Android's send log and message pool
    already moved to JSON in `b4ac843`. A share-sheet export/import round trip (send yourself the
    JSON file, or hand it to a partner setting up their own install) would make rebuilding a pool
    after a reinstall, or copying favorites between the two split platform repos' respective
    testers, much less tedious than retyping every message.

15. **A per-message "last sent" timestamp visible in the pool editor.** The anti-repeat window
    (`MessageSelector`'s last-five tracking) already exists internally; surfacing "last sent 3 days
    ago" next to each message in the editing UI (not just in the history log) would make it obvious
    at a glance which messages have gone stale without digging through the send history.

## Privacy and safety

16. **A biometric lock on opening the app.** The message pool and send history contain personal,
    specific content about a real relationship, and the app currently opens straight to it. A
    Face ID / fingerprint prompt (`LocalAuthentication` on iOS, `BiometricPrompt` on Android)
    gated on app foreground, optional and off by default, would matter to anyone who shares a
    device or leaves a phone unlocked around others.

17. **Encrypt the message pool and send log at rest.** Both currently live in plain
    `SharedPreferences`/`UserDefaults`. Given the content is personal correspondence, wrapping the
    stored values with `EncryptedSharedPreferences` (Android) or the Keychain (iOS) instead of
    plaintext prefs would close a real gap for anyone whose device backup isn't itself encrypted.

## Delivery confidence

18. **Surface Android's delivery confirmation in the widget, not just the history log.**
    `4608812` added real `sentIntent`/`deliveryIntent` tracking with a `SENT`/`DELIVERED`/`FAILED`
    status. Today that status only shows up in the send-history list. A small delivery indicator
    on the Home Screen widget itself (last send: delivered / failed / pending) would put that
    signal somewhere it's actually glanced at, rather than requiring a trip into the app.
