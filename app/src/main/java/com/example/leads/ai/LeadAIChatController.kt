package com.example.leads.ai

import com.example.ai.intent.AIIntent
import com.example.ai.intent.ParsedCommand
import com.example.data.database.LeadEntity

enum class LeadAIChatStatus {
    NOT_HANDLED,
    QUESTION,
    INVALID_ANSWER,
    READY_FOR_CONFIRMATION,
    CANCELLED,
    EXECUTION_SUCCESS,
    EXECUTION_FAILED,
    ALREADY_RUNNING,
    ALREADY_EXECUTED,
    REJECTED
}

data class LeadAIChatResult(
    val status: LeadAIChatStatus,
    val text: String,
    val actionCardType: String? = null,
    val isConfirmation: Boolean = false,
    val isError: Boolean = false,
    val confirmation: LeadAIConfirmationPayload? = null,
    val savedLead: LeadEntity? = null,
    val warnings: List<String> = emptyList()
) {
    val handled: Boolean
        get() = status != LeadAIChatStatus.NOT_HANDLED
}

/**
 * UI-independent bridge between CRM chat messages and the Leads AI workflow.
 *
 * CRMViewModel will later use this controller to:
 * - route a CREATE_LEAD command into the multi-turn draft workflow
 * - route follow-up messages into the active draft
 * - keep confirmation payloads associated with AI message IDs
 * - execute only the confirmation selected by the user
 * - keep failed confirmations available for retry
 *
 * This class does not use Compose, Room entities for chat, or Android UI state.
 */
