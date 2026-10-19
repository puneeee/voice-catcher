package com.puneeee.voicecatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class DailyBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderSetup.notificationsAllowed(context)) return
        ensureChannel(context)
        val tasks = TaskStore(context).load()
        val today = java.time.LocalDate.now()
        val openTasks = TaskQueries.forDay(tasks, today, includeOverdue = true)
        val morning = intent.action == ACTION_MORNING
        val title = if (morning) "Good morning — your plan" else "Evening review"
        val taskPreview = openTasks.take(3).joinToString { it.title }.ifBlank { "No open tasks" }
        val message = if (morning) "$taskPreview${if (openTasks.size > 3) " and ${openTasks.size - 3} more" else ""}" else "${tasks.count { it.status == TaskStatus.DONE && it.completedAt?.toLocalDate() == today }} completed today · ${openTasks.size} still open. Review your plan or move tasks to tomorrow."
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, "PLAN"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            if (morning) MORNING_NOTIFICATION_ID else EVENING_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val ACTION_MORNING = "com.puneeee.voicecatcher.DAILY_MORNING"
        const val ACTION_EVENING = "com.puneeee.voicecatcher.DAILY_EVENING"
        private const val CHANNEL_ID = "daily_briefs"
        private const val MORNING_NOTIFICATION_ID = 301
        private const val EVENING_NOTIFICATION_ID = 302

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily planning", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Your morning plan and evening review"
                },
            )
        }
    }
}
