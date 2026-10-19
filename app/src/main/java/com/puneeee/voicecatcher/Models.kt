package com.puneeee.voicecatcher

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class ReminderPriority { P1, P2 }
enum class TaskStatus { OPEN, DONE }
enum class TaskCategory(val label: String) {
    GENERAL("Other"), WORK("Work"), PERSONAL("Personal"), HEALTH("Health"), ERRANDS("Errands"), LEARNING("Learning")
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: ReminderPriority,
    val dueAt: LocalDateTime? = null,
    val repeatMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val category: TaskCategory = TaskCategory.GENERAL,
    val plannedFor: LocalDate? = dueAt?.toLocalDate(),
    val completedAt: LocalDateTime? = null,
    val snoozedAt: LocalDateTime? = null,
    val originalDueAt: LocalDateTime? = null,
    val snoozeCount: Int = 0,
)

data class CaptureLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null, val placeLabel: String? = null)
data class CaptureRecord(
    val id: String = UUID.randomUUID().toString(),
    val transcript: String,
    val capturedAt: LocalDateTime,
    val location: CaptureLocation? = null,
    val outcome: String,
)

/** Shared, Android-independent transitions used by UI, notifications and the alarm service. */
object TaskState {
    const val MAX_SNOOZE_MINUTES = 7 * 24 * 60
    fun complete(task: Task, now: LocalDateTime): Task = if (task.status == TaskStatus.DONE) task else
        task.copy(status = TaskStatus.DONE, completedAt = now)

    fun snooze(task: Task, minutes: Int, now: LocalDateTime): Task {
        require(task.status == TaskStatus.OPEN) { "Completed tasks cannot be snoozed." }
        require(minutes in 1..MAX_SNOOZE_MINUTES) { "Choose 1 to $MAX_SNOOZE_MINUTES minutes." }
        val next = now.plusMinutes(minutes.toLong())
        return task.copy(dueAt = next, plannedFor = next.toLocalDate(), snoozedAt = now,
            originalDueAt = task.originalDueAt ?: task.dueAt, snoozeCount = task.snoozeCount + 1)
    }

    fun edited(original: Task, title: String, priority: ReminderPriority, dueAt: LocalDateTime?,
               repeatMinutes: Int?, category: TaskCategory, plannedFor: LocalDate): Task {
        require(title.isNotBlank()) { "Enter a task name." }
        require(priority != ReminderPriority.P1 || dueAt != null) { "P1 alarms need a date and time." }
        require(repeatMinutes == null || repeatMinutes in 1..1440) { "Repeat gap must be 1–1440 minutes." }
        require(priority != ReminderPriority.P1 || repeatMinutes == null) { "Repeating reminders use P2." }
        val scheduleChanged = original.dueAt != dueAt || original.priority != priority || original.repeatMinutes != repeatMinutes
        return original.copy(title = title.trim(), priority = priority, dueAt = dueAt, repeatMinutes = repeatMinutes,
            category = category, plannedFor = plannedFor,
            snoozedAt = if (scheduleChanged) null else original.snoozedAt,
            originalDueAt = if (scheduleChanged) null else original.originalDueAt,
            snoozeCount = if (scheduleChanged) 0 else original.snoozeCount)
    }
}

object TaskQueries {
    fun forDay(tasks: List<Task>, day: LocalDate, includeOverdue: Boolean = false): List<Task> = tasks
        .filter { it.status == TaskStatus.OPEN }
        .filter { task ->
            val date = task.plannedFor ?: task.dueAt?.toLocalDate()
            date == day || (includeOverdue && (date == null || date.isBefore(day)))
        }
        .sortedWith(compareBy<Task> { it.dueAt ?: LocalDateTime.MAX }.thenBy { it.priority.ordinal })

    fun completed(tasks: List<Task>): List<Task> = tasks.filter { it.status == TaskStatus.DONE }
        .sortedByDescending { it.completedAt ?: LocalDateTime.MIN }

    fun completionMatches(tasks: List<Task>, query: String): List<Task> {
        fun normalize(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ").trim().removePrefix("the ")
        val needle = normalize(query)
        if (needle.isBlank()) return emptyList()
        val open = tasks.filter { it.status == TaskStatus.OPEN }
        val exact = open.filter { normalize(it.title) == needle }
        return exact.ifEmpty { open.filter { normalize(it.title).contains(needle) } }
    }
}
