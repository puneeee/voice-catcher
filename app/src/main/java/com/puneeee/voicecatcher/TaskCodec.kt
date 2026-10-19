package com.puneeee.voicecatcher

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** Additive JSON fields preserve existing encrypted task payloads during upgrades. */
object TaskCodec {
    fun encode(tasks: List<Task>): String = JSONArray().apply {
        tasks.forEach { task -> put(JSONObject().apply {
            put("id", task.id); put("title", task.title); put("priority", task.priority.name)
            put("dueAt", task.dueAt?.toString().orEmpty()); put("repeatMinutes", task.repeatMinutes)
            put("status", task.status.name); put("category", task.category.name)
            put("plannedFor", task.plannedFor?.toString().orEmpty()); put("completedAt", task.completedAt?.toString().orEmpty())
            put("snoozedAt", task.snoozedAt?.toString().orEmpty()); put("originalDueAt", task.originalDueAt?.toString().orEmpty())
            put("snoozeCount", task.snoozeCount)
        }) }
    }.toString()

    fun decode(payload: String): List<Task> {
        val array = JSONArray(payload)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            fun dateTime(key: String): LocalDateTime? = item.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let(LocalDateTime::parse)
            val due = dateTime("dueAt")
            Task(id = item.getString("id"), title = item.getString("title"),
                priority = ReminderPriority.valueOf(item.getString("priority")), dueAt = due,
                repeatMinutes = item.optInt("repeatMinutes", 0).takeIf { it > 0 },
                status = TaskStatus.valueOf(item.optString("status", "OPEN")),
                category = runCatching { TaskCategory.valueOf(item.optString("category", "GENERAL")) }.getOrDefault(TaskCategory.GENERAL),
                plannedFor = item.optString("plannedFor").takeIf { it.isNotBlank() && it != "null" }?.let(LocalDate::parse) ?: due?.toLocalDate(),
                completedAt = dateTime("completedAt"), snoozedAt = dateTime("snoozedAt"),
                originalDueAt = dateTime("originalDueAt"), snoozeCount = item.optInt("snoozeCount", 0))
        }
    }
}
