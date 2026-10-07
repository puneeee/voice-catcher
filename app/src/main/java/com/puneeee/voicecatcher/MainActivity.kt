package com.puneeee.voicecatcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
        setContent { MaterialTheme { VoiceCatcherApp() } }
    }

    override fun onResume() {
        super.onResume()
        TaskStore(this).load()
            .filter { it.status == TaskStatus.OPEN && it.priority == ReminderPriority.P1 }
            .forEach { ReminderScheduler.schedule(this, it) }
    }
}

@Composable
private fun VoiceCatcherApp() {
    val context = LocalContext.current
    val store = remember { TaskStore(context.applicationContext) }
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(store.load()) } }
    val captures = remember { mutableStateListOf<CaptureRecord>().apply { addAll(store.loadCaptures()) } }
    val locationCapture = remember { LocationCapture(context.applicationContext) }
    var voiceStatus by remember { mutableStateOf("Tap the microphone and say a task or reminder.") }
    var typedTask by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var exactAlarmNeeded by remember { mutableStateOf(false) }

    fun persist() = store.save(tasks)
    fun addTask(task: Task): Boolean {
        tasks.add(0, task)
        persist()
        return ReminderScheduler.schedule(context, task)
    }
    fun saveCapture(transcript: String, location: CaptureLocation?, outcome: String, priority: ReminderPriority, dueAt: LocalDateTime? = null) {
        val capture = CaptureRecord(transcript = transcript, capturedAt = LocalDateTime.now(), location = location, outcome = outcome)
        captures.add(0, capture)
        store.saveCaptures(captures)
        BackendSync.upload(capture, priority, dueAt)
    }
    fun handleTranscript(transcript: String, location: CaptureLocation?) {
        when (val action = VoiceCommandParser.parse(transcript)) {
            is VoiceAction.CreateTask -> {
                val scheduled = addTask(Task(title = action.title, priority = action.priority, dueAt = action.dueAt))
                exactAlarmNeeded = !scheduled
                voiceStatus = if (action.priority == ReminderPriority.P1) {
                    "Added alarm: ${action.title}. ${action.dueAt?.format(DateTimeFormatter.ofPattern("EEE h:mm a"))}."
                } else {
                    "Added to your to-do list: ${action.title}."
                }
                saveCapture(transcript, location, voiceStatus, action.priority, action.dueAt)
            }
            is VoiceAction.CompleteTask -> {
                val index = tasks.indexOfFirst { it.status == TaskStatus.OPEN && it.title.contains(action.query, ignoreCase = true) }
                if (index < 0) {
                    voiceStatus = "I could not find an open task matching ‘${action.query}’."
                } else {
                    tasks[index] = tasks[index].copy(status = TaskStatus.DONE)
                    persist()
                    voiceStatus = "Marked ‘${tasks[index].title}’ as done."
                }
                saveCapture(transcript, location, voiceStatus, ReminderPriority.P2)
            }
            is VoiceAction.Clarify -> {
                voiceStatus = action.message
                saveCapture(transcript, location, voiceStatus, ReminderPriority.P2)
            }
        }
    }
    val recognizer = remember {
        VoiceRecognizer(
            context.applicationContext,
            onResult = { transcript ->
                voiceStatus = "Saving the capture and its location…"
                locationCapture.capture { location -> handleTranscript(transcript, location) }
            },
            onState = { voiceStatus = it },
        )
    }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }
    val capturePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) recognizer.start()
        else voiceStatus = "Microphone permission is required for voice capture."
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceStatus = if (granted) "Notifications enabled. P1 alarms can alert you." else "Notifications are off, so alarms cannot appear as alerts."
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Voice Catcher", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Say it once. Keep your day clear.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Voice command", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(voiceStatus)
                        Button(onClick = {
                            val microphoneAllowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            val locationAllowed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (microphoneAllowed && locationAllowed) recognizer.start()
                            else capturePermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }) { Text("Speak now") }
                        Text("Try: “Remind me at 5 PM to have lunch” or “Add buy milk to my todo list.”", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = typedTask,
                    onValueChange = { typedTask = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Add a task without speaking") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (typedTask.isNotBlank()) {
                        addTask(Task(title = typedTask.trim(), priority = ReminderPriority.P2))
                        voiceStatus = "Added to your to-do list: ${typedTask.trim()}."
                        typedTask = ""
                    }
                }) { Text("Add task") }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search old voice notes, ideas, places, or dates") },
                    singleLine = true,
                )
            }
            val matches = captures.filter { capture ->
                searchQuery.isBlank() || capture.transcript.contains(searchQuery, ignoreCase = true) ||
                    capture.outcome.contains(searchQuery, ignoreCase = true) ||
                    capture.capturedAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy h:mm a")).contains(searchQuery, ignoreCase = true)
            }
            if (searchQuery.isNotBlank()) {
                item { Text("Voice history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                if (matches.isEmpty()) item { Text("No matching captured voice notes.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(matches, key = { it.id }) { capture -> CaptureHistoryCard(capture) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("${tasks.count { it.status == TaskStatus.OPEN }} open", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tasks.isEmpty()) item { Text("No tasks yet. Capture one by voice or add one above.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onComplete = {
                        ReminderScheduler.cancel(context, task)
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) tasks[index] = task.copy(status = TaskStatus.DONE)
                        persist()
                    },
                    onDelete = {
                        ReminderScheduler.cancel(context, task)
                        tasks.remove(task)
                        persist()
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                item { TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable reminder notifications") } }
            }
            if (exactAlarmNeeded) {
                item { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }) { Text("Allow exact alarms so your P1 reminder can ring") } }
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, onComplete: () -> Unit, onDelete: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${task.priority} · ${if (task.priority == ReminderPriority.P1) "Alarm" else "To-do"}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                task.dueAt?.let { Text(it.format(DateTimeFormatter.ofPattern("EEE, h:mm a")), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (task.status == TaskStatus.DONE) Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            else TextButton(onClick = onComplete) { Text("Done") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun CaptureHistoryCard(capture: CaptureRecord) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(capture.transcript, style = MaterialTheme.typography.titleMedium)
            Text(capture.capturedAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")), color = MaterialTheme.colorScheme.onSurfaceVariant)
            capture.location?.let { location ->
                Text(
                    "Location: %.5f, %.5f%s".format(
                        location.latitude,
                        location.longitude,
                        location.accuracyMeters?.let { " · ±${it.toInt()} m" }.orEmpty(),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text("Location was not available for this capture.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(capture.outcome, style = MaterialTheme.typography.bodySmall)
        }
    }
}
