package com.example.ai.chat.provider

import com.example.ai.chat.model.ChatMessage

/**
 * Common provider interface allowing multiple AI backends (Groq, Gemini, etc.)
 * to be integrated and swapped seamlessly without altering UI or Repository code.
 */
interface AIProvider {
    val name: String
    val isConfigured: Boolean

    suspend fun generateResponse(
        messages: List<ChatMessage>,
        systemInstruction: String
    ): AIProviderResult
}
