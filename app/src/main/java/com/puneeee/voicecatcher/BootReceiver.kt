package com.puneeee.voicecatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyBriefScheduler.reschedule(context)
        TaskStore(context).load()
            .filter { it.status == TaskStatus.OPEN && it.dueAt != null && it.dueAt.isAfter(java.time.LocalDateTime.now()) }
            .forEach { ReminderScheduler.schedule(context, it) }
    }
}
