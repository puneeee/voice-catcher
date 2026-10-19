package com.puneeee.voicecatcher

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomePage(tasks: List<Task>, voiceStatus: String, speaking: Boolean, onSpeak: () -> Unit,
             onCommand: (String) -> Unit, onNew: () -> Unit, actions: TaskUiActions, onSettings: () -> Unit) {
    var command by rememberSaveable { mutableStateOf("") }
    var showCommand by rememberSaveable { mutableStateOf(false) }
    var taskFilter by rememberSaveable { mutableStateOf("Today") }
    val today = LocalDate.now()
    val allOpen = tasks.filter { it.status == TaskStatus.OPEN }.sortedBy { it.dueAt ?: java.time.LocalDateTime.MAX }
    val open = when (taskFilter) {
        "All" -> allOpen
        "P1 alarms" -> allOpen.filter { it.priority == ReminderPriority.P1 }
        "P2 notifications" -> allOpen.filter { it.priority == ReminderPriority.P2 }
        else -> TaskQueries.forDay(tasks, today, includeOverdue = true)
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Your tasks", today.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM"))) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(voiceStatus, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onSpeak, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(if (speaking) Icons.Outlined.Stop else Icons.Outlined.Mic, null)
                        Spacer(Modifier.width(8.dp)); Text(if (speaking) "Finish speaking" else "Start speaking")
                    }
                }
            }
        }
        item {
            if (showCommand) OutlinedTextField(command, { command = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Type a command") }, maxLines = 3)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (showCommand) TextButton(onClick = { if (command.isNotBlank()) { onCommand(command); command = "" } }, enabled = command.isNotBlank()) { Text("Use command") }
                else TextButton(onClick = { showCommand = true }) { Text("Type instead") }
                FilledTonalButton(onClick = onNew) { Icon(Icons.Outlined.Add, null); Text("Add task") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(taskFilter, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${open.size} open · ${open.count { it.priority == ReminderPriority.P1 }} urgent", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = onSettings) { Text("Alert settings") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Today", "All", "P1 alarms", "P2 notifications").forEach { filter ->
                    FilterChip(taskFilter == filter, { taskFilter = filter }, label = { Text(filter) })
                }
            }
            Text("All, P1 and P2 include future dates. Edit a task to change its alert time.", style = MaterialTheme.typography.bodySmall)
        }
        if (open.isEmpty()) item { EmptyCard("No open tasks in this view", "Try All to include future tasks, or add a task. Finished work lives in History → Completed.") }
        items(open, key = { it.id }) { TaskCard(it, actions) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanPage(tasks: List<Task>, actions: TaskUiActions, onNew: (LocalDate) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var categoryName by rememberSaveable { mutableStateOf("ALL") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val day = LocalDate.parse(dayText)
    val planned = TaskQueries.forDay(tasks, day)
    val selected = planned.filter { categoryName == "ALL" || it.category.name == categoryName }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Your daily plan", "Choose a day. Group the work. Leave space to breathe.") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(day == LocalDate.now(), { dayText = LocalDate.now().toString() }, label = { Text("Today") })
                FilterChip(day == LocalDate.now().plusDays(1), { dayText = LocalDate.now().plusDays(1).toString() }, label = { Text("Tomorrow") })
                OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> dayText = LocalDate.of(y, m + 1, d).toString() }, day.year, day.monthValue - 1, day.dayOfMonth).show() }) { Text("Date…") }
            }
            Text(day.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showGuide = !showGuide }) { Text("How to organize your day") }
                    if (showGuide) Text("1. Add a task for this date.\n2. Choose Work, Personal, Health or another category.\n3. Add a time and P1/P2 if you need an alert.\n4. Tap Done when finished; it moves to Completed.")
                    Button(onClick = { onNew(day) }) { Text("Add to ${day.format(DateTimeFormatter.ofPattern("dd MMM"))}") }
                    if (showGuide) TextButton(onClick = onSettings) { Text("Set morning plan & evening review times") }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(categoryName == "ALL", { categoryName = "ALL" }, label = { Text("All · ${planned.size}") })
            TaskCategory.entries.forEach { category -> FilterChip(categoryName == category.name, { categoryName = category.name },
                label = { Text("${category.label} · ${planned.count { it.category == category }}") }) }
        } }
        if (selected.isEmpty()) item { EmptyCard("Nothing planned here yet", "Add a task above, or edit an existing task to move it to this date and category.") }
        TaskCategory.entries.forEach { category ->
            val group = selected.filter { it.category == category }
            if (group.isNotEmpty()) {
                item(key = "heading-${category.name}") { Text(category.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(group, key = { it.id }) { TaskCard(it, actions) }
            }
        }
    }
}

