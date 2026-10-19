package com.puneeee.voicecatcher

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class PriorityAndCategoryTest {
    private val now = LocalDateTime.of(2026, 10, 19, 14, 0)

    @Test fun routineTimedReminderDefaultsToP2() {
        listOf("remind me to take a bath in 5 minutes", "remind me to run at 5:00 PM",
            "at 4 o'clock I should be eating lunch today").forEach { spoken ->
            val result = VoiceCommandParser.parse(spoken, now) as VoiceAction.CreateTask
            assertEquals(spoken, ReminderPriority.P2, result.priority)
            assertNotNull(result.dueAt)
        }
    }

    @Test fun eachDocumentedUrgencyWordSelectsP1() {
        VoiceCommandParser.priorityOneWords.forEach { word ->
            val result = VoiceCommandParser.parse("remind me to call in 5 minutes $word", now) as VoiceAction.CreateTask
            assertEquals(word, ReminderPriority.P1, result.priority)
        }
        assertEquals(ReminderPriority.P1, (VoiceCommandParser.parse("set an alarm in 5 minutes to call", now) as VoiceAction.CreateTask).priority)
    }

    @Test fun noTimeIsClarifiedAndConflictingPrioritiesAreNotSilentlyChosen() {
        assertTrue(VoiceCommandParser.parse("urgent call", now) is VoiceAction.Clarify)
        assertTrue(VoiceCommandParser.parse("p2 urgent call in 5 minutes", now) is VoiceAction.Clarify)
    }

    @Test fun oclockPhraseFromScreenshotUsesNextAfternoonTime() {
        val result = VoiceCommandParser.parse("at 4 o'clock I should be eating lunch today", now) as VoiceAction.CreateTask
        assertEquals(now.withHour(16), result.dueAt)
    }

    @Test fun categoriesMatchUserExamplesWithoutSubstringFalsePositives() {
        assertEquals(TaskCategory.HEALTH, CategorySuggestions.suggest("take bath"))
        assertEquals(TaskCategory.HEALTH, CategorySuggestions.suggest("go for a run"))
        assertEquals(TaskCategory.WORK, CategorySuggestions.suggest("check the app status"))
        assertEquals(TaskCategory.LEARNING, CategorySuggestions.suggest("study the course"))
        assertEquals(TaskCategory.ERRANDS, CategorySuggestions.suggest("buy groceries"))
        assertEquals(TaskCategory.GENERAL, CategorySuggestions.suggest("prune this tree"))
    }
}
