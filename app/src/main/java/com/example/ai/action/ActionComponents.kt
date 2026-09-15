package com.example.ai.action

import com.example.ai.intent.ExtractedEntities

enum class AITool {
    CREATE_LEAD,
    CREATE_REMINDER,
    UPDATE_LEAD_STATUS,
    ADD_LEAD_NOTE
}

data class ActionRequest(
    val tool: AITool,
    val entities: ExtractedEntities,
    val messageId: String
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, Any> = emptyMap()
)

enum class ConfirmationStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class PendingConfirmation(
    val request: ActionRequest,
    val status: ConfirmationStatus = ConfirmationStatus.PENDING,
    val errorText: String? = null,
    val successText: String? = null
)

object ToolRegistry {
    fun getToolForIntent(intent: com.example.ai.intent.AIIntent): AITool? {
        return when (intent) {
            com.example.ai.intent.AIIntent.CREATE_LEAD -> AITool.CREATE_LEAD
            com.example.ai.intent.AIIntent.CREATE_REMINDER -> AITool.CREATE_REMINDER
            com.example.ai.intent.AIIntent.UPDATE_LEAD_STATUS -> AITool.UPDATE_LEAD_STATUS
            com.example.ai.intent.AIIntent.ADD_LEAD_NOTE -> AITool.ADD_LEAD_NOTE
            else -> null
        }
    }
}
