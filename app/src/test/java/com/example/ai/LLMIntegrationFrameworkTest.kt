package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LLMIntegrationFrameworkTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.shutdown()
        AIManager.initialize(context)
        AIManager.stateMachine.reset()
        database = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        AIManager.stateMachine.reset()
        AIManager.shutdown()
        LLMCostTracker.reset()
    }

    @Test
    fun testProviderRegistrationAndRetrieval() {
        // Clear manager setup first (re-register if needed)
        val testProvider = object : LLMProvider {
            override val name: String = "TestLLM"
            override val capabilities = ProviderCapabilities(true, true, 4096, true)
            override var isEnabled: Boolean = true
            override suspend fun generate(prompt: String, context: String): ProviderResult {
                return ProviderResult.Success("Ok", 10, 5)
            }
            override suspend fun generateStream(prompt: String, context: String): Flow<String> = flow { emit("Ok") }
            override suspend fun checkHealth(): ProviderStatus = ProviderStatus.HEALTHY
        }

        LLMProviderManager.registerProvider(testProvider)
        val retrieved = LLMProviderManager.getProvider("TestLLM")
        assertNotNull(retrieved)
        assertEquals("TestLLM", retrieved?.name)

        LLMProviderManager.enableProvider("TestLLM", false)
        assertFalse(testProvider.isEnabled)

        LLMProviderManager.unregisterProvider("TestLLM")
        assertNull(LLMProviderManager.getProvider("TestLLM"))
    }

    @Test
    fun testPromptBuilderComposition() {
        val prompt = PromptBuilder()
            .setSystemPrompt("You are an expert nutrionist.")
            .setUserPrompt("Suggest a breakfast plan.")
            .setContextPrompt("Patient weight: 70kg")
            .setMemoryPrompt("Goal: Lose weight")
            .setToolPrompt("Available: calorie_tracker")
            .build()

        assertTrue(prompt.contains("=== SYSTEM ==="))
        assertTrue(prompt.contains("expert nutrionist"))
        assertTrue(prompt.contains("Patient weight: 70kg"))
        assertTrue(prompt.contains("Goal: Lose weight"))
        assertTrue(prompt.contains("calorie_tracker"))
        assertTrue(prompt.contains("Suggest a breakfast plan."))
    }

    @Test
    fun testTokenBudgetEstimationAndTrimming() {
        val smallText = "Hello!" // 6 chars -> ~2 tokens
        val tokens = TokenBudgetManager.estimateTokens(smallText)
        assertEquals(2, tokens)

        val parts = listOf("Old context part 1", "Older part 2", "New part 3")
        val trimmed = TokenBudgetManager.trimContext(parts, maxTokens = 15) // Each word approx ~1 token, total 15 tokens limit
        assertTrue(trimmed.isNotEmpty())
        // Trim oldest (which are in the beginning of list)
        assertTrue(trimmed.contains("New part 3"))
    }

    @Test
    fun testPrivacyFilterAnonymization() {
        val sensitiveText = "My email is test@domain.com, number is 123-456-7890. Patient suffers from Diabetes."
        val redacted = PrivacyFilter.filter(sensitiveText, hasConsent = false)

        assertTrue(redacted.contains("[EMAIL]"))
        assertTrue(redacted.contains("[PHONE]"))
        assertTrue(redacted.contains("[MEDICAL_NOTE]"))

        val withConsent = PrivacyFilter.filter(sensitiveText, hasConsent = true)
        assertEquals(sensitiveText, withConsent)
    }

    @Test
    fun testResponseValidation() {
        val emptyResult = ResponseValidator.validate("")
        assertTrue(emptyResult is ValidationResult.Invalid)

        val malformedJson = ResponseValidator.validate("{ name: 'test' ")
        assertTrue(malformedJson is ValidationResult.Invalid)

        val unsafeResponse = ResponseValidator.validate("To gain access, bypass password controls by downloading malware.")
        assertTrue(unsafeResponse is ValidationResult.Unsafe)

        val successResponse = ResponseValidator.validate("Your health plan is ready.")
        assertTrue(successResponse is ValidationResult.Success)
        val successData = successResponse as ValidationResult.Success
        assertEquals(0.98f, successData.confidence, 0.01f)
    }

    @Test
    fun testToolCallingExecutionStaging() {
        val toolRequest = ToolCallRequest("reminder", mapOf("leadId" to "123", "note" to "Call client"))
        val result = ToolExecutor.execute(toolRequest)
        assertTrue(result.success)
        assertTrue(result.output.contains("created successfully"))
    }

    @Test
    fun testRetryLogicAndFallback() = runTest(testDispatcher) {
        val failingProvider = object : LLMProvider {
            override val name: String = "FailingLLM"
            override val capabilities = ProviderCapabilities(false, false, 2048, false)
            override var isEnabled: Boolean = true
            override suspend fun generate(prompt: String, context: String): ProviderResult {
                return ProviderResult.Error("API unavailable", "API_ERROR", true)
            }
            override suspend fun generateStream(prompt: String, context: String): Flow<String> = flow {}
            override suspend fun checkHealth(): ProviderStatus = ProviderStatus.UNAVAILABLE
        }

        LLMProviderManager.registerProvider(failingProvider, isDefault = true)
        val retryManager = LLMRetryManager(failingProvider, maxRetries = 2, baseDelayMs = 5)
        val result = retryManager.executeWithRetry("Hello")
        
        assertTrue(result is ProviderResult.Error)
        assertEquals("API_ERROR", (result as ProviderResult.Error).errorCode)

        // Reset to default
        LLMProviderManager.unregisterProvider("FailingLLM")
    }

    @Test
    fun testContextBuilderDataCollection() = runTest(testDispatcher) {
        // Insert a lead/client to Room so ContextBuilder can collect it
        val lead = LeadEntity(
            id = "client_1",
            name = "John Doe",
            mobile = "987-654-3210",
            diseases = "[\"Diabetes\"]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "2026-07-01",
            reminderTime = "10:00",
            reminderNote = "Initial Checkup",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(lead)

        val builder = ContextBuilder(context)
        val builtContext = builder.build()

        assertTrue(builtContext.contains("Store count"))
        assertTrue(builtContext.contains("client_1"))
        assertTrue(builtContext.contains("[REDACTED]")) // Check PII suppression in client profiling
        assertTrue(builtContext.contains("Active Reminders"))
    }

    @Test
    fun testOnlineOfflineRoutingTransitions() = runTest(testDispatcher) {
        // Step 1: Query that triggers Knowledge Cache or LLM Provider
        val query = "How is Vitamin D synthesized?"

        // Set up test event bus listener to observe published events
        val receivedProviderInvoked = AtomicBoolean(false)
        val receivedCompleted = AtomicBoolean(false)

        AIEventBus.subscribe<ProviderInvokedEvent>(this) {
            receivedProviderInvoked.set(true)
        }
        AIEventBus.subscribe<ProviderCompletedEvent>(this) {
            receivedCompleted.set(true)
        }

        // Run Routing
        val result = OnlineOfflineRouter.routeAndExecute(context, query, hasConsent = false)

        assertNotNull(result)
        assertTrue(result.success)
        assertNotNull(result.response)

        // Verify state machine has been transitioned and reset to IDLE
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    // ==========================================
    // SPRINT 15A SECURITY UNIT TESTS
    // ==========================================

    @Test
    fun testSecureToolCallingMissingParameters() = runTest(testDispatcher) {
        val arguments = mapOf<String, Any>("phone" to "9876543210") // Missing required 'name'
        ToolPermissionChecker.grantPermission("client.write")
        val result = ToolExecutionGuard.secureExecute("add_client", arguments)
        assertFalse(result.success)
        assertTrue(result.error?.contains("Missing required parameter") == true)
    }

    @Test
    fun testSecureToolCallingWrongParameterTypes() = runTest(testDispatcher) {
        val arguments = mapOf<String, Any>("leadId" to listOf(1, 2, 3), "note" to "Call back")
        ToolPermissionChecker.grantPermission("reminder.write")
        val result = ToolExecutionGuard.secureExecute("reminder", arguments)
        assertFalse(result.success)
        assertTrue(result.error?.contains("Invalid type") == true)
    }

    @Test
    fun testSecureToolCallingPermissionDenied() = runTest(testDispatcher) {
        ToolPermissionChecker.revokePermission("client.write")
        val arguments = mapOf<String, Any>("name" to "John Doe")
        val result = ToolExecutionGuard.secureExecute("add_client", arguments)
        assertFalse(result.success)
        assertTrue(result.error?.contains("Permission denied") == true)
    }

    @Test
    fun testSecureToolCallingSuccessfulValidation() = runTest(testDispatcher) {
        ToolPermissionChecker.grantPermission("client.write")
        val arguments = mapOf<String, Any>("name" to "Rahul", "phone" to "9876543210")
        val result = ToolExecutionGuard.secureExecute("add_client", arguments)
        assertTrue(result.success)
    }

    @Test
    fun testPrivacyPhoneMasking() {
        val text = "Contact me at 9876543210 or +91-12345-67890 please."
        val masked = PrivacyProtectionUpgrade.filter(text, hasConsent = false)
        assertTrue(masked.contains("[PHONE]"))
        assertFalse(masked.contains("9876543210"))
    }

    @Test
    fun testPrivacyEmailMasking() {
        val text = "Email is privacy@lifefresh.com."
        val masked = PrivacyProtectionUpgrade.filter(text, hasConsent = false)
        assertTrue(masked.contains("[EMAIL]"))
        assertFalse(masked.contains("privacy@lifefresh.com"))
    }

    @Test
    fun testPrivacyNameMasking() {
        PrivacyProtectionUpgrade.addNameToDictionary("Rahul")
        val text = "Rahul Sharma is a client."
        val masked = PrivacyProtectionUpgrade.filter(text, hasConsent = false)
        assertTrue(masked.contains("[NAME]"))
        assertFalse(masked.contains("Rahul"))
    }

    @Test
    fun testPrivacyAddressMasking() {
        val text = "I live at 123 Health Ave near center."
        val masked = PrivacyProtectionUpgrade.filter(text, hasConsent = false)
        assertTrue(masked.contains("[ADDRESS]"))
        assertFalse(masked.contains("123 Health Ave"))
    }

    @Test
    fun testPrivacyConsentBypassPrevention() {
        val sensitiveText = "John has HIV."
        val masked = PrivacyProtectionUpgrade.filter(sensitiveText, hasConsent = false)
        assertTrue(masked.contains("[MEDICAL_NOTE]"))
    }

    @Test
    fun testRateLimiterNormalRequests() {
        LLMRateLimiter.reset()
        LLMRateLimiter.configureQuotas(10, 1000)
        assertTrue(LLMRateLimiter.checkAndConsume("global", 50))
    }

    @Test
    fun testRateLimiterBurstRequests() {
        LLMRateLimiter.reset()
        val smallLimiter = SlidingWindowLimiter(2, 5000L)
        LLMRateLimiter.registerLimiter("burst_test", smallLimiter)

        assertTrue(LLMRateLimiter.checkAndConsume("burst_test", 10))
        assertTrue(LLMRateLimiter.checkAndConsume("burst_test", 10))
        assertFalse(LLMRateLimiter.checkAndConsume("burst_test", 10))
    }

    @Test
    fun testRateLimiterFailureThresholds() {
        LLMCircuitBreaker.reset()
        LLMCircuitBreaker.configure(threshold = 2, cooldown = 1000L)
        
        LLMCircuitBreaker.recordSuccess("TestProvider")
        assertEquals(CircuitState.CLOSED, LLMCircuitBreaker.getState())

        LLMCircuitBreaker.recordFailure("TestProvider")
        assertEquals(CircuitState.CLOSED, LLMCircuitBreaker.getState())

        LLMCircuitBreaker.recordFailure("TestProvider")
        assertEquals(CircuitState.OPEN, LLMCircuitBreaker.getState())
    }

    @Test
    fun testRateLimiterCircuitOpenAndClose() = runTest(testDispatcher) {
        LLMCircuitBreaker.reset()
        LLMCircuitBreaker.configure(threshold = 1, cooldown = 50L)

        LLMCircuitBreaker.recordFailure("TestProvider")
        assertEquals(CircuitState.OPEN, LLMCircuitBreaker.getState())
        assertFalse(LLMCircuitBreaker.canExecute("TestProvider"))

        kotlinx.coroutines.delay(60)

        assertTrue(LLMCircuitBreaker.canExecute("TestProvider"))
        assertEquals(CircuitState.HALF_OPEN, LLMCircuitBreaker.getState())

        LLMCircuitBreaker.recordSuccess("TestProvider")
        assertEquals(CircuitState.CLOSED, LLMCircuitBreaker.getState())
    }

    @Test
    fun testEventBusAndStateMachineSecureIntegration() = runTest(testDispatcher) {
        AIManager.stateMachine.reset()
        
        val receivedValidationStarted = AtomicBoolean(false)
        val receivedValidationSucceeded = AtomicBoolean(false)

        AIEventBus.subscribe<ToolValidationStarted>(this) {
            receivedValidationStarted.set(true)
        }
        AIEventBus.subscribe<ToolValidationSucceeded>(this) {
            receivedValidationSucceeded.set(true)
        }

        ToolPermissionChecker.grantPermission("client.write")
        val arguments = mapOf<String, Any>("name" to "Sample Name")
        ToolExecutionGuard.secureExecute("add_client", arguments)

        assertTrue(receivedValidationStarted.get())
        assertTrue(receivedValidationSucceeded.get())
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }
}