@Composable
fun SearchPage(tasks: List<Task>, captures: List<CaptureRecord>, actions: TaskUiActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val taskMatches = if (needle.isBlank()) emptyList() else tasks.filter {
        "${it.title} ${it.priority} ${it.category.label} ${it.status} ${it.dueAt.orEmptyText()} ${it.plannedFor}".contains(needle, true)
    }
    val captureMatches = if (needle.isBlank()) emptyList() else captures.filter {
        "${it.transcript} ${it.outcome} ${it.location?.placeLabel.orEmpty()} ${it.location?.latitude} ${it.location?.longitude} ${it.capturedAt.format(taskTimeFormat)}".contains(needle, true) ||
            (needle.equals("today", true) && it.capturedAt.toLocalDate() == LocalDate.now()) ||
            (needle.equals("yesterday", true) && it.capturedAt.toLocalDate() == LocalDate.now().minusDays(1))
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("Find something", "Search tasks, captures, categories, places and dates.") }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search your saved content") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (needle.isBlank()) item { EmptyCard("What are you looking for?", "Try a task name, Work, P1, today, yesterday, or a saved place. Search stays on this page.") }
        else {
            item { Text("Tasks · ${taskMatches.size}", style = MaterialTheme.typography.titleLarge) }
            items(taskMatches, key = { "task-${it.id}" }) { TaskCard(it, actions) }
            item { Text("Captures · ${captureMatches.size}", style = MaterialTheme.typography.titleLarge) }
            items(captureMatches, key = { "capture-${it.id}" }) { CaptureCard(it) }
            if (taskMatches.isEmpty() && captureMatches.isEmpty()) item { EmptyCard("No matches", "Try a shorter phrase or another date/category.") }
        }
    }
}
private fun java.time.LocalDateTime?.orEmptyText(): String = this?.format(taskTimeFormat).orEmpty()

@Composable
fun HistoryPage(tasks: List<Task>, captures: List<CaptureRecord>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val completed = TaskQueries.completed(tasks)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("Your history", "Finished work and the words that started it.") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("Completed · ${completed.size}") })
            FilterChip(tab == 1, { tab = 1 }, label = { Text("Captures · ${captures.size}") })
        } }
        if (tab == 0) {
            if (completed.isEmpty()) item { EmptyCard("A fresh start", "Tap Done on Home, in Plan, or on an alarm. Your completed tasks will appear here.") }
            items(completed, key = { it.id }) { TaskCard(it) }
        } else {
            if (captures.isEmpty()) item { EmptyCard("No captures yet", "Voice and typed commands appear here with the result and time. Optional saved locations are included when available.") }
            items(captures, key = { it.id }) { CaptureCard(it) }
        }
    }
}

@Composable
private fun SettingsCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            body()
        }
    }
}

