package com.puneeee.voicecatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DailyBriefScheduler {
    private const val PREFERENCES = "voice_catcher_daily_briefs"
    private const val MORNING_KEY = "morning_time"
    private const val EVENING_KEY = "evening_time"

    fun times(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val morning = prefs.getString(MORNING_KEY, null) ?: return null
        val evening = prefs.getString(EVENING_KEY, null) ?: return null
        return morning to evening
    }

    fun saveAndSchedule(context: Context, morning: String, evening: String): String? {
        val morningTime = parseTime(morning) ?: return "Use 24-hour time such as 07:00 for the morning plan."
        val eveningTime = parseTime(evening) ?: return "Use 24-hour time such as 20:00 for the evening review."
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(MORNING_KEY, morning).putString(EVENING_KEY, evening).apply()
        schedule(context, DailyBriefReceiver.ACTION_MORNING, morningTime)
        schedule(context, DailyBriefReceiver.ACTION_EVENING, eveningTime)
        return null
    }

    fun reschedule(context: Context) {
        times(context)?.let { (morning, evening) ->
            parseTime(morning)?.let { schedule(context, DailyBriefReceiver.ACTION_MORNING, it) }
            parseTime(evening)?.let { schedule(context, DailyBriefReceiver.ACTION_EVENING, it) }
        }
    }

    fun disable(context: Context) {
        listOf(DailyBriefReceiver.ACTION_MORNING, DailyBriefReceiver.ACTION_EVENING).forEach { action ->
            val pending = PendingIntent.getBroadcast(context, action.hashCode(), Intent(context, DailyBriefReceiver::class.java).setAction(action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) { context.getSystemService(AlarmManager::class.java).cancel(pending); pending.cancel() }
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun schedule(context: Context, action: String, time: LocalTime) {
        if (!ReminderSetup.notificationsAllowed(context)) return
        val now = LocalDateTime.now()
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, DailyBriefReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun parseTime(value: String): LocalTime? = runCatching {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()
}
