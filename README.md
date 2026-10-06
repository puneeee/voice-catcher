# Voice Catcher

An Android-first voice companion that turns natural speech into reliable reminders, tasks, and gentle daily accountability. The Android app is the source of truth; a dedicated WhatsApp Business number will provide the companion conversation.

## Current foundation

- Kotlin + Jetpack Compose Android app shell
- Today screen with priority-labelled items
- Real microphone recording to the app cache after permission is granted
- P1/P2 reminder model ready for the reminder engine
- Explicit architecture boundaries for transcription, AI action extraction, and WhatsApp delivery

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
4. Grant microphone access, then tap **Capture voice note** to begin and **Stop and save** when finished.

> This first commit deliberately does not connect a cloud AI provider, Meta WhatsApp Cloud API, or an alarm scheduler. Those integrations need server-side credentials, approved WhatsApp templates, and explicit user configuration; the interfaces are documented in the product plan.

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
