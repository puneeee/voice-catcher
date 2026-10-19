package com.puneeee.voicecatcher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val taskTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM · h:mm a")
data class TaskUiActions(val onEdit: (Task) -> Unit, val onDone: (Task) -> Unit, val onSnooze: (Task) -> Unit, val onDelete: (Task) -> Unit)

@Composable
fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyCard(title: String, message: String) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(task: Task, actions: TaskUiActions? = null) {
    Card(Modifier.fillMaxWidth().testTag("task-${task.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = if (task.priority == ReminderPriority.P1) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small) {
                    Text("${task.priority} · ${if (task.priority == ReminderPriority.P1) "Alarm" else "Task"}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
                Text(task.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.status == TaskStatus.DONE) Text("Completed", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
            }
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            task.dueAt?.let { due -> Text(due.format(taskTimeFormat), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ?: Text("Planned ${task.plannedFor?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: "without a date"} · No alert", style = MaterialTheme.typography.bodySmall)
            if (task.status == TaskStatus.OPEN && task.snoozedAt != null) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Snoozed until ${task.dueAt?.format(taskTimeFormat)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("${task.snoozeCount} snooze(s) · Updated ${task.snoozedAt.format(DateTimeFormatter.ofPattern("h:mm a"))}", style = MaterialTheme.typography.labelSmall)
                        task.originalDueAt?.let { Text("Originally ${it.format(taskTimeFormat)}", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            } else if (task.status == TaskStatus.OPEN && task.snoozeCount > 0) Text("Previously snoozed ${task.snoozeCount} time(s)", style = MaterialTheme.typography.bodySmall)
            task.repeatMinutes?.let { Text("Repeats every $it minutes until completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            task.completedAt?.let { Text("Completed ${it.format(taskTimeFormat)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            if (actions != null && task.status == TaskStatus.OPEN) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { actions.onDone(task) }) { Text("Done") }
                    TextButton(onClick = { actions.onEdit(task) }) { Text("Edit") }
                    TextButton(onClick = { actions.onSnooze(task) }) { Text("Snooze") }
                    TextButton(onClick = { actions.onDelete(task) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
fun CaptureCard(capture: CaptureRecord) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(capture.transcript, style = MaterialTheme.typography.titleMedium)
            Text(capture.capturedAt.format(taskTimeFormat), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(capture.outcome, style = MaterialTheme.typography.bodyMedium)
            capture.location?.let { location -> Text("${location.placeLabel ?: "Saved location"} · %.5f, %.5f".format(location.latitude, location.longitude),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(original: Task?, defaultDay: LocalDate, onDismiss: () -> Unit, onSave: (Task) -> String?) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }
    var priority by rememberSaveable { mutableStateOf(original?.priority?.name ?: "P2") }
    var category by rememberSaveable { mutableStateOf(original?.category?.name ?: "GENERAL") }
    var date by rememberSaveable { mutableStateOf((original?.plannedFor ?: original?.dueAt?.toLocalDate() ?: defaultDay).toString()) }
    var time by rememberSaveable { mutableStateOf((original?.dueAt?.toLocalTime() ?: LocalTime.now().plusMinutes(10).withSecond(0).withNano(0)).toString()) }
    var timed by rememberSaveable { mutableStateOf(original?.dueAt != null || original?.priority == ReminderPriority.P1) }
    var repeat by rememberSaveable { mutableStateOf(original?.repeatMinutes?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original == null) "Add to your plan" else "Edit task") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Task name") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPriority.entries.forEach { value -> FilterChip(selected = priority == value.name,
                        onClick = { priority = value.name; if (value == ReminderPriority.P1) { timed = true; repeat = "" } }, label = { Text(if (value == ReminderPriority.P1) "P1 · Urgent" else "P2 · Routine") }) }
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskCategory.entries.forEach { value -> FilterChip(selected = category == value.name, onClick = { category = value.name }, label = { Text(value.label) }) }
                }
                OutlinedButton(onClick = {
                    val selected = LocalDate.parse(date)
                    DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                }) { Text("Plan date · ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))}") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alert me at a time", Modifier.weight(1f))
                    Switch(checked = timed, onCheckedChange = { timed = it }, enabled = priority != "P1")
                }
                if (timed) {
                    OutlinedButton(onClick = {
                        val selected = LocalTime.parse(time)
                        TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString() }, selected.hour, selected.minute, DateFormat.is24HourFormat(context)).show()
                    }) { Text("Alert time · ${LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a"))}") }
                    Text("The alert uses the plan date above.", style = MaterialTheme.typography.bodySmall)
                    if (priority == "P2") OutlinedTextField(repeat, { repeat = it }, label = { Text("Repeat gap in minutes (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (priority == "P1") Text("P1 rings once at the exact time. Enable notifications and exact alarms in Settings.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { Button(onClick = {
            try {
                require(title.isNotBlank() && title.length <= 300) { "Enter a task name of 1–300 characters." }
                val due = if (timed) LocalDate.parse(date).atTime(LocalTime.parse(time)) else null
                require(due == null || due == original?.dueAt || due.isAfter(LocalDateTime.now())) { "Choose a future alert date and time." }
                val interval = if (timed && repeat.isNotBlank() && priority == "P2") repeat.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Enter a whole-number repeat gap.") else null
                val base = original ?: Task(title = title, priority = ReminderPriority.valueOf(priority))
                val updated = TaskState.edited(base, title, ReminderPriority.valueOf(priority), due, interval, TaskCategory.valueOf(category), LocalDate.parse(date))
                error = onSave(updated)
                if (error == null) onDismiss()
            } catch (invalid: IllegalArgumentException) { error = invalid.message }
        }) { Text("Save task") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeDialog(title: String, initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int, Boolean) -> String?) {
    var value by rememberSaveable { mutableStateOf(initialMinutes.toString()) }
    var rememberDefault by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Snooze reminder") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 30, 60).forEach { minutes -> FilterChip(selected = value == minutes.toString(), onClick = { value = minutes.toString() }, label = { Text("${minutes}m") }) }
            }
            OutlinedTextField(value, { value = it }, label = { Text("Custom minutes") }, modifier = Modifier.fillMaxWidth().testTag("snooze-minutes"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                supportingText = { Text("Any whole number from 1 to ${TaskState.MAX_SNOOZE_MINUTES} minutes") })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberDefault, { rememberDefault = it }); Text("Use as my default") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(onClick = {
        val minutes = value.trim().toIntOrNull()
        if (minutes == null || minutes !in 1..TaskState.MAX_SNOOZE_MINUTES) error = "Choose 1–${TaskState.MAX_SNOOZE_MINUTES} whole minutes."
        else { error = onConfirm(minutes, rememberDefault); if (error == null) onDismiss() }
    }, modifier = Modifier.testTag("confirm-snooze")) { Text("Snooze") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
