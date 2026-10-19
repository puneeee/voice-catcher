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
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.getStringExtra(ReminderReceiver.EXTRA_TASK_ID)
        if (intent?.action in setOf(ACTION_STOP, ACTION_SILENCE, ACTION_SNOOZE)) {
            val activeId = ActiveAlarmStore.taskId(this)
            if (target != null && target != activeId) return START_NOT_STICKY
            if (intent?.action == ACTION_STOP && activeId != null) TaskOperations.complete(this, activeId)
            else if (intent?.action == ACTION_SNOOZE && activeId != null) {
                // Compatibility with notifications created before the upgrade.
                val result = TaskOperations.snooze(this, activeId, ReminderPreferences.snoozeMinutes(this))
                if (result != ReminderScheduler.Result.SCHEDULED) return START_NOT_STICKY
            }
            finishAlarm()
            return START_NOT_STICKY
        }
        val task = TaskStore(this).load().find { it.id == target && it.status == TaskStatus.OPEN }
        if (task == null) { stopSelf(); return START_NOT_STICKY }
        stopSoundAndVibration()
        ActiveAlarmStore.set(this, task.title, task.id)
        startForeground(NOTIFICATION_ID, alarmNotification(task))
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.also {
            it.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
            it.play()
        }
        vibrator = getSystemService(Vibrator::class.java)?.also {
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { stopSoundAndVibration(); ActiveAlarmStore.clear(this); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun finishAlarm() {
        stopSoundAndVibration(); ActiveAlarmStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    private fun stopSoundAndVibration() { ringtone?.stop(); ringtone = null; vibrator?.cancel(); vibrator = null }

    private fun alarmNotification(task: Task): android.app.Notification {
        ensureChannel(this)
        fun activity(snooze: Boolean): PendingIntent = PendingIntent.getActivity(this, if (snooze) 1 else 0,
            Intent(this, AlarmActivity::class.java).setData(Uri.parse("voicecatcher://alarm/${task.id}/${if (snooze) "snooze" else "open"}"))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id).putExtra(ReminderReceiver.EXTRA_TITLE, task.title)
                .putExtra(AlarmActivity.EXTRA_PICK_SNOOZE, snooze), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = activity(false)
        val done = PendingIntent.getService(this, 0,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP).setData(Uri.parse("voicecatcher://alarm/${task.id}/done"))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("P1 · ${task.title}").setContentText("Open to choose a snooze duration or mark done")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_save, "Mark done", done)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze…", activity(true))
            .apply { if (ReminderSetup.fullScreenAlarmsAllowed(this@AlarmService)) setFullScreenIntent(open, true) }.build()
    }

    companion object {
        const val ACTION_STOP = "com.puneeee.voicecatcher.STOP_ALARM"
        const val ACTION_SNOOZE = "com.puneeee.voicecatcher.SNOOZE_ALARM"
        const val ACTION_SILENCE = "com.puneeee.voicecatcher.SILENCE_ALARM"
        const val CHANNEL_ID = "p1_alarms"
        private const val NOTIFICATION_ID = 101
        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "P1 alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ringing alarms created by Voice Catcher"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(true); lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        fun stop(context: Context) {
            val id = ActiveAlarmStore.taskId(context) ?: return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP).putExtra(ReminderReceiver.EXTRA_TASK_ID, id))
        }
        fun silence(context: Context, taskId: String) {
            if (ActiveAlarmStore.taskId(context) == taskId) context.startService(Intent(context, AlarmService::class.java)
                .setAction(ACTION_SILENCE).putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId))
        }
    }
}
