# Voice Catcher

An Android-first voice companion that turns natural speech into reliable reminders, tasks, and gentle daily accountability. The Android app is the source of truth; a dedicated WhatsApp Business number will provide the companion conversation.

## Current foundation

- Kotlin + Jetpack Compose Android app shell
- Today screen with priority-labelled items
- Android speech recognition for English and Hinglish-style voice commands
- Persistent P1/P2 tasks, local exact P1 alarms, and reminder notifications
- Explicit architecture boundaries for cloud AI action extraction and WhatsApp delivery

## Product decisions

- **P1**: urgent, exact-time Android alarm. It must work locally even without a network connection.
- **P2**: routine reminder delivered through WhatsApp where policy permits, with Android notification fallback.
- The assistant uses a dedicated WhatsApp Business number; it never automates a personal WhatsApp account.
- The pilot is single-user, cloud-AI assisted, privacy-conscious, and supports English plus Hinglish.

Read the full product and technical plan in [docs/PRODUCT_PLAN.md](docs/PRODUCT_PLAN.md).

## Run locally

1. Open this folder in Android Studio.
2. Let Android Studio install the Android SDK and create/use the Gradle wrapper if it is not already available on the machine.
3. Select an Android 8.0+ device or emulator and run `app`.
4. Grant microphone and notification access. Tap **Speak now** and say a task or reminder.

> The offline app recognizes simple commands, stores tasks, and schedules P1 alarms. Cloud AI and Meta WhatsApp Cloud API remain separate integrations because they need server-side credentials, explicit opt-in, and approved WhatsApp templates.

## Repository layout

```text
app/                    Android application
docs/PRODUCT_PLAN.md    product, architecture, rollout, and test plan
```

## Next implementation milestones

1. Persist captures and tasks with Room, then add exact P1 alarms and notification actions.
2. Add a managed backend for transcription and typed action extraction.
3. Connect the dedicated WhatsApp Business number, approved templates, and webhook delivery status.
4. Build daily planning and evening-review workflows.
