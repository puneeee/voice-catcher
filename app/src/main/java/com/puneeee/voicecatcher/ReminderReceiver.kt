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
import androidx.core.content.ContextCompat
import java.time.LocalDateTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val store = TaskStore(context)
        val task = store.load().firstOrNull { it.id == taskId } ?: return
        if (intent.action == ACTION_DONE) {
            ReminderScheduler.cancel(context, task)
            store.updateTask(task.copy(status = TaskStatus.DONE))
            NotificationManagerCompat.from(context).cancel(task.id.hashCode())
            return
        }
        if (task.status == TaskStatus.DONE) return
        if (task.priority == ReminderPriority.P1) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .putExtra(EXTRA_TITLE, task.title)
                    .putExtra(EXTRA_TASK_ID, task.id),
            )
            return
        }
        showRepeatingNotification(context, task)
        task.repeatMinutes?.let { interval ->
            val updated = task.copy(dueAt = LocalDateTime.now().plusMinutes(interval.toLong()))
            store.updateTask(updated)
            ReminderScheduler.schedule(context, updated)
        }
    }

    private fun showRepeatingNotification(context: Context, task: Task) {
        createChannel(context)
        val doneIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_DONE)
                .putExtra(EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, P2_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Still pending")
            .setContentText(task.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_save, "Done", doneIntent)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(P2_CHANNEL_ID, "Repeating reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "P2 reminders that repeat until marked done"
            },
        )
    }

    companion object {
        const val ACTION_DONE = "com.puneeee.voicecatcher.DONE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TASK_ID = "task_id"
        private const val P2_CHANNEL_ID = "p2_reminders"
    }
}
