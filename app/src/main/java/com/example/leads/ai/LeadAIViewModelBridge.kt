package com.example.leads.ai

import com.example.ai.intent.ParsedCommand
import com.example.data.database.LeadEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * StateFlow bridge between CRMViewModel and LeadAIChatController.
 *
 * It keeps Leads AI confirmation-card lifecycle state outside Compose UI,
 * while all real writes still go through LeadAIChatController -> LeadAIWorkflow
 * -> LeadOperationService.
 *
 * This class does not create coroutines and does not perform actions automatically.
 * CRMViewModel must call its suspend functions from viewModelScope.
 */
class LeadAIViewModelBridge(
    private val controller: LeadAIChatController
) {
    private val _confirmationStates =
        MutableStateFlow<Map<String, LeadAIChatConfirmationState>>(emptyMap())

    val confirmationStates:
        StateFlow<Map<String, LeadAIChatConfirmationState>> =
        _confirmationStates.asStateFlow()

    fun hasActiveDraft(): Boolean {
        return controller.hasActiveDraft()
    }

    fun getActiveDraft(): LeadAIDraft? {
        return controller.getActiveDraft()
    }

    fun getConfirmationState(
        messageId: String
    ): LeadAIChatConfirmationState? {
        return _confirmationStates.value[messageId.trim()]
    }

    /**
     * Routes one user message into the Leads AI controller.
     *
     * When a confirmation becomes ready, this method stores a PENDING
     * lifecycle state under the exact AI response-message ID.
     */
    fun handleMessage(
        rawText: String,
        parsedCommand: ParsedCommand,
        responseMessageId: String,
        sourceMessageId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIChatResult {
        val cleanMessageId = responseMessageId.trim()
        require(cleanMessageId.isNotEmpty()) {
            "responseMessageId cannot be blank."
        }

        val result = controller.handleMessage(
            rawText = rawText,
            parsedCommand = parsedCommand,
            responseMessageId = cleanMessageId,
            sourceMessageId = sourceMessageId,
            nowMillis = nowMillis
        )

        val payload = result.confirmation
        if (
            result.status == LeadAIChatStatus.READY_FOR_CONFIRMATION &&
            payload != null
        ) {
            removeStatesForDraft(payload.draftId)

            _confirmationStates.update { states ->
                states + (
                    cleanMessageId to LeadAIChatConfirmationState(
                        payload = payload
                    )
                )
            }
        }

        if (result.status == LeadAIChatStatus.CANCELLED) {
            val draftId = result.confirmation?.draftId
            if (draftId != null) {
                removeStatesForDraft(draftId)
            }
        }

        return result
    }

    /**
     * Executes one exact pending confirmation.
     *
     * The card becomes EXECUTING before the suspend save call starts.
     * SUCCESS is terminal.
     * FAILED stays retryable.
     */
    suspend fun confirm(
        messageId: String,
        currentLeads: List<LeadEntity>
    ): LeadAIChatResult {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isEmpty()) {
            return rejected("Confirmation message ID missing hai.")
        }

        val currentState = _confirmationStates.value[cleanMessageId]
            ?: return rejected(
                "Is message ke liye Leads AI confirmation state nahi mili."
            )

        if (!currentState.canConfirm) {
            return when (currentState.lifecycle) {
                LeadAIConfirmationLifecycle.EXECUTING ->
                    LeadAIChatResult(
                        status = LeadAIChatStatus.ALREADY_RUNNING,
                        text = "Yeh lead action already execute ho raha hai.",
                        actionCardType = "lead_ai_confirmation",
                        isConfirmation = true,
                        confirmation = currentState.payload
                    )

                LeadAIConfirmationLifecycle.SUCCESS ->
                    LeadAIChatResult(
                        status = LeadAIChatStatus.ALREADY_EXECUTED,
                        text = currentState.successText
                            ?: "Yeh lead action pehle hi execute ho chuka hai."
                    )

                LeadAIConfirmationLifecycle.CANCELLED ->
                    rejected("Cancelled lead action confirm nahi kiya ja sakta.")

                LeadAIConfirmationLifecycle.PENDING,
                LeadAIConfirmationLifecycle.FAILED ->
                    rejected("Lead confirmation current state mein execute nahi ho sakti.")
            }
        }

        updateState(
            messageId = cleanMessageId,
            newState = currentState.markExecuting()
        )

        val result = try {
            controller.confirm(
                messageId = cleanMessageId,
                currentLeads = currentLeads
            )
        } catch (error: Exception) {
            val failureText =
                "Lead action execute nahi ho saka: ${error.message.orEmpty()}"

            updateState(
                messageId = cleanMessageId,
                newState = currentState.markFailed(failureText)
            )

            return LeadAIChatResult(
                status = LeadAIChatStatus.EXECUTION_FAILED,
                text = failureText,
                actionCardType = "lead_ai_confirmation",
                isConfirmation = true,
                isError = true,
                confirmation = currentState.payload
            )
        }

        when (result.status) {
            LeadAIChatStatus.EXECUTION_SUCCESS -> {
                updateState(
                    messageId = cleanMessageId,
                    newState = currentState.markSuccess(
                        message = result.text,
                        leadId = result.savedLead?.id
                    )
                )
            }

            LeadAIChatStatus.ALREADY_EXECUTED -> {
                updateState(
                    messageId = cleanMessageId,
                    newState = currentState.markSuccess(
                        message = result.text,
                        leadId = result.savedLead?.id
                    )
                )
            }

            LeadAIChatStatus.ALREADY_RUNNING -> {
                updateState(
                    messageId = cleanMessageId,
                    newState = currentState.markExecuting()
                )
            }

            LeadAIChatStatus.EXECUTION_FAILED,
            LeadAIChatStatus.REJECTED,
            LeadAIChatStatus.INVALID_ANSWER -> {
                updateState(
                    messageId = cleanMessageId,
                    newState = currentState.markFailed(result.text)
                )
            }

            else -> {
                updateState(
                    messageId = cleanMessageId,
                    newState = currentState.markFailed(
                        result.text.ifBlank {
                            "Lead action expected result ke bina finish hua."
                        }
                    )
                )
            }
        }

        return result
    }

    /**
     * Cancels one exact pending/failed confirmation.
     */
    fun cancelConfirmation(
        messageId: String
    ): LeadAIChatResult {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isEmpty()) {
            return rejected("Confirmation message ID missing hai.")
        }

        val currentState = _confirmationStates.value[cleanMessageId]
            ?: return rejected(
                "Is message ke liye Leads AI confirmation state nahi mili."
            )

        if (!currentState.canCancel) {
            return when (currentState.lifecycle) {
                LeadAIConfirmationLifecycle.EXECUTING ->
                    rejected("Executing lead action cancel nahi kiya ja sakta.")

                LeadAIConfirmationLifecycle.SUCCESS ->
                    rejected("Successful lead action cancel nahi kiya ja sakta.")

                LeadAIConfirmationLifecycle.CANCELLED ->
                    LeadAIChatResult(
                        status = LeadAIChatStatus.CANCELLED,
                        text = currentState.successText
                            ?: "Lead action pehle hi cancel ho chuka hai."
                    )

                LeadAIConfirmationLifecycle.PENDING,
                LeadAIConfirmationLifecycle.FAILED ->
                    rejected("Lead confirmation current state mein cancel nahi ho sakti.")
            }
        }

        val result = controller.cancelConfirmation(cleanMessageId)

        if (result.status == LeadAIChatStatus.CANCELLED) {
            updateState(
                messageId = cleanMessageId,
                newState = currentState.markCancelled(result.text)
            )
        } else {
            updateState(
                messageId = cleanMessageId,
                newState = currentState.markFailed(result.text)
            )
        }

        return result
    }

    /**
     * Cancels an incomplete multi-turn draft before a confirmation card exists.
     */
    fun cancelActiveDraft(): LeadAIChatResult {
        return controller.cancelActiveDraft()
    }

    private fun updateState(
        messageId: String,
        newState: LeadAIChatConfirmationState
    ) {
        _confirmationStates.update { states ->
            states + (messageId to newState)
        }
    }

    private fun removeStatesForDraft(
        draftId: String
    ) {
        val cleanDraftId = draftId.trim()
        if (cleanDraftId.isEmpty()) return

        _confirmationStates.update { states ->
            states.filterValues { state ->
                state.payload.draftId != cleanDraftId
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
