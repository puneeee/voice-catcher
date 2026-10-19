package com.puneeee.voicecatcher

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TaskCodecTest {
    private val now = LocalDateTime.of(2026, 10, 19, 12, 0)
    @Test fun `legacy encrypted payload contents migrate with defaults`() {
        val legacy = """[{"id":"existing","title":"Call","priority":"P1","dueAt":"2026-10-19T12:30","status":"OPEN","repeatMinutes":null}]"""
        val task = TaskCodec.decode(legacy).single()
        assertEquals("existing", task.id); assertEquals(TaskCategory.GENERAL, task.category)
        assertEquals(now.toLocalDate(), task.plannedFor); assertNull(task.completedAt); assertEquals(0, task.snoozeCount)
    }
    @Test fun `snooze category and time survive restart serialization`() {
        val task = TaskState.snooze(Task(title = "Stretch", priority = ReminderPriority.P1, dueAt = now, category = TaskCategory.HEALTH), 37, now)
        assertEquals(task, TaskCodec.decode(TaskCodec.encode(listOf(task))).single())
    }
    @Test fun `completion survives serialization`() {
        val task = TaskState.complete(Task(title = "Finish", priority = ReminderPriority.P2), now)
        assertEquals(task, TaskCodec.decode(TaskCodec.encode(listOf(task))).single())
    }
    @Test fun `unknown category preserves record`() {
        val task = TaskCodec.decode("""[{"id":"a","title":"Task","priority":"P2","status":"OPEN","category":"OLD_CATEGORY"}]""").single()
        assertEquals(TaskCategory.GENERAL, task.category)
    }
}