@Composable
fun SettingsPage(accessRevision: Int, onRequestNotifications: () -> Unit, onLocation: () -> Unit,
                 onMessage: (String) -> Unit, onErase: () -> Unit) {
    val context = LocalContext.current
    val dailyTimes = remember { DailyBriefScheduler.times(context) }
    var morning by rememberSaveable { mutableStateOf(dailyTimes?.first ?: "07:00") }
    var evening by rememberSaveable { mutableStateOf(dailyTimes?.second ?: "20:00") }
    var dailyEnabled by remember { mutableStateOf(dailyTimes != null) }
    var snooze by rememberSaveable { mutableStateOf(ReminderPreferences.snoozeMinutes(context).toString()) }
    var confirmErase by remember { mutableStateOf(false) }
    var themeChoice by remember { mutableStateOf(AppearancePreferences.theme(context)) }
    val access = remember(accessRevision) { Triple(ReminderSetup.notificationsAllowed(context), ReminderSetup.exactAlarmsAllowed(context), ReminderSetup.fullScreenAlarmsAllowed(context)) }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { onMessage("This phone could not open that setting. Use Android Settings → Apps → Voice Catcher.") } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Make it yours", "Notification access, planning times, snooze and privacy.") }
        item { SettingsCard("Appearance") {
            Text("Choose a theme. System follows your phone; Light and Dark override it immediately.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(themeChoice == choice, {
                        themeChoice = choice
                        AppearancePreferences.setTheme(context, choice)
                    }, label = { Text(choice.label) })
                }
            }
        } }
        item { SettingsCard("P1 words & P2 reminders") {
            Text("P1 rings an alarm. These words choose P1 automatically:")
            Text(VoiceCommandParser.priorityOneWords.joinToString(" · "), fontWeight = FontWeight.SemiBold)
            Text("An explicit ‘alarm’ request also selects P1. Give a time or delay; without one we ask when.")
            Text("Routine reminders use P2 notifications. Say ‘remind me to take a bath in 5 minutes’ for P2, or add ‘urgent’ for P1. Say ‘every 10 minutes’ to repeat a P2 until Done.")
            Text("Find and edit P2 tasks under Home → P2 notifications, including future tasks. An untimed P2 is a to-do only: Edit → turn on Alert me at a time to schedule it.")
            Text("P2 uses precise timing when Android allows exact alarms; otherwise Android may delay it to save battery.", style = MaterialTheme.typography.bodySmall)
        } }
        item { SettingsCard("Category suggestions") {
            Text("Categories group tasks; they do not change alarm priority or alert timing.")
            Text("Health: bath, run, meals, medicine\nWork: app status, project, meeting\nLearning: study, read, course\nErrands: shopping, groceries\nPersonal: family, calls, bills\nOther: no clear match")
            Text("Suggestions are local keyword rules, not cloud AI. Voice tasks get a suggestion automatically. In the task editor, choose any category to override it.")
        } }
        item { SettingsCard("Notifications & P1 alarms") {
            Text("Notifications: ${if (access.first) "Allowed" else "Off"}")
            OutlinedButton(onClick = onRequestNotifications) { Text(if (access.first) "Notification settings" else "Enable notifications") }
            Text("Exact P1 alarms: ${if (access.second) "Allowed" else "Not allowed"}")
            if (!access.second && Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))) }) { Text("Allow exact alarms") }
            Text("Full-screen controls: ${if (access.third) "Allowed" else "Not allowed"}")
            if (!access.third && Build.VERSION.SDK_INT >= 34) OutlinedButton(onClick = { open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:${context.packageName}"))) }) { Text("Allow full-screen alarms") }
            TextButton(onClick = { open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID)) }) { Text("P1 sound & notification channel") }
            TextButton(onClick = { open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderReceiver.P2_CHANNEL_ID)) }) { Text("P2 notification channel") }
        } }
        item { SettingsCard("Daily planning notifications") {
            Text("${if (dailyEnabled) "Enabled" else "Not enabled"} · Morning plan and evening review use the tasks organized on the Plan page.")
            OutlinedTextField(morning, { morning = it }, label = { Text("Morning · 24-hour time, e.g. 07:00") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(evening, { evening = it }, label = { Text("Evening · 24-hour time, e.g. 20:00") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val error = DailyBriefScheduler.saveAndSchedule(context, morning, evening)
                if (error != null) onMessage(error) else {
                    dailyEnabled = true
                    onMessage(if (ReminderSetup.notificationsAllowed(context)) "Daily planning times saved." else "Times saved. Enable notifications above to receive daily planning alerts.")
                }
            }) { Text("Save daily times") }
            if (dailyEnabled) TextButton(onClick = { DailyBriefScheduler.disable(context); dailyEnabled = false; onMessage("Daily planning notifications disabled.") }) { Text("Disable daily notifications") }
            Text("Plan → Add task → choose a category and date. Morning shows priorities; evening shows today's completions and what remains. Android may deliver daily briefs approximately.", style = MaterialTheme.typography.bodySmall)
        } }
        item { SettingsCard("Default snooze") {
            Text("Every snooze also offers presets or custom minutes. Today updates with the new time and a Snoozed label.")
            OutlinedTextField(snooze, { snooze = it }, label = { Text("Default minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            Button(onClick = {
                val minutes = snooze.trim().toIntOrNull()
                if (minutes == null || minutes !in 1..TaskState.MAX_SNOOZE_MINUTES) onMessage("Choose 1–${TaskState.MAX_SNOOZE_MINUTES} minutes.")
                else { ReminderPreferences.setSnoozeMinutes(context, minutes); onMessage("Default snooze saved: $minutes minutes.") }
            }) { Text("Save default snooze") }
        } }
        item { SettingsCard("Private, on this phone") {
            Text("Tasks and transcripts are encrypted on this device. Device backup is disabled. Location is optional and is only captured with permission when you save a command.")
            TextButton(onClick = onLocation) { Text("Allow location for future captures (optional)") }
            TextButton(onClick = { confirmErase = true }) { Text("Erase all saved data", color = MaterialTheme.colorScheme.error) }
        } }
        item { Text("Voice Catcher · 0.3.0", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
    }
    if (confirmErase) AlertDialog(onDismissRequest = { confirmErase = false }, title = { Text("Erase saved data?") },
        text = { Text("This permanently removes tasks and capture history on this phone and cancels their reminders and daily briefs.") },
        confirmButton = { TextButton(onClick = { onErase(); dailyEnabled = false; confirmErase = false }) { Text("Erase all data") } },
        dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } })
}
