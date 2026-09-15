package com.example.leads.ai

import com.example.leads.domain.LeadDraft
import com.example.leads.domain.LeadValidationIssue
import com.example.leads.domain.LeadValidator
import java.util.Locale

enum class LeadAIConfirmationStatus {
    READY,
    INVALID
}

data class LeadAIConfirmationField(
    val key: String,
    val label: String,
    val value: String
)

data class LeadAIConfirmationPayload(
    val confirmationId: String,
    val draftId: String,
    val sourceMessageId: String?,
    val mode: LeadAIDraftMode,
    val targetLeadId: String?,
    val title: String,
    val status: LeadAIConfirmationStatus,
    val normalizedDraft: LeadDraft?,
    val fields: List<LeadAIConfirmationField>,
    val validationIssues: List<LeadValidationIssue>,
    val message: String
) {
    val isReady: Boolean
        get() = status == LeadAIConfirmationStatus.READY &&
            normalizedDraft != null &&
            validationIssues.isEmpty()
}

object LeadAIConfirmationMapper {

    fun build(
        draft: LeadAIDraft,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadAIConfirmationPayload {
        val confirmationId = buildConfirmationId(draft)
        val leadDraft = draft.toLeadDraftOrNull()

        if (leadDraft == null) {
            return LeadAIConfirmationPayload(
                confirmationId = confirmationId,
                draftId = draft.draftId,
                sourceMessageId = draft.sourceMessageId,
                mode = draft.mode,
                targetLeadId = draft.targetLeadId,
                title = titleFor(draft.mode),
                status = LeadAIConfirmationStatus.INVALID,
                normalizedDraft = null,
                fields = previewFieldsFromDraft(draft),
                validationIssues = emptyList(),
                message = "Lead draft abhi complete nahi hai."
            )
        }

        val validationResult = LeadValidator.validate(
            draft = leadDraft,
            nowMillis = nowMillis
        )

        return if (validationResult.isValid) {
            val normalized = validationResult.normalizedDraft

            LeadAIConfirmationPayload(
                confirmationId = confirmationId,
                draftId = draft.draftId,
                sourceMessageId = draft.sourceMessageId,
                mode = draft.mode,
                targetLeadId = draft.targetLeadId,
                title = titleFor(draft.mode),
                status = LeadAIConfirmationStatus.READY,
                normalizedDraft = normalized,
                fields = previewFieldsFromLeadDraft(
                    leadDraft = normalized,
                    reminderRequested = draft.reminderRequested == true
                ),
                validationIssues = emptyList(),
                message = confirmationMessageFor(draft.mode)
            )
        } else {
            LeadAIConfirmationPayload(
                confirmationId = confirmationId,
                draftId = draft.draftId,
                sourceMessageId = draft.sourceMessageId,
                mode = draft.mode,
                targetLeadId = draft.targetLeadId,
                title = titleFor(draft.mode),
                status = LeadAIConfirmationStatus.INVALID,
                normalizedDraft = validationResult.normalizedDraft,
                fields = previewFieldsFromLeadDraft(
                    leadDraft = validationResult.normalizedDraft,
                    reminderRequested = draft.reminderRequested == true
                ),
                validationIssues = validationResult.issues,
                message = validationResult.issues
                    .firstOrNull()
                    ?.message
                    ?: "Lead details valid nahi hain."
            )
        }
    }

    private fun buildConfirmationId(
        draft: LeadAIDraft
    ): String {
        val modePart = draft.mode.name.lowercase(Locale.ROOT)
        return "lead-$modePart-${draft.draftId}"
    }

    private fun titleFor(
        mode: LeadAIDraftMode
    ): String {
        return when (mode) {
            LeadAIDraftMode.CREATE -> "Create Lead"
            LeadAIDraftMode.UPDATE -> "Update Lead"
        }
    }

    private fun confirmationMessageFor(
        mode: LeadAIDraftMode
    ): String {
        return when (mode) {
            LeadAIDraftMode.CREATE ->
                "Kya aap is lead ko create karna chahte hain?"

            LeadAIDraftMode.UPDATE ->
                "Kya aap ye lead changes save karna chahte hain?"
        }
    }

    private fun previewFieldsFromDraft(
        draft: LeadAIDraft
    ): List<LeadAIConfirmationField> {
        val fields = mutableListOf<LeadAIConfirmationField>()

        fields += field("name", "Name", display(draft.name))
        fields += field("mobile", "Mobile", display(draft.mobile))
        fields += field(
            "diseases",
            "Wellness Categories",
            displayList(draft.diseases)
        )

        if (
            draft.diseases.any { it.equals("Other", ignoreCase = true) } ||
            draft.otherDisease.isNotBlank()
        ) {
            fields += field(
                "otherDisease",
                "Other Category Detail",
                display(draft.otherDisease)
            )
        }

        fields += field("relation", "Relation", display(draft.relation))

        if (
            draft.relation.equals("Other", ignoreCase = true) ||
            draft.otherRelation.isNotBlank()
        ) {
            fields += field(
                "otherRelation",
                "Other Relation Detail",
                display(draft.otherRelation)
            )
        }

        fields += field("status", "Status", display(draft.status))
        fields += field(
            "reminderRequested",
            "Reminder",
            reminderChoiceText(draft.reminderRequested)
        )

        if (draft.reminderRequested == true) {
            fields += field(
                "reminderDate",
                "Reminder Date",
                display(draft.reminderDate)
            )
            fields += field(
                "reminderTime",
                "Reminder Time",
                display(draft.reminderTime)
            )
            fields += field(
                "reminderNote",
                "Reminder Note",
                display(draft.reminderNote)
            )
        }

        fields += field(
            "notes",
            "Quick Notes",
            quickNotesText(
                notes = draft.notes,
                skipped = draft.quickNotesSkipped
            )
        )

        return fields.toList()
    }

    private fun previewFieldsFromLeadDraft(
        leadDraft: LeadDraft,
        reminderRequested: Boolean
    ): List<LeadAIConfirmationField> {
        val fields = mutableListOf<LeadAIConfirmationField>()

        fields += field("name", "Name", display(leadDraft.name))
        fields += field("mobile", "Mobile", display(leadDraft.mobile))
        fields += field(
            "diseases",
            "Wellness Categories",
            displayList(leadDraft.diseases)
        )

        if (
            leadDraft.diseases.any { it.equals("Other", ignoreCase = true) } ||
            leadDraft.otherDisease.isNotBlank()
        ) {
            fields += field(
                "otherDisease",
                "Other Category Detail",
                display(leadDraft.otherDisease)
            )
        }

        fields += field(
            "relation",
            "Relation",
            display(leadDraft.relation)
        )

        if (
            leadDraft.relation.equals("Other", ignoreCase = true) ||
            leadDraft.otherRelation.isNotBlank()
        ) {
            fields += field(
                "otherRelation",
                "Other Relation Detail",
                display(leadDraft.otherRelation)
            )
        }

        fields += field("status", "Status", display(leadDraft.status))
        fields += field(
            "reminderRequested",
            "Reminder",
            if (reminderRequested) "Yes" else "No"
        )

        if (reminderRequested) {
            fields += field(
                "reminderDate",
                "Reminder Date",
                display(leadDraft.reminderDate)
            )
            fields += field(
                "reminderTime",
                "Reminder Time",
                display(leadDraft.reminderTime)
            )
            fields += field(
                "reminderNote",
                "Reminder Note",
                display(leadDraft.reminderNote)
            )
        }

        fields += field(
            "notes",
            "Quick Notes",
            leadDraft.notes.ifBlank { "Skipped" }
        )

        return fields.toList()
    }

    private fun field(
        key: String,
        label: String,
        value: String
    ): LeadAIConfirmationField {
        return LeadAIConfirmationField(
            key = key,
            label = label,
            value = value
        )
    }

    private fun display(
        value: String
    ): String {
        return value.trim().ifBlank { "Not provided" }
    }

    private fun displayList(
        values: List<String>
    ): String {
        return values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "Not provided" }
    }

    private fun reminderChoiceText(
        value: Boolean?
    ): String {
        return when (value) {
            true -> "Yes"
            false -> "No"
            null -> "Not selected"
        }
    }

    private fun quickNotesText(
        notes: String,
        skipped: Boolean
    ): String {
        return when {
            notes.isNotBlank() -> notes.trim()
            skipped -> "Skipped"
            else -> "Not provided"
        }
    }
}
