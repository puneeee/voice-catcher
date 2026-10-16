package com.puneeee.voicecatcher

import android.content.Context

object ActiveAlarmStore {
    private const val PREFERENCES = "voice_catcher_alarm"
    private const val TITLE = "active_title"
    private const val TASK_ID = "active_task_id"

    fun set(context: Context, title: String, taskId: String?) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(TITLE, title)
            .putString(TASK_ID, taskId)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(TITLE).remove(TASK_ID).apply()
    }

    fun title(context: Context): String? = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(TITLE, null)

    fun taskId(context: Context): String? = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(TASK_ID, null)
}
