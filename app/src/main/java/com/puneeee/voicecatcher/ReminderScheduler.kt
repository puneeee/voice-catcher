package com.puneeee.voicecatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import java.time.ZoneId

object ReminderScheduler {
    enum class Result { SCHEDULED, NOTIFICATIONS_DISABLED, EXACT_ALARMS_DISABLED, PAST_DUE, NOT_REQUIRED }

    fun schedule(context: Context, task: Task): Result {
        if (task.dueAt == null || task.status == TaskStatus.DONE) return Result.NOT_REQUIRED
        if (!ReminderSetup.notificationsAllowed(context)) return Result.NOTIFICATIONS_DISABLED
        if (task.priority == ReminderPriority.P1) AlarmService.ensureChannel(context) else ReminderReceiver.ensureChannel(context)
        val channelId = if (task.priority == ReminderPriority.P1) AlarmService.CHANNEL_ID else ReminderReceiver.P2_CHANNEL_ID
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        if (notifications.getNotificationChannel(channelId)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return Result.NOTIFICATIONS_DISABLED
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAtMillis = task.dueAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAtMillis <= System.currentTimeMillis()) return Result.PAST_DUE
        if (task.priority == ReminderPriority.P1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return Result.EXACT_ALARMS_DISABLED
        }
        if (task.priority == ReminderPriority.P1) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, openAppIntent(context)),
                pendingIntent(context, task),
            )
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context, task))
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context, task))
            }
        }
        return Result.SCHEDULED
    }

    fun rearmOpen(context: Context) {
        val store = TaskStore(context)
        val now = java.time.LocalDateTime.now()
        store.load().filter { it.status == TaskStatus.OPEN && it.dueAt != null }.forEach { task ->
            val updated = if (task.priority == ReminderPriority.P2 && !task.dueAt!!.isAfter(now) && task.repeatMinutes != null)
                task.copy(dueAt = now.plusMinutes(task.repeatMinutes.toLong())) else task
            if (updated != task) store.updateTask(updated)
            if (updated.dueAt!!.isAfter(now)) schedule(context, updated)
        }
    }

    fun cancel(context: Context, task: Task) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pendingIntent(context, task))
        // Cancel pre-upgrade identities too, so edits do not leave an old alarm behind.
        val legacy = PendingIntent.getBroadcast(context, task.id.hashCode(), Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (legacy != null) { manager.cancel(legacy); legacy.cancel() }
    }

    private fun pendingIntent(context: Context, task: Task): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("voicecatcher://reminder/${task.id}"))
            .putExtra(ReminderReceiver.EXTRA_TITLE, task.title)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        context.packageManager.getLaunchIntentForPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
