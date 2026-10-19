package com.puneeee.voicecatcher

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import java.io.File
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
    private fun taskVisible() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("task-${sample.id}"))
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= 29) {
            // Public test output survives the test runner uninstalling the app.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VoiceCatcherScreenshots")
            }
            val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun pagesHaveDistinctContent() {
        screenshot("home")
        compose.onNodeWithText("Plan", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Your daily plan").assertIsDisplayed()
        compose.onNodeWithText("How to organize your day").assertExists()
        screenshot("plan")
        compose.onNodeWithText("Settings", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("Notifications & P1 alarms").assertExists()
        screenshot("settings")
        compose.onNodeWithText("How to organize your day").assertDoesNotExist()
        compose.onNodeWithText("Search", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Find something").assertIsDisplayed()
        screenshot("search")
        compose.onNodeWithText("History", useUnmergedTree = false).performClick()
        screenshot("history")
    }
    @Test fun completedTaskLeavesHomeAndAppearsInCompleted() {
        taskVisible()
        compose.onNodeWithText("Done", useUnmergedTree = false).performScrollTo().performClick()
        compose.onNodeWithTag("task-${sample.id}").assertDoesNotExist()
        compose.onNodeWithText("History", useUnmergedTree = false).performClick()
        compose.onNodeWithText(sample.title).assertExists()
        compose.onNodeWithText("Completed", useUnmergedTree = false).assertExists()
    }
    @Test fun serviceStyleCompletionRefreshesTheVisibleList() {
        taskVisible()
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
        taskVisible()
        compose.onNodeWithText("Snoozed until", substring = true).assertExists()
        compose.onNodeWithText("1 snooze(s)", substring = true).assertExists()
    }
    @Test fun customSnoozePickerAcceptsNonPresetMinutes() {
        taskVisible()
        compose.onNodeWithText("Snooze", useUnmergedTree = false).performScrollTo().performClick()
        compose.onNodeWithTag("snooze-minutes").performTextReplacement("17")
        compose.onNodeWithTag("confirm-snooze").assertIsDisplayed()
        compose.onNodeWithTag("confirm-snooze").performClick()
        compose.waitForIdle()
        val updated = TaskStore(compose.activity).load().single { it.id == sample.id }
        org.junit.Assert.assertEquals(1, updated.snoozeCount)
        val difference = java.time.Duration.between(updated.snoozedAt, updated.dueAt).toMinutes()
        org.junit.Assert.assertEquals(17L, difference)
        taskVisible()
        compose.onNodeWithText("Snoozed until", substring = true).assertExists()
    }
    @Test fun editingUpdatesTheSameTaskAndCategory() {
        taskVisible()
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Task name").performTextReplacement("Updated task")
        compose.onNodeWithText("Health", useUnmergedTree = false).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText("Save task").performClick()
        compose.waitForIdle()
        val saved = TaskStore(compose.activity).load()
        org.junit.Assert.assertEquals(1, saved.size)
        org.junit.Assert.assertEquals(sample.id, saved.single().id)
        org.junit.Assert.assertEquals(TaskCategory.HEALTH, saved.single().category)
        taskVisible()
        compose.onNodeWithText("Updated task").assertExists()
        compose.onNodeWithText(sample.title).assertDoesNotExist()
    }
}
