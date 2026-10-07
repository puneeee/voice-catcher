package com.puneeee.voicecatcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

/** Optional private-pilot sync. It does nothing until the backend URL and pilot token are configured at build time. */
object BackendSync {
    fun upload(capture: CaptureRecord, priority: ReminderPriority, dueAt: LocalDateTime?) {
        if (BuildConfig.BACKEND_BASE_URL.isBlank() || BuildConfig.PILOT_TOKEN.isBlank()) return
        Thread {
            runCatching {
                val payload = JSONObject()
                    .put("externalId", capture.id)
                    .put("transcript", capture.transcript)
                    .put("capturedAt", capture.capturedAt.toString())
                    .put("outcome", capture.outcome)
                    .put("priority", priority.name)
                    .put("dueAt", dueAt?.toString())
                capture.location?.let { location ->
                    payload.put("location", JSONObject()
                        .put("latitude", location.latitude)
                        .put("longitude", location.longitude)
                        .put("accuracyMeters", location.accuracyMeters))
                }
                val connection = URL("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/captures").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-pilot-token", BuildConfig.PILOT_TOKEN)
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                connection.inputStream.close()
                connection.disconnect()
            }
        }.start()
    }
}
