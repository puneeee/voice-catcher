package com.puneeee.voicecatcher

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDateTime

/** All entry points use the same durable updates; screens observe the encrypted store. */
object TaskOperations {
    fun save(context: Context, task: Task): ReminderScheduler.Result {
        val store = TaskStore(context)
        store.upsert(task)
        ReminderScheduler.cancel(context, task)
        dismiss(context, task.id)
        return ReminderScheduler.schedule(context, task)
    }
    fun complete(context: Context, id: String) {
        val task = TaskStore(context).mutateTask(id) { TaskState.complete(it, LocalDateTime.now()) } ?: return
        ReminderScheduler.cancel(context, task)
        dismiss(context, id)
    }
    fun delete(context: Context, id: String) {
        val task = TaskStore(context).load().find { it.id == id } ?: return
        TaskStore(context).deleteTask(id)
        ReminderScheduler.cancel(context, task)
        dismiss(context, id)
    }
    fun snooze(context: Context, id: String, minutes: Int): ReminderScheduler.Result {
        val store = TaskStore(context)
        val task = store.load().find { it.id == id } ?: throw IllegalArgumentException("This task no longer exists.")
        val updated = TaskState.snooze(task, minutes, LocalDateTime.now())
        val scheduled = ReminderScheduler.schedule(context, updated)
        if (scheduled != ReminderScheduler.Result.SCHEDULED) return scheduled
        try { store.upsert(updated) }
        catch (error: Exception) { ReminderScheduler.cancel(context, updated); throw error }
        dismiss(context, id)
        return scheduled
    }
    private fun dismiss(context: Context, id: String) {
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        AlarmService.silence(context, id)
    }
}
