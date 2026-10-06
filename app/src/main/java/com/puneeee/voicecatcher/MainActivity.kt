package com.puneeee.voicecatcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VoiceCatcherApp()
            }
        }
    }
}

@Composable
private fun VoiceCatcherApp() {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    val tasks = remember {
        mutableStateListOf(
            Task(title = "Have lunch", priority = ReminderPriority.P1, dueAt = LocalDateTime.now().withHour(17).withMinute(0)),
            Task(title = "Review today’s priorities", priority = ReminderPriority.P2),
        )
    }
    var captureState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        captureState = if (granted) {
            recorder.start().fold(
                onSuccess = { CaptureState.Recording },
                onFailure = { CaptureState.Failed(it.message ?: "Could not start recording.") },
            )
        } else {
            CaptureState.Failed("Microphone access is needed to capture a voice note.")
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Voice Catcher", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Capture it now. Your day stays clear.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                CaptureCard(
                    state = captureState,
                    onStart = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            recorder.start().fold(
                                onSuccess = { captureState = CaptureState.Recording },
                                onFailure = { captureState = CaptureState.Failed(it.message ?: "Could not start recording.") },
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStop = {
                        recorder.stop().fold(
                            onSuccess = { captureState = CaptureState.Saved(it.name) },
                            onFailure = { captureState = CaptureState.Failed(it.message ?: "Could not save recording.") },
                        )
                    },
                )
            }
            item { Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(tasks, key = { it.id }) { task ->
                TaskCard(task = task, onComplete = {
                    val index = tasks.indexOfFirst { it.id == task.id }
                    if (index >= 0) tasks[index] = task.copy(status = TaskStatus.DONE)
                })
            }
            item {
                Text(
                    "Next: secure transcription, AI action review, exact alarms, and WhatsApp delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(state: CaptureState, onStart: () -> Unit, onStop: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Voice note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when (state) {
                    CaptureState.Idle -> "Speak naturally: “Remind me at 5 PM to have lunch.”"
                    CaptureState.Recording -> "Recording locally… tap stop when you’re finished."
                    is CaptureState.Saved -> "Saved ${state.fileName}. It will be ready for secure processing in the next milestone."
                    is CaptureState.Failed -> state.message
                },
            )
            if (state == CaptureState.Recording) {
                Button(onClick = onStop) { Text("Stop and save") }
            } else {
                Button(onClick = onStart) { Text("Capture voice note") }
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, onComplete: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${task.priority} · ${if (task.priority == ReminderPriority.P1) "Alarm" else "Gentle reminder"}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                task.dueAt?.let {
                    Text(it.format(DateTimeFormatter.ofPattern("EEE, h:mm a")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (task.status == TaskStatus.DONE) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                TextButton(onClick = onComplete) { Text("Done") }
            }
        }
    }
}
