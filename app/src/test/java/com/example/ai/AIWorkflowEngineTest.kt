package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIWorkflowEngineTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
    }

    @After
    fun tearDown() {
        AIManager.shutdown()
    }

    @Test
    fun testTwoStepWorkflowSuccess() = runTest(testDispatcher) {
        var step1RunCount = 0
        var step2RunCount = 0

        val step1 = WorkflowStep(
            id = "step1",
            name = "First Step",
            execute = { context ->
                step1RunCount++
                StepResult(true, mapOf("val1" to "hello"))
            }
        )

        val step2 = WorkflowStep(
            id = "step2",
            name = "Second Step",
            dependsOn = listOf("step1"),
            execute = { context ->
                step2RunCount++
                val inherited = context.get("step1.val1") as? String
                assertEquals("hello", inherited)
                StepResult(true, mapOf("val2" to "world"))
            }
        )

        val workflow = WorkflowDefinition(
            name = "Two Step Test",
            steps = listOf(step1, step2)
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertTrue(result.success)
        assertEquals(1, step1RunCount)
        assertEquals(1, step2RunCount)
        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(listOf("step1", "step2"), result.completedSteps)
    }

    @Test
    fun testThreeStepWorkflowSuccess() = runTest(testDispatcher) {
        var runCount = 0
        val steps = (1..3).map { i ->
            WorkflowStep(
                id = "step$i",
                name = "Step $i",
                execute = {
                    runCount++
                    StepResult(true, mapOf("step$i" to i))
                }
            )
        }

        val workflow = WorkflowDefinition(
            name = "Three Step Test",
            steps = steps
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertTrue(result.success)
        assertEquals(3, runCount)
        assertEquals(3, result.successCount)
        assertEquals(listOf("step1", "step2", "step3"), result.completedSteps)
    }

    @Test
    fun testDependencyFailure() = runTest(testDispatcher) {
        val step1 = WorkflowStep(
            id = "step1",
            name = "Failing Step",
            execute = {
                StepResult(false, error = "Simulated Fail")
            }
        )

        var step2Run = false
        val step2 = WorkflowStep(
            id = "step2",
            name = "Dependent Step",
            dependsOn = listOf("step1"),
            execute = {
                step2Run = true
                StepResult(true)
            }
        )

        val workflow = WorkflowDefinition(
            name = "Dependency Failure Test",
            steps = listOf(step1, step2)
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertFalse(result.success)
        assertFalse(step2Run)
        assertEquals("step1", result.failedStepId)
        assertEquals(1, result.failureCount) // step1 failed, step2 skipped
    }

    @Test
    fun testRollbackSupport() = runTest(testDispatcher) {
        var step1RolledBack = false

        val step1 = WorkflowStep(
            id = "step1",
            name = "Step 1 with rollback",
            execute = {
                StepResult(true, mapOf("data" to "abc"))
            },
            rollback = { context ->
                step1RolledBack = true
            }
        )

        val step2 = WorkflowStep(
            id = "step2",
            name = "Failing Step 2",
            dependsOn = listOf("step1"),
            execute = {
                StepResult(false, error = "Forced Step 2 Failure")
            }
        )

        val workflow = WorkflowDefinition(
            name = "Rollback Test",
            steps = listOf(step1, step2),
            isTransactional = true
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertFalse(result.success)
        assertTrue(step1RolledBack)
        assertEquals("step2", result.failedStepId)
    }

    @Test
    fun testConditionalBranching() = runTest(testDispatcher) {
        var step1Run = false
        var step2Run = false

        val step1 = WorkflowStep(
            id = "step1",
            name = "Conditional Step True",
            condition = { context -> true },
            execute = {
                step1Run = true
                StepResult(true)
            }
        )

        val step2 = WorkflowStep(
            id = "step2",
            name = "Conditional Step False",
            condition = { context -> false },
            execute = {
                step2Run = true
                StepResult(true)
            }
        )

        val workflow = WorkflowDefinition(
            name = "Conditional Test",
            steps = listOf(step1, step2)
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertTrue(result.success)
        assertTrue(step1Run)
        assertFalse(step2Run)
    }

    @Test
    fun testProgressTracking() = runTest(testDispatcher) {
        val step1 = WorkflowStep(
            id = "step1",
            name = "Step 1",
            execute = { StepResult(true) }
        )

        val workflow = WorkflowDefinition(
            name = "Progress Tracking Test",
            steps = listOf(step1)
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())
        assertTrue(result.success)

        val currentProgress = AIWorkflowEngine.progress.value
        assertNotNull(currentProgress)
        assertEquals(workflow.id, currentProgress?.workflowId)
        assertEquals(listOf("step1"), currentProgress?.completedSteps)
        assertEquals(0, currentProgress?.remainingSteps?.size)
    }

    @Test
    fun testWorkflowCancellation() = runTest(testDispatcher) {
        val step1 = WorkflowStep(
            id = "step1",
            name = "Step 1",
            execute = {
                AIWorkflowEngine.cancelWorkflow("wf_cancel", "Cancelled by test")
                StepResult(true)
            }
        )

        var step2Run = false
        val step2 = WorkflowStep(
            id = "step2",
            name = "Step 2",
            execute = {
                step2Run = true
                StepResult(true)
            }
        )

        val workflow = WorkflowDefinition(
            id = "wf_cancel",
            name = "Cancellation Test",
            steps = listOf(step1, step2)
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertFalse(result.success)
        assertFalse(step2Run)
        assertEquals("Workflow was cancelled", result.error)
    }

    @Test
    fun testRetryPolicy() = runTest(testDispatcher) {
        var attempts = 0

        val step1 = WorkflowStep(
            id = "step1",
            name = "Step with retry",
            maxRetries = 2,
            execute = {
                attempts++
                if (attempts < 3) {
                    StepResult(false, error = "Attempt $attempts failing")
                } else {
                    StepResult(true, mapOf("retried" to "success"))
                }
            }
        )

        val workflow = WorkflowDefinition(
            name = "Retry Test",
            steps = listOf(step1)
        )

        val result = AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertTrue(result.success)
        assertEquals(3, attempts) // 1 initial + 2 retries
    }

    @Test
    fun testEventBusNotifications() = runTest(testDispatcher) {
        val eventsReceived = mutableListOf<AIEvent>()

        AIEventBus.subscribe<WorkflowStartedEvent>(backgroundScope) { event ->
            eventsReceived.add(event)
        }
        AIEventBus.subscribe<WorkflowCompletedEvent>(backgroundScope) { event ->
            eventsReceived.add(event)
        }

        val step1 = WorkflowStep(
            id = "step1",
            name = "Step 1",
            execute = { StepResult(true) }
        )

        val workflow = WorkflowDefinition(
            name = "Event Bus Test",
            steps = listOf(step1)
        )

        AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        // Allow subscriber to collect the events
        Thread.sleep(100)

        val started = eventsReceived.any { it is WorkflowStartedEvent }
        val completed = eventsReceived.any { it is WorkflowCompletedEvent }

        assertTrue("Started event should be published", started)
        assertTrue("Completed event should be published", completed)
    }

    @Test
    fun testStateTransitions() = runTest(testDispatcher) {
        val statesVisited = mutableListOf<AIState>()
        
        // Listen to state changes if possible or check current final state
        val step1 = WorkflowStep(
            id = "step1",
            name = "Step 1",
            execute = {
                statesVisited.add(AIManager.stateMachine.currentState.value)
                StepResult(true)
            }
        )

        val workflow = WorkflowDefinition(
            name = "State Transition Test",
            steps = listOf(step1)
        )

        AIWorkflowEngine.executeWorkflow(workflow, emptyMap())

        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
        assertTrue(statesVisited.contains(AIState.EXECUTING_STEPS))
    }
}
