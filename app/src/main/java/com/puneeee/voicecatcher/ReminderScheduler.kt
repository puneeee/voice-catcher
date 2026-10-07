package com.puneeee.voicecatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

object ReminderScheduler {
    fun schedule(context: Context, task: Task): Boolean {
        if (task.priority != ReminderPriority.P1 || task.dueAt == null) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return false
        val triggerAtMillis = task.dueAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context, task))
        return true
    }

    fun cancel(context: Context, task: Task) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, task))
    }

    private fun pendingIntent(context: Context, task: Task): PendingIntent = PendingIntent.getBroadcast(
        context,
        task.id.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TITLE, task.title)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
