package com.example.leads.ai

import com.example.leads.domain.LeadDraft
import java.util.Locale

enum class LeadAIExecutionRequestStatus {
    READY,
    REJECTED
}

data class LeadAIExecutionRequest(
    val requestId: String,
    val confirmationId: String,
    val draftId: String,
    val sourceMessageId: String?,
    val mode: LeadAIDraftMode,
    val targetLeadId: String?,
    val leadDraft: LeadDraft,
    val idempotencyKey: String
)

data class LeadAIExecutionRequestResult(
    val status: LeadAIExecutionRequestStatus,
    val request: LeadAIExecutionRequest? = null,
    val message: String
) {
    val isReady: Boolean
        get() = status == LeadAIExecutionRequestStatus.READY &&
            request != null
}

object LeadAIExecutionRequestFactory {

    fun build(
        confirmation: LeadAIConfirmationPayload
    ): LeadAIExecutionRequestResult {
        if (!confirmation.isReady) {
            return rejected(
                "Confirmation payload valid aur ready nahi hai."
            )
        }

        val confirmationId = confirmation.confirmationId.trim()
        if (confirmationId.isEmpty()) {
            return rejected(
                "Confirmation ID missing hai."
            )
        }

        val draftId = confirmation.draftId.trim()
        if (draftId.isEmpty()) {
            return rejected(
                "Draft ID missing hai."
            )
        }

        val normalizedDraft = confirmation.normalizedDraft
            ?: return rejected(
                "Normalized lead draft missing hai."
            )

        val normalizedTargetLeadId = confirmation.targetLeadId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val executionDraft = when (confirmation.mode) {
            LeadAIDraftMode.CREATE -> {
                if (normalizedTargetLeadId != null) {
                    return rejected(
                        "Create Lead request ke saath target lead ID allowed nahi hai."
                    )
                }

                normalizedDraft.copy(id = null)
            }

            LeadAIDraftMode.UPDATE -> {
                val targetLeadId = normalizedTargetLeadId
                    ?: return rejected(
                        "Update Lead request ke liye target lead ID required hai."
                    )

                normalizedDraft.copy(id = targetLeadId)
            }
        }

        val modeKey = confirmation.mode.name.lowercase(Locale.ROOT)
        val idempotencyKey = "lead:$modeKey:$draftId"
        val requestId = confirmationId

        val request = LeadAIExecutionRequest(
            requestId = requestId,
            confirmationId = confirmationId,
            draftId = draftId,
            sourceMessageId = confirmation.sourceMessageId,
            mode = confirmation.mode,
            targetLeadId = normalizedTargetLeadId,
            leadDraft = executionDraft,
            idempotencyKey = idempotencyKey
        )

        return LeadAIExecutionRequestResult(
            status = LeadAIExecutionRequestStatus.READY,
            request = request,
            message = when (confirmation.mode) {
                LeadAIDraftMode.CREATE ->
                    "Create Lead execution request ready hai."

                LeadAIDraftMode.UPDATE ->
                    "Update Lead execution request ready hai."
            }
        )
    }

    private fun rejected(
        message: String
    ): LeadAIExecutionRequestResult {
        return LeadAIExecutionRequestResult(
            status = LeadAIExecutionRequestStatus.REJECTED,
            request = null,
            message = message
        )
    }
}
