package com.example.leads.ai

import com.example.leads.domain.LeadDraft

enum class LeadAIDraftConversationStatus {
    NO_ACTIVE_DRAFT,
    CANCELLED,
    INVALID_ANSWER,
    NEXT_QUESTION,
    READY_FOR_CONFIRMATION
}

data class LeadAIDraftConversationResult(
    val status: LeadAIDraftConversationStatus,
    val draft: LeadAIDraft? = null,
    val nextQuestion: String? = null,
    val message: String? = null,
    val leadDraftForConfirmation: LeadDraft? = null
)

class LeadAIDraftConversationEngine(
    private val draftManager: LeadAIDraftManager
) {

    fun handleUserAnswer(rawText: String): LeadAIDraftConversationResult {
        val activeDraft = draftManager.getActiveDraft()
            ?: return LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.NO_ACTIVE_DRAFT,
                message = "Koi active lead draft nahi hai."
            )

        val currentStep = draftManager.nextRequiredStep()
            ?: return LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.NO_ACTIVE_DRAFT,
                message = "Koi active lead draft nahi hai."
            )

        /*
         * At the optional Quick Notes step, phrases such as "skip",
         * "nahi", "rehne do", or "chhodo" must skip only the note.
         * They must not accidentally cancel the complete lead draft.
         *
         * An explicit "cancel" command still cancels the full workflow.
         */
        val isQuickNotesSkip =
            currentStep == LeadAIDraftStep.QUICK_NOTES &&
                LeadAIDraftAnswerParser.isSkipCommand(rawText)

        if (
            LeadAIDraftAnswerParser.isCancelCommand(rawText) &&
            !isQuickNotesSkip
        ) {
            val cancelled = draftManager.cancelActiveDraft(
                expectedDraftId = activeDraft.draftId
            )

            return if (cancelled) {
                LeadAIDraftConversationResult(
                    status = LeadAIDraftConversationStatus.CANCELLED,
                    message = "Lead draft cancel kar diya gaya."
                )
            } else {
                LeadAIDraftConversationResult(
                    status = LeadAIDraftConversationStatus.INVALID_ANSWER,
                    draft = draftManager.getActiveDraft(),
                    nextQuestion = draftManager.nextQuestion(),
                    message = "Lead draft cancel nahi ho saka. Dobara try karein."
                )
            }
        }

        if (currentStep == LeadAIDraftStep.READY_FOR_CONFIRMATION) {
            return readyResult()
        }

        val parsedAnswer = LeadAIDraftAnswerParser.parse(
            step = currentStep,
            rawText = rawText
        )

        if (!parsedAnswer.isAccepted) {
            return LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.INVALID_ANSWER,
                draft = draftManager.getActiveDraft(),
                nextQuestion = draftManager.nextQuestion(),
                message = parsedAnswer.message ?: "Jawab samajh nahi aaya."
            )
        }

        if (currentStep == LeadAIDraftStep.REMINDER_TIME) {
            val dateVal = activeDraft.reminderDate
            val timeCandidate = parsedAnswer.patch.reminderTime ?: ""
            val currentMillis = LeadAIDraftAnswerParser.calendarProvider().timeInMillis
            if (!isFutureDateTime(dateVal, timeCandidate, currentMillis)) {
                val cleanText = rawText.trim()
                val errorMsg = if (cleanText.contains("subah 2", ignoreCase = true) || cleanText.contains("2", ignoreCase = true)) {
                    "Aaj subah 2 baje ka samay beet chuka hai. Koi future time batayein."
                } else {
                    "Aaj $cleanText ka samay beet chuka hai. Koi future time batayein."
                }
                return LeadAIDraftConversationResult(
                    status = LeadAIDraftConversationStatus.INVALID_ANSWER,
                    draft = activeDraft,
                    nextQuestion = "Reminder kis time par lagana hai?",
                    message = errorMsg
                )
            }
        }

        val patch = parsedAnswer.patch
        val updatedDraft = draftManager.updateActiveDraft(
            name = patch.name,
            mobile = patch.mobile,
            diseases = patch.diseases,
            otherDisease = patch.otherDisease,
            relation = patch.relation,
            otherRelation = patch.otherRelation,
            status = patch.status,
            reminderRequested = patch.reminderRequested,
            reminderDate = patch.reminderDate,
            reminderTime = patch.reminderTime,
            reminderNote = patch.reminderNote,
            notes = patch.notes,
            quickNotesSkipped = patch.quickNotesSkipped
        ) ?: return LeadAIDraftConversationResult(
            status = LeadAIDraftConversationStatus.NO_ACTIVE_DRAFT,
            message = "Active lead draft available nahi hai."
        )

        return if (updatedDraft.isReadyForConfirmation()) {
            readyResult()
        } else {
            LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.NEXT_QUESTION,
                draft = updatedDraft,
                nextQuestion = draftManager.nextQuestion()
                    ?: "Agli required detail batayein."
            )
        }
    }

    fun currentState(): LeadAIDraftConversationResult {
        val activeDraft = draftManager.getActiveDraft()
            ?: return LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.NO_ACTIVE_DRAFT,
                message = "Koi active lead draft nahi hai."
            )

        return if (activeDraft.isReadyForConfirmation()) {
            readyResult()
        } else {
            LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.NEXT_QUESTION,
                draft = activeDraft,
                nextQuestion = draftManager.nextQuestion()
                    ?: "Agli required detail batayein."
            )
        }
    }

    private fun readyResult(): LeadAIDraftConversationResult {
        val activeDraft = draftManager.getActiveDraft()
            ?: return LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.NO_ACTIVE_DRAFT,
                message = "Koi active lead draft nahi hai."
            )

        val leadDraft = draftManager.buildLeadDraftForConfirmation()
            ?: return LeadAIDraftConversationResult(
                status = LeadAIDraftConversationStatus.INVALID_ANSWER,
                draft = activeDraft,
                nextQuestion = draftManager.nextQuestion(),
                message = "Lead draft abhi complete nahi hai."
            )

        return LeadAIDraftConversationResult(
            status = LeadAIDraftConversationStatus.READY_FOR_CONFIRMATION,
            draft = activeDraft,
            message = "Lead details confirmation ke liye ready hain.",
            leadDraftForConfirmation = leadDraft
        )
    }

    private fun isFutureDateTime(date: String, time: String, nowMillis: Long): Boolean {
        if (date.isEmpty() || time.isEmpty()) return true
        val value = "$date $time"
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply {
            isLenient = false
        }
        return try {
            val position = java.text.ParsePosition(0)
            val parsed = formatter.parse(value, position)
            if (parsed != null && position.index == value.length) {
                parsed.time > nowMillis
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
}
