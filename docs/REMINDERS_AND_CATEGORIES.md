# Voice Catcher 0.3 — reminders, categories and appearance

## What changed

- Manual task save errors stay beside Save task even when alert fields are expanded. An empty task name means “Enter a task name before saving”; 300 is the maximum length, not a minimum.
- Settings → Appearance offers System, Light and Dark; the choice persists and also applies to the alarm screen.
- Home offers Today, All, P1 alarms and P2 notifications. All/P1/P2 include future dates, and every open task has Edit, Done, Snooze and Delete.
- Routine voice reminders now default to **P2 notifications**. Previously generic timed requests became P1.
- New captures suggest a category locally. Existing open Other tasks receive a one-time suggestion on upgrade; later manual choices stay unchanged.

## Priority words

These case-insensitive words/phrases select P1: **urgent, emergency, critical, ASAP, immediately, P1 (including P 1 / P-1), priority one, priority 1**. An explicit **alarm** request also selects P1.

A time or delay is still needed. “Urgent call Sandy” asks when; it never invents an immediate alarm. Conflicting explicit P1/P2 requests ask for clarification.

| Say | Result |
| --- | --- |
| Remind me to take bath in 5 minutes | P2, Health, notification in 5 minutes |
| Remind me to check the app status in 5 minutes | P2, Work |
| Urgent remind me to call Sandy in 2 minutes | P1 ringing alarm |
| Set an alarm after one hour to leave | P1 ringing alarm |
| Remind me to stretch every 10 minutes | Repeating P2 until Done |
| At 4 o'clock I should be eating lunch today | P2, Health; next applicable 4 PM today |
| Mark take bath as done | Completes the matching open task |

Ambiguous requests are stored as captures with an explanation, not scheduled tasks. Read their outcome in History → Captures. Rephrase with a valid time, or use Add task.

## Where P2 tasks live

Home → P2 notifications shows every open P2 task across all dates. Edit changes its name, date, category, alert time or repeat interval. Plan groups them by date/category; Search finds them by title, P2 or category.

A P2 **without an alert time** is a to-do only. Turn on “Alert me at a time”, choose the plan date and alert time, then Save task. A blank repeat gap produces one notification; a gap of 1–1440 minutes repeats until Done.

The P2 notification has a Done action and opens Home when tapped. P2 uses exact scheduling if Android exact-alarm access is allowed; otherwise delivery can be delayed by Android battery management. Notifications and the P2 channel must be enabled in Settings.

## Categories

Suggestions use local keyword rules, not cloud AI:

- Health: bath, shower, run, walk, exercise, medicine, meals, sleep.
- Work: app status, application, project, meeting, report, code, email.
- Learning: study, course, read, class, exam.
- Errands: buy, groceries, shopping, bank, pharmacy.
- Personal: family, calls, friends, bills, laundry.
- Other: no recognized match.

Voice/typed commands get suggestions. A new manual task updates its suggestion while you type, until you explicitly select a category. Tap a category to override it. Categories organize work and do not change alert priority or time.

## Verification and limits

Unit tests cover routine P2 defaults, all documented P1 words, clock phrasing and category suggestions. Android emulator checks cover visible form errors with alert on/off, theme persistence, future P2 visibility, notification posting/completion, editing and custom snooze. Emulator results do not establish delivery reliability on every manufacturer's physical phone.
