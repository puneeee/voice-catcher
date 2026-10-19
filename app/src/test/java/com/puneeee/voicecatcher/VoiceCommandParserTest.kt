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

    @Test fun `relative minutes and urgent keyword work together`() {
        val result = VoiceCommandParser.parse("remind me to close my laptop in 2 minutes urgent", now) as VoiceAction.CreateTask
        assertEquals(ReminderPriority.P1, result.priority)
        assertEquals(now.plusMinutes(2), result.dueAt)
        assertEquals("close my laptop", result.title)
    }
    @Test fun `after five minutes schedules relative to capture`() {
        val result = VoiceCommandParser.parse("remind me after 5 minutes to stretch", now) as VoiceAction.CreateTask
        assertEquals(now.plusMinutes(5), result.dueAt)
        assertEquals("stretch", result.title)
    }
    @Test fun `spoken one hour and two minutes are understood`() {
        val hour = VoiceCommandParser.parse("remind me to leave after one hour", now) as VoiceAction.CreateTask
        val minutes = VoiceCommandParser.parse("remind me to leave in two minutes", now) as VoiceAction.CreateTask
        assertEquals(now.plusHours(1), hour.dueAt); assertEquals(now.plusMinutes(2), minutes.dueAt)
    }
    @Test fun `compound duration and half hour work`() {
        val compound = VoiceCommandParser.parse("remind me to leave in one hour and thirty minutes urgent", now) as VoiceAction.CreateTask
        val half = VoiceCommandParser.parse("remind me in half an hour to leave", now) as VoiceAction.CreateTask
        assertEquals(now.plusMinutes(90), compound.dueAt)
        assertEquals(now.plusMinutes(30), half.dueAt)
        val direct = VoiceCommandParser.parse("remind me in half hour to leave", now) as VoiceAction.CreateTask
        assertEquals(now.plusMinutes(30), direct.dueAt)
    }
    @Test fun `invalid relative delays request clarification`() {
        listOf("in 0 minutes", "after -5 minutes", "in 999999999 hours", "after a few minutes").forEach {
            assertTrue(it, VoiceCommandParser.parse("remind me to stretch $it urgent", now) is VoiceAction.Clarify)
        }
    }
    @Test fun `relative delay rolls across midnight`() {
        val late = now.withHour(23).withMinute(59)
        val result = VoiceCommandParser.parse("remind me to stretch in 2 minutes", late) as VoiceAction.CreateTask
        assertEquals(late.plusMinutes(2), result.dueAt)
    }
    @Test fun `priority two is explicit even with a relative time`() {
        val result = VoiceCommandParser.parse("p2 remind me to stretch in 15 minutes", now) as VoiceAction.CreateTask
        assertEquals(ReminderPriority.P2, result.priority); assertEquals("stretch", result.title)
    }
    @Test fun `today past time does not silently become tomorrow`() {
        assertTrue(VoiceCommandParser.parse("remind me today at 8:00 AM to leave", now) is VoiceAction.Clarify)
    }
    @Test fun `ambiguous delay plus clock is clarified`() {
        assertTrue(VoiceCommandParser.parse("remind me in 5 minutes at 10 AM to leave", now) is VoiceAction.Clarify)
    }
    @Test fun `mark as done extracts task name only`() {
        assertEquals(VoiceAction.CompleteTask("the report"), VoiceCommandParser.parse("mark the report as done", now))
        assertEquals(VoiceAction.CompleteTask("the report"), VoiceCommandParser.parse("I finished the report", now))
    }
    @Test fun `clock punctuation and urgent are removed from title`() {
        val result = VoiceCommandParser.parse("remind me to stretch at 11:48 a.m. today urgent", now) as VoiceAction.CreateTask
        assertEquals("stretch", result.title); assertEquals(11, result.dueAt?.hour); assertEquals(48, result.dueAt?.minute)
    }
}
