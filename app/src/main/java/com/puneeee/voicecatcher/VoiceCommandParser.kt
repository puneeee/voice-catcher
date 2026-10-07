package com.puneeee.voicecatcher

import java.time.LocalDateTime

sealed interface VoiceAction {
    data class CreateTask(val title: String, val priority: ReminderPriority, val dueAt: LocalDateTime?, val repeatMinutes: Int? = null) : VoiceAction
    data class CompleteTask(val query: String) : VoiceAction
    data class Clarify(val message: String) : VoiceAction
}

/** Deterministic offline parser for the first usable build. */
object VoiceCommandParser {
    private val completionPattern = Regex("""^(?:i )?(?:completed|finished|done with|mark)\s+(.+?)(?:\s+(?:as\s+)?done)?$""", RegexOption.IGNORE_CASE)
    private val timePattern = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE)
    private val urgentPattern = Regex("""\b(emergency|urgent|asap|critical|immediately)\b""", RegexOption.IGNORE_CASE)
    private val intervalPattern = Regex("""\bevery\s+(\d+)\s*(minute|minutes|hour|hours)\b""", RegexOption.IGNORE_CASE)

    fun parse(transcript: String, now: LocalDateTime = LocalDateTime.now()): VoiceAction {
        val cleaned = transcript.trim().replace(Regex("[.!?]+$"), "")
        if (cleaned.isBlank()) return VoiceAction.Clarify("I did not catch that. Please try again.")
        completionPattern.find(cleaned)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return VoiceAction.CompleteTask(it)
        }
        val isUrgent = urgentPattern.containsMatchIn(cleaned)
        val intervalMatch = intervalPattern.find(cleaned)
        if (intervalMatch != null && !isUrgent) {
            val amount = intervalMatch.groupValues[1].toIntOrNull()
            val unit = intervalMatch.groupValues[2].lowercase()
            val minutes = amount?.times(if (unit.startsWith("hour")) 60 else 1)
            if (minutes == null || minutes !in 1..(24 * 60)) return VoiceAction.Clarify("Choose a repeating gap between 1 minute and 24 hours.")
            val title = cleaned
                .replaceFirst(Regex("""^remind me\s+""", RegexOption.IGNORE_CASE), "")
                .replace(intervalPattern, "")
                .replaceFirst(Regex("""^\s*to\s+""", RegexOption.IGNORE_CASE), "")
                .trim().ifBlank { "Reminder" }
            return VoiceAction.CreateTask(title, ReminderPriority.P2, now.plusMinutes(minutes.toLong()), minutes)
        }
        if (cleaned.contains("remind me", ignoreCase = true) || isUrgent) {
            val time = timePattern.find(cleaned)
            if (time == null && !isUrgent) return VoiceAction.Clarify("For an alarm, include a time such as ‘remind me at 5 PM to have lunch’.")
            if (time == null) {
                return VoiceAction.CreateTask(
                    title = cleaned.replace(urgentPattern, "").trim().ifBlank { "Urgent reminder" },
                    priority = ReminderPriority.P1,
                    dueAt = now.plusSeconds(10),
                )
            }
            val hourInput = time.groupValues[1].toInt()
            val minute = time.groupValues[2].ifBlank { "0" }.toInt()
            val suffix = time.groupValues[3].lowercase().replace(".", "")
            if (hourInput !in 1..12 || minute !in 0..59) return VoiceAction.Clarify("That time is not clear. Please try a time such as 5 PM.")
            val hour24 = when {
                suffix == "am" && hourInput == 12 -> 0
                suffix == "pm" && hourInput != 12 -> hourInput + 12
                else -> hourInput
            }
            var dueAt = now.withHour(hour24).withMinute(minute).withSecond(0).withNano(0)
            if (!dueAt.isAfter(now)) dueAt = dueAt.plusDays(1)
            val title = cleaned.substring(time.range.last + 1)
                .replaceFirst(Regex("""^\s*(?:to|that I should)\s+""", RegexOption.IGNORE_CASE), "")
                .trim().ifBlank { "Reminder" }
            return VoiceAction.CreateTask(title, ReminderPriority.P1, dueAt)
        }
        val taskTitle = cleaned
            .replaceFirst(Regex("""^(?:add|note)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:to\s+)?(?:my\s+)?(?:to ?do|todo|task|list).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return VoiceAction.CreateTask(taskTitle.ifBlank { "New task" }, ReminderPriority.P2, null)
    }
}
