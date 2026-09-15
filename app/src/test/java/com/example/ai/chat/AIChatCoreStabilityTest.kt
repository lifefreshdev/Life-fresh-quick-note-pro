package com.example.ai.chat

import com.example.ai.chat.model.ChatMessage
import com.example.ai.chat.model.ChatRole
import com.example.ai.chat.provider.AIProvider
import com.example.ai.chat.provider.AIProviderResult
import com.example.ai.chat.provider.AIProviderRouter
import com.example.ai.chat.repository.DefaultAIChatRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AIChatCoreStabilityTest {
    private class FakeProvider(
        override val name: String,
        override val isConfigured: Boolean = true,
        private val result: AIProviderResult
    ) : AIProvider {
        var callCount = 0
        override suspend fun generateResponse(
            messages: List<ChatMessage>,
            systemInstruction: String
        ): AIProviderResult {
            callCount++
            return result
        }
    }

    @Test
    fun routerSkipsUnconfiguredPrimaryAndUsesFallback() = runTest {
        val groq = FakeProvider(
            "Groq", false,
            AIProviderResult.Failure("Groq", "internal", false)
        )
        val gemini = FakeProvider(
            "Gemini", true,
            AIProviderResult.Success("Fallback", "Gemini")
        )

        val result = AIProviderRouter(listOf(groq, gemini)).routeChat(
            listOf(ChatMessage(role = ChatRole.USER, content = "Hello")),
            "System"
        )

        assertTrue(result is AIProviderResult.Success)
        assertEquals(0, groq.callCount)
        assertEquals(1, gemini.callCount)
    }

    @Test
    fun routerReturnsNonRetryableFriendlyErrorWhenNothingIsConfigured() = runTest {
        val groq = FakeProvider("Groq", false, AIProviderResult.Failure("Groq", "raw", false))
        val gemini = FakeProvider("Gemini", false, AIProviderResult.Failure("Gemini", "raw", false))

        val result = AIProviderRouter(listOf(groq, gemini)).routeChat(emptyList(), "System")
            as AIProviderResult.Failure

        assertFalse(result.isRetryable)
        assertTrue(result.errorMessage.contains("not configured"))
        assertFalse(result.errorMessage.contains("raw"))
        assertEquals(0, groq.callCount)
        assertEquals(0, gemini.callCount)
    }

    @Test
    fun concurrentDuplicateSendIsRejectedInsteadOfQueued() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val provider = object : AIProvider {
            override val name = "Blocking"
            override val isConfigured = true
            override suspend fun generateResponse(
                messages: List<ChatMessage>,
                systemInstruction: String
            ): AIProviderResult {
                calls++
                gate.await()
                return AIProviderResult.Success("Done", name)
            }
        }
        val repository = DefaultAIChatRepository(AIProviderRouter(listOf(provider)))

        val first = launch { repository.sendMessage("Same") }
        runCurrent()
        val duplicate = launch { repository.sendMessage("Same") }
        runCurrent()

        assertEquals(1, calls)
        assertEquals(1, repository.uiState.value.messages.count { it.role == ChatRole.USER })

        gate.complete(Unit)
        runCurrent()
        first.join()
        duplicate.join()
        assertEquals(2, repository.uiState.value.messages.size)
    }

    @Test
    fun clearInvalidatesLateResponseAndResetsTransientState() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = object : AIProvider {
            override val name = "Blocking"
            override val isConfigured = true
            override suspend fun generateResponse(
                messages: List<ChatMessage>,
                systemInstruction: String
            ): AIProviderResult {
                gate.await()
                return AIProviderResult.Success("Late", name)
            }
        }
        val repository = DefaultAIChatRepository(AIProviderRouter(listOf(provider)))

        val request = launch { repository.sendMessage("Clear") }
        runCurrent()
        repository.clearConversation()
        gate.complete(Unit)
        runCurrent()
        request.join()

        val state = repository.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isThinking)
        assertFalse(state.canRetry)
        assertNull(state.errorMessage)
        assertNull(state.activeProvider)
    }
}
