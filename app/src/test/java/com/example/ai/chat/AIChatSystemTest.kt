package com.example.ai.chat

import com.example.ai.chat.model.ChatMessage
import com.example.ai.chat.model.ChatRole
import com.example.ai.chat.provider.AIProvider
import com.example.ai.chat.provider.AIProviderResult
import com.example.ai.chat.provider.AIProviderRouter
import com.example.ai.chat.repository.DefaultAIChatRepository
import com.example.ai.chat.viewmodel.AIChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class AIChatSystemTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class FakeProvider(
        override val name: String,
        override val isConfigured: Boolean = true,
        var shouldSucceed: Boolean = true,
        var responseText: String = "Test response",
        var errorMessage: String = "Service unavailable",
        var isRateLimitOrTimeout: Boolean = false
    ) : AIProvider {
        var callCount = 0
        var lastReceivedMessages: List<ChatMessage> = emptyList()

        override suspend fun generateResponse(
            messages: List<ChatMessage>,
            systemInstruction: String
        ): AIProviderResult {
            callCount++
            lastReceivedMessages = messages
            return if (shouldSucceed) {
                AIProviderResult.Success(text = responseText, providerName = name)
            } else {
                AIProviderResult.Failure(
                    providerName = name,
                    errorMessage = errorMessage,
                    isRetryable = true,
                    isRateLimitOrTimeout = isRateLimitOrTimeout
                )
            }
        }
    }

    @Test
    fun testRouter_primaryGroqSuccess() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = true, responseText = "Hello from Groq!")
        val gemini = FakeProvider(name = "Gemini", shouldSucceed = true, responseText = "Hello from Gemini!")

        val router = AIProviderRouter(listOf(groq, gemini))
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val result = router.routeChat(messages, "System instruction")
        assertTrue(result is AIProviderResult.Success)
        val success = result as AIProviderResult.Success
        assertEquals("Groq", success.providerName)
        assertEquals("Hello from Groq!", success.text)
        assertEquals(1, groq.callCount)
        assertEquals(0, gemini.callCount) // Gemini not called because Groq succeeded
    }

    @Test
    fun testRouter_groqFails_fallbackToGemini() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = false, errorMessage = "Groq rate limit exceeded", isRateLimitOrTimeout = true)
        val gemini = FakeProvider(name = "Gemini", shouldSucceed = true, responseText = "Hello from Gemini Fallback!")

        val router = AIProviderRouter(listOf(groq, gemini))
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val result = router.routeChat(messages, "System instruction")
        assertTrue(result is AIProviderResult.Success)
        val success = result as AIProviderResult.Success
        assertEquals("Gemini", success.providerName)
        assertEquals("Hello from Gemini Fallback!", success.text)
        assertEquals(1, groq.callCount)
        assertEquals(1, gemini.callCount)
    }

    @Test
    fun testRouter_allProvidersFail_returnsFriendlyMessage() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = false, errorMessage = "Groq timeout")
        val gemini = FakeProvider(name = "Gemini", shouldSucceed = false, errorMessage = "Gemini unavailable")

        val router = AIProviderRouter(listOf(groq, gemini))
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val result = router.routeChat(messages, "System instruction")
        assertTrue(result is AIProviderResult.Failure)
        val failure = result as AIProviderResult.Failure
        assertTrue(failure.errorMessage.contains("couldn't reach the AI service"))
    }

    @Test
    fun testRouter_unconfiguredProviders_returnsHelpfulSetupMessage() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", isConfigured = false, shouldSucceed = false)
        val gemini = FakeProvider(name = "Gemini", isConfigured = false, shouldSucceed = false)

        val router = AIProviderRouter(listOf(groq, gemini))
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val result = router.routeChat(messages, "System instruction")
        assertTrue(result is AIProviderResult.Failure)
        val failure = result as AIProviderResult.Failure
        assertTrue(failure.errorMessage.contains("not configured"))
    }

    @Test
    fun testRepository_multiTurnConversationContext() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = true, responseText = "Hi there! How can I assist you with your day?")
        val router = AIProviderRouter(listOf(groq))
        val repository = DefaultAIChatRepository(router = router)

        // Turn 1: User sends "Hello"
        repository.sendMessage("Hello")
        val state1 = repository.uiState.value
        assertEquals(2, state1.messages.size)
        assertEquals(ChatRole.USER, state1.messages[0].role)
        assertEquals("Hello", state1.messages[0].content)
        assertEquals(ChatRole.ASSISTANT, state1.messages[1].role)
        assertEquals("Hi there! How can I assist you with your day?", state1.messages[1].content)

        // Turn 2: User sends second message "I speak Hindi too"
        groq.responseText = "नमस्ते! मैं आपकी हिंदी में भी मदद कर सकता हूँ।"
        repository.sendMessage("I speak Hindi too")
        val state2 = repository.uiState.value
        assertEquals(4, state2.messages.size)
        assertEquals("I speak Hindi too", state2.messages[2].content)
        assertEquals("नमस्ते! मैं आपकी हिंदी में भी मदद कर सकता हूँ।", state2.messages[3].content)

        // Verify the provider received full context (all 3 prior messages + new message)
        assertEquals(3, groq.lastReceivedMessages.size)
        assertEquals("Hello", groq.lastReceivedMessages[0].content)
        assertEquals("Hi there! How can I assist you with your day?", groq.lastReceivedMessages[1].content)
        assertEquals("I speak Hindi too", groq.lastReceivedMessages[2].content)
    }

    @Test
    fun testRepository_preventsBlankMessageAndDuplicateSend() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = true, responseText = "Sure!")
        val router = AIProviderRouter(listOf(groq))
        val repository = DefaultAIChatRepository(router = router)

        repository.sendMessage("   ")
        assertEquals(0, repository.uiState.value.messages.size)
        assertEquals(0, groq.callCount)
    }

    @Test
    fun testRepository_retryMechanism() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = false, errorMessage = "Temporary network glitch")
        val router = AIProviderRouter(listOf(groq))
        val repository = DefaultAIChatRepository(router = router)

        repository.sendMessage("Can you help me?")
        val failedState = repository.uiState.value
        assertTrue(failedState.canRetry)
        assertEquals(2, failedState.messages.size)
        assertTrue(failedState.messages[1].isError)

        // Fix the backend provider and trigger retry
        groq.shouldSucceed = true
        groq.responseText = "Yes, absolutely! What do you need?"
        repository.retry()

        val recoveredState = repository.uiState.value
        assertFalse(recoveredState.canRetry)
        assertEquals(2, recoveredState.messages.size)
        assertFalse(recoveredState.messages[1].isError)
        assertEquals("Yes, absolutely! What do you need?", recoveredState.messages[1].content)
    }

    @Test
    fun testRepository_clearConversation() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = true, responseText = "Done")
        val router = AIProviderRouter(listOf(groq))
        val repository = DefaultAIChatRepository(router = router)

        repository.sendMessage("First message")
        assertEquals(2, repository.uiState.value.messages.size)

        repository.clearConversation()
        assertEquals(0, repository.uiState.value.messages.size)
        assertNull(repository.uiState.value.errorMessage)
    }

    @Test
    fun testViewModel_inputAndActions() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", shouldSucceed = true, responseText = "Hello from ViewModel test")
        val router = AIProviderRouter(listOf(groq))
        val repository = DefaultAIChatRepository(router = router)
        val viewModel = AIChatViewModel(repository = repository)

        viewModel.onInputChanged("Hello from input")
        assertEquals("Hello from input", viewModel.inputText.value)

        viewModel.sendMessage()
        // Input text is cleared after sending
        assertEquals("", viewModel.inputText.value)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("Hello from input", viewModel.uiState.value.messages[0].content)
    }

    @Test
    fun router_skipsUnconfiguredProviders() = runTest(testDispatcher) {
        val groq = FakeProvider(name = "Groq", isConfigured = false, shouldSucceed = false)
        val gemini = FakeProvider(name = "Gemini", responseText = "Fallback works")

        val result = AIProviderRouter(listOf(groq, gemini)).routeChat(
            listOf(ChatMessage(role = ChatRole.USER, content = "Hello")),
            "System"
        )

        assertTrue(result is AIProviderResult.Success)
        assertEquals(0, groq.callCount)
        assertEquals(1, gemini.callCount)
    }

    @Test
    fun repository_rejectsConcurrentDuplicateSend() = runTest(testDispatcher) {
        val responseGate = CompletableDeferred<Unit>()
        val provider = object : AIProvider {
            override val name = "Blocking"
            override val isConfigured = true
            var callCount = 0

            override suspend fun generateResponse(
                messages: List<ChatMessage>,
                systemInstruction: String
            ): AIProviderResult {
                callCount++
                responseGate.await()
                return AIProviderResult.Success("Done", name)
            }
        }
        val repository = DefaultAIChatRepository(AIProviderRouter(listOf(provider)))

        val first = launch { repository.sendMessage("Same prompt") }
        runCurrent()
        val duplicate = launch { repository.sendMessage("Same prompt") }
        runCurrent()

        assertEquals(1, provider.callCount)
        assertEquals(1, repository.uiState.value.messages.count { it.role == ChatRole.USER })

        responseGate.complete(Unit)
        first.join()
        duplicate.join()
        assertEquals(2, repository.uiState.value.messages.size)
    }

    @Test
    fun repository_clearDiscardsLateResponseAndResetsTransientState() = runTest(testDispatcher) {
        val responseGate = CompletableDeferred<Unit>()
        val provider = object : AIProvider {
            override val name = "Blocking"
            override val isConfigured = true

            override suspend fun generateResponse(
                messages: List<ChatMessage>,
                systemInstruction: String
            ): AIProviderResult {
                responseGate.await()
                return AIProviderResult.Success("Late response", name)
            }
        }
        val repository = DefaultAIChatRepository(AIProviderRouter(listOf(provider)))

        val request = launch { repository.sendMessage("Clear me") }
        runCurrent()
        assertTrue(repository.uiState.value.isThinking)

        repository.clearConversation()
        responseGate.complete(Unit)
        request.join()

        val state = repository.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isThinking)
        assertNull(state.errorMessage)
        assertFalse(state.canRetry)
        assertNull(state.activeProvider)
    }

    @Test
    fun repository_cancellationAlwaysClearsThinkingState() = runTest(testDispatcher) {
        val provider = object : AIProvider {
            override val name = "Cancellable"
            override val isConfigured = true

            override suspend fun generateResponse(
                messages: List<ChatMessage>,
                systemInstruction: String
            ): AIProviderResult {
                awaitCancellation()
            }
        }
        val repository = DefaultAIChatRepository(AIProviderRouter(listOf(provider)))

        val request = launch { repository.sendMessage("Cancel me") }
        runCurrent()
        assertTrue(repository.uiState.value.isThinking)

        request.cancelAndJoin()

        assertFalse(repository.uiState.value.isThinking)
        assertNull(repository.uiState.value.errorMessage)
        assertEquals(1, repository.uiState.value.messages.size)
    }

    @Test
    fun viewModel_clearCancelsActiveRequest() = runTest(testDispatcher) {
        val started = CompletableDeferred<Unit>()
        val provider = object : AIProvider {
            override val name = "Cancellable"
            override val isConfigured = true

            override suspend fun generateResponse(
                messages: List<ChatMessage>,
                systemInstruction: String
            ): AIProviderResult {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val repository = DefaultAIChatRepository(AIProviderRouter(listOf(provider)))
        val viewModel = AIChatViewModel(repository)

        viewModel.sendMessage("Cancel through ViewModel")
        runCurrent()
        started.await()
        viewModel.clearConversation()
        runCurrent()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.isThinking)
        assertEquals("", viewModel.inputText.value)
    }
}
