package com.example.ai

import com.example.ai.intent.AIIntent
import com.example.ai.intent.IntentParser
import org.junit.Assert.*
import org.junit.Test

class IntentParserTest {

    @Test
    fun testShowPendingLeads() {
        val parsed = IntentParser.parseCommand("pending leads dikhao")
        assertEquals(AIIntent.SHOW_PENDING_LEADS, parsed.intent)
        assertEquals("VALID", parsed.validationStatus)
    }

    @Test
    fun testShowTodayReminders() {
        val parsed = IntentParser.parseCommand("aaj ke reminders batao")
        assertEquals(AIIntent.SHOW_TODAY_REMINDERS, parsed.intent)
        assertEquals("VALID", parsed.validationStatus)
    }

    @Test
    fun testShowWeeklyReport() {
        val parsed = IntentParser.parseCommand("weekly report banao")
        assertEquals(AIIntent.SHOW_WEEKLY_REPORT, parsed.intent)
        assertEquals("VALID", parsed.validationStatus)
    }

    @Test
    fun testOpenAddLead() {
        val parsed = IntentParser.parseCommand("naya lead add karo")
        assertEquals(AIIntent.OPEN_ADD_LEAD, parsed.intent)
        assertEquals("VALID", parsed.validationStatus)
    }

    @Test
    fun testCreateLead_Valid() {
        val parsed = IntentParser.parseCommand("Salman naam ka lead add karo number 9876543210")
        assertEquals(AIIntent.CREATE_LEAD, parsed.intent)
        assertEquals("Salman", parsed.entities.name)
        assertEquals("9876543210", parsed.entities.phone)
        assertEquals("VALID", parsed.validationStatus)
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun testCreateLead_InvalidPhone() {
        val parsed = IntentParser.parseCommand("Salman naam ka lead add karo number 12345")
        assertEquals(AIIntent.CREATE_LEAD, parsed.intent)
        assertEquals("Salman", parsed.entities.name)
        assertEquals("12345", parsed.entities.phone)
        assertEquals("INVALID", parsed.validationStatus)
    }

    @Test
    fun testCreateReminder_Valid() {
        val parsed = IntentParser.parseCommand("Rahul ka reminder kal subah 9 baje laga do")
        assertEquals(AIIntent.CREATE_REMINDER, parsed.intent)
        assertEquals("Rahul", parsed.entities.name)
        assertEquals("tomorrow", parsed.entities.relativeDate)
        assertEquals("09:00", parsed.entities.time)
        assertEquals("VALID", parsed.validationStatus)
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun testCreateReminder_MissingTime() {
        val parsed = IntentParser.parseCommand("Rahul ka reminder kal laga do")
        assertEquals(AIIntent.CREATE_REMINDER, parsed.intent)
        assertEquals("Rahul", parsed.entities.name)
        assertEquals("tomorrow", parsed.entities.relativeDate)
        assertNull(parsed.entities.time)
        assertEquals("INCOMPLETE", parsed.validationStatus)
        assertTrue(parsed.missingRequiredFields.contains("time"))
        assertNotNull(parsed.clarificationQuestion)
    }

    @Test
    fun testUpdateLeadStatus() {
        val parsed = IntentParser.parseCommand("Ramesh ka status completed kar do")
        assertEquals(AIIntent.UPDATE_LEAD_STATUS, parsed.intent)
        assertEquals("Ramesh", parsed.entities.name)
        assertEquals("Complete", parsed.entities.status)
        assertEquals("VALID", parsed.validationStatus)
    }

    @Test
    fun testAddLeadNote() {
        val parsed = IntentParser.parseCommand("Amit ka note: high blood pressure")
        assertEquals(AIIntent.ADD_LEAD_NOTE, parsed.intent)
        assertEquals("Amit", parsed.entities.name)
        assertEquals("high blood pressure", parsed.entities.noteText)
        assertEquals("VALID", parsed.validationStatus)
    }

    @Test
    fun testUnknownCommand() {
        val parsed = IntentParser.parseCommand("kuch bhi random command")
        assertEquals(AIIntent.UNKNOWN, parsed.intent)
        assertEquals("INVALID", parsed.validationStatus)
    }

    @Test
    fun testDayPartSynonyms() {
        // Morning
        val subah = IntentParser.parseCommand("Rahul ka reminder kal subah 5 baje laga do")
        assertEquals("05:00", subah.entities.time)

        val subha = IntentParser.parseCommand("Rahul ka reminder kal subha 9 baje")
        assertEquals("09:00", subha.entities.time)

        val morning = IntentParser.parseCommand("Rahul ka reminder tomorrow morning 10:30")
        assertEquals("10:30", morning.entities.time)

        // Afternoon
        val dopahar = IntentParser.parseCommand("Rahul ka reminder kal dopahar 12 baje")
        assertEquals("12:00", dopahar.entities.time)

        val dopahar1 = IntentParser.parseCommand("Rahul ka reminder kal dopahar 1 baje")
        assertEquals("13:00", dopahar1.entities.time)

        // Evening
        val shaam = IntentParser.parseCommand("Rahul ka reminder kal shaam 5 baje")
        assertEquals("17:00", shaam.entities.time)

        val evening = IntentParser.parseCommand("Rahul ka reminder kal evening 7 PM")
        assertEquals("19:00", evening.entities.time)

        // Night
        val raat = IntentParser.parseCommand("Rahul ka reminder kal raat 10 baje")
        assertEquals("22:00", raat.entities.time)
    }

    @Test
    fun testAmbiguityDetection() {
        // Missing day part (ambiguous 10 baje)
        val ambiguous10 = IntentParser.parseCommand("Rahul ka reminder kal 10 baje laga do")
        assertEquals("INCOMPLETE", ambiguous10.validationStatus)
        assertEquals("Kal subah 10 baje ya raat 10 baje?", ambiguous10.clarificationQuestion)

        // Ambiguous raat 1 baje
        val ambiguousRaat1 = IntentParser.parseCommand("Rahul ka reminder kal raat 1 baje laga do")
        assertEquals("INCOMPLETE", ambiguousRaat1.validationStatus)
        assertEquals("Kya aap raat 1:00 AM, yani midnight ke baad ka samay keh rahe hain?", ambiguousRaat1.clarificationQuestion)
    }

    @Test
    fun testRelativeDurationDeterministic() {
        val staticCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JULY, 17, 19, 8, 0)
        }
        com.example.ai.extraction.EntityExtractor.calendarProvider = { staticCal.clone() as java.util.Calendar }

        // 5 minute baad
        val p5min = IntentParser.parseCommand("Rahul ko 5 minute baad call reminder laga do")
        assertEquals("19:13", p5min.entities.time)
        assertEquals("2026-07-17", p5min.entities.resolvedDate)

        // aadhe ghante baad
        val pHalfHour = IntentParser.parseCommand("Rahul ko aadhe ghante baad call reminder laga do")
        assertEquals("19:38", pHalfHour.entities.time)

        // 1 ghante baad
        val p1Hour = IntentParser.parseCommand("Rahul ko 1 ghante baad call reminder")
        assertEquals("20:08", p1Hour.entities.time)

        // Reset calendarProvider
        com.example.ai.extraction.EntityExtractor.calendarProvider = { java.util.Calendar.getInstance() }
    }
}
