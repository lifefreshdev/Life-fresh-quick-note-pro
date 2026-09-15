package com.example.leads.ai

enum class LeadAIConfirmationLifecycle {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class LeadAIChatConfirmationState(
    val payload: LeadAIConfirmationPayload,
    val lifecycle: LeadAIConfirmationLifecycle =
        LeadAIConfirmationLifecycle.PENDING,
    val successText: String? = null,
    val errorText: String? = null,
    val savedLeadId: String? = null
) {
    val canConfirm: Boolean
        get() = lifecycle == LeadAIConfirmationLifecycle.PENDING ||
            lifecycle == LeadAIConfirmationLifecycle.FAILED

    val canCancel: Boolean
        get() = lifecycle == LeadAIConfirmationLifecycle.PENDING ||
            lifecycle == LeadAIConfirmationLifecycle.FAILED

    val isTerminal: Boolean
        get() = lifecycle == LeadAIConfirmationLifecycle.SUCCESS ||
            lifecycle == LeadAIConfirmationLifecycle.CANCELLED

    fun markExecuting(): LeadAIChatConfirmationState {
        return copy(
            lifecycle = LeadAIConfirmationLifecycle.EXECUTING,
            successText = null,
            errorText = null
        )
    }

    fun markSuccess(
        message: String,
        leadId: String?
    ): LeadAIChatConfirmationState {
        return copy(
            lifecycle = LeadAIConfirmationLifecycle.SUCCESS,
            successText = message.trim(),
            errorText = null,
            savedLeadId = leadId
        )
    }

    fun markFailed(
        message: String
    ): LeadAIChatConfirmationState {
        return copy(
            lifecycle = LeadAIConfirmationLifecycle.FAILED,
            successText = null,
            errorText = message.trim()
        )
    }

    fun markCancelled(
        message: String = "Lead action cancel kar diya gaya."
    ): LeadAIChatConfirmationState {
        return copy(
            lifecycle = LeadAIConfirmationLifecycle.CANCELLED,
            successText = message.trim(),
            errorText = null
        )
    }
}
