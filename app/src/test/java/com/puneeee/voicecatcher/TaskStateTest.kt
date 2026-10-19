package com.puneeee.voicecatcher

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TaskStateTest {
    private val now = LocalDateTime.of(2026, 10, 19, 12, 0)
    private val task = Task(id = "task", title = "Finish report", priority = ReminderPriority.P1, dueAt = now.plusMinutes(5), category = TaskCategory.WORK)
    @Test fun `completion moves work out of today and plan`() {
        val done = TaskState.complete(task, now)
        assertEquals(TaskStatus.DONE, done.status)
        assertEquals(now, done.completedAt)
        assertTrue(TaskQueries.forDay(listOf(done), now.toLocalDate(), true).isEmpty())
        assertEquals(listOf(done), TaskQueries.completed(listOf(done)))
    }
    @Test fun `completion is idempotent`() {
        val done = TaskState.complete(task, now)
        assertEquals(done, TaskState.complete(done, now.plusMinutes(1)))
    }
    @Test fun `custom snooze updates next time and tracks original time`() {
        val snoozed = TaskState.snooze(task, 17, now)
        assertEquals(now.plusMinutes(17), snoozed.dueAt)
        assertEquals(now, snoozed.snoozedAt)
        assertEquals(task.dueAt, snoozed.originalDueAt)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(ReminderPriority.P1, snoozed.priority)
    }
    @Test fun `repeated snooze retains original due time`() {
        val first = TaskState.snooze(task, 17, now)
        val second = TaskState.snooze(first, 63, now.plusMinutes(2))
        assertEquals(2, second.snoozeCount)
        assertEquals(task.dueAt, second.originalDueAt)
        assertEquals(now.plusMinutes(65), second.dueAt)
    }
    @Test fun `snooze across midnight moves task into tomorrow plan`() {
        val snoozed = TaskState.snooze(task, 90, now.withHour(23))
        assertEquals(now.toLocalDate().plusDays(1), snoozed.plannedFor)
        assertTrue(TaskQueries.forDay(listOf(snoozed), now.toLocalDate()).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun `completed tasks cannot snooze`() { TaskState.snooze(TaskState.complete(task, now), 5, now) }
    @Test(expected = IllegalArgumentException::class) fun `zero snooze is invalid`() { TaskState.snooze(task, 0, now) }
    @Test(expected = IllegalArgumentException::class) fun `negative snooze is invalid`() { TaskState.snooze(task, -5, now) }
    @Test(expected = IllegalArgumentException::class) fun `excessive snooze is invalid`() { TaskState.snooze(task, Int.MAX_VALUE, now) }
    @Test fun `edit changes name priority date and category without changing identity`() {
        val due = now.plusDays(1)
        val edited = TaskState.edited(task, "Read", ReminderPriority.P2, due, 30, TaskCategory.LEARNING, due.toLocalDate())
        assertEquals(task.id, edited.id); assertEquals("Read", edited.title)
        assertEquals(ReminderPriority.P2, edited.priority); assertEquals(TaskCategory.LEARNING, edited.category)
        assertEquals(due, edited.dueAt); assertEquals(due.toLocalDate(), edited.plannedFor)
    }
    @Test fun `editing time clears obsolete snooze label`() {
        val snoozed = TaskState.snooze(task, 5, now)
        val edited = TaskState.edited(snoozed, "New name", task.priority, now.plusHours(1), null, task.category, now.toLocalDate())
        assertNull(edited.snoozedAt); assertEquals(0, edited.snoozeCount)
    }
    @Test fun `title only edit preserves snooze track`() {
        val snoozed = TaskState.snooze(task, 17, now)
        val edited = TaskState.edited(snoozed, "New name", task.priority, snoozed.dueAt, null, task.category, now.toLocalDate())
        assertEquals(snoozed.snoozedAt, edited.snoozedAt); assertEquals(1, edited.snoozeCount)
    }
    @Test fun `today excludes future work and includes overdue work`() {
        val past = task.copy(id = "past", dueAt = now.minusDays(1), plannedFor = now.toLocalDate().minusDays(1))
        val future = task.copy(id = "future", dueAt = now.plusDays(1), plannedFor = now.toLocalDate().plusDays(1))
        assertEquals(setOf(task.id, past.id), TaskQueries.forDay(listOf(task, past, future), now.toLocalDate(), true).map { it.id }.toSet())
    }
    @Test fun `daily plan keeps date and category`() {
        val untimed = task.copy(dueAt = null, priority = ReminderPriority.P2, plannedFor = LocalDate.of(2026, 10, 20))
        assertEquals(listOf(untimed), TaskQueries.forDay(listOf(untimed), LocalDate.of(2026, 10, 20)))
    }
    @Test fun `matching completion prefers exact task and ignores completed work`() {
        val done = TaskState.complete(task, now)
        assertEquals(listOf(task), TaskQueries.completionMatches(listOf(done, task, task.copy(id = "other", title = "Finish report draft")), "the finish report"))
    }
    @Test fun `ambiguous completion returns all candidates`() {
        assertEquals(2, TaskQueries.completionMatches(listOf(task, task.copy(id = "other", title = "Report draft")), "report").size)
    }
}
