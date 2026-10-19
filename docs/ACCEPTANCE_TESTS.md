# Voice Catcher 0.3.0 acceptance checks

New regression checks:

- Save an empty task with alert off/on: error remains visible beside Save; no empty task is created.
- Choose Dark, recreate the app, choose Light then System: selection persists and changes immediately.
- Add a future P2 and select Home → P2 notifications: it remains visible and editable.
- Deliver a due P2: a system notification appears; Done completes the task.
- Say bath/run/app status: suggestions are Health/Health/Work; editor selection overrides them.
- Routine reminders default to P2; documented urgency words and explicit alarm requests select P1.

Use a disposable test installation or update with the original signing key. Do not uninstall a populated installation to bypass a signature mismatch. Start with notifications, exact alarms and the P1 notification channel enabled.

1. **Relative time / urgent:** Speak, or tap Type instead and type “remind me to check the oven after two minutes urgent.” Check that the saved card says P1 and has a due time two minutes later. Repeat with “in 5 minutes urgent” and “in one hour urgent.” Without urgency, expect P2 notifications. Lock the phone and confirm the short P1 rings at the expected time.
2. **Separate pages:** Switch Home → Plan → Search → History → Settings. Each page should show only its own content. Rotate the phone and check the selected page/filter survives. Notification settings belong on Settings; capture history belongs under History → Captures.
3. **Planning:** On Plan, choose tomorrow and add Work / P2 / no alert. It should appear only under tomorrow's Work category. Edit it to today and Health; check the category counts and Home update. Configure morning/evening times in Settings; confirm tapping a daily notification opens Plan.
4. **Editing:** Create a P1 for three minutes from now. Edit its name, category and time to five minutes from now. Check only the new time rings. Switch to P2 and confirm it uses a notification. Test P2 repeat-gap edits. Open task count must not increase when editing.
5. **Completion:** Tap Done on Home and confirm the card leaves the open list immediately. Check History → Completed and the completion time. Repeat using “mark [task] as done”, the P2 notification action, and Stop & mark done on the P1 screen. Reopen the app; tasks must remain completed and must not ring again.
6. **Custom snooze / tracking:** Ring a P1, choose Snooze, enter 17 minutes and confirm. Ringing must stop only after scheduling succeeds. Home/Plan should immediately show the new due time, Snoozed until, count 1 and original time. Snooze again with another duration; count becomes 2. Try a duration crossing midnight: Plan date moves to the next day. Set a default duration in Settings and check it pre-fills the next picker. Empty, zero, negative or over-10080 durations must be rejected.
7. **Access and persistence:** Deny notifications and try an alarm; the app must report that no alert was armed rather than claiming success. Restore permission and return to the app. Restart the phone with future P1/P2 tasks and verify re-arming. Confirm pre-0.2.0 tasks/captures load, completed items move into History, and missing category fields show Other.

Automated JVM tests cover relative time parsing, priority detection, completion matching, schedule edits, arbitrary snooze transitions, day/category filtering and backward-compatible JSON. Emulator tests cover page separation and live UI updates. Real microphone recognition, lock-screen presentation, hardware audio/vibration, reboot behavior and manufacturer power restrictions still need checks on the target phone.
