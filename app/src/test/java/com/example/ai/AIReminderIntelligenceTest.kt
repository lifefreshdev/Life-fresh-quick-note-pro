package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AIReminderIntelligenceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
        database = AppDatabase.getDatabase(context)
        ConversationMemory.clearSession()
    }

    @After
    fun tearDown() {
        runTest(testDispatcher) {
            database.leadDao.clearAllLeads()
        }
        AIManager.shutdown()
    }

    @Test
    fun testNaturalLanguageDateParsing_English() {
        val today = LocalDate.now()
        assertEquals(today, NaturalLanguageTimeParser.parseDate("today"))
        assertEquals(today.plusDays(1), NaturalLanguageTimeParser.parseDate("tomorrow"))
        assertEquals(today.plusDays(2), NaturalLanguageTimeParser.parseDate("day after tomorrow"))
        
        // Exact ISO Date Check
        assertEquals(LocalDate.of(2026, 12, 25), NaturalLanguageTimeParser.parseDate("2026-12-25"))
    }

    @Test
    fun testNaturalLanguageDateParsing_Hinglish() {
        val today = LocalDate.now()
        assertEquals(today, NaturalLanguageTimeParser.parseDate("aaj"))
        assertEquals(today.plusDays(1), NaturalLanguageTimeParser.parseDate("kal"))
        assertEquals(today.plusDays(2), NaturalLanguageTimeParser.parseDate("parso"))
        assertEquals(today.plusDays(2), NaturalLanguageTimeParser.parseDate("parson"))
    }

    @Test
    fun testNaturalLanguageTimeParsing_English() {
        assertEquals(LocalTime.of(17, 0), NaturalLanguageTimeParser.parseTime("5 PM"))
        assertEquals(LocalTime.of(17, 30), NaturalLanguageTimeParser.parseTime("5:30 PM"))
        assertEquals(LocalTime.of(9, 0), NaturalLanguageTimeParser.parseTime("morning"))
        assertEquals(LocalTime.of(14, 0), NaturalLanguageTimeParser.parseTime("afternoon"))
        assertEquals(LocalTime.of(18, 0), NaturalLanguageTimeParser.parseTime("evening"))
        assertEquals(LocalTime.of(21, 0), NaturalLanguageTimeParser.parseTime("night"))
    }

    @Test
    fun testNaturalLanguageTimeParsing_Hinglish() {
        assertEquals(LocalTime.of(9, 0), NaturalLanguageTimeParser.parseTime("subah"))
        assertEquals(LocalTime.of(14, 0), NaturalLanguageTimeParser.parseTime("dopahar"))
        assertEquals(LocalTime.of(18, 0), NaturalLanguageTimeParser.parseTime("shaam"))
        assertEquals(LocalTime.of(21, 0), NaturalLanguageTimeParser.parseTime("raat"))
    }

    @Test
    fun testReminderCreation_Success() = runTest(testDispatcher) {
        // Prepare: Reminder needs a pre-existing client
        val clientName = "Rahul"
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = clientName,
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        val tomorrowStr = LocalDate.now().plusDays(1).toString()
        val params = mapOf(
            "operation" to "create",
            "name" to clientName,
            "date" to tomorrowStr,
            "time" to "15:00",
            "keyword" to "Follow-up Consultation"
        )

        // Capture events
        val eventList = mutableListOf<AIEvent>()
        val job = launch {
            AIEventBus.events.collect { eventList.add(it) }
        }

        val tool = ReminderTool()
        val result = tool.execute(params)

        assertTrue(result.success)
        assertEquals("SUCCESS", result.data["status"])

        // Verify database state has changed
        val updatedClient = database.leadDao.getLeadById(client.id)
        assertNotNull(updatedClient)
        assertEquals(tomorrowStr, updatedClient?.reminderDate)
        assertEquals("15:00", updatedClient?.reminderTime)
        assertEquals("Follow-up Consultation", updatedClient?.reminderNote)
        assertEquals("Pending", updatedClient?.reminderStatus)

        // Verify Event
        assertTrue(eventList.any { it is ReminderCreatedEvent && it.leadId == client.id })

        job.cancel()
    }

    @Test
    fun testReminderCreation_FailsOnMissingClient() = runTest(testDispatcher) {
        val params = mapOf(
            "operation" to "create",
            "name" to "NonExistentClient",
            "date" to "2026-07-01",
            "time" to "15:00"
        )

        val eventList = mutableListOf<AIEvent>()
        val job = launch {
            AIEventBus.events.collect { eventList.add(it) }
        }

        val tool = ReminderTool()
        val result = tool.execute(params)

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("not found") == true)

        // Verify Event
        assertTrue(eventList.any { it is ReminderOperationFailedEvent && it.operation == "create" })

        job.cancel()
    }

    @Test
    fun testReminderCreation_FailsOnPastReminder() = runTest(testDispatcher) {
        val clientName = "Seema"
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = clientName,
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        val pastDateStr = LocalDate.now().minusDays(1).toString()
        val params = mapOf(
            "operation" to "create",
            "name" to clientName,
            "date" to pastDateStr,
            "time" to "09:00"
        )

        val tool = ReminderTool()
        val result = tool.execute(params)

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("past") == true)
    }

    @Test
    fun testReminderCreation_FailsOnDuplicate() = runTest(testDispatcher) {
        val clientName = "Rakesh"
        val tomorrowStr = LocalDate.now().plusDays(1).toString()
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = clientName,
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = tomorrowStr,
            reminderTime = "10:00",
            reminderNote = "Meeting",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        val params = mapOf(
            "operation" to "create",
            "name" to clientName,
            "date" to tomorrowStr,
            "time" to "10:00",
            "keyword" to "Duplicate check"
        )

        val tool = ReminderTool()
        val result = tool.execute(params)

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("Duplicate") == true)
    }

    @Test
    fun testReminderUpdate_Success() = runTest(testDispatcher) {
        val clientName = "Preeti"
        val tomorrowStr = LocalDate.now().plusDays(1).toString()
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = clientName,
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = tomorrowStr,
            reminderTime = "10:00",
            reminderNote = "Meeting",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        val dayAfterTomorrowStr = LocalDate.now().plusDays(2).toString()
        val params = mapOf(
            "operation" to "edit",
            "name" to clientName,
            "date" to dayAfterTomorrowStr,
            "time" to "11:30",
            "keyword" to "New Meeting Details"
        )

        val eventList = mutableListOf<AIEvent>()
        val job = launch {
            AIEventBus.events.collect { eventList.add(it) }
        }

        val tool = ReminderTool()
        val result = tool.execute(params)

        assertTrue(result.success)

        val updatedClient = database.leadDao.getLeadById(client.id)
        assertEquals(dayAfterTomorrowStr, updatedClient?.reminderDate)
        assertEquals("11:30", updatedClient?.reminderTime)
        assertEquals("New Meeting Details", updatedClient?.reminderNote)

        assertTrue(eventList.any { it is ReminderUpdatedEvent && it.leadId == client.id })

        job.cancel()
    }

    @Test
    fun testReminderCompletion_Success() = runTest(testDispatcher) {
        val clientName = "Anjali"
        val tomorrowStr = LocalDate.now().plusDays(1).toString()
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = clientName,
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = tomorrowStr,
            reminderTime = "10:00",
            reminderNote = "Meeting",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        val params = mapOf(
            "operation" to "complete",
            "name" to clientName
        )

        val eventList = mutableListOf<AIEvent>()
        val job = launch {
            AIEventBus.events.collect { eventList.add(it) }
        }

        val tool = ReminderTool()
        val result = tool.execute(params)

        assertTrue(result.success)

        val updatedClient = database.leadDao.getLeadById(client.id)
        assertEquals("Completed", updatedClient?.reminderStatus)

        assertTrue(eventList.any { it is ReminderCompletedEvent && it.leadId == client.id })

        job.cancel()
    }

    @Test
    fun testReminderDeletion_ConfirmationRequiredAndSuccess() = runTest(testDispatcher) {
        val clientName = "Vikram"
        val tomorrowStr = LocalDate.now().plusDays(1).toString()
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = clientName,
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = tomorrowStr,
            reminderTime = "10:00",
            reminderNote = "Meeting",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        val tool = ReminderTool()

        // Step 1: Request delete without confirmation
        val initResult = tool.execute(mapOf("operation" to "delete", "name" to clientName))
        assertFalse(initResult.success)
        assertEquals("CONFIRMATION_REQUIRED", initResult.data["status"])

        // Step 2: Request delete with confirmation
        val eventList = mutableListOf<AIEvent>()
        val job = launch {
            AIEventBus.events.collect { eventList.add(it) }
        }

        val confirmResult = tool.execute(mapOf("operation" to "delete", "name" to clientName, "confirm" to true))
        assertTrue(confirmResult.success)

        val updatedClient = database.leadDao.getLeadById(client.id)
        assertEquals("", updatedClient?.reminderDate)
        assertEquals("", updatedClient?.reminderTime)
        assertEquals("", updatedClient?.reminderNote)

        assertTrue(eventList.any { it is ReminderDeletedEvent && it.leadId == client.id })

        job.cancel()
    }

    @Test
    fun testConversationFlow_MissingFieldClarification() = runTest(testDispatcher) {
        val conversationEngine = AIConversationEngine(AIManager.stateMachine, AIEventBus)

        // Request reminder for Vikram without date/time
        val response = conversationEngine.processMessage("Vikram ko reminder laga do")
        assertEquals(IntentType.CREATE_REMINDER, response.intentType)
        assertFalse(response.isComplete)
        assertEquals("When should we set this reminder for? (e.g. today, tomorrow, or YYYY-MM-DD)", response.replyText)
    }
}
