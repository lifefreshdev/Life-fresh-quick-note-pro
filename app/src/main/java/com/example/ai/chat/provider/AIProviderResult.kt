package com.example.ai.chat.provider

sealed class AIProviderResult {
    data class Success(
        val text: String,
        val providerName: String
    ) : AIProviderResult()

    data class Failure(
        val providerName: String,
        val errorMessage: String,
        val isRetryable: Boolean = true,
        val isRateLimitOrTimeout: Boolean = false,
        val cause: Throwable? = null
    ) : AIProviderResult()
}
