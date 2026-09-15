package com.example.leads.ai

import com.example.leads.domain.LeadDraft

class LeadAIDraftManager {

    private var activeDraft: LeadAIDraft? = null

    fun hasActiveDraft(): Boolean {
        return activeDraft != null
    }

    fun getActiveDraft(): LeadAIDraft? {
        return activeDraft
    }

    fun nextRequiredStep(): LeadAIDraftStep? {
        return activeDraft?.nextRequiredStep()
    }

    fun nextQuestion(): String? {
        val step = nextRequiredStep() ?: return null
        return when (step) {
            LeadAIDraftStep.NAME -> "Lead ka naam kya hai?"
            LeadAIDraftStep.MOBILE -> "Mobile number kya hai?"
            LeadAIDraftStep.WELLNESS_CATEGORIES -> "Wellness category kya rakhni hai?"
            LeadAIDraftStep.OTHER_CATEGORY_DETAIL -> "Other category ki detail kya hai?"
            LeadAIDraftStep.RELATION -> "Client ke saath relation kya hai?"
            LeadAIDraftStep.OTHER_RELATION_DETAIL -> "Other relation ki detail kya hai?"
            LeadAIDraftStep.STATUS -> "Status Pending rakhna hai ya Complete?"
            LeadAIDraftStep.REMINDER_CHOICE -> "Kya is lead ke liye reminder bhi lagana hai?"
            LeadAIDraftStep.REMINDER_DATE -> "Reminder kis date ko lagana hai?"
            LeadAIDraftStep.REMINDER_TIME -> "Reminder kis time par lagana hai?"
            LeadAIDraftStep.REMINDER_NOTE -> "Reminder kis kaam ke liye hai?"
            LeadAIDraftStep.QUICK_NOTES -> "Koi Quick Note add karna hai? Aap skip bhi bol sakte hain."
            LeadAIDraftStep.READY_FOR_CONFIRMATION -> null
        }
    }

    fun isReadyForConfirmation(): Boolean {
        return activeDraft?.isReadyForConfirmation() == true
    }

    fun startCreateDraft(
        sourceMessageId: String? = null,
        initialDraft: LeadAIDraft? = null
    ): LeadAIDraft {
        val draft = if (initialDraft != null) {
            initialDraft.copy(
                mode = LeadAIDraftMode.CREATE,
                targetLeadId = null,
                sourceMessageId = sourceMessageId
            )
        } else {
            LeadAIDraft(
                mode = LeadAIDraftMode.CREATE,
                sourceMessageId = sourceMessageId
            )
        }
        activeDraft = draft
        return draft
    }

    fun startUpdateDraft(
        targetLeadId: String,
        sourceMessageId: String? = null
    ): LeadAIDraft {
        require(targetLeadId.isNotBlank()) { "Target lead ID cannot be blank" }
        val draft = LeadAIDraft(
            mode = LeadAIDraftMode.UPDATE,
            targetLeadId = targetLeadId.trim(),
            sourceMessageId = sourceMessageId
        )
        activeDraft = draft
        return draft
    }

    fun updateActiveDraft(
        name: String? = null,
        mobile: String? = null,
        diseases: List<String>? = null,
        otherDisease: String? = null,
        relation: String? = null,
        otherRelation: String? = null,
        status: String? = null,
        reminderRequested: Boolean? = null,
        reminderDate: String? = null,
        reminderTime: String? = null,
        reminderNote: String? = null,
        notes: String? = null,
        quickNotesSkipped: Boolean? = null
    ): LeadAIDraft? {
        val current = activeDraft ?: return null
        val updated = current.update(
            name = name,
            mobile = mobile,
            diseases = diseases,
            otherDisease = otherDisease,
            relation = relation,
            otherRelation = otherRelation,
            status = status,
            reminderRequested = reminderRequested,
            reminderDate = reminderDate,
            reminderTime = reminderTime,
            reminderNote = reminderNote,
            notes = notes,
            quickNotesSkipped = quickNotesSkipped
        )
        activeDraft = updated
        return updated
    }

    fun skipQuickNotes(): LeadAIDraft? {
        return updateActiveDraft(quickNotesSkipped = true)
    }

    fun buildLeadDraftForConfirmation(): LeadDraft? {
        return activeDraft?.toLeadDraftOrNull()
    }

    fun cancelActiveDraft(expectedDraftId: String? = null): Boolean {
        val current = activeDraft ?: return false
        if (expectedDraftId != null && current.draftId != expectedDraftId) {
            return false
        }
        activeDraft = null
        return true
    }

    fun clearAfterSuccessfulExecution(draftId: String): Boolean {
        require(draftId.isNotBlank()) { "Draft ID cannot be blank" }
        val current = activeDraft ?: return false
        if (current.draftId == draftId) {
            activeDraft = null
            return true
        }
        return false
    }

    fun replaceActiveDraft(draft: LeadAIDraft): LeadAIDraft {
        activeDraft = draft
        return draft
    }
}
