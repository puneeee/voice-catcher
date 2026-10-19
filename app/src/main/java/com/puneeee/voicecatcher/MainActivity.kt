package com.puneeee.voicecatcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class AppPage(val label: String) { HOME("Home"), PLAN("Plan"), SEARCH("Search"), HISTORY("History"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    private var page by mutableStateOf(AppPage.HOME)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CategorySuggestions.migrateExisting(this)
        page = runCatching { AppPage.valueOf(savedInstanceState?.getString("page") ?: intent.getStringExtra(EXTRA_PAGE).orEmpty()) }.getOrDefault(AppPage.HOME)
        setContent { VoiceTheme { VoiceCatcherApp(page) { page = it } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        intent.getStringExtra(EXTRA_PAGE)?.let { value -> runCatching { AppPage.valueOf(value) }.getOrNull()?.let { page = it } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page", page.name); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); ReminderScheduler.rearmOpen(this); DailyBriefScheduler.reschedule(this) }
    companion object { const val EXTRA_PAGE = "destination_page" }
}

@Composable
fun VoiceTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var choice by remember { mutableStateOf(AppearancePreferences.theme(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> choice = AppearancePreferences.theme(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = choice == ThemeChoice.DARK || (choice == ThemeChoice.SYSTEM && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xFFADC6FF), tertiary = Color(0xFF8FD5B5))
    else lightColorScheme(primary = Color(0xFF3859D6), secondary = Color(0xFF52617E), tertiary = Color(0xFF167258),
        background = Color(0xFFF5F7FC), surface = Color.White, surfaceVariant = Color(0xFFE8EDF7))
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun VoiceCatcherApp(page: AppPage, onPage: (AppPage) -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val store = remember { TaskStore(context.applicationContext) }
    var tasks by remember { mutableStateOf(store.load()) }
    var captures by remember { mutableStateOf(store.loadCaptures()) }
    var accessRevision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var voiceStatus by rememberSaveable { mutableStateOf("Routine reminders use P2 notifications. Say ‘urgent’ or ‘P1’ for a ringing alarm.") }
    var isSpeaking by remember { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var snoozeId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val locations = remember { LocationCapture(context.applicationContext) }
    fun feedback(message: String) { scope.launch { snackbars.showSnackbar(message) } }
    fun reload() { tasks = store.load(); captures = store.loadCaptures(); accessRevision++ }
    DisposableEffect(store, activity) {
        val subscription = store.observe { reload() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload() }
        activity.lifecycle.addObserver(observer)
        onDispose { subscription.close(); activity.lifecycle.removeObserver(observer) }
    }
    fun safely(action: () -> Unit) { try { action() } catch (error: Exception) { feedback(error.message ?: "Could not save this change. Please try again.") } }
    fun acceptTranscript(transcript: String) {
        val capturedAt = LocalDateTime.now()
        safely {
            val result = when (val action = VoiceCommandParser.parse(transcript, capturedAt)) {
                is VoiceAction.CreateTask -> {
                    val task = Task(title = action.title, priority = action.priority, dueAt = action.dueAt,
                        repeatMinutes = action.repeatMinutes, plannedFor = action.dueAt?.toLocalDate() ?: capturedAt.toLocalDate(),
                        category = CategorySuggestions.suggest(action.title))
                    val status = TaskOperations.save(context, task)
                    if (status == ReminderScheduler.Result.SCHEDULED)
                        "${action.priority} ${if (action.priority == ReminderPriority.P1) "alarm" else "reminder"} set: ${action.title} · ${action.dueAt?.format(DateTimeFormatter.ofPattern("EEE, h:mm a"))}."
                    else if (status == ReminderScheduler.Result.NOT_REQUIRED) "Added to today's plan: ${action.title}. Edit it to choose a category or time."
                    else "Saved, but no alert was armed. ${status.message()} Open Settings to fix reminder access."
                }
                is VoiceAction.CompleteTask -> {
                    val matches = TaskQueries.completionMatches(store.load(), action.query)
                    when (matches.size) {
                        0 -> "No open task matches ‘${action.query}’."
                        1 -> { TaskOperations.complete(context, matches.single().id); "Completed ‘${matches.single().title}’. Find it in History → Completed." }
                        else -> "${matches.size} tasks match ‘${action.query}’. Choose the right one in Home or Plan and tap Done."
                    }
                }
                is VoiceAction.Clarify -> action.message
            }
            voiceStatus = result
            val record = CaptureRecord(transcript = transcript, capturedAt = capturedAt, outcome = result)
            store.saveCaptures(listOf(record) + store.loadCaptures())
            // Scheduling happens immediately. Optional location can enrich history later.
            locations.capture { location ->
                if (location != null) safely {
                    val latest = store.loadCaptures()
                    if (latest.any { it.id == record.id }) store.saveCaptures(latest.map { if (it.id == record.id) it.copy(location = location) else it })
                }
            }
        }
    }
    val currentTranscriptHandler by rememberUpdatedState<(String) -> Unit> { acceptTranscript(it) }
    val recognizer = remember { VoiceRecognizer(context.applicationContext,
        onResult = { currentTranscriptHandler(it) }, onState = { voiceStatus = it }, onCaptureState = { isSpeaking = it }) }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recognizer.start() else voiceStatus = "Microphone access is off. You can still type a command or add a task."
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        accessRevision++
        if (granted) { ReminderScheduler.rearmOpen(context); DailyBriefScheduler.reschedule(context) }
        feedback(if (granted) "Notifications enabled." else "Notifications are off; tasks are saved, but alerts cannot appear.")
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { feedback("Location permission updated. Future captures use it only when allowed.") }
    LaunchedEffect(Unit) { AlarmService.ensureChannel(context); ReminderReceiver.ensureChannel(context); DailyBriefReceiver.ensureChannel(context) }
    val pageState = rememberSaveableStateHolder()
    fun newTask(date: LocalDate) { editId = null; editorDate = date.toString(); showEditor = true }
    val actions = TaskUiActions(
        onEdit = { editId = it.id; editorDate = (it.plannedFor ?: it.dueAt?.toLocalDate() ?: LocalDate.now()).toString(); showEditor = true },
        onDone = { task -> safely { TaskOperations.complete(context, task.id); feedback("Moved to History → Completed.") } },
        onSnooze = { snoozeId = it.id }, onDelete = { deleteId = it.id })

    Scaffold(snackbarHost = { SnackbarHost(snackbars) }, bottomBar = {
        NavigationBar {
            AppPage.entries.forEach { destination ->
                val icon = when (destination) { AppPage.HOME -> Icons.Outlined.Home; AppPage.PLAN -> Icons.Outlined.DateRange
                    AppPage.SEARCH -> Icons.Outlined.Search; AppPage.HISTORY -> Icons.Outlined.History; AppPage.SETTINGS -> Icons.Outlined.Settings }
                NavigationBarItem(selected = page == destination, onClick = { onPage(destination) },
                    icon = { Icon(icon, contentDescription = destination.label) }, label = { Text(destination.label) })
            }
        }
    }) { padding ->
        pageState.SaveableStateProvider(page.name) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    AppPage.HOME -> HomePage(tasks, voiceStatus, isSpeaking,
                        onSpeak = { if (isSpeaking) recognizer.finish() else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) recognizer.start() else microphone.launch(Manifest.permission.RECORD_AUDIO) },
                        onCommand = { acceptTranscript(it) }, onNew = { newTask(LocalDate.now()) }, actions = actions,
                        onSettings = { onPage(AppPage.SETTINGS) })
                    AppPage.PLAN -> PlanPage(tasks, actions, onNew = { newTask(it) }, onSettings = { onPage(AppPage.SETTINGS) })
                    AppPage.SEARCH -> SearchPage(tasks, captures, actions)
                    AppPage.HISTORY -> HistoryPage(tasks, captures)
                    AppPage.SETTINGS -> SettingsPage(accessRevision,
                        onRequestNotifications = { if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                        onLocation = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        onMessage = { feedback(it) }, onErase = {
                            safely {
                                store.load().forEach { ReminderScheduler.cancel(context, it); AlarmService.silence(context, it.id) }
                                androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
                                DailyBriefScheduler.disable(context); store.clearTasks(); store.clearCaptures(); reload()
                                feedback("Tasks and capture history erased. Daily notifications disabled.")
                            }
                        })
                }
            }
        }
    }
    if (showEditor) TaskEditorDialog(tasks.find { it.id == editId }, LocalDate.parse(editorDate), onDismiss = { showEditor = false }) { task ->
        try {
            val result = TaskOperations.save(context, task)
            feedback(if (result == ReminderScheduler.Result.SCHEDULED || result == ReminderScheduler.Result.NOT_REQUIRED) "Saved to ${task.category.label} · ${task.plannedFor}." else "Saved, but alert is not armed. ${result.message()}")
            null
        } catch (error: Exception) { error.message ?: "Could not save the task." }
    }
    tasks.find { it.id == snoozeId && it.status == TaskStatus.OPEN }?.let { task ->
        SnoozeDialog(task.title, ReminderPreferences.snoozeMinutes(context), onDismiss = { snoozeId = null }) { minutes, rememberDefault ->
            try {
                val result = TaskOperations.snooze(context, task.id, minutes)
                if (result == ReminderScheduler.Result.SCHEDULED) {
                    if (rememberDefault) ReminderPreferences.setSnoozeMinutes(context, minutes)
                    feedback("Snoozed for $minutes minutes. New time is shown on the task."); null
                } else result.message()
            } catch (error: Exception) { error.message ?: "Could not snooze." }
        }
    }
    tasks.find { it.id == deleteId }?.let { task -> AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("Delete this task?") }, text = { Text(task.title) },
        confirmButton = { TextButton(onClick = { safely { TaskOperations.delete(context, task.id); deleteId = null } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep task") } }) }
}

fun ReminderScheduler.Result.message(): String = when (this) {
    ReminderScheduler.Result.SCHEDULED -> "Alert scheduled."
    ReminderScheduler.Result.NOTIFICATIONS_DISABLED -> "Enable notifications in Settings."
    ReminderScheduler.Result.EXACT_ALARMS_DISABLED -> "Allow exact alarms in Settings for P1."
    ReminderScheduler.Result.PAST_DUE -> "Choose a future alert time."
    ReminderScheduler.Result.NOT_REQUIRED -> "This is a task without an alert."
}
