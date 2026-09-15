package com.example.leads.ai

import com.example.ai.intent.ParsedCommand
import com.example.data.database.LeadEntity
import com.example.leads.operation.LeadOperationService

enum class LeadAIWorkflowStatus {
    STARTED,
    NEXT_QUESTION,
    INVALID_ANSWER,
    CANCELLED,
    READY_FOR_CONFIRMATION,
    NO_ACTIVE_DRAFT,
    REJECTED,
    EXECUTION_SUCCESS,
    EXECUTION_FAILED,
    ALREADY_RUNNING,
    ALREADY_EXECUTED
}

data class LeadAIWorkflowResult(
    val status: LeadAIWorkflowStatus,
    val draft: LeadAIDraft? = null,
    val nextQuestion: String? = null,
    val confirmation: LeadAIConfirmationPayload? = null,
    val executionRequest: LeadAIExecutionRequest? = null,
    val savedLead: LeadEntity? = null,
    val warnings: List<String> = emptyList(),
    val message: String? = null
)

/**
 * Single entry point for the Leads AI create-lead conversation workflow.
 *
 * This class coordinates:
 * - starting a create-lead draft
 * - collecting follow-up answers
 * - preparing a validated confirmation payload
 * - building a safe execution request
 * - executing only after an explicit confirmation call
 * - clearing only the matching draft after successful save
 *
 * It contains no Compose UI and performs no action automatically.
 */
