package com.puneeee.voicecatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Offline encrypted persistence that falls back to the last valid saved payload. */
class TaskStore(context: Context) {
    private val appContext = context.applicationContext
    private val secureStore = SecurePreferenceStore(appContext)

    fun load(): List<Task> = synchronized(LOCK) {
        secureStore.readCandidates(TASKS_KEY)
            .firstNotNullOfOrNull { payload -> runCatching { TaskCodec.decode(payload) }.getOrNull() }.orEmpty()
    }

    fun save(tasks: List<Task>) = synchronized(LOCK) { secureStore.write(TASKS_KEY, TaskCodec.encode(tasks)) }
    fun updateTask(updated: Task) = mutateTask(updated.id) { updated }
    fun upsert(task: Task) = synchronized(LOCK) {
        val current = load()
        save(if (current.any { it.id == task.id }) current.map { if (it.id == task.id) task else it } else listOf(task) + current)
    }
    fun mutateTask(id: String, transform: (Task) -> Task): Task? = synchronized(LOCK) {
        val current = load()
        val task = current.find { it.id == id } ?: return@synchronized null
        val updated = transform(task)
        save(current.map { if (it.id == id) updated else it })
        updated
    }
    fun deleteTask(id: String) = synchronized(LOCK) { save(load().filterNot { it.id == id }) }
    fun clearTasks() = synchronized(LOCK) { secureStore.clear(TASKS_KEY) }

    fun observe(onChange: () -> Unit): AutoCloseable {
        val preferences = appContext.getSharedPreferences("voice_catcher_tasks", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "tasks.v2" || key == "captures.v2" || key == null) onChange()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

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

    private companion object { val LOCK = Any(); const val TASKS_KEY = "tasks"; const val CAPTURES_KEY = "captures" }
}
