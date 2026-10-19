package com.puneeee.voicecatcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val sample = Task(id = "ui-sample", title = "UI test task", priority = ReminderPriority.P2,
        plannedFor = java.time.LocalDate.now(), category = TaskCategory.WORK)
    @Before fun reset() {
        compose.runOnIdle { val store = TaskStore(compose.activity); store.clearTasks(); store.clearCaptures(); store.upsert(sample) }
    }
    @Test fun pagesHaveDistinctContent() {
        compose.onNodeWithText("Plan", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Your daily plan").assertIsDisplayed()
        compose.onNodeWithText("How to organize your day").assertExists()
        compose.onNodeWithText("Settings", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("Notifications & P1 alarms").assertExists()
        compose.onNodeWithText("How to organize your day").assertDoesNotExist()
        compose.onNodeWithText("Search", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Find something").assertIsDisplayed()
    }
    @Test fun completedTaskLeavesHomeAndAppearsInCompleted() {
        compose.onNodeWithText("Done", useUnmergedTree = false).performScrollTo().performClick()
        compose.onNodeWithTag("task-${sample.id}").assertDoesNotExist()
        compose.onNodeWithText("History", useUnmergedTree = false).performClick()
        compose.onNodeWithText(sample.title).assertExists()
        compose.onNodeWithText("Completed", useUnmergedTree = false).assertExists()
    }
    @Test fun serviceStyleCompletionRefreshesTheVisibleList() {
        compose.onNodeWithTag("task-${sample.id}").assertExists()
        compose.runOnIdle { TaskOperations.complete(compose.activity, sample.id) }
        compose.waitForIdle()
        compose.onNodeWithTag("task-${sample.id}").assertDoesNotExist()
        compose.onNodeWithText("History", useUnmergedTree = false).performClick()
        compose.onNodeWithText(sample.title).assertExists()
    }
    @Test fun externalSnoozeUpdateIsVisibleWithoutRestart() {
        compose.runOnIdle { TaskStore(compose.activity).mutateTask(sample.id) { TaskState.snooze(it, 17, LocalDateTime.now()) } }
        compose.waitForIdle()
        compose.onNodeWithText("Snoozed until", substring = true).assertExists()
        compose.onNodeWithText("1 snooze(s)", substring = true).assertExists()
    }
    @Test fun customSnoozePickerAcceptsNonPresetMinutes() {
        compose.onNodeWithText("Snooze", useUnmergedTree = false).performScrollTo().performClick()
        compose.onNodeWithTag("snooze-minutes").performTextReplacement("17")
        compose.onNodeWithTag("confirm-snooze").assertIsDisplayed()
        // Permission-dependent AlarmManager delivery is device-tested separately.
    }
}
