package com.example.ai.chat.model

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val activeProvider: String? = null
)
