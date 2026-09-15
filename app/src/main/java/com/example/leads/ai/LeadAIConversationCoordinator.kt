package com.example.leads.ai

import com.example.ai.intent.AIIntent
import com.example.ai.intent.ExtractedEntities
import com.example.ai.intent.ParsedCommand

enum class LeadAIConversationStartStatus {
    STARTED,
    REJECTED
}

data class LeadAIConversationStartResult(
    val status: LeadAIConversationStartStatus,
    val draft: LeadAIDraft? = null,
    val nextQuestion: String? = null,
    val message: String? = null
)

class LeadAIConversationCoordinator(
    private val draftManager: LeadAIDraftManager = LeadAIDraftManager()
) {

    private val conversationEngine = LeadAIDraftConversationEngine(draftManager)

    fun startBlankCreateDraft(
        sourceMessageId: String? = null
    ): LeadAIConversationStartResult {
        val draft = draftManager.startCreateDraft(
            sourceMessageId = sourceMessageId
        )

        return LeadAIConversationStartResult(
            status = LeadAIConversationStartStatus.STARTED,
            draft = draft,
            nextQuestion = draftManager.nextQuestion()
        )
    }

    fun startCreateDraftFromCommand(
        parsedCommand: ParsedCommand,
        sourceMessageId: String? = null
    ): LeadAIConversationStartResult {
        if (
            parsedCommand.intent != AIIntent.CREATE_LEAD &&
            parsedCommand.intent != AIIntent.OPEN_ADD_LEAD
        ) {
            return LeadAIConversationStartResult(
                status = LeadAIConversationStartStatus.REJECTED,
                message = "Yeh command lead creation ke liye nahi hai."
            )
        }

        return startCreateDraftFromEntities(
            entities = parsedCommand.entities,
            sourceMessageId = sourceMessageId
        )
    }

    fun startCreateDraftFromEntities(
        entities: ExtractedEntities,
        sourceMessageId: String? = null
    ): LeadAIConversationStartResult {
        val reminderRequested = when {
            !entities.resolvedDate.isNullOrBlank() -> true
            !entities.time.isNullOrBlank() -> true
            !entities.reminderDescription.isNullOrBlank() -> true
            else -> null
        }

        val initialDraft = LeadAIDraft(
            sourceMessageId = sourceMessageId,
            mode = LeadAIDraftMode.CREATE,
            targetLeadId = null,
            name = entities.name.orEmpty().trim(),
            mobile = entities.phone.orEmpty().trim(),
            diseases = emptyList(),
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = entities.status.orEmpty().trim(),
            reminderRequested = reminderRequested,
            reminderDate = entities.resolvedDate.orEmpty().trim(),
            reminderTime = entities.time
                ?.takeUnless { it.startsWith("AMBIGUOUS_") }
                .orEmpty()
                .trim(),
            reminderNote = entities.reminderDescription.orEmpty().trim(),
            notes = entities.noteText.orEmpty().trim(),
            quickNotesSkipped = false
        )

        val draft = draftManager.startCreateDraft(
            sourceMessageId = sourceMessageId,
            initialDraft = initialDraft
        )

        return LeadAIConversationStartResult(
            status = LeadAIConversationStartStatus.STARTED,
            draft = draft,
            nextQuestion = draftManager.nextQuestion()
        )
    }

    fun submitAnswer(
        rawText: String
    ): LeadAIDraftConversationResult {
        return conversationEngine.handleUserAnswer(rawText)
    }

    fun currentState(): LeadAIDraftConversationResult {
        return conversationEngine.currentState()
    }

    fun getActiveDraft(): LeadAIDraft? {
        return draftManager.getActiveDraft()
    }

    fun hasActiveDraft(): Boolean {
        return draftManager.hasActiveDraft()
    }

    fun cancelCurrentDraft(
        expectedDraftId: String? = null
    ): Boolean {
        return draftManager.cancelActiveDraft(expectedDraftId)
    }

    fun clearAfterSuccessfulExecution(
        draftId: String
    ): Boolean {
        return draftManager.clearAfterSuccessfulExecution(draftId)
    }
}
