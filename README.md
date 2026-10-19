# Voice Catcher

An Android app for voice reminders and an organized daily plan. Kotlin and Jetpack Compose; Android 8.0 or newer. Version 0.2.0 separates the app into five pages and adds task editing, custom snooze and live completion updates.

## Download and install

1. Open [Build Android app](https://github.com/puneeee/voice-catcher/actions/workflows/build-android.yml).
2. Choose the latest **successful run on main**. Download `voice-catcher-debug-apk` from **Artifacts** (GitHub sign-in required).
3. Extract the ZIP, transfer `app-debug.apk` to your Android phone, and open it. Allow installation from that source if Android asks.
4. Open **Settings** in Voice Catcher. Enable notifications and, for P1, exact alarms. Allow full-screen alarms if you want the alarm controls to appear over the lock screen. Check the P1 sound channel and your phone's battery restrictions.
5. Allow the microphone when you first tap **Start speaking**. Location is optional. You can use typed commands and **Add task** without microphone permission.

**Updating an existing installation:** Android requires the same signing key to update an app in place. Keep using your original Android Studio/debug or release signing key. CI debug builds may use a different key from your existing installation. Do not uninstall your existing app just to bypass a signing error: uninstalling removes its encrypted tasks and captures. The new task fields are backward compatible when installed as an in-place update.

## Find your way around

| Page | What it does |
| --- | --- |
| **Home** | Speak/type commands, add tasks, see today's open and overdue tasks. |
| **Plan** | Choose a day, add tasks, organize by category and filter the day's work. |
| **Search** | Search task names, priorities, categories, saved captures, places and dates. |
| **History** | Separate **Completed** tasks and **Captures** (voice/typed commands with their outcomes). |
| **Settings** | Notification/alarm access, morning/evening planning times, default snooze and privacy. |

## Create reminders

Tap **Start speaking**, then **Finish speaking** when ready, or type the same command on Home and tap **Use command**.

- “Remind me to stretch in 5 minutes urgent” → P1 alarm five minutes from capture time.
- “Remind me after two minutes to check the oven” → P1 alarm two minutes later.
- “Remind me to call Alex in one hour” → P1 alarm one hour later.
- “P2 remind me to check email in 15 minutes” → routine notification.
- “Remind me to take medicine at 8:30 PM tomorrow” → timed P1 alarm.
- “Remind me to drink water every twenty minutes” → P2 notifications repeating until Done.
- “Add buy groceries” → untimed P2 task on today's plan.
- “Mark buy groceries as done” → completes a matching open task. Ambiguous matches ask you to choose the task.

Relative delays support numeric or common spoken quantities, seconds/minutes/hours, and combinations such as “in one hour and thirty minutes” (up to seven days). **Urgent**, **critical**, **emergency**, **ASAP** and **P1** select P1. P1 needs a time or delay; an urgent command without either asks for one. A timed reminder defaults to P1 unless you explicitly say P2. P1 alarms ring once; P2 may be a one-time notification or repeat every 1–1440 minutes.

The app reports whether an alert was scheduled. If it says **Saved, but no alert was armed**, enable the required access in Settings. Future reminders are re-armed when you return to the app or restart the phone. A missed past P1 alarm is not silently moved to a new time; edit or snooze it.

## Organize your daily plan

1. Open **Plan** and select **Today**, **Tomorrow**, or **Choose date**.
2. Tap **Add to [date]**. Enter the task name and choose **Work**, **Personal**, **Health**, **Errands**, **Learning**, or **Other**.
3. Choose **P2 · Routine** for an ordinary task. Leave **Alert me at a time** off for a checklist item, or enable it and choose an alert time. Choose **P1 · Urgent** for an exact-time alarm.
4. Tap **Save task**. The task appears under its category for that date. Use category chips to filter the plan.
5. To reorganize an existing task, tap **Edit** on Home, Plan or a Search result and change its date/category.
6. Open **Settings → Daily planning notifications**, enter morning/evening times in 24-hour format, then tap **Save daily times**. Morning summarizes today's open priorities; evening summarizes today's completions and remaining work. Tapping the notification opens Plan. Daily summaries may arrive approximately because Android batches background notifications.

Voice-created tasks start in **Other**; tap **Edit** to assign their category. Home includes overdue and older undated tasks so they are not lost; Plan shows tasks assigned to the selected date.

## Edit, finish and snooze

- **Edit:** Change a task's name, priority, category, date, alert time or P2 repeat gap. Saving replaces the old alarm and preserves the task's identity.
- **Done:** Removes the task from open lists immediately, cancels its reminders, and moves it to **History → Completed** with a completion time. The same applies to **Stop & mark done** on the alarm screen and **Mark done** in a notification.
- **Snooze:** Choose 5, 10, 15, 30 or 60 minutes, or enter any whole-number duration from **1 to 10080 minutes**. This is available on task cards and the P1 alarm screen/notification. Optionally save the duration as your default, or set it in Settings.
- **Track snoozes:** The task immediately shows the new due date/time, **Snoozed until**, snooze count, last update and original alert time. Snoozing across midnight moves the task to the new plan date. Completed tasks retain their snooze count in storage. Editing the schedule resets the old snooze state.
- **Delete:** Removes the task after confirmation and cancels its alarm. This is different from completing it.

## Build from source

This is an **Android** project; it requires the Android SDK and JDK, not the .NET SDK.

Requirements: **JDK 17**, **Gradle 8.9**, Android SDK Platform **35**, SDK build tools, and an Android 8.0+ device/emulator. Android Studio can install the SDK components. Set `ANDROID_HOME` or configure `sdk.dir` in an untracked `local.properties`. There is currently no checked-in Gradle wrapper; use the pinned Gradle version below.

On Windows, keep downloads and tooling on D: if desired, for example `D:\DevTools\Android\Sdk`, `D:\DevTools\gradle-8.9` and `D:\Projects\voice-catcher`. Set `GRADLE_USER_HOME=D:\DevTools\GradleCache` before building. In Android Studio, choose the SDK location under Settings → Languages & Frameworks → Android SDK.

```powershell
cd D:\Projects
git clone https://github.com/puneeee/voice-catcher.git
cd voice-catcher
$env:ANDROID_HOME = 'D:\DevTools\Android\Sdk'
$env:GRADLE_USER_HOME = 'D:\DevTools\GradleCache'
# JDK 17 and Gradle 8.9 must be installed and on PATH.
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
# With an emulator running or a USB debugging device connected:
gradle --no-daemon :app:connectedDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Alternatively open this repository in Android Studio, select JDK 17 and a local Gradle 8.9 distribution, sync, and run `app`. Never commit `local.properties`, signing keys, local captures or build outputs.

## Verification and limitations

GitHub Actions builds the APK, runs JVM tests for parsing/state/legacy-data decoding, and runs Compose UI tests on an Android emulator for navigation, completion, custom-snooze input and external task updates. Device acceptance steps are in [docs/ACCEPTANCE_TESTS.md](docs/ACCEPTANCE_TESTS.md).

Tasks and capture metadata are encrypted on the phone with Android Keystore; app backup is disabled. Raw audio is not saved. Android's speech recognition service may need a network connection depending on your phone and installed language packs. Alarms themselves are scheduled locally. Delivery also depends on Android notification/alarm access, volume, Do Not Disturb and manufacturer battery settings; verify those on your actual phone.

## Repository layout

- `app/src/main/` — Compose pages, command parsing, encrypted storage, alarms and notifications.
- `app/src/test/` — JVM regression tests.
- `app/src/androidTest/` — Android UI regression tests.
- `docs/PRODUCT_PLAN.md` — longer-term product plan; items there may be roadmap proposals.
- `docs/ACCEPTANCE_TESTS.md` — on-device release checks for the six reported issues.
