package com.puneeee.voicecatcher

import java.time.LocalDateTime

sealed interface VoiceAction {
    data class CreateTask(val title: String, val priority: ReminderPriority, val dueAt: LocalDateTime?, val repeatMinutes: Int? = null) : VoiceAction
    data class CompleteTask(val query: String) : VoiceAction
    data class Clarify(val message: String) : VoiceAction
}

/** Deterministic local command parsing. Explicit priority words always win over routine reminders. */
object VoiceCommandParser {
    private val completionPattern = Regex("""^(?:i )?(?:completed|finished|done with|mark)\s+(.+?)(?:\s+(?:as\s+)?done)?$""", RegexOption.IGNORE_CASE)
    private val priorityOnePattern = Regex("""\b(?:p\s*[- ]?\s*1|priority\s*1|urgent|emergency|critical|asap|immediately)\b""", RegexOption.IGNORE_CASE)
    private val intervalPattern = Regex("""\bevery\s+(\d+)\s*(minute|minutes|hour|hours)\b""", RegexOption.IGNORE_CASE)
    private val timePattern = Regex("""\bat\s+(\d{1,2})(?:(?::|\.|\s+)(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\b""", RegexOption.IGNORE_CASE)
    private val reminderLeadPattern = Regex("""^(?:remind me|tell me|reminder|alarm|notify me)\s*(?:to)?\s*""", RegexOption.IGNORE_CASE)
    private val dayWordPattern = Regex("""\b(?:today|tomorrow|tonight)\b""", RegexOption.IGNORE_CASE)

    fun parse(transcript: String, now: LocalDateTime = LocalDateTime.now()): VoiceAction {
        val cleaned = transcript.trim().replace(Regex("[.!?]+$"), "")
        if (cleaned.isBlank()) return VoiceAction.Clarify("I did not catch that. Please try again.")
        completionPattern.find(cleaned)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return VoiceAction.CompleteTask(it)
        }

        val isPriorityOne = priorityOnePattern.containsMatchIn(cleaned)
        val intervalMatch = intervalPattern.find(cleaned)
        if (intervalMatch != null && !isPriorityOne) {
            val amount = intervalMatch.groupValues[1].toIntOrNull()
            val unit = intervalMatch.groupValues[2].lowercase()
            val minutes = amount?.times(if (unit.startsWith("hour")) 60 else 1)
            if (minutes == null || minutes !in 1..(24 * 60)) return VoiceAction.Clarify("Choose a repeating gap between 1 minute and 24 hours.")
            return VoiceAction.CreateTask(
                title = taskTitle(cleaned, intervalPattern),
                priority = ReminderPriority.P2,
                dueAt = now.plusMinutes(minutes.toLong()),
                repeatMinutes = minutes,
            )
        }

        val time = timePattern.find(cleaned)
        val isReminder = cleaned.contains("remind me", ignoreCase = true) || cleaned.contains("tell me", ignoreCase = true) || cleaned.contains("alarm", ignoreCase = true)
        if (isPriorityOne || isReminder) {
            val dueAt = time?.let { parseTime(it, cleaned, now) } ?: if (isPriorityOne) now.plusSeconds(10) else null
            if (dueAt == null) return VoiceAction.Clarify("For an alarm, include a time such as ‘remind me at 5:15 PM to have lunch’.")
            return VoiceAction.CreateTask(
                title = taskTitle(cleaned, timePattern),
                priority = ReminderPriority.P1,
                dueAt = dueAt,
            )
        }

        val taskTitle = cleaned
            .replaceFirst(Regex("""^(?:add|note)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:to\s+)?(?:my\s+)?(?:to ?do|todo|task|list).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return VoiceAction.CreateTask(taskTitle.ifBlank { "New task" }, ReminderPriority.P2, null)
    }

    private fun parseTime(match: MatchResult, transcript: String, now: LocalDateTime): LocalDateTime? {
        val hourInput = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        if (hourInput !in 1..12 || minute !in 0..59) return null
        val suffix = match.groupValues[3].lowercase().replace(".", "")
        val hour24 = when {
            suffix == "am" && hourInput == 12 -> 0
            suffix == "pm" && hourInput != 12 -> hourInput + 12
            suffix.isBlank() -> hourInput
            else -> hourInput
        }
        var dueAt = now.withHour(hour24).withMinute(minute).withSecond(0).withNano(0)
        if (transcript.contains("tomorrow", ignoreCase = true) || !dueAt.isAfter(now)) dueAt = dueAt.plusDays(1)
        return dueAt
    }

    private fun taskTitle(transcript: String, removable: Regex?): String = transcript
        .replace(priorityOnePattern, "")
        .replaceFirst(Regex("""^\s*[-,:]?\s*"""), "")
        .replaceFirst(reminderLeadPattern, "")
        .let { value -> removable?.let { value.replace(it, "") } ?: value }
        .replace(dayWordPattern, "")
        .replaceFirst(Regex("""^\s*(?:to|that I should)\s+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', ',', ':')
        .ifBlank { "Reminder" }
}