class LeadAIWorkflow(
    operationService: LeadOperationService,
    private val conversationCoordinator: LeadAIConversationCoordinator =
        LeadAIConversationCoordinator()
) {
    private val executionCoordinator = LeadAIExecutionCoordinator(
        operationService = operationService,
        clearDraftAfterSuccess = conversationCoordinator::clearAfterSuccessfulExecution
    )

    fun startBlankCreate(
        sourceMessageId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIWorkflowResult {
        val startResult = conversationCoordinator.startBlankCreateDraft(
            sourceMessageId = sourceMessageId
        )

        return mapStartResult(
            startResult = startResult,
            nowMillis = nowMillis
        )
    }

    fun startCreateFromCommand(
        parsedCommand: ParsedCommand,
        sourceMessageId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIWorkflowResult {
        val startResult = conversationCoordinator.startCreateDraftFromCommand(
            parsedCommand = parsedCommand,
            sourceMessageId = sourceMessageId
        )

        return mapStartResult(
            startResult = startResult,
            nowMillis = nowMillis
        )
    }

    fun submitAnswer(
        rawText: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIWorkflowResult {
        val conversationResult = conversationCoordinator.submitAnswer(rawText)

        return mapConversationResult(
            conversationResult = conversationResult,
            nowMillis = nowMillis
        )
    }

    fun currentState(
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIWorkflowResult {
        val conversationResult = conversationCoordinator.currentState()

        return mapConversationResult(
            conversationResult = conversationResult,
            nowMillis = nowMillis
        )
    }

    fun prepareConfirmation(
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIWorkflowResult {
        val activeDraft = conversationCoordinator.getActiveDraft()
            ?: return LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.NO_ACTIVE_DRAFT,
                message = "Koi active lead draft nahi hai."
            )

        return buildConfirmationResult(
            draft = activeDraft,
            nowMillis = nowMillis
        )
    }

    fun buildExecutionRequest(
        confirmation: LeadAIConfirmationPayload
    ): LeadAIWorkflowResult {
        val requestResult = LeadAIExecutionRequestFactory.build(confirmation)

        if (!requestResult.isReady || requestResult.request == null) {
            return LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.REJECTED,
                confirmation = confirmation,
                message = requestResult.message
            )
        }

        return LeadAIWorkflowResult(
            status = LeadAIWorkflowStatus.READY_FOR_CONFIRMATION,
            draft = conversationCoordinator.getActiveDraft(),
            confirmation = confirmation,
            executionRequest = requestResult.request,
            message = requestResult.message
        )
    }

    suspend fun executeConfirmed(
        confirmation: LeadAIConfirmationPayload,
        currentLeads: List<LeadEntity>
    ): LeadAIWorkflowResult {
        val requestResult = LeadAIExecutionRequestFactory.build(confirmation)
        val request = requestResult.request

        if (!requestResult.isReady || request == null) {
            return LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.REJECTED,
                confirmation = confirmation,
                message = requestResult.message
            )
        }

        /*
         * A successful request may be confirmed again after its matching
         * draft has already been cleared. In that case the execution
         * coordinator returns the cached original success without creating
         * another lead.
         */
        val alreadySucceeded =
            executionCoordinator.hasSucceeded(request.idempotencyKey)

        if (!alreadySucceeded) {
            val activeDraft = conversationCoordinator.getActiveDraft()
                ?: return LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.REJECTED,
                    confirmation = confirmation,
                    executionRequest = request,
                    message =
                        "Yeh lead confirmation expire ho chuki hai. " +
                            "Naya lead workflow shuru karein."
                )

            if (activeDraft.draftId != confirmation.draftId) {
                return LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.REJECTED,
                    draft = activeDraft,
                    confirmation = confirmation,
                    executionRequest = request,
                    message =
                        "Confirmation active lead draft se match nahi karti. " +
                            "Current draft preserve kiya gaya hai."
                )
            }

            if (
                activeDraft.mode != confirmation.mode ||
                activeDraft.targetLeadId != confirmation.targetLeadId
            ) {
                return LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.REJECTED,
                    draft = activeDraft,
                    confirmation = confirmation,
                    executionRequest = request,
                    message =
                        "Confirmation ka lead operation active draft se " +
                            "match nahi karta."
                )
            }

            val freshConfirmation = LeadAIConfirmationMapper.build(activeDraft)

            if (
                !freshConfirmation.isReady ||
                freshConfirmation.normalizedDraft !=
                    confirmation.normalizedDraft
            ) {
                return LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.REJECTED,
                    draft = activeDraft,
                    confirmation = confirmation,
                    executionRequest = request,
                    message =
                        "Lead details confirmation ke baad badal gayi hain. " +
                            "Updated confirmation review karein."
                )
            }
        }

        val executionResult = executionCoordinator.execute(
            request = request,
            currentLeads = currentLeads
        )

        return when (executionResult.status) {
            LeadAIExecutionStatus.SUCCESS -> LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.EXECUTION_SUCCESS,
                confirmation = confirmation,
                executionRequest = request,
                savedLead = executionResult.savedLead,
                warnings = executionResult.warnings,
                message = executionResult.message
            )

            LeadAIExecutionStatus.ALREADY_RUNNING -> LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.ALREADY_RUNNING,
                draft = conversationCoordinator.getActiveDraft(),
                confirmation = confirmation,
                executionRequest = request,
                warnings = executionResult.warnings,
                message = executionResult.message
            )

            LeadAIExecutionStatus.ALREADY_EXECUTED -> LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.ALREADY_EXECUTED,
                confirmation = confirmation,
                executionRequest = request,
                savedLead = executionResult.savedLead,
                warnings = executionResult.warnings,
                message = executionResult.message
            )

            LeadAIExecutionStatus.REJECTED -> LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.REJECTED,
                draft = conversationCoordinator.getActiveDraft(),
                confirmation = confirmation,
                executionRequest = request,
                warnings = executionResult.warnings,
                message = executionResult.message
            )

            LeadAIExecutionStatus.FAILED -> LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.EXECUTION_FAILED,
                draft = conversationCoordinator.getActiveDraft(),
                confirmation = confirmation,
                executionRequest = request,
                warnings = executionResult.warnings,
                message = executionResult.message
            )
        }
    }

    fun cancel(
        expectedDraftId: String? = null
    ): LeadAIWorkflowResult {
        val draftBeforeCancel = conversationCoordinator.getActiveDraft()

        val cancelled = conversationCoordinator.cancelCurrentDraft(
            expectedDraftId = expectedDraftId
        )

        return if (cancelled) {
            LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.CANCELLED,
                draft = draftBeforeCancel,
                message = "Lead draft cancel kar diya gaya."
            )
        } else {
            LeadAIWorkflowResult(
                status = if (conversationCoordinator.hasActiveDraft()) {
                    LeadAIWorkflowStatus.REJECTED
                } else {
                    LeadAIWorkflowStatus.NO_ACTIVE_DRAFT
                },
                draft = conversationCoordinator.getActiveDraft(),
                nextQuestion = conversationCoordinator
                    .currentState()
                    .nextQuestion,
                message = if (conversationCoordinator.hasActiveDraft()) {
                    "Lead draft ID match nahi hua; active draft preserve kiya gaya."
                } else {
                    "Koi active lead draft nahi hai."
                }
            )
        }
    }

    fun hasActiveDraft(): Boolean {
        return conversationCoordinator.hasActiveDraft()
    }

    fun getActiveDraft(): LeadAIDraft? {
        return conversationCoordinator.getActiveDraft()
    }

    private fun mapStartResult(
        startResult: LeadAIConversationStartResult,
        nowMillis: Long
    ): LeadAIWorkflowResult {
        if (startResult.status == LeadAIConversationStartStatus.REJECTED) {
            return LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.REJECTED,
                message = startResult.message
            )
        }

        val draft = startResult.draft
            ?: return LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.REJECTED,
                message = "Lead draft start nahi ho saka."
            )

        return if (draft.isReadyForConfirmation()) {
            buildConfirmationResult(
                draft = draft,
                nowMillis = nowMillis
            )
        } else {
            LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.STARTED,
                draft = draft,
                nextQuestion = startResult.nextQuestion
                    ?: conversationCoordinator.currentState().nextQuestion,
                message = startResult.message
            )
        }
    }

    private fun mapConversationResult(
        conversationResult: LeadAIDraftConversationResult,
        nowMillis: Long
    ): LeadAIWorkflowResult {
        return when (conversationResult.status) {
            LeadAIDraftConversationStatus.NO_ACTIVE_DRAFT ->
                LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.NO_ACTIVE_DRAFT,
                    message = conversationResult.message
                )

            LeadAIDraftConversationStatus.CANCELLED ->
                LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.CANCELLED,
                    message = conversationResult.message
                )

            LeadAIDraftConversationStatus.INVALID_ANSWER ->
                LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.INVALID_ANSWER,
                    draft = conversationResult.draft,
                    nextQuestion = conversationResult.nextQuestion,
                    message = conversationResult.message
                )

            LeadAIDraftConversationStatus.NEXT_QUESTION ->
                LeadAIWorkflowResult(
                    status = LeadAIWorkflowStatus.NEXT_QUESTION,
                    draft = conversationResult.draft,
                    nextQuestion = conversationResult.nextQuestion,
                    message = conversationResult.message
                )

            LeadAIDraftConversationStatus.READY_FOR_CONFIRMATION -> {
                val draft = conversationResult.draft
                    ?: conversationCoordinator.getActiveDraft()
                    ?: return LeadAIWorkflowResult(
                        status = LeadAIWorkflowStatus.NO_ACTIVE_DRAFT,
                        message = "Confirmation ke liye active draft nahi mila."
                    )

                buildConfirmationResult(
                    draft = draft,
                    nowMillis = nowMillis
                )
            }
        }
    }

    private fun buildConfirmationResult(
        draft: LeadAIDraft,
        nowMillis: Long
    ): LeadAIWorkflowResult {
        val confirmation = LeadAIConfirmationMapper.build(
            draft = draft,
            nowMillis = nowMillis
        )

        return if (confirmation.isReady) {
            LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.READY_FOR_CONFIRMATION,
                draft = draft,
                confirmation = confirmation,
                message = confirmation.message
            )
        } else {
            LeadAIWorkflowResult(
                status = LeadAIWorkflowStatus.REJECTED,
                draft = draft,
                confirmation = confirmation,
                nextQuestion = conversationCoordinator.currentState().nextQuestion,
                message = confirmation.message
            )
        }
    }
}
