package com.puneeee.voicecatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java)
                .putExtra(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE))
                .putExtra(EXTRA_TASK_ID, intent.getStringExtra(EXTRA_TASK_ID)),
        )
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TASK_ID = "task_id"
    }
}
