package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
@Config(sdk = [36])
class HybridRuntimeTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var manager: RuntimeManager

    // Dummy remote provider for tests
    class MockRemoteProvider(
        override val id: String,
        override val name: String,
        private val shouldSucceed: Boolean = true,
        private val supportedCapabilities: Set<Capability> = setOf(Capability.WEATHER, Capability.SUMMARIZE_PDF)
    ) : RemoteProvider {
        var callCount = 0
            private set

        override fun isAvailable(): Boolean = true

        override fun supports(capability: Capability): Boolean = supportedCapabilities.contains(capability)

        override suspend fun execute(task: AITask): TaskResult {
            callCount++
            return if (shouldSucceed) {
                TaskResult(true, "Remote Success response for ${task.capability}", null, id)
            } else {
                TaskResult(false, "", "Remote simulated failure", id)
            }
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
        manager = AIManager.runtimeManager
    }

    @After
    fun tearDown() {
        AIManager.shutdown()
    }

    @Test
    fun testDefaultLocalProviderCapabilities() {
        val providers = manager.registry.getProviders()
        assertTrue(providers.isNotEmpty())
        val defaultLocal = providers.first { it is LocalProvider }
        
        assertTrue(defaultLocal.supports(Capability.ADD_CLIENT))
        assertTrue(defaultLocal.supports(Capability.REMINDER))
        assertTrue(defaultLocal.supports(Capability.SUMMARIZE_PDF))
        assertFalse(defaultLocal.supports(Capability.WEATHER))
    }

    @Test
    fun testRuntimeModeSwitchingAndStateTransitions() = runTest(testDispatcher) {
        // Default state
        assertEquals(RuntimeMode.OFFLINE, manager.mode.value)
        assertEquals(HybridRuntimeState.OFFLINE, manager.stateMachine.state.value)

        // Switch to Hybrid
        manager.setRuntimeMode(RuntimeMode.HYBRID)
        assertEquals(RuntimeMode.HYBRID, manager.mode.value)
        assertEquals(HybridRuntimeState.HYBRID, manager.stateMachine.state.value)

        // Switch to Online
        manager.setRuntimeMode(RuntimeMode.ONLINE)
        assertEquals(RuntimeMode.ONLINE, manager.mode.value)
        assertEquals(HybridRuntimeState.ONLINE, manager.stateMachine.state.value)

        // Reset back to Offline
        manager.setRuntimeMode(RuntimeMode.OFFLINE)
        assertEquals(RuntimeMode.OFFLINE, manager.mode.value)
        assertEquals(HybridRuntimeState.OFFLINE, manager.stateMachine.state.value)
    }

    @Test
    fun testProviderRegistration() {
        val remote = MockRemoteProvider("gemini_mock", "Gemini AI")
        manager.registry.registerProvider(remote)

        val retrieved = manager.registry.getProviders()
        assertTrue(retrieved.any { it.id == "gemini_mock" })

        // Find remote provider for Weather capability
        val provider = manager.registry.findProviderFor(Capability.WEATHER, preferRemote = true)
        assertNotNull(provider)
        assertEquals("gemini_mock", provider?.id)
    }

    @Test
    fun testConnectivityMonitorAndEvents() = runTest(testDispatcher) {
        val eventsReceived = mutableListOf<AIEvent>()
        
        val job = backgroundScope.launch {
            AIEventBus.events.collect {
                eventsReceived.add(it)
            }
        }

        manager.connectivityMonitor.setInternetConnected(true)
        assertTrue(manager.connectivityMonitor.isInternetConnected.value)

        manager.connectivityMonitor.setInternetConnected(false)
        assertFalse(manager.connectivityMonitor.isInternetConnected.value)

        // Wait slightly for events to stream in
        assertTrue(eventsReceived.any { it is ProviderConnectedEvent && it.providerId == "internet_gateway" })
        assertTrue(eventsReceived.any { it is ProviderDisconnectedEvent && it.providerId == "internet_gateway" })

        job.cancel()
    }

    @Test
    fun testConsentSecurityVerification() = runTest(testDispatcher) {
        // Register remote provider
        val remote = MockRemoteProvider("gemini_mock", "Gemini AI")
        manager.registry.registerProvider(remote)

        // Enable online mode & internet
        manager.setRuntimeMode(RuntimeMode.ONLINE)
        manager.connectivityMonitor.setInternetConnected(true)

        // Task requiring consent containing sensitive payload keys
        val task = AITask(
            capability = Capability.WEATHER,
            payload = mapOf("client_phone" to "9876543210"),
            requiresConsent = true
        )

        val resultBeforeConsent = manager.executeTask(task)
        assertFalse(resultBeforeConsent.success)
        assertTrue(resultBeforeConsent.error?.contains("Consent required") == true)

        // Grant consent
        ConsentManager.grantConsent(task.id)

        val resultAfterConsent = manager.executeTask(task)
        assertTrue(resultAfterConsent.success)
        assertEquals("gemini_mock", resultAfterConsent.executedByProviderId)

        // Clean up
        ConsentManager.revokeConsent(task.id)
    }

    @Test
    fun testHybridExecutionRouting() = runTest(testDispatcher) {
        val remote = MockRemoteProvider("gemini_mock", "Gemini AI")
        manager.registry.registerProvider(remote)

        manager.setRuntimeMode(RuntimeMode.HYBRID)
        manager.connectivityMonitor.setInternetConnected(true)

        // Task supported locally (ADD_CLIENT) -> Executed locally first
        val localTask = AITask(capability = Capability.ADD_CLIENT, payload = emptyMap())
        val localResult = manager.executeTask(localTask)
        assertTrue(localResult.success)
        assertEquals("local_default", localResult.executedByProviderId)

        // Task requiring remote provider (WEATHER) -> Delegated to remote provider
        val remoteTask = AITask(capability = Capability.WEATHER, payload = emptyMap())
        val remoteResult = manager.executeTask(remoteTask)
        assertTrue(remoteResult.success)
        assertEquals("gemini_mock", remoteResult.executedByProviderId)
    }

    @Test
    fun testAutomaticFallbackAndEvents() = runTest(testDispatcher) {
        // Register a failing remote provider for SUMMARIZE_PDF capability
        val remoteFailing = MockRemoteProvider("gemini_failing", "Gemini Failing", shouldSucceed = false)
        manager.registry.registerProvider(remoteFailing)

        manager.setRuntimeMode(RuntimeMode.ONLINE)
        manager.connectivityMonitor.setInternetConnected(true)

        val eventsReceived = mutableListOf<AIEvent>()
        val job = backgroundScope.launch {
            AIEventBus.events.collect {
                eventsReceived.add(it)
            }
        }

        // Execute task that is supported by both, but remote will fail, triggering fallback to local
        val task = AITask(
            capability = Capability.SUMMARIZE_PDF,
            payload = emptyMap(),
            supportsRetry = false
        )

        val result = manager.executeTask(task)
        assertTrue(result.success) // Succeeded because of local fallback execution!
        assertEquals("local_default", result.executedByProviderId) // Executed by local fallback

        // Verify fallback events were published
        assertTrue(eventsReceived.any { it is ProviderFailedEvent && it.providerId == "gemini_failing" })
        assertTrue(eventsReceived.any { it is FallbackActivatedEvent && it.failedProviderId == "remote_provider" })

        job.cancel()
    }

    @Test
    fun testTaskRetryQueue() = runTest(testDispatcher) {
        val task = AITask(
            capability = Capability.WEATHER,
            payload = emptyMap(),
            supportsRetry = true
        )

        // Queue is initially empty
        assertTrue(manager.retryQueue.getPendingTasks().isEmpty())

        // Queue task
        manager.retryQueue.queueTask(task)
        assertEquals(1, manager.retryQueue.getPendingTasks().size)

        // Register a working provider for WEATHER so that retry succeeds
        val workingRemote = MockRemoteProvider("gemini_working", "Gemini Working")
        manager.registry.registerProvider(workingRemote)

        // Configure connectivity & online mode for retry processing
        manager.setRuntimeMode(RuntimeMode.ONLINE)
        manager.connectivityMonitor.setInternetConnected(true)

        // Process queue
        manager.retryQueue.processQueue(manager)

        // Queue should be cleared now since task executed successfully
        assertTrue(manager.retryQueue.getPendingTasks().isEmpty())
        assertEquals(1, workingRemote.callCount)
    }
}
