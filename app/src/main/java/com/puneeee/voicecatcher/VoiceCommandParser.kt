package com.puneeee.voicecatcher

import java.time.LocalDateTime

sealed interface VoiceAction {
    data class CreateTask(val title: String, val priority: ReminderPriority, val dueAt: LocalDateTime?, val repeatMinutes: Int? = null) : VoiceAction
    data class CompleteTask(val query: String) : VoiceAction
    data class Clarify(val message: String) : VoiceAction
}

/** All duration arithmetic is anchored to the capture time, not delayed location lookups. */
object VoiceCommandParser {
    val priorityOneWords = listOf("urgent", "emergency", "critical", "ASAP", "immediately", "P1", "priority one", "priority 1")
    private val completion = Regex("""^(?:i(?:'ve| have)?\s+)?(?:completed|finished|done with)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val markDone = Regex("""^mark\s+(.+?)\s+(?:(?:as|has)\s+)?(?:done|complete|completed)$""", RegexOption.IGNORE_CASE)
    private val p1 = Regex("""\b(?:p\s*[- ]?\s*1|priority\s*(?:one|1)|urgent|emergency|critical|asap|immediately)\b""", RegexOption.IGNORE_CASE)
    private val p2 = Regex("""\b(?:p\s*[- ]?\s*2|priority\s*(?:two|2))\b""", RegexOption.IGNORE_CASE)
    private const val quantity = "(?:[0-9]+(?:\\.[0-9]+)?|half|quarter|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty(?:[ -](?:one|two|three|four|five|six|seven|eight|nine))?|thirty|forty|forty-five|forty five|sixty|ninety)"
    private val relative = Regex("""\b(?:in|after)\s+($quantity)\s*(?:an?\s+)?(seconds?|secs?|minutes?|mins?|hours?|hrs?)(?:\s+(?:and\s+)?($quantity)\s*(minutes?|mins?|seconds?|secs?))?\b""", RegexOption.IGNORE_CASE)
    private val interval = Regex("""\bevery\s+($quantity)\s*(minutes?|mins?|hours?|hrs?)\b""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""\bat\s+(\d{1,2})(?:(?::|\.|\s+)(\d{2}))?\s*(?:o['’]?clock\s*)?(a\.?m\.?|p\.?m\.?)?\b""", RegexOption.IGNORE_CASE)
    private val lead = Regex("""^(?:please\s+)?(?:remind me|tell me|set (?:an? )?(?:alarm|reminder)|reminder|alarm|notify me)\s*(?:to)?\s*""", RegexOption.IGNORE_CASE)
    private val day = Regex("""\b(?:today|tomorrow|tonight)\b""", RegexOption.IGNORE_CASE)
    private val words = mapOf("a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0,
        "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0, "fifteen" to 15.0, "twenty" to 20.0,
        "thirty" to 30.0, "forty" to 40.0, "sixty" to 60.0, "ninety" to 90.0, "half" to .5, "quarter" to .25)

    fun parse(transcript: String, now: LocalDateTime = LocalDateTime.now()): VoiceAction {
        val text = transcript.trim().replace(Regex("[.!?]+$"), "")
        if (text.isBlank()) return VoiceAction.Clarify("I did not catch that. Please try again.")
        (markDone.find(text) ?: completion.find(text))?.groupValues?.get(1)?.trim()?.let { return VoiceAction.CompleteTask(it) }
        val urgent = p1.containsMatchIn(text)
        val gentle = p2.containsMatchIn(text)
        val explicitAlarm = Regex("""\balarm\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (urgent && gentle) return VoiceAction.Clarify("Choose P1 or P2 for this reminder.")
        val repeats = interval.find(text)
        if (repeats != null) {
            if (urgent) return VoiceAction.Clarify("Use a specific time or delay for an urgent P1 alarm. Repeating reminders use P2.")
            val seconds = seconds(repeats.groupValues[1], repeats.groupValues[2])
            val minutes = seconds?.div(60)?.takeIf { it in 1..1440 }
            if (minutes == null) return VoiceAction.Clarify("Choose a repeating gap between 1 minute and 24 hours.")
            return VoiceAction.CreateTask(title(text, interval), ReminderPriority.P2, now.plusMinutes(minutes), minutes.toInt())
        }
        val delay = relative.find(text)
        val time = clock.find(text)
        if (delay != null && time != null) return VoiceAction.Clarify("Choose a delay or a clock time, not both.")
        if (delay != null) {
            if (day.containsMatchIn(text)) return VoiceAction.Clarify("For a delay, say ‘in 5 minutes’ without today or tomorrow, or choose a clock time.")
            val first = seconds(delay.groupValues[1], delay.groupValues[2])
            val second = if (delay.groupValues[3].isBlank()) 0L else seconds(delay.groupValues[3], delay.groupValues[4])
            val total = if (first == null || second == null) null else first + second
            if (total == null || total !in 1L..604800L) return VoiceAction.Clarify("Choose a positive delay of up to 7 days.")
            return VoiceAction.CreateTask(title(text, relative), if (urgent || (explicitAlarm && !gentle)) ReminderPriority.P1 else ReminderPriority.P2, now.plusSeconds(total))
        }
        val isReminder = lead.containsMatchIn(text) || urgent || gentle || time != null
        if (isReminder) {
            val due = time?.let { parseClock(it, text, now) }
                ?: return VoiceAction.Clarify("Try ‘remind me in 5 minutes’, ‘after one hour’, or ‘at 5:15 PM’. Urgent sets P1.")
            return VoiceAction.CreateTask(title(text, clock), if (urgent || (explicitAlarm && !gentle)) ReminderPriority.P1 else ReminderPriority.P2, due)
        }
        val name = text.replaceFirst(Regex("""^(?:add|note)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:to\s+)?(?:my\s+)?(?:to ?do|todo|task|list).*$""", RegexOption.IGNORE_CASE), "").trim()
        return VoiceAction.CreateTask(name.ifBlank { "New task" }, ReminderPriority.P2, null)
    }

    private fun seconds(value: String, unit: String): Long? {
        val amount = value.lowercase().let { it.toDoubleOrNull() ?: it.replace('-', ' ').split(' ').map { part -> words[part] ?: return null }.sum() }
        val multiplier = when { unit.lowercase().startsWith("h") -> 3600; unit.lowercase().startsWith("m") -> 60; else -> 1 }
        val result = amount * multiplier
        return result.takeIf { it.isFinite() && it > 0 && it <= 604800 }?.toLong()
    }

    private fun parseClock(match: MatchResult, text: String, now: LocalDateTime): LocalDateTime? {
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        val suffix = match.groupValues[3].lowercase().replace(".", "")
        if (m !in 0..59 || (suffix.isBlank() && h !in 0..23) || (suffix.isNotBlank() && h !in 1..12)) return null
        val hour = when { suffix == "am" && h == 12 -> 0; suffix == "pm" && h != 12 -> h + 12; else -> h }
        var due = now.withHour(hour).withMinute(m).withSecond(0).withNano(0)
        if (suffix.isBlank() && h in 1..11 && !due.isAfter(now) && due.plusHours(12).isAfter(now)) due = due.plusHours(12)
        if (Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) due = due.plusDays(1)
        else if (!due.isAfter(now)) {
            if (Regex("""\btoday\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
            due = due.plusDays(1)
        }
        return due
    }

    private fun title(text: String, timing: Regex): String = text.replace(p1, "").replace(p2, "")
        .trim(' ', '-', ',', ':').replaceFirst(lead, "").replace(timing, "").replace(day, "")
        .replaceFirst(Regex("""^\s*(?:to|that I should)\s+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ").trim(' ', '-', ',', ':', '.').ifBlank { "Reminder" }
}
