package com.puneeee.voicecatcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
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
            .filter { task ->
                task.status == TaskStatus.OPEN && task.dueAt != null &&
                    (task.priority == ReminderPriority.P2 || task.dueAt.isAfter(LocalDateTime.now()))
            }
            .forEach { ReminderScheduler.schedule(this, it) }
    }
}

@Composable
private fun VoiceCatcherApp() {
    val context = LocalContext.current
    val store = remember { TaskStore(context.applicationContext) }
    val tasks = remember {
        val saved = store.load()
        val migrated = saved.map { task ->
            if (task.priority != ReminderPriority.P2) task else {
                when (val action = VoiceCommandParser.parse(task.title)) {
                    is VoiceAction.CreateTask -> if (action.priority == ReminderPriority.P1) {
                        task.copy(title = action.title, priority = ReminderPriority.P1, dueAt = action.dueAt, repeatMinutes = null)
                    } else task
                    else -> task
                }
            }
        }
        if (migrated != saved) store.save(migrated)
        mutableStateListOf<Task>().apply { addAll(migrated) }
    }
    val captures = remember { mutableStateListOf<CaptureRecord>().apply { addAll(store.loadCaptures()) } }
    val locationCapture = remember { LocationCapture(context.applicationContext) }
    var voiceStatus by remember { mutableStateOf("Tap the microphone and say a task or reminder.") }
    var typedTask by remember { mutableStateOf("") }
    var repeatMinutesText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var reminderSetupMessage by remember { mutableStateOf<String?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var eraseConfirmationNeeded by remember { mutableStateOf(false) }
    val savedDailyTimes = remember { DailyBriefScheduler.times(context) }
    var morningPlanTime by remember { mutableStateOf(savedDailyTimes?.first ?: "07:00") }
    var eveningReviewTime by remember { mutableStateOf(savedDailyTimes?.second ?: "20:00") }

    fun persist() = store.save(tasks)
    fun addTask(task: Task): ReminderScheduler.Result {
        tasks.add(0, task)
        persist()
        return ReminderScheduler.schedule(context, task)
    }
    fun saveCapture(transcript: String, location: CaptureLocation?, outcome: String) {
        val capture = CaptureRecord(transcript = transcript, capturedAt = LocalDateTime.now(), location = location, outcome = outcome)
        captures.add(0, capture)
        store.saveCaptures(captures)
    }
    fun handleTranscript(transcript: String, location: CaptureLocation?) {
        when (val action = VoiceCommandParser.parse(transcript)) {
            is VoiceAction.CreateTask -> {
                val scheduled = addTask(Task(title = action.title, priority = action.priority, dueAt = action.dueAt, repeatMinutes = action.repeatMinutes))
                reminderSetupMessage = when (scheduled) {
                    ReminderScheduler.Result.NOTIFICATIONS_DISABLED -> "Enable notifications before this reminder can alert you."
                    ReminderScheduler.Result.EXACT_ALARMS_DISABLED -> "Allow exact alarms before this P1 alarm can ring."
                    ReminderScheduler.Result.PAST_DUE -> "That P1 time has already passed, so it was not armed."
                    else -> null
                }
                voiceStatus = if (action.priority == ReminderPriority.P1) {
                    if (scheduled == ReminderScheduler.Result.SCHEDULED) "Alarm set: ${action.title}. ${action.dueAt?.format(DateTimeFormatter.ofPattern("EEE h:mm a"))}."
                    else "Alarm saved but not armed. ${reminderSetupMessage.orEmpty()}"
                } else {
                    "Added to your to-do list: ${action.title}."
                }
                saveCapture(transcript, location, voiceStatus)
            }
            is VoiceAction.CompleteTask -> {
                val index = tasks.indexOfFirst { it.status == TaskStatus.OPEN && it.title.contains(action.query, ignoreCase = true) }
                if (index < 0) {
                    voiceStatus = "I could not find an open task matching ‘${action.query}’."
                } else {
                    ReminderScheduler.cancel(context, tasks[index])
                    tasks[index] = tasks[index].copy(status = TaskStatus.DONE)
                    persist()
                    voiceStatus = "Marked ‘${tasks[index].title}’ as done."
                }
                saveCapture(transcript, location, voiceStatus)
            }
            is VoiceAction.Clarify -> {
                voiceStatus = action.message
                saveCapture(transcript, location, voiceStatus)
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
            onCaptureState = { isSpeaking = it },
        )
    }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }
    val capturePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) recognizer.start()
        else voiceStatus = "Microphone permission is required for voice capture."
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceStatus = if (granted) "Notifications enabled. Open reminders have been armed." else "Notifications are off, so alarms cannot appear as alerts."
        if (granted) {
            tasks.filter { it.status == TaskStatus.OPEN && it.dueAt != null && (it.priority == ReminderPriority.P2 || it.dueAt.isAfter(LocalDateTime.now())) }
                .forEach { ReminderScheduler.schedule(context, it) }
            reminderSetupMessage = null
        }
    }

    LaunchedEffect(Unit) {
        AlarmService.ensureChannel(context)
        ReminderReceiver.ensureChannel(context)
        DailyBriefReceiver.ensureChannel(context)
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
                TextButton(onClick = { AlarmService.stop(context) }) { Text("Stop a ringing alarm") }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Alarm safety check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        when {
                            !ReminderSetup.notificationsAllowed(context) -> {
                                Text("Notifications are disabled. P1 alarms must not be armed until alerts are allowed.")
                                Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable notifications") }
                            }
                            !ReminderSetup.exactAlarmsAllowed(context) -> {
                                Text("Exact alarms are disabled. P1 cannot ring at the requested time yet.")
                                Button(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }) { Text("Allow exact alarms") }
                            }
                            !ReminderSetup.fullScreenAlarmsAllowed(context) -> {
                                Text("Full-screen alarm controls are disabled. The notification will still show, but unlock may be required.")
                                Button(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:${context.packageName}")))
                                }) { Text("Allow full-screen alarms") }
                            }
                            else -> Text("P1 alarm alerts, stop, and snooze controls are ready.")
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID))
                        }) { Text("Check P1 alarm notification settings") }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Daily planning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Receive a local morning plan and evening task review. Use 24-hour times.")
                        OutlinedTextField(value = morningPlanTime, onValueChange = { morningPlanTime = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Morning plan time, e.g. 07:00") }, singleLine = true)
                        OutlinedTextField(value = eveningReviewTime, onValueChange = { eveningReviewTime = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Evening review time, e.g. 20:00") }, singleLine = true)
                        Button(onClick = {
                            reminderSetupMessage = DailyBriefScheduler.saveAndSchedule(context, morningPlanTime, eveningReviewTime)
                            if (reminderSetupMessage == null) voiceStatus = "Daily plan and evening review are scheduled."
                        }) { Text("Save daily times") }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Voice command", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(voiceStatus)
                        Button(onClick = {
                            if (isSpeaking) {
                                recognizer.finish()
                            } else {
                                val microphoneAllowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                val locationAllowed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (microphoneAllowed && locationAllowed) recognizer.start()
                                else capturePermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                        }) { Text(if (isSpeaking) "Finish speaking" else "Start speaking") }
                        Text("You may pause naturally. It waits five seconds before submitting, or use Finish speaking.", style = MaterialTheme.typography.bodySmall)
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
                        val interval = repeatMinutesText.toIntOrNull()?.takeIf { it in 1..(24 * 60) }
                        val result = addTask(Task(
                            title = typedTask.trim(),
                            priority = ReminderPriority.P2,
                            dueAt = interval?.let { LocalDateTime.now().plusMinutes(it.toLong()) },
                            repeatMinutes = interval,
                        ))
                        voiceStatus = if (interval == null) "Added to your to-do list: ${typedTask.trim()}." else "Reminder repeats every $interval minutes until done."
                        if (result == ReminderScheduler.Result.NOTIFICATIONS_DISABLED) reminderSetupMessage = "Enable notifications for the repeating reminder."
                        typedTask = ""
                        repeatMinutesText = ""
                    }
                }) { Text("Add task") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeatMinutesText,
                    onValueChange = { repeatMinutesText = it.filter { char -> char.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repeat every N minutes (optional)") },
                    singleLine = true,
                )
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
                val query = searchQuery.trim()
                query.isBlank() || capture.transcript.contains(query, ignoreCase = true) ||
                    capture.outcome.contains(searchQuery, ignoreCase = true) ||
                    capture.capturedAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy h:mm a")).contains(query, ignoreCase = true) ||
                    capture.location?.placeLabel?.contains(query, ignoreCase = true) == true ||
                    capture.location?.let { "${it.latitude}, ${it.longitude}".contains(query, ignoreCase = true) } == true ||
                    (query.equals("today", ignoreCase = true) && capture.capturedAt.toLocalDate() == java.time.LocalDate.now()) ||
                    (query.equals("yesterday", ignoreCase = true) && capture.capturedAt.toLocalDate() == java.time.LocalDate.now().minusDays(1))
            }
            if (searchQuery.isNotBlank()) {
                item { Text("Voice history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                if (matches.isEmpty()) item { Text("No matching captured voice notes.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(matches, key = { it.id }) { capture -> CaptureHistoryCard(capture) }
            } else if (captures.isNotEmpty()) {
                item { Text("Recent voice history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(captures.take(5), key = { it.id }) { capture -> CaptureHistoryCard(capture) }
            }
            reminderSetupMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Your privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Transcripts and saved locations are encrypted on this phone. Device backup is disabled. Location is optional and is used only when you record a voice capture.")
                        if (eraseConfirmationNeeded) {
                            Text("This permanently erases all tasks and voice history from this phone.", color = MaterialTheme.colorScheme.error)
                            Button(onClick = {
                                tasks.forEach { ReminderScheduler.cancel(context, it) }
                                tasks.clear()
                                captures.clear()
                                store.clearTasks()
                                store.clearCaptures()
                                eraseConfirmationNeeded = false
                                voiceStatus = "All saved tasks and voice history were erased."
                            }) { Text("Confirm erase all data") }
                            TextButton(onClick = { eraseConfirmationNeeded = false }) { Text("Cancel") }
                        } else {
                            TextButton(onClick = { eraseConfirmationNeeded = true }) { Text("Erase all saved data") }
                        }
                    }
                }
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
                task.repeatMinutes?.let { Text("Repeats every $it minutes until done", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    "Location: %s · %.5f, %.5f%s".format(
                        location.placeLabel ?: "Unlabelled place",
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
