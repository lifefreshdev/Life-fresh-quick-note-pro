package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentIntelligenceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var engine: DocumentIntelligenceEngine

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
    fun testTxtParsing_Success() = runTest(testDispatcher) {
        val txtContent = """
            Name: Manoj Kumar
            Phone: 9876543210
            Email: manoj@example.com
            Address: New Delhi
            Disease: Diabetes, Hypertension
            Notes: Regular checkup needed
            Follow-Up: tomorrow
            Reminder Note: Call Manoj for appointment
            
            Name: Sneha Patel
            Phone: 9988776655
            Email: sneha@example.com
            Disease: Thyroid
            Notes: High BP
        """.trimIndent()

        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream(txtContent.toByteArray())
        val result = engine.importDocument("clients_list.txt", inputStream)

        assertTrue(result.success)
        assertEquals(2, result.totalProcessed)
        assertEquals(2, result.importedCount)
        assertEquals(0, result.failedCount)

        // Verify database insertions
        val leads = database.leadDao.getAllLeadsList()
        assertEquals(2, leads.size)

        val manoj = leads.find { it.name == "Manoj Kumar" }
        assertNotNull(manoj)
        assertEquals("9876543210", manoj?.mobile)
        assertEquals("Regular checkup needed", manoj?.notes)
        assertEquals("Call Manoj for appointment", manoj?.reminderNote)
        assertEquals(LocalDate.now().plusDays(1).toString(), manoj?.reminderDate)

        val sneha = leads.find { it.name == "Sneha Patel" }
        assertNotNull(sneha)
        assertEquals("9988776655", sneha?.mobile)
        assertEquals("High BP", sneha?.notes)
    }

    @Test
    fun testCsvParsing_Success() = runTest(testDispatcher) {
        val csvContent = """
            Name,Phone,Email,Disease,Notes,Follow-Up
            Rajesh Sharma,9123456789,rajesh@example.com,Insomnia,Needs counseling,2026-07-05
            Amit Singh,8234567890,amit@example.com,,No issues,
        """.trimIndent()

        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream(csvContent.toByteArray())
        val result = engine.importDocument("clients_list.csv", inputStream)

        assertTrue(result.success)
        assertEquals(2, result.totalProcessed)
        assertEquals(2, result.importedCount)

        val leads = database.leadDao.getAllLeadsList()
        assertEquals(2, leads.size)

        val rajesh = leads.find { it.name == "Rajesh Sharma" }
        assertNotNull(rajesh)
        assertEquals("9123456789", rajesh?.mobile)
        assertEquals("Needs counseling", rajesh?.notes)
        assertEquals("2026-07-05", rajesh?.reminderDate)
    }

    @Test
    fun testPdfParsing_Success() = runTest(testDispatcher) {
        val pdfContent = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R] /Count 1 >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>
            endobj
            4 0 obj
            << /Length 120 >>
            stream
            (Name: Vikram Sen) Tj
            (Phone: 8877665544) Tj
            (Email: vikram@example.com) Tj
            (Disease: Asthma) Tj
            (Notes: Patient needs nebulizer) Tj
            (Follow-Up: today) Tj
            endstream
            endobj
            xref
            trailer
            << /Root 1 0 R >>
            %%EOF
        """.trimIndent()

        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream(pdfContent.toByteArray())
        val result = engine.importDocument("patients_report.pdf", inputStream)

        assertTrue(result.success)
        assertEquals(1, result.totalProcessed)
        assertEquals(1, result.importedCount)

        val leads = database.leadDao.getAllLeadsList()
        val vikram = leads.find { it.name == "Vikram Sen" }
        assertNotNull(vikram)
        assertEquals("8877665544", vikram?.mobile)
        assertEquals("Patient needs nebulizer", vikram?.notes)
        assertEquals(LocalDate.now().toString(), vikram?.reminderDate)
    }

    @Test
    fun testValidation_EmptyDocument() = runTest(testDispatcher) {
        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream("".toByteArray())
        val result = engine.importDocument("empty_file.txt", inputStream)

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("empty") == true)
        assertEquals(ImportProgressState.FAILED, engine.progressState.value)
    }

    @Test
    fun testValidation_NoClientSynonyms() = runTest(testDispatcher) {
        val invalidContent = """
            This is just random text talking about weather in Bangalore.
            It is very pleasant today and we might get some rain.
            No personal information is present here.
        """.trimIndent()

        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream(invalidContent.toByteArray())
        val result = engine.importDocument("weather_report.txt", inputStream)

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("Validation failed") == true)
    }

    @Test
    fun testValidation_InvalidFields() = runTest(testDispatcher) {
        val txtContent = """
            Name: Raman
            Phone: 123  // too short, invalid
            
            Name: 
            Phone: 9876543210 // missing name
            
            Name: Akash
            Phone: 9876543210
            Follow-Up: 2020-01-01 // past date
        """.trimIndent()

        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream(txtContent.toByteArray())
        val result = engine.importDocument("invalid_fields.txt", inputStream)

        assertTrue(result.success)
        assertEquals(3, result.totalProcessed)
        assertEquals(0, result.importedCount)
        assertEquals(3, result.failedCount)
        assertEquals(3, result.failures.size)
    }

    @Test
    fun testDuplicateDetection() = runTest(testDispatcher) {
        val existingClient = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = "Ravi Kumar",
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
        database.leadDao.insertLead(existingClient)

        val txtContent = """
            Name: Ravi Kumar
            Phone: 9876543210
            Notes: Some import notes
        """.trimIndent()

        engine = DocumentIntelligenceEngine()
        val inputStream = ByteArrayInputStream(txtContent.toByteArray())
        val result = engine.importDocument("ravi_list.txt", inputStream)

        assertTrue(result.success)
        assertEquals(1, result.totalProcessed)
        assertEquals(0, result.importedCount)
        assertEquals(1, result.duplicateCount)
    }
}
