package com.puneeee.voicecatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID) ?: ActiveAlarmStore.taskId(this)
        val task = TaskStore(this).load().find { it.id == id && it.status == TaskStatus.OPEN }
        if (task == null || ActiveAlarmStore.taskId(this) != id) { finish(); return }
        setContent { VoiceTheme {
            var picker by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_PICK_SNOOZE, false)) }
            var error by remember { mutableStateOf<String?>(null) }
            DisposableEffect(id) {
                val listener = TaskStore(this@AlarmActivity).observe {
                    val current = TaskStore(this@AlarmActivity).load().find { it.id == id }
                    if (current == null || current.status == TaskStatus.DONE) finish()
                }
                onDispose { listener.close() }
            }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("P1 · TIME TO ACT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(task.title, style = MaterialTheme.typography.headlineLarge)
                    task.dueAt?.let { Text(it.format(taskTimeFormat), style = MaterialTheme.typography.titleMedium) }
                    Button(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text("Snooze · choose duration") }
                    FilledTonalButton(onClick = {
                        runCatching { TaskOperations.complete(this@AlarmActivity, task.id) }.onSuccess { finish() }.onFailure { error = it.message }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Stop & mark done") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (picker) SnoozeDialog(task.title, ReminderPreferences.snoozeMinutes(this@AlarmActivity), onDismiss = { picker = false }) { minutes, rememberDefault ->
                try {
                    val result = TaskOperations.snooze(this@AlarmActivity, task.id, minutes)
                    if (result == ReminderScheduler.Result.SCHEDULED) {
                        if (rememberDefault) ReminderPreferences.setSnoozeMinutes(this@AlarmActivity, minutes)
                        finish(); null
                    } else result.message()
                } catch (failure: Exception) { failure.message ?: "Could not snooze. The alarm has not been dismissed." }
            }
        } }
    }
    companion object { const val EXTRA_PICK_SNOOZE = "pick_snooze_duration" }
}
