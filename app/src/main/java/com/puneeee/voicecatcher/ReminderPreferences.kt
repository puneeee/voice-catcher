package com.puneeee.voicecatcher

import android.content.Context

object ReminderPreferences {
    private const val STORE = "voice_catcher_preferences"
    fun snoozeMinutes(context: Context): Int = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getInt("snooze_minutes", 5)
    fun setSnoozeMinutes(context: Context, minutes: Int) {
        require(minutes in 1..TaskState.MAX_SNOOZE_MINUTES)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putInt("snooze_minutes", minutes).apply()
    }
}
