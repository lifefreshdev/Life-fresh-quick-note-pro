package com.example.ai.chat.provider

import com.example.ai.chat.model.ChatMessage

class AIProviderRouter(
    private val providers: List<AIProvider> = listOf(GeminiProvider(), GroqProvider())
) {
    suspend fun routeChat(
        messages: List<ChatMessage>,
        systemInstruction: String
    ): AIProviderResult {
        val configuredProviders = providers.filter { it.isConfigured }
            .sortedByDescending { it is GeminiProvider }

        if (configuredProviders.isEmpty()) {
            return AIProviderResult.Failure(
                providerName = "Router",
                errorMessage = "LifeFresh AI is not configured yet. Add a Gemini API key in Settings to start chatting.",
                isRetryable = false
            )
        }

        var lastFailure: AIProviderResult.Failure? = null

        for (provider in configuredProviders) {
            when (val result = provider.generateResponse(messages, systemInstruction)) {
                is AIProviderResult.Success -> {
                    return result
                }
                is AIProviderResult.Failure -> {
                    lastFailure = result
                    // If auth failure (invalid API key), stop and show exact error
                    if (!result.isRetryable && !result.isRateLimitOrTimeout) {
                        return result
                    }
                }
            }
        }

        return AIProviderResult.Failure(
            providerName = "Router",
            errorMessage = "I'm sorry, I couldn't reach the AI service right now. Please check your connection and try again.",
            isRetryable = true,
            isRateLimitOrTimeout = lastFailure?.isRateLimitOrTimeout ?: false,
            cause = lastFailure?.cause
        )
    }
}
