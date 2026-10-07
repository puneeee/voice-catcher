package com.puneeee.voicecatcher

import java.time.LocalDateTime
import java.util.UUID

/**
 * P1 is an exact, urgent local alarm. P2 is a gentle remote or local prompt.
 */
enum class ReminderPriority { P1, P2 }

enum class TaskStatus { OPEN, DONE }

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: ReminderPriority,
    val dueAt: LocalDateTime? = null,
    val repeatMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.OPEN,
)

data class CaptureLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
)

data class CaptureRecord(
    val id: String = UUID.randomUUID().toString(),
    val transcript: String,
    val capturedAt: LocalDateTime,
    val location: CaptureLocation? = null,
    val outcome: String,
)
