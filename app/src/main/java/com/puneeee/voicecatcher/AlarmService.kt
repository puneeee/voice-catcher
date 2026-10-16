package com.puneeee.voicecatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm(markDone = true)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SNOOZE) {
            snoozeAlarm()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty().ifBlank { "Reminder" }
        val taskId = intent?.getStringExtra(ReminderReceiver.EXTRA_TASK_ID)
        stopSoundAndVibration()
        ActiveAlarmStore.set(this, title, taskId)
        startForeground(NOTIFICATION_ID, alarmNotification(title, taskId))
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).also {
            it.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            it.play()
        }
        vibrator = getSystemService(Vibrator::class.java).also {
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSoundAndVibration()
        ActiveAlarmStore.clear(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopAlarm(markDone: Boolean) {
        ActiveAlarmStore.taskId(this)?.let { taskId ->
            val store = TaskStore(this)
            store.load().firstOrNull { it.id == taskId }?.let { task ->
                ReminderScheduler.cancel(this, task)
                if (markDone) store.updateTask(task.copy(status = TaskStatus.DONE))
            }
        }
        stopSoundAndVibration()
        ActiveAlarmStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun snoozeAlarm() {
        ActiveAlarmStore.taskId(this)?.let { taskId ->
            val store = TaskStore(this)
            store.load().firstOrNull { it.id == taskId }?.let { task ->
                val snoozed = task.copy(dueAt = java.time.LocalDateTime.now().plusMinutes(SNOOZE_MINUTES))
                store.updateTask(snoozed)
                ReminderScheduler.schedule(this, snoozed)
            }
        }
        stopSoundAndVibration()
        ActiveAlarmStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSoundAndVibration() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun alarmNotification(title: String, taskId: String?): android.app.Notification {
        ensureChannel(this)
        val requestCode = taskId?.hashCode() ?: 0
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, AlarmActivity::class.java)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            requestCode + 1,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getService(
            this,
            requestCode + 2,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("P1 alarm")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(fullScreenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop alarm", stopIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 5 min", snoozeIntent)
        if (ReminderSetup.fullScreenAlarmsAllowed(this)) builder.setFullScreenIntent(fullScreenIntent, true)
        return builder.build()
    }

    companion object {
        const val ACTION_STOP = "com.puneeee.voicecatcher.STOP_ALARM"
        const val ACTION_SNOOZE = "com.puneeee.voicecatcher.SNOOZE_ALARM"
        const val CHANNEL_ID = "p1_alarms"
        private const val NOTIFICATION_ID = 101
        private const val SNOOZE_MINUTES = 5L

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "P1 alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ringing alarms created by Voice Catcher"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }
}
