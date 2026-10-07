package com.puneeee.voicecatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Small, offline-first persistence for the private pilot. */
class TaskStore(context: Context) {
    private val preferences = context.getSharedPreferences("voice_catcher_tasks", Context.MODE_PRIVATE)

    fun load(): List<Task> = runCatching {
        val tasks = JSONArray(preferences.getString("tasks", "[]"))
        buildList {
            for (index in 0 until tasks.length()) {
                val item = tasks.getJSONObject(index)
                add(Task(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    priority = ReminderPriority.valueOf(item.getString("priority")),
                    dueAt = item.optString("dueAt").takeIf { it.isNotBlank() }?.let(LocalDateTime::parse),
                    status = TaskStatus.valueOf(item.getString("status")),
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun save(tasks: List<Task>) {
        val values = JSONArray()
        tasks.forEach { task ->
            values.put(JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("priority", task.priority.name)
                .put("dueAt", task.dueAt?.toString().orEmpty())
                .put("status", task.status.name))
        }
        preferences.edit().putString("tasks", values.toString()).apply()
    }

    fun loadCaptures(): List<CaptureRecord> = runCatching {
        val captures = JSONArray(preferences.getString("captures", "[]"))
        buildList {
            for (index in 0 until captures.length()) {
                val item = captures.getJSONObject(index)
                val location = item.optJSONObject("location")?.let {
                    CaptureLocation(
                        latitude = it.getDouble("latitude"),
                        longitude = it.getDouble("longitude"),
                        accuracyMeters = it.optDouble("accuracyMeters", Double.NaN).takeUnless(Double::isNaN)?.toFloat(),
                    )
                }
                add(CaptureRecord(
                    id = item.getString("id"),
                    transcript = item.getString("transcript"),
                    capturedAt = LocalDateTime.parse(item.getString("capturedAt")),
                    location = location,
                    outcome = item.getString("outcome"),
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun saveCaptures(captures: List<CaptureRecord>) {
        val values = JSONArray()
        captures.forEach { capture ->
            val value = JSONObject()
                .put("id", capture.id)
                .put("transcript", capture.transcript)
                .put("capturedAt", capture.capturedAt.toString())
                .put("outcome", capture.outcome)
            capture.location?.let { location ->
                value.put("location", JSONObject()
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
                    .put("accuracyMeters", location.accuracyMeters))
            }
            values.put(value)
        }
        preferences.edit().putString("captures", values.toString()).apply()
    }
}
