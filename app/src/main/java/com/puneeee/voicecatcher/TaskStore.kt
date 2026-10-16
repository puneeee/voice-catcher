package com.puneeee.voicecatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Offline encrypted persistence that falls back to the last valid saved payload. */
class TaskStore(context: Context) {
    private val secureStore = SecurePreferenceStore(context)

    fun load(): List<Task> = secureStore.readCandidates(TASKS_KEY)
        .firstNotNullOfOrNull { payload -> runCatching { parseTasks(payload) }.getOrNull() }
        .orEmpty()

    fun save(tasks: List<Task>) {
        val values = JSONArray()
        tasks.forEach { task -> values.put(JSONObject()
            .put("id", task.id).put("title", task.title).put("priority", task.priority.name)
            .put("dueAt", task.dueAt?.toString().orEmpty()).put("repeatMinutes", task.repeatMinutes)
            .put("status", task.status.name)) }
        secureStore.write(TASKS_KEY, values.toString())
    }

    fun updateTask(updated: Task) = save(load().map { if (it.id == updated.id) updated else it })
    fun clearTasks() = secureStore.clear(TASKS_KEY)

    fun loadCaptures(): List<CaptureRecord> = secureStore.readCandidates(CAPTURES_KEY)
        .firstNotNullOfOrNull { payload -> runCatching { parseCaptures(payload) }.getOrNull() }
        .orEmpty()

    fun saveCaptures(captures: List<CaptureRecord>) {
        val values = JSONArray()
        captures.forEach { capture ->
            val value = JSONObject().put("id", capture.id).put("transcript", capture.transcript)
                .put("capturedAt", capture.capturedAt.toString()).put("outcome", capture.outcome)
            capture.location?.let { location -> value.put("location", JSONObject()
                .put("latitude", location.latitude).put("longitude", location.longitude)
                .put("accuracyMeters", location.accuracyMeters).put("placeLabel", location.placeLabel)) }
            values.put(value)
        }
        secureStore.write(CAPTURES_KEY, values.toString())
    }

    fun clearCaptures() = secureStore.clear(CAPTURES_KEY)

    private fun parseTasks(payload: String): List<Task> {
        val tasks = JSONArray(payload)
        return buildList {
            for (index in 0 until tasks.length()) {
                val item = tasks.getJSONObject(index)
                add(Task(item.getString("id"), item.getString("title"), ReminderPriority.valueOf(item.getString("priority")),
                    item.optString("dueAt").takeIf { it.isNotBlank() }?.let(LocalDateTime::parse),
                    item.optInt("repeatMinutes", 0).takeIf { it > 0 }, TaskStatus.valueOf(item.getString("status"))))
            }
        }
    }

    private fun parseCaptures(payload: String): List<CaptureRecord> {
        val captures = JSONArray(payload)
        return buildList {
            for (index in 0 until captures.length()) {
                val item = captures.getJSONObject(index)
                val location = item.optJSONObject("location")?.let { CaptureLocation(
                    it.getDouble("latitude"), it.getDouble("longitude"),
                    it.optDouble("accuracyMeters", Double.NaN).takeUnless(Double::isNaN)?.toFloat(),
                    it.optString("placeLabel").takeIf(String::isNotBlank)) }
                add(CaptureRecord(item.getString("id"), item.getString("transcript"),
                    LocalDateTime.parse(item.getString("capturedAt")), location, item.getString("outcome")))
            }
        }
    }

    private companion object { const val TASKS_KEY = "tasks"; const val CAPTURES_KEY = "captures" }
}
