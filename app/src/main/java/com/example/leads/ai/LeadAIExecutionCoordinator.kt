package com.example.leads.ai

import com.example.data.database.LeadEntity
import com.example.leads.operation.LeadOperationService
import com.example.leads.operation.LeadSaveResult
import com.example.leads.operation.LeadSaveStatus

enum class LeadAIExecutionStatus {
    SUCCESS,
    ALREADY_RUNNING,
    ALREADY_EXECUTED,
    REJECTED,
    FAILED
}

data class LeadAIExecutionResult(
    val status: LeadAIExecutionStatus,
    val requestId: String?,
    val draftId: String?,
    val savedLead: LeadEntity? = null,
    val draftCleared: Boolean = false,
    val warnings: List<String> = emptyList(),
    val message: String
) {
    val isSuccess: Boolean
        get() = (
            status == LeadAIExecutionStatus.SUCCESS ||
                status == LeadAIExecutionStatus.ALREADY_EXECUTED
            ) && savedLead != null
}

/**
 * Executes a confirmed Leads AI request through the shared
 * LeadOperationService.
 *
 * Safety responsibilities:
 * - rejects malformed requests
 * - prevents the same request from running twice at the same time
 * - prevents a successfully completed request from running again
 * - returns the original saved lead for repeated confirmation taps
 * - allows retry after a failed save
 * - clears only the matching AI draft after successful execution
 *
 * Idempotency state is process-memory only. Persistent idempotency may be
 * added later without changing callers.
 */
