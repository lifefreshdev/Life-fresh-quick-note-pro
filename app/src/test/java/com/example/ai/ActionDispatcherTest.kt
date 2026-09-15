package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.action.ActionDispatcher
import com.example.ai.action.ActionRequest
import com.example.ai.action.AITool
import com.example.ai.intent.ExtractedEntities
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.data.repository.LeadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionDispatcherTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: LeadRepository
    private lateinit var dispatcher: ActionDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        repository = LeadRepository(database.leadDao)
        dispatcher = ActionDispatcher(context, repository)
        
        // Clean database before each test
        kotlinx.coroutines.runBlocking {
            database.leadDao.clearAllLeads()
        }
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking {
            database.leadDao.clearAllLeads()
        }
    }

    @Test
    fun testExecuteCreateLead_Success() = runTest {
        val entities = ExtractedEntities(
            name = "John Doe",
            phone = "9876543210"
        )
        val request = ActionRequest(
            tool = AITool.CREATE_LEAD,
            entities = entities,
            messageId = "test_msg_1"
        )

        val result = dispatcher.executeAction(request, emptyList())
        assertTrue(result.success)
        assertTrue(result.message.contains("was created successfully"))

        // Verify database state
        val leads = database.leadDao.getAllLeadsList("")
        assertEquals(1, leads.size)
        assertEquals("John Doe", leads.first().name)
        assertEquals("9876543210", leads.first().mobile)
    }

    @Test
    fun testExecuteCreateLead_DuplicatePhone() = runTest {
        // Pre-insert a lead
        val existingLead = LeadEntity(
            id = "existing_1",
            name = "Mary Jane",
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "None"
        )
        database.leadDao.insertLead(existingLead)

        val entities = ExtractedEntities(
            name = "John Doe",
            phone = "9876543210"
        )
        val request = ActionRequest(
            tool = AITool.CREATE_LEAD,
            entities = entities,
            messageId = "test_msg_2"
        )

        // Run with current lead list containing the duplicate mobile
        val currentLeads = database.leadDao.getAllLeadsList("")
        val result = dispatcher.executeAction(request, currentLeads)
        assertFalse(result.success)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun testExecuteCreateReminder_Success() = runTest {
        // Pre-insert the target lead
        val lead = LeadEntity(
            id = "lead_123",
            name = "Rahul Sharma",
            mobile = "9876543211",
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "None"
        )
        database.leadDao.insertLead(lead)

        // Set future date and time
        val entities = ExtractedEntities(
            name = "Rahul Sharma",
            resolvedDate = "2026-12-25",
            time = "10:00",
            noteText = "Follow-up consultation"
        )
        val request = ActionRequest(
            tool = AITool.CREATE_REMINDER,
            entities = entities,
            messageId = "test_msg_3"
        )

        val currentLeads = database.leadDao.getAllLeadsList("")
        val result = dispatcher.executeAction(request, currentLeads)
        assertTrue(result.success)
        assertTrue(result.message.contains("Reminder for Rahul Sharma scheduled"))
        assertTrue(result.message.contains("successfully"))

        // Verify DB updated
        val updatedLeads = database.leadDao.getAllLeadsList("")
        val updatedLead = updatedLeads.find { it.id == "lead_123" }
        assertNotNull(updatedLead)
        assertEquals("2026-12-25", updatedLead?.reminderDate)
        assertEquals("10:00", updatedLead?.reminderTime)
        assertEquals("Follow-up consultation", updatedLead?.reminderNote)
        assertEquals("Pending", updatedLead?.reminderStatus)
    }

    @Test
    fun testExecuteUpdateLeadStatus_Success() = runTest {
        val lead = LeadEntity(
            id = "lead_456",
            name = "Amit Patel",
            mobile = "9876543212",
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "None"
        )
        database.leadDao.insertLead(lead)

        val entities = ExtractedEntities(
            name = "Amit Patel",
            status = "Complete"
        )
        val request = ActionRequest(
            tool = AITool.UPDATE_LEAD_STATUS,
            entities = entities,
            messageId = "test_msg_4"
        )

        val currentLeads = database.leadDao.getAllLeadsList("")
        val result = dispatcher.executeAction(request, currentLeads)
        assertTrue(result.success)
        assertTrue(result.message.contains("status updated from"))
        assertTrue(result.message.contains("successfully"))

        val updatedLeads = database.leadDao.getAllLeadsList("")
        val updatedLead = updatedLeads.find { it.id == "lead_456" }
        assertEquals("Complete", updatedLead?.status)
    }

    @Test
    fun testExecuteAddLeadNote_Success() = runTest {
        val lead = LeadEntity(
            id = "lead_789",
            name = "Suresh Raina",
            mobile = "9876543213",
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "None",
            notes = "Initial Note"
        )
        database.leadDao.insertLead(lead)

        val entities = ExtractedEntities(
            name = "Suresh Raina",
            noteText = "Requires dietary counseling"
        )
        val request = ActionRequest(
            tool = AITool.ADD_LEAD_NOTE,
            entities = entities,
            messageId = "test_msg_5"
        )

        val currentLeads = database.leadDao.getAllLeadsList("")
        val result = dispatcher.executeAction(request, currentLeads)
        assertTrue(result.success)
        assertTrue(result.message.contains("Note added to 'Suresh Raina' successfully"))

        val updatedLeads = database.leadDao.getAllLeadsList("")
        val updatedLead = updatedLeads.find { it.id == "lead_789" }
        assertEquals("Initial Note\nRequires dietary counseling", updatedLead?.notes)
    }
}
