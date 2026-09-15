package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.data.database.ReviewQueueEntity
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AISchedulerEngineTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Ensure AIManager is initialized
        if (!AIManager.isInitialized()) {
            AIManager.initialize(context)
        }
    }

    @After
    fun tearDown() {
        // Shutdown AI systems
        if (AIManager.isInitialized()) {
            AIManager.shutdown()
        }
    }

    @Test
    fun testTaskSchedulingAndExposeAPI() = runTest(testDispatcher) {
        val task = ScheduledTask(
            id = "test_sched_1",
            name = "Test Sched 1",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.Manual,
            execute = { TaskExecutionResult(true) }
        )

        AISchedulerEngine.scheduleTask(context, task)

        val pending = AISchedulerEngine.getPendingTasks()
        assertTrue(pending.any { it.id == "test_sched_1" })
        assertEquals("PENDING", task.status)
    }

    @Test
    fun testPriorityOrdering() = runTest(testDispatcher) {
        val executionOrder = mutableListOf<String>()

        val normalTask = ScheduledTask(
            id = "normal_task",
            name = "Normal Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.TimeTrigger(0L),
            execute = {
                executionOrder.add("NORMAL")
                TaskExecutionResult(true)
            }
        )

        val criticalTask = ScheduledTask(
            id = "critical_task",
            name = "Critical Task",
            priority = TaskPriority.CRITICAL,
            trigger = TaskTrigger.TimeTrigger(0L),
            execute = {
                executionOrder.add("CRITICAL")
                TaskExecutionResult(true)
            }
        )

        // Reset tasks in engine for predictable testing
        AISchedulerEngine.scheduleTask(context, normalTask)
        AISchedulerEngine.scheduleTask(context, criticalTask)

        // Force execute due tasks
        AISchedulerEngine.executePeriodicTasks(context)

        assertEquals(listOf("CRITICAL", "NORMAL"), executionOrder)
    }

    @Test
    fun testDuplicatePrevention() = runTest(testDispatcher) {
        var runCount = 0
        lateinit var taskRef: ScheduledTask
        val task = ScheduledTask(
            id = "dup_prevent_task",
            name = "Duplicate Prevent Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.Manual,
            execute = {
                runCount++
                // Try executing the same task recursively to test duplicate prevention
                val recursiveResult = AISchedulerEngine.executeTask(it, taskRef)
                assertFalse("Recursive attempt of same running task must fail", recursiveResult.success)
                TaskExecutionResult(true)
            }
        )
        taskRef = task

        val result = AISchedulerEngine.executeTask(context, task)
        assertTrue(result.success)
        assertEquals(1, runCount)
    }

    @Test
    fun testRetryAndBackoffLogic() = runTest(testDispatcher) {
        var attempts = 0
        val failingTask = ScheduledTask(
            id = "fail_retry_task",
            name = "Fail Retry Task",
            priority = TaskPriority.HIGH,
            trigger = TaskTrigger.Manual,
            maxRetries = 2,
            backoffMs = 50L,
            execute = {
                attempts++
                if (attempts < 3) {
                    TaskExecutionResult(false, "Simulated Error $attempts")
                } else {
                    TaskExecutionResult(true)
                }
            }
        )

        AISchedulerEngine.executeTask(context, failingTask)

        // Wait for retry delayed execution coroutine to complete
        Thread.sleep(250)

        assertEquals(3, attempts)
        assertEquals("COMPLETED", failingTask.status)
        assertNull(failingTask.error)
    }

    @Test
    fun testTriggerHandling() = runTest(testDispatcher) {
        var dbTriggered = false
        val dbTask = ScheduledTask(
            id = "db_trigger_task",
            name = "DB Trigger Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.DatabaseChangeTrigger("leads"),
            execute = {
                dbTriggered = true
                TaskExecutionResult(true)
            }
        )

        AISchedulerEngine.scheduleTask(context, dbTask)
        AISchedulerEngine.triggerDatabaseChange(context, "leads")

        Thread.sleep(100)
        assertTrue(dbTriggered)
    }

    @Test
    fun testPeriodicExecution() = runTest(testDispatcher) {
        var periodicCount = 0
        val periodicTask = ScheduledTask(
            id = "periodic_task_test",
            name = "Periodic Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.TimeTrigger(100L, isPeriodic = true),
            execute = {
                periodicCount++
                TaskExecutionResult(true)
            }
        )

        AISchedulerEngine.scheduleTask(context, periodicTask)
        periodicTask.nextExecutionTime = System.currentTimeMillis() - 10L // Force due

        AISchedulerEngine.executePeriodicTasks(context)
        assertEquals(1, periodicCount)
        assertEquals("PENDING", periodicTask.status)
        assertTrue(periodicTask.nextExecutionTime > System.currentTimeMillis())
    }

    @Test
    fun testEventBusNotifications() = runTest(testDispatcher) {
        val receivedEvents = mutableListOf<AIEvent>()
        
        AIEventBus.subscribe<TaskStartedEvent>(backgroundScope) { event ->
            receivedEvents.add(event)
        }
        AIEventBus.subscribe<TaskCompletedEvent>(backgroundScope) { event ->
            receivedEvents.add(event)
        }

        val testTask = ScheduledTask(
            id = "bus_test_task",
            name = "Bus Test Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.Manual,
            execute = { TaskExecutionResult(true) }
        )

        AISchedulerEngine.executeTask(context, testTask)

        Thread.sleep(100)
        assertTrue(receivedEvents.any { it is TaskStartedEvent })
        assertTrue(receivedEvents.any { it is TaskCompletedEvent })
    }

    @Test
    fun testStateTransitions() = runTest(testDispatcher) {
        val stateChanges = mutableListOf<AIState>()
        
        // Listen to state changes
        val testTask = ScheduledTask(
            id = "state_test_task",
            name = "State Test Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.Manual,
            execute = {
                stateChanges.add(AIManager.stateMachine.currentState.value)
                TaskExecutionResult(true)
            }
        )

        AISchedulerEngine.executeTask(context, testTask)
        
        assertTrue(stateChanges.contains(AIState.RUNNING))
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    @Test
    fun testDuplicateScannerTask() = runTest(testDispatcher) {
        val db = AppDatabase.getDatabase(context)
        db.leadDao.clearAllLeads()
        db.reviewQueueDao.clearReviewQueue()

        // Create duplicate leads
        val lead1 = LeadEntity(
            id = "lead_1",
            name = "Amit Sharma",
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
        val lead2 = LeadEntity(
            id = "lead_2",
            name = "Amit Sharma",
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

        db.leadDao.insertLead(lead1)
        db.leadDao.insertLead(lead2)

        // Find and trigger Duplicate Scanner task
        val dupTask = AISchedulerEngine.getPendingTasks().find { it.id == "duplicate_scanner" }
        assertNotNull(dupTask)

        val result = AISchedulerEngine.executeTask(context, dupTask!!)
        assertTrue(result.success)

        val reviewQueueItems = db.reviewQueueDao.getAllReviewItems()
        assertTrue(reviewQueueItems.isNotEmpty())
        assertTrue(reviewQueueItems.any { it.reason.contains("Duplicate detected") })
    }

    @Test
    fun testHealthMonitorTask() = runTest(testDispatcher) {
        var healthCompleted = false
        AIEventBus.subscribe<HealthCheckCompletedEvent>(backgroundScope) {
            healthCompleted = true
        }

        val healthTask = AISchedulerEngine.getPendingTasks().find { it.id == "runtime_health_check" }
        assertNotNull(healthTask)

        val result = AISchedulerEngine.executeTask(context, healthTask!!)
        assertTrue(result.success)
        
        Thread.sleep(100)
        assertTrue(healthCompleted)
    }

    @Test
    fun testStartupRestoration() = runTest(testDispatcher) {
        val task = ScheduledTask(
            id = "restore_test_task",
            name = "Restore Test Task",
            priority = TaskPriority.NORMAL,
            trigger = TaskTrigger.Manual,
            execute = { TaskExecutionResult(true) }
        )

        AISchedulerEngine.scheduleTask(context, task)
        task.status = "COMPLETED"
        task.lastExecutionTime = 123456L

        // Trigger persistence
        AISchedulerEngine.scheduleTask(context, task)

        // Now shutdown and initialize to test restoration
        AIManager.shutdown()
        AIManager.initialize(context)

        val restoredTask = AISchedulerEngine.getCompletedTasks().find { it.id == "restore_test_task" }
        assertNotNull(restoredTask)
        assertEquals("COMPLETED", restoredTask?.status)
        assertEquals(123456L, restoredTask?.lastExecutionTime)
    }
}
