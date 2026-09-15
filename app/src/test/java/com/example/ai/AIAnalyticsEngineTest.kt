package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.data.database.ReviewQueueEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIAnalyticsEngineTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
        database = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        runTest(testDispatcher) {
            database.leadDao.clearAllLeads()
            database.reviewQueueDao.clearReviewQueue()
        }
        AIManager.shutdown()
    }

    @Test
    fun testRunAnalyticsSuccessfullyCalculatesMetrics() = runTest(testDispatcher) {
        // Insert active and archived leads with diseases
        val lead1 = LeadEntity(
            id = "lead_1",
            name = "John Doe",
            mobile = "1234567890",
            diseases = "[\"Diabetes\", \"Hypertension\"]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "2026-06-20",
            reminderTime = "10:00",
            reminderNote = "Follow-up",
            reminderStatus = "Pending",
            archived = false,
            timestamp = System.currentTimeMillis()
        )

        val lead2 = LeadEntity(
            id = "lead_2",
            name = "Jane Smith",
            mobile = "0987654321",
            diseases = "[\"Diabetes\"]",
            otherDisease = "Asthma",
            relation = "Mother",
            otherRelation = "",
            status = "Complete",
            reminderDate = "2026-06-25",
            reminderTime = "15:00",
            reminderNote = "Check blood pressure",
            reminderStatus = "Completed",
            archived = true,
            timestamp = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L) // 5 days ago
        )

        database.leadDao.insertLead(lead1)
        database.leadDao.insertLead(lead2)

        val result = AIAnalyticsEngine.runAnalytics(context)

        assertTrue(result.success)
        val summary = result.summary

        // Client metrics
        assertEquals(2, summary["total_clients"])
        assertEquals(1, summary["active_clients"])
        assertEquals(1, summary["archived_clients"])
        assertEquals(2, summary["newly_added_clients"])

        // Disease metrics
        assertEquals("Diabetes", summary["most_common_disease"])
        val freq = summary["disease_frequency"] as? Map<*, *>
        assertNotNull(freq)
        assertEquals(2, freq?.get("Diabetes"))
        assertEquals(1, freq?.get("Hypertension"))
        assertEquals(1, freq?.get("Asthma"))

        // Overdue and reminder metrics
        val pendingCount = summary["pending_reminders"] as? Int ?: 0
        assertEquals(1, pendingCount)

        // Monitoring API fields are populated correctly
        assertTrue(AIAnalyticsEngine.lastAnalyticsTime > 0)
        assertTrue(AIAnalyticsEngine.executionDuration >= 0)
        assertTrue(AIAnalyticsEngine.insightCount > 0)
        assertNotNull(AIAnalyticsEngine.statisticsSummary["total_clients"])
    }

    @Test
    fun testThresholdAlertsForOverdueReminders() = runTest(testDispatcher) {
        // Create 6 overdue reminders to trigger CRITICAL/WARNING alert
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val pastDateStr = sdf.format(Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)) // 2 days ago

        for (i in 1..6) {
            val lead = LeadEntity(
                id = "lead_overdue_$i",
                name = "Client $i",
                mobile = "1234567890",
                diseases = "[]",
                otherDisease = "",
                relation = "Self",
                otherRelation = "",
                status = "Pending",
                reminderDate = pastDateStr,
                reminderTime = "09:00",
                reminderNote = "Overdue task",
                reminderStatus = "Pending",
                timestamp = System.currentTimeMillis()
            )
            database.leadDao.insertLead(lead)
        }

        val result = AIAnalyticsEngine.runAnalytics(context)
        assertTrue(result.success)

        val overdueCount = result.summary["overdue_reminders"] as? Int ?: 0
        assertTrue("Overdue reminders should be at least 6", overdueCount >= 6)

        // Find critical/warning insight about overdue reminders
        val overdueInsight = result.insights.find { it.text.contains("overdue reminders") }
        assertNotNull(overdueInsight)
        assertEquals(InsightSeverity.CRITICAL, overdueInsight?.severity)
    }

    @Test
    fun testNaturalLanguageQueries() = runTest(testDispatcher) {
        val lead = LeadEntity(
            id = "lead_nl",
            name = "Test User",
            mobile = "1112223333",
            diseases = "[\"Diabetes\"]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            timestamp = System.currentTimeMillis()
        )
        database.leadDao.insertLead(lead)

        val result = AIAnalyticsEngine.runAnalytics(context)
        assertTrue(result.success)

        // Query: How many clients?
        val replyClients = AIAnalyticsEngine.handleNaturalLanguageQuery(context, "how many clients?", result)
        assertTrue(replyClients.contains("Total clients registered: 1"))

        // Query: Most common disease?
        val replyDisease = AIAnalyticsEngine.handleNaturalLanguageQuery(context, "most common disease?", result)
        assertTrue(replyDisease.contains("Diabetes"))

        // Query: Overdue reminders?
        val replyOverdue = AIAnalyticsEngine.handleNaturalLanguageQuery(context, "overdue reminders?", result)
        assertTrue(replyOverdue.contains("overdue reminder"))
    }
}
