package com.example.leads.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LeadAIReminderTimeValidationTest {

    private val originalProvider = LeadAIDraftAnswerParser.calendarProvider

    @Before
    fun setUp() {
        // Mock the calendar to return 2026-07-19 15:31:00 UTC (or local)
        LeadAIDraftAnswerParser.calendarProvider = {
            Calendar.getInstance().apply {
                timeZone = TimeZone.getTimeZone("UTC")
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.JULY)
                set(Calendar.DAY_OF_MONTH, 19)
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 31)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    @After
    fun tearDown() {
        LeadAIDraftAnswerParser.calendarProvider = originalProvider
    }

    @Test
    fun testReminderTimeValidation() {
        val manager = LeadAIDraftManager()
        val engine = LeadAIDraftConversationEngine(manager)

        // 1. Start a draft and fill up to REMINDER_DATE
        manager.startCreateDraft(sourceMessageId = "msg-1")
        manager.updateActiveDraft(
            name = "Test Lead",
            mobile = "9876543210",
            diseases = listOf("General Wellness"),
            relation = "Customer",
            status = "Pending",
            reminderRequested = true,
            reminderDate = "2026-07-19" // Today's date
        )

        // Ensure nextRequiredStep is indeed REMINDER_TIME
        assertEquals(LeadAIDraftStep.REMINDER_TIME, manager.nextRequiredStep())

        // 2. today + past time is rejected at REMINDER_TIME
        // Current mocked device time is 15:31. User inputs "15:30" (past time)
        val resultPast = engine.handleUserAnswer("15:30")
        assertEquals(LeadAIDraftConversationStatus.INVALID_ANSWER, resultPast.status)
        assertTrue(resultPast.message!!.contains("samay beet chuka hai", ignoreCase = true))

        // past-time rejection preserves the active draft as is
        val activeDraft = manager.getActiveDraft()
        assertNotNull(activeDraft)
        assertEquals("", activeDraft!!.reminderTime) // reminderTime remains blank/empty

        // past-time rejection keeps nextRequiredStep as REMINDER_TIME
        assertEquals(LeadAIDraftStep.REMINDER_TIME, manager.nextRequiredStep())
        assertEquals(LeadAIDraftStep.REMINDER_TIME, activeDraft.nextRequiredStep())

        // progress card/draft reports the real required step (which is REMINDER_TIME, not READY_FOR_CONFIRMATION)
        assertEquals(LeadAIDraftStep.REMINDER_TIME, activeDraft.nextRequiredStep())

        // no confirmation is generated after past-time rejection (status is not READY_FOR_CONFIRMATION, leadDraftForConfirmation is null)
        assertNull(resultPast.leadDraftForConfirmation)

        // 3. today + future time is accepted
        // User inputs "15:32" (future time)
        val resultFuture = engine.handleUserAnswer("15:32")
        // Since we didn't advance past-time answer, submitting 15:32 should now be accepted!
        // Wait, handleUserAnswer returns next step (REMINDER_NOTE)
        assertEquals(LeadAIDraftConversationStatus.NEXT_QUESTION, resultFuture.status)
        assertEquals("15:32", manager.getActiveDraft()!!.reminderTime)
        assertEquals(LeadAIDraftStep.REMINDER_NOTE, manager.nextRequiredStep())
    }

    @Test
    fun testTomorrowAnyValidTimeIsAccepted() {
        val manager = LeadAIDraftManager()
        val engine = LeadAIDraftConversationEngine(manager)

        manager.startCreateDraft(sourceMessageId = "msg-2")
        manager.updateActiveDraft(
            name = "Test Lead 2",
            mobile = "9876543210",
            diseases = listOf("General Wellness"),
            relation = "Customer",
            status = "Pending",
            reminderRequested = true,
            reminderDate = "2026-07-20" // Tomorrow
        )

        assertEquals(LeadAIDraftStep.REMINDER_TIME, manager.nextRequiredStep())

        // tomorrow + any valid time (even earlier hour in the day e.g., "subah 2" / 02:00 AM) is accepted
        val resultTomorrow = engine.handleUserAnswer("subah 2")
        assertEquals(LeadAIDraftConversationStatus.NEXT_QUESTION, resultTomorrow.status)
        assertEquals("02:00", manager.getActiveDraft()!!.reminderTime)
        assertEquals(LeadAIDraftStep.REMINDER_NOTE, manager.nextRequiredStep())
    }
}
