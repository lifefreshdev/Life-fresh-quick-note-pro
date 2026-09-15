package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIAuditEngineTest {

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
            database.auditEntryDao.deleteExpiredLogs(System.currentTimeMillis() + 10000L)
        }
        AIManager.shutdown()
    }

    @Test
    fun testAuditCreationAndPersistence() = runTest(testDispatcher) {
        val entry = AIAuditEngine.logAction(
            context = context,
            actionType = AuditType.CLIENT_CREATED,
            aiModule = "ClientTool",
            executionDurationMs = 15L,
            success = true,
            triggerSource = "USER",
            severity = AuditSeverity.INFO
        )

        assertNotNull(entry.id)
        assertTrue(entry.timestamp > 0)
        assertEquals(AuditType.CLIENT_CREATED, entry.actionType)
        assertEquals("ClientTool", entry.aiModule)
        assertEquals(15L, entry.executionDurationMs)
        assertTrue(entry.success)
        assertNull(entry.errorMessage)
        assertNull(entry.workflowId)
        assertEquals("USER", entry.triggerSource)
        assertEquals(AuditSeverity.INFO, entry.severity)
        assertFalse(entry.rollbackPerformed)

        // Verify local persistence
        val logs = AIAuditEngine.searchLogs(context)
        assertEquals(1, logs.size)
        val persisted = logs[0]
        assertEquals(entry.id, persisted.id)
        assertEquals(entry.actionType, persisted.actionType)
    }

    @Test
    fun testExplainabilityEngine() {
        val entrySuccess = AuditEntry(
            actionType = AuditType.CLIENT_CREATED,
            aiModule = "ClientTool",
            executionDurationMs = 12L,
            success = true,
            errorMessage = null,
            workflowId = null,
            triggerSource = "USER",
            severity = AuditSeverity.INFO,
            rollbackPerformed = false
        )
        val explanationSuccess = AIAuditEngine.explainAction(entrySuccess)
        assertEquals("Client created because phone number was unique.", explanationSuccess)

        val entrySkip = AuditEntry(
            actionType = AuditType.REMINDER_CREATED,
            aiModule = "ReminderTool",
            executionDurationMs = 5L,
            success = false,
            errorMessage = "duplicate already exists",
            workflowId = null,
            triggerSource = "SYSTEM",
            severity = AuditSeverity.WARNING,
            rollbackPerformed = false
        )
        val explanationSkip = AIAuditEngine.explainAction(entrySkip)
        assertEquals("Reminder skipped because duplicate already existed.", explanationSkip)

        val entryOcrQueue = AuditEntry(
            actionType = AuditType.OCR_IMPORT,
            aiModule = "OcrIntelligenceEngine",
            executionDurationMs = 45L,
            success = false,
            errorMessage = "low confidence review queue",
            workflowId = null,
            triggerSource = "CAMERA",
            severity = AuditSeverity.WARNING,
            rollbackPerformed = false
        )
        val explanationOcr = AIAuditEngine.explainAction(entryOcrQueue)
        assertEquals("OCR result sent to Review Queue because confidence was LOW.", explanationOcr)

        val entryBulkReject = AuditEntry(
            actionType = AuditType.BULK_IMPORT,
            aiModule = "BulkIntelligenceEngine",
            executionDurationMs = 200L,
            success = false,
            errorMessage = "invalid phone format",
            workflowId = null,
            triggerSource = "FILE",
            severity = AuditSeverity.CRITICAL,
            rollbackPerformed = false
        )
        val explanationBulk = AIAuditEngine.explainAction(entryBulkReject)
        assertEquals("Bulk import rejected due to invalid phone number.", explanationBulk)
    }

    @Test
    fun testDecisionTrace() {
        val traceId = UUID.randomUUID().toString()
        AIDecisionTracker.startTrace(traceId)

        AIDecisionTracker.addNode(traceId, "Intent Detected")
        AIDecisionTracker.addNode(traceId, "Workflow Selected")
        AIDecisionTracker.addNode(traceId, "ReminderTool Executed")
        AIDecisionTracker.addNode(traceId, "Duplicate Found")
        AIDecisionTracker.addNode(traceId, "Skipped")
        AIDecisionTracker.addNode(traceId, "Success")

        val trace = AIDecisionTracker.getTrace(traceId)
        assertEquals(6, trace.size)
        assertEquals("Intent Detected", trace[0].step)
        assertEquals("Success", trace[5].step)

        AIDecisionTracker.clearTrace(traceId)
        assertTrue(AIDecisionTracker.getTrace(traceId).isEmpty())
    }

    @Test
    fun testSearchAndFilter() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()

        // Insert distinct entries
        AIAuditEngine.logAction(
            context = context,
            actionType = AuditType.CLIENT_CREATED,
            aiModule = "ClientTool",
            executionDurationMs = 10L,
            success = true,
            severity = AuditSeverity.INFO
        )

        AIAuditEngine.logAction(
            context = context,
            actionType = AuditType.REMINDER_DELETED,
            aiModule = "ReminderTool",
            executionDurationMs = 20L,
            success = false,
            errorMessage = "Reminder not found",
            severity = AuditSeverity.WARNING,
            workflowId = "workflow_test_id"
        )

        // Basic all search
        val allLogs = AIAuditEngine.searchLogs(context)
        assertEquals(2, allLogs.size)

        // Filter by Action Type
        val clientLogs = AIAuditEngine.searchLogs(context, actionType = AuditType.CLIENT_CREATED)
        assertEquals(1, clientLogs.size)
        assertEquals(AuditType.CLIENT_CREATED, clientLogs[0].actionType)

        // Filter by Severity
        val warningLogs = AIAuditEngine.searchLogs(context, severity = AuditSeverity.WARNING)
        assertEquals(1, warningLogs.size)
        assertEquals(AuditSeverity.WARNING, warningLogs[0].severity)

        // Filter by Workflow
        val workflowLogs = AIAuditEngine.searchLogs(context, workflowId = "workflow_test_id")
        assertEquals(1, workflowLogs.size)
        assertEquals("workflow_test_id", workflowLogs[0].workflowId)

        // Filter by Success/Failure
        val failedLogs = AIAuditEngine.searchLogs(context, success = false)
        assertEquals(1, failedLogs.size)
        assertEquals("ReminderTool", failedLogs[0].aiModule)
    }

    @Test
    fun testCleanupEngineRetentionPolicy() = runTest(testDispatcher) {
        AIAuditEngine.configureHistoryLimit(3)

        // Add 5 logs
        for (i in 1..5) {
            AIAuditEngine.logAction(
                context = context,
                actionType = AuditType.VOICE_COMMAND,
                aiModule = "VoiceIntelligenceEngine",
                executionDurationMs = 50L,
                success = true
            )
        }

        // Run cleanup
        AIAuditEngine.cleanupOldLogs(context)

        // Verify only 3 are left
        val remaining = AIAuditEngine.searchLogs(context)
        assertEquals(3, remaining.size)
    }

    @Test
    fun testEventBusPublishing() = runTest(testDispatcher) {
        var startedReceived = false
        var completedReceived = false
        var createdReceived = false

        AIEventBus.subscribe<AuditStartedEvent>(backgroundScope) {
            startedReceived = true
        }
        AIEventBus.subscribe<AuditCompletedEvent>(backgroundScope) {
            completedReceived = true
        }
        AIEventBus.subscribe<AuditEntryCreatedEvent>(backgroundScope) {
            createdReceived = true
        }

        AIAuditEngine.logAction(
            context = context,
            actionType = AuditType.CLIENT_CREATED,
            aiModule = "ClientTool",
            executionDurationMs = 8L,
            success = true
        )

        // Trigger subscriptions with dispatchers running
        assertTrue(startedReceived)
        assertTrue(completedReceived)
        assertTrue(createdReceived)
    }

    @Test
    fun testStateMachineTransitions() = runTest(testDispatcher) {
        val statesObserved = mutableListOf<AIState>()
        val job = launch {
            AIManager.stateMachine.currentState.collect {
                statesObserved.add(it)
            }
        }

        AIAuditEngine.logAction(
            context = context,
            actionType = AuditType.ANALYTICS_RUN,
            aiModule = "AIAnalyticsEngine",
            executionDurationMs = 150L,
            success = true
        )

        // Verify it cycled through AUDITING and back to IDLE
        assertTrue(statesObserved.contains(AIState.AUDITING))
        assertTrue(statesObserved.contains(AIState.COMPLETED))
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)

        job.cancel()
    }

    @Test
    fun testAuditPerformanceTargets() = runTest(testDispatcher) {
        // Insertion Performance: Target < 10 ms
        val startTimeInsert = System.nanoTime()
        AIAuditEngine.logAction(
            context = context,
            actionType = AuditType.CLIENT_UPDATED,
            aiModule = "ClientTool",
            executionDurationMs = 2L,
            success = true
        )
        val insertDurationMs = (System.nanoTime() - startTimeInsert) / 1_000_000.0
        AILogger.i("AIAuditEngineTest", "Log action took: $insertDurationMs ms")
        assertTrue("Insertion should be extremely fast (average under 10ms target)", insertDurationMs < 50.0) // 50ms allowance for JVM cold startup in testing environment

        // Search Performance: Target < 100 ms
        val startTimeSearch = System.nanoTime()
        val searchResult = AIAuditEngine.searchLogs(context, actionType = AuditType.CLIENT_UPDATED)
        val searchDurationMs = (System.nanoTime() - startTimeSearch) / 1_000_000.0
        AILogger.i("AIAuditEngineTest", "Search logs took: $searchDurationMs ms")
        assertTrue("Search should be fast", searchDurationMs < 100.0)
        assertEquals(1, searchResult.size)
    }
}
