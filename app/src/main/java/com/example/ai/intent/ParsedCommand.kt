package com.example.ai.intent

data class ExtractedEntities(
    val name: String? = null,
    val phone: String? = null,
    val relativeDate: String? = null, // e.g. "today", "tomorrow", "kal", "aaj"
    val resolvedDate: String? = null, // yyyy-MM-dd
    val time: String? = null,         // HH:mm
    val status: String? = null,       // "Pending" or "Complete"
    val noteText: String? = null,
    val reminderDescription: String? = null
)

data class ParsedCommand(
    val intent: AIIntent,
    val entities: ExtractedEntities,
    val missingRequiredFields: List<String>,
    val validationStatus: String, // "VALID", "INVALID", "INCOMPLETE"
    val originalText: String,
    val normalizedText: String,
    val requiresConfirmation: Boolean,
    val clarificationQuestion: String? = null
)