class LeadAIChatController(
    private val workflow: LeadAIWorkflow
) {
    private val pendingConfirmations =
        linkedMapOf<String, LeadAIConfirmationPayload>()

    fun hasActiveDraft(): Boolean {
        return workflow.hasActiveDraft()
    }

    fun getActiveDraft(): LeadAIDraft? {
        return workflow.getActiveDraft()
    }

    fun getPendingConfirmation(
        messageId: String
    ): LeadAIConfirmationPayload? {
        return pendingConfirmations[messageId.trim()]
    }

    fun pendingConfirmationsSnapshot():
        Map<String, LeadAIConfirmationPayload> {
        return pendingConfirmations.toMap()
    }

    /**
     * Handles only Leads AI create-lead conversation messages.
     *
     * When no Leads draft is active:
     * - CREATE_LEAD and OPEN_ADD_LEAD start the workflow.
     * - every other intent returns NOT_HANDLED so the legacy command router
     *   can continue processing reminders, status, notes, reports, etc.
     *
     * When a draft is active:
     * - the next user message is treated as the answer to the current
     *   required draft step.
     */
    fun handleMessage(
        rawText: String,
        parsedCommand: ParsedCommand,
        responseMessageId: String,
        sourceMessageId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIChatResult {
        val cleanResponseMessageId = responseMessageId.trim()
        require(cleanResponseMessageId.isNotEmpty()) {
            "responseMessageId cannot be blank."
        }

        val workflowResult = when {
            workflow.hasActiveDraft() -> {
                workflow.submitAnswer(
                    rawText = rawText,
                    nowMillis = nowMillis
                )
            }

            parsedCommand.intent == AIIntent.CREATE_LEAD ||
                parsedCommand.intent == AIIntent.OPEN_ADD_LEAD -> {
                workflow.startCreateFromCommand(
                    parsedCommand = parsedCommand,
                    sourceMessageId = sourceMessageId
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: cleanResponseMessageId,
                    nowMillis = nowMillis
                )
            }

            else -> {
                return LeadAIChatResult(
                    status = LeadAIChatStatus.NOT_HANDLED,
                    text = ""
                )
            }
        }

        val mapped = mapWorkflowResult(workflowResult)

        if (
            mapped.status == LeadAIChatStatus.READY_FOR_CONFIRMATION &&
            mapped.confirmation != null
        ) {
            removeConfirmationsForDraft(
                draftId = mapped.confirmation.draftId
            )
            pendingConfirmations[cleanResponseMessageId] =
                mapped.confirmation
        }

        if (mapped.status == LeadAIChatStatus.CANCELLED) {
            val cancelledDraftId =
                workflowResult.draft?.draftId
                    ?: mapped.confirmation?.draftId

            if (cancelledDraftId != null) {
                removeConfirmationsForDraft(cancelledDraftId)
            } else {
                pendingConfirmations.clear()
            }
        }

        return mapped
    }

    /**
     * Executes one exact pending confirmation.
     *
     * Success removes the confirmation.
     * Failure/rejection keeps it available so the UI may show Retry or Cancel.
     */
    suspend fun confirm(
        messageId: String,
        currentLeads: List<LeadEntity>
    ): LeadAIChatResult {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isEmpty()) {
            return rejected("Confirmation message ID missing hai.")
        }

        val confirmation = pendingConfirmations[cleanMessageId]
            ?: return rejected(
                "Is message ke liye pending lead confirmation nahi mili."
            )

        val workflowResult = workflow.executeConfirmed(
            confirmation = confirmation,
            currentLeads = currentLeads
        )

        val mapped = mapWorkflowResult(workflowResult)

        if (
            mapped.status == LeadAIChatStatus.EXECUTION_SUCCESS ||
            mapped.status == LeadAIChatStatus.ALREADY_EXECUTED
        ) {
            pendingConfirmations.remove(cleanMessageId)
        }

        return mapped
    }

    /**
     * Cancels one exact confirmation and its matching active draft.
     */
    fun cancelConfirmation(
        messageId: String
    ): LeadAIChatResult {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isEmpty()) {
            return rejected("Confirmation message ID missing hai.")
        }

        val confirmation = pendingConfirmations[cleanMessageId]
            ?: return rejected(
                "Is message ke liye pending lead confirmation nahi mili."
            )

        val workflowResult = workflow.cancel(
            expectedDraftId = confirmation.draftId
        )

        val mapped = mapWorkflowResult(workflowResult)

        if (mapped.status == LeadAIChatStatus.CANCELLED) {
            pendingConfirmations.remove(cleanMessageId)
            removeConfirmationsForDraft(confirmation.draftId)
        }

        return mapped
    }

    /**
     * Cancels an incomplete draft before a confirmation card exists.
     */
    fun cancelActiveDraft(): LeadAIChatResult {
        val activeDraftId = workflow.getActiveDraft()?.draftId

        val result = workflow.cancel(
            expectedDraftId = activeDraftId
        )

        if (
            result.status == LeadAIWorkflowStatus.CANCELLED &&
            activeDraftId != null
        ) {
            removeConfirmationsForDraft(activeDraftId)
        }

        return mapWorkflowResult(result)
    }

    private fun removeConfirmationsForDraft(
        draftId: String
    ) {
        val cleanDraftId = draftId.trim()
        if (cleanDraftId.isEmpty()) return

        val keysToRemove = pendingConfirmations
            .filterValues { it.draftId == cleanDraftId }
            .keys
            .toList()

        keysToRemove.forEach(pendingConfirmations::remove)
    }

    private fun mapWorkflowResult(
        result: LeadAIWorkflowResult
    ): LeadAIChatResult {
        return when (result.status) {
            LeadAIWorkflowStatus.STARTED,
            LeadAIWorkflowStatus.NEXT_QUESTION -> {
                LeadAIChatResult(
                    status = LeadAIChatStatus.QUESTION,
                    text = result.nextQuestion
                        ?: result.message
                        ?: "Agli lead detail batayein.",
                    actionCardType = "lead_ai_question"
                )
            }

            LeadAIWorkflowStatus.INVALID_ANSWER -> {
                val text = listOfNotNull(
                    result.message,
                    result.nextQuestion
                )
                    .distinct()
                    .joinToString("\n")
                    .ifBlank { "Jawab samajh nahi aaya." }

                LeadAIChatResult(
                    status = LeadAIChatStatus.INVALID_ANSWER,
                    text = text,
                    actionCardType = "lead_ai_question",
                    isError = true
                )
            }

            LeadAIWorkflowStatus.READY_FOR_CONFIRMATION -> {
                val confirmation = result.confirmation

                if (confirmation == null || !confirmation.isReady) {
                    rejected(
                        confirmation?.message
                            ?: "Lead confirmation ready nahi hai."
                    )
                } else {
                    LeadAIChatResult(
                        status =
                            LeadAIChatStatus.READY_FOR_CONFIRMATION,
                        text = confirmation.message,
                        actionCardType = "lead_ai_confirmation",
                        isConfirmation = true,
                        confirmation = confirmation
                    )
                }
            }

            LeadAIWorkflowStatus.CANCELLED -> {
                LeadAIChatResult(
                    status = LeadAIChatStatus.CANCELLED,
                    text = result.message
                        ?: "Lead draft cancel kar diya gaya."
                )
            }

            LeadAIWorkflowStatus.EXECUTION_SUCCESS -> {
                val warningText = result.warnings
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\n")

                LeadAIChatResult(
                    status = LeadAIChatStatus.EXECUTION_SUCCESS,
                    text = listOfNotNull(
                        result.message
                            ?: "Lead successfully save hua.",
                        warningText
                    ).joinToString("\n"),
                    savedLead = result.savedLead,
                    warnings = result.warnings
                )
            }

            LeadAIWorkflowStatus.EXECUTION_FAILED -> {
                LeadAIChatResult(
                    status = LeadAIChatStatus.EXECUTION_FAILED,
                    text = result.message
                        ?: "Lead save nahi ho saka.",
                    actionCardType = "lead_ai_confirmation",
                    isConfirmation = true,
                    isError = true,
                    confirmation = result.confirmation,
                    warnings = result.warnings
                )
            }

            LeadAIWorkflowStatus.ALREADY_RUNNING -> {
                LeadAIChatResult(
                    status = LeadAIChatStatus.ALREADY_RUNNING,
                    text = result.message
                        ?: "Lead action already execute ho raha hai.",
                    actionCardType = "lead_ai_confirmation",
                    isConfirmation = true,
                    confirmation = result.confirmation
                )
            }

            LeadAIWorkflowStatus.ALREADY_EXECUTED -> {
                LeadAIChatResult(
                    status = LeadAIChatStatus.ALREADY_EXECUTED,
                    text = result.message
                        ?: "Lead action pehle hi execute ho chuka hai.",
                    savedLead = result.savedLead,
                    warnings = result.warnings
                )
            }

            LeadAIWorkflowStatus.REJECTED -> {
                val isConfirmationReady = result.confirmation?.isReady == true
                LeadAIChatResult(
                    status = LeadAIChatStatus.REJECTED,
                    text = result.message
                        ?: "Lead AI request reject hui.",
                    actionCardType = if (isConfirmationReady) "lead_ai_confirmation" else null,
                    isConfirmation = isConfirmationReady,
                    isError = true,
                    confirmation = if (isConfirmationReady) result.confirmation else null,
                    warnings = result.warnings
                )
            }

            LeadAIWorkflowStatus.NO_ACTIVE_DRAFT -> {
                LeadAIChatResult(
                    status = LeadAIChatStatus.NOT_HANDLED,
                    text = result.message.orEmpty()
                )
            }
        }
    }

    private fun rejected(
        message: String
    ): LeadAIChatResult {
        return LeadAIChatResult(
            status = LeadAIChatStatus.REJECTED,
            text = message,
            isError = true
        )
    }
}