class LeadAIExecutionCoordinator(
    private val operationService: LeadOperationService,
    private val clearDraftAfterSuccess: (draftId: String) -> Boolean = { false }
) {
    private sealed interface InternalExecutionState {
        data object Running : InternalExecutionState

        data class Completed(
            val savedLead: LeadEntity,
            val draftCleared: Boolean,
            val warnings: List<String>,
            val message: String
        ) : InternalExecutionState
    }

    private val stateLock = Any()
    private val executionStates =
        mutableMapOf<String, InternalExecutionState>()

    suspend fun execute(
        request: LeadAIExecutionRequest,
        currentLeads: List<LeadEntity>
    ): LeadAIExecutionResult {
        val validationError = validateRequest(request)
        if (validationError != null) {
            return LeadAIExecutionResult(
                status = LeadAIExecutionStatus.REJECTED,
                requestId = request.requestId,
                draftId = request.draftId,
                message = validationError
            )
        }

        when (val previousState = reserveExecution(request.idempotencyKey)) {
            InternalExecutionState.Running -> {
                return LeadAIExecutionResult(
                    status = LeadAIExecutionStatus.ALREADY_RUNNING,
                    requestId = request.requestId,
                    draftId = request.draftId,
                    message = "Yeh lead action already execute ho raha hai."
                )
            }

            is InternalExecutionState.Completed -> {
                return LeadAIExecutionResult(
                    status = LeadAIExecutionStatus.ALREADY_EXECUTED,
                    requestId = request.requestId,
                    draftId = request.draftId,
                    savedLead = previousState.savedLead,
                    draftCleared = previousState.draftCleared,
                    warnings = previousState.warnings,
                    message = previousState.message
                )
            }

            null -> Unit
        }

        val saveResult = try {
            operationService.saveLead(
                draft = request.leadDraft,
                currentLeads = currentLeads
            )
        } catch (error: Exception) {
            releaseFailedExecution(request.idempotencyKey)

            return LeadAIExecutionResult(
                status = LeadAIExecutionStatus.FAILED,
                requestId = request.requestId,
                draftId = request.draftId,
                message = buildString {
                    append("Lead action execute nahi ho saka.")
                    error.message
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            append(" ")
                            append(it)
                        }
                }
            )
        }

        return handleSaveResult(
            request = request,
            saveResult = saveResult
        )
    }

    fun hasSucceeded(
        idempotencyKey: String
    ): Boolean {
        val key = idempotencyKey.trim()
        if (key.isEmpty()) return false

        return synchronized(stateLock) {
            executionStates[key] is InternalExecutionState.Completed
        }
    }

    fun isRunning(
        idempotencyKey: String
    ): Boolean {
        val key = idempotencyKey.trim()
        if (key.isEmpty()) return false

        return synchronized(stateLock) {
            executionStates[key] == InternalExecutionState.Running
        }
    }

    private fun validateRequest(
        request: LeadAIExecutionRequest
    ): String? {
        if (request.requestId.isBlank()) {
            return "Execution request ID missing hai."
        }

        if (request.confirmationId.isBlank()) {
            return "Confirmation ID missing hai."
        }

        if (request.draftId.isBlank()) {
            return "Draft ID missing hai."
        }

        if (request.idempotencyKey.isBlank()) {
            return "Idempotency key missing hai."
        }

        return when (request.mode) {
            LeadAIDraftMode.CREATE -> {
                if (
                    request.targetLeadId != null ||
                    request.leadDraft.id != null
                ) {
                    "Create Lead request mein target lead ID allowed nahi hai."
                } else {
                    null
                }
            }

            LeadAIDraftMode.UPDATE -> {
                val targetId = request.targetLeadId?.trim()

                when {
                    targetId.isNullOrEmpty() ->
                        "Update Lead request ke liye target lead ID required hai."

                    request.leadDraft.id != targetId ->
                        "Update Lead request ka target ID match nahi karta."

                    else -> null
                }
            }
        }
    }

    /**
     * Returns the previous state.
     * Null means this call successfully reserved execution.
     */
    private fun reserveExecution(
        idempotencyKey: String
    ): InternalExecutionState? {
        val key = idempotencyKey.trim()

        return synchronized(stateLock) {
            val previous = executionStates[key]

            if (previous == null) {
                executionStates[key] = InternalExecutionState.Running
            }

            previous
        }
    }

    private fun handleSaveResult(
        request: LeadAIExecutionRequest,
        saveResult: LeadSaveResult
    ): LeadAIExecutionResult {
        if (
            saveResult.status == LeadSaveStatus.SUCCESS &&
            saveResult.entity != null
        ) {
            val draftCleared = try {
                clearDraftAfterSuccess(request.draftId)
            } catch (_: Exception) {
                false
            }

            val completedState = InternalExecutionState.Completed(
                savedLead = saveResult.entity,
                draftCleared = draftCleared,
                warnings = saveResult.warnings.toList(),
                message = saveResult.message
            )

            markCompleted(
                idempotencyKey = request.idempotencyKey,
                completedState = completedState
            )

            return LeadAIExecutionResult(
                status = LeadAIExecutionStatus.SUCCESS,
                requestId = request.requestId,
                draftId = request.draftId,
                savedLead = completedState.savedLead,
                draftCleared = completedState.draftCleared,
                warnings = completedState.warnings,
                message = completedState.message
            )
        }

        releaseFailedExecution(request.idempotencyKey)

        return LeadAIExecutionResult(
            status = when (saveResult.status) {
                LeadSaveStatus.VALIDATION_FAILED,
                LeadSaveStatus.DUPLICATE_MOBILE,
                LeadSaveStatus.DUPLICATE_REMINDER,
                LeadSaveStatus.LEAD_NOT_FOUND ->
                    LeadAIExecutionStatus.REJECTED

                LeadSaveStatus.DATABASE_ERROR ->
                    LeadAIExecutionStatus.FAILED

                LeadSaveStatus.SUCCESS ->
                    LeadAIExecutionStatus.FAILED
            },
            requestId = request.requestId,
            draftId = request.draftId,
            savedLead = saveResult.entity,
            warnings = saveResult.warnings,
            message = saveResult.message
        )
    }

    private fun markCompleted(
        idempotencyKey: String,
        completedState: InternalExecutionState.Completed
    ) {
        val key = idempotencyKey.trim()

        synchronized(stateLock) {
            executionStates[key] = completedState
        }
    }

    private fun releaseFailedExecution(
        idempotencyKey: String
    ) {
        val key = idempotencyKey.trim()

        synchronized(stateLock) {
            if (executionStates[key] == InternalExecutionState.Running) {
                executionStates.remove(key)
            }
        }
    }
}
