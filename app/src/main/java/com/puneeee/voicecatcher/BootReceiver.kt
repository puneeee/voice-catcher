package com.puneeee.voicecatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore(context).load()
            .filter { it.status == TaskStatus.OPEN && it.priority == ReminderPriority.P1 }
            .forEach { ReminderScheduler.schedule(context, it) }
    }
}
