# ───────────────────────────────────────────────────────────────────
# Shared architecture notes — read before touching either platform
# ───────────────────────────────────────────────────────────────────
# Both apps (Android Kotlin, iOS Swift) share the same design:
#
#   Core Loop:
#     1. User sets a time window (e.g. 9 AM – 9 PM)
#     2. App picks a random time each day within that window
#     3. At that time, it picks a random message from the pool
#     4. Sends via SMS (Android) or opens pre-filled Messages (iOS)
#     5. Logs the send so you can track
#
#   Android CAN send silently from your SIM.
#   iOS CANNOT send silently — Apple blocks it. Instead it opens
#   the Messages app with a pre-filled message; you tap Send once.
#
#   Future: cross-platform notifications via Firebase if Apple
#   ever opens up SMS access.
#
#   Stats dashboard (StatsCalculator + StatsActivity/StatsView):
#     Both platforms persist a structured send log (Android always
#     did; iOS's used to be an unpersisted [String] reset every
#     launch — now it's a persisted [SentLogEntry], same as Android,
#     so there's real history to summarize). StatsCalculator turns
#     that log into success/engagement rate, sends-per-day, top
#     messages, and a streak count. Status vocabulary differs by
#     platform (Android: "sent"/"failed" — a real outcome; iOS:
#     "pending"/"opened" — iOS can't confirm an actual send, only
#     that the user tapped through to Messages).
# ───────────────────────────────────────────────────────────────────
