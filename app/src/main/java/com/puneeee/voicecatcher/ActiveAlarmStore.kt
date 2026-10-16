package com.puneeee.voicecatcher

import android.content.Context

object ActiveAlarmStore {
    private const val PREFERENCES = "voice_catcher_alarm"
    private const val TITLE = "active_title"

    fun set(context: Context, title: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(TITLE, title).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(TITLE).apply()
    }

    fun title(context: Context): String? = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(TITLE, null)
}
