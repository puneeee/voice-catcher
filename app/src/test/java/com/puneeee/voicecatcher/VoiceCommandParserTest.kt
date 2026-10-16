package com.puneeee.voicecatcher

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private val now = LocalDateTime.of(2026, 10, 16, 9, 0)

    @Test fun `urgent reminder at a time is a P1`() {
        val result = VoiceCommandParser.parse("urgent remind me to call Sandy at 11:30 AM", now) as VoiceAction.CreateTask
        assertEquals(ReminderPriority.P1, result.priority)
        assertEquals(LocalDateTime.of(2026, 10, 16, 11, 30), result.dueAt)
    }

    @Test fun `urgent without a time asks for clarification`() {
        assertTrue(VoiceCommandParser.parse("urgent call Sandy", now) is VoiceAction.Clarify)
    }

    @Test fun `repeating reminder remains P2`() {
        val result = VoiceCommandParser.parse("remind me to stretch every 10 minutes", now) as VoiceAction.CreateTask
        assertEquals(ReminderPriority.P2, result.priority)
        assertEquals(10, result.repeatMinutes)
    }

    @Test fun `mixed urgent repeat asks for clarification`() {
        assertTrue(VoiceCommandParser.parse("urgent remind me every 10 minutes", now) is VoiceAction.Clarify)
    }

    @Test fun `twenty four hour time is understood`() {
        val result = VoiceCommandParser.parse("remind me to eat at 18:00", now) as VoiceAction.CreateTask
        assertEquals(18, result.dueAt?.hour)
    }
}
