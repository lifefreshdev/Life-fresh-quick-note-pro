package com.example.leads.ai

import com.example.leads.domain.LeadDraft
import java.util.UUID
import java.util.Locale
import java.text.SimpleDateFormat
import java.text.ParsePosition

enum class LeadAIDraftMode {
    CREATE,
    UPDATE
}

enum class LeadAIDraftStep {
    NAME,
    MOBILE,
    WELLNESS_CATEGORIES,
    OTHER_CATEGORY_DETAIL,
    RELATION,
    OTHER_RELATION_DETAIL,
    STATUS,
    REMINDER_CHOICE,
    REMINDER_DATE,
    REMINDER_TIME,
    REMINDER_NOTE,
    QUICK_NOTES,
    READY_FOR_CONFIRMATION
}

data class LeadAIDraft(
    val draftId: String = UUID.randomUUID().toString(),
    val sourceMessageId: String? = null,
    val mode: LeadAIDraftMode = LeadAIDraftMode.CREATE,
    val targetLeadId: String? = null,

    val name: String = "",
    val mobile: String = "",
    val diseases: List<String> = emptyList(),
    val otherDisease: String = "",
    val relation: String = "",
    val otherRelation: String = "",
    val status: String = "",

    val reminderRequested: Boolean? = null,
    val reminderDate: String = "",
    val reminderTime: String = "",
    val reminderNote: String = "",
    val notes: String = "",
    val quickNotesSkipped: Boolean = false
) {

    fun nextRequiredStep(): LeadAIDraftStep {
        if (name.isBlank()) return LeadAIDraftStep.NAME
        if (mobile.isBlank()) return LeadAIDraftStep.MOBILE
        if (diseases.isEmpty()) return LeadAIDraftStep.WELLNESS_CATEGORIES
        if (diseases.any { it.equals("Other", ignoreCase = true) } && otherDisease.isBlank()) {
            return LeadAIDraftStep.OTHER_CATEGORY_DETAIL
        }
        if (relation.isBlank()) return LeadAIDraftStep.RELATION
        if (relation.equals("Other", ignoreCase = true) && otherRelation.isBlank()) {
            return LeadAIDraftStep.OTHER_RELATION_DETAIL
        }
        if (status.isBlank()) return LeadAIDraftStep.STATUS
        if (reminderRequested == null) return LeadAIDraftStep.REMINDER_CHOICE
        if (reminderRequested == true) {
            if (reminderDate.isBlank()) return LeadAIDraftStep.REMINDER_DATE
            if (reminderTime.isBlank()) return LeadAIDraftStep.REMINDER_TIME
            if (!isFutureDateTime(reminderDate, reminderTime)) {
                return LeadAIDraftStep.REMINDER_TIME
            }
            if (reminderNote.isBlank()) return LeadAIDraftStep.REMINDER_NOTE
        }
        if (notes.isBlank() && !quickNotesSkipped) return LeadAIDraftStep.QUICK_NOTES
        return LeadAIDraftStep.READY_FOR_CONFIRMATION
    }

    private fun isFutureDateTime(date: String, time: String): Boolean {
        if (date.isBlank() || time.isBlank()) return true
        val value = "$date $time"
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            isLenient = false
        }
        return try {
            val position = ParsePosition(0)
            val parsed = formatter.parse(value, position)
            if (parsed != null && position.index == value.length) {
                parsed.time > LeadAIDraftAnswerParser.calendarProvider().timeInMillis
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    fun isReadyForConfirmation(): Boolean {
        return nextRequiredStep() == LeadAIDraftStep.READY_FOR_CONFIRMATION
    }

    fun toLeadDraftOrNull(): LeadDraft? {
        if (!isReadyForConfirmation()) return null
        return LeadDraft(
            id = targetLeadId,
            name = name,
            mobile = mobile,
            diseases = diseases,
            otherDisease = otherDisease,
            relation = relation,
            otherRelation = otherRelation,
            status = status,
            reminderDate = if (reminderRequested == true) reminderDate else "",
            reminderTime = if (reminderRequested == true) reminderTime else "",
            reminderNote = if (reminderRequested == true) reminderNote else "",
            notes = notes
        )
    }

    fun update(
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
    ): LeadAIDraft {
        val trimmedDiseases = diseases?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinctBy { it.lowercase(Locale.ROOT) }
        return this.copy(
            name = name?.trim() ?: this.name,
            mobile = mobile?.trim() ?: this.mobile,
            diseases = trimmedDiseases ?: this.diseases,
            otherDisease = otherDisease?.trim() ?: this.otherDisease,
            relation = relation?.trim() ?: this.relation,
            otherRelation = otherRelation?.trim() ?: this.otherRelation,
            status = status?.trim() ?: this.status,
            reminderRequested = reminderRequested ?: this.reminderRequested,
            reminderDate = reminderDate?.trim() ?: this.reminderDate,
            reminderTime = reminderTime?.trim() ?: this.reminderTime,
            reminderNote = reminderNote?.trim() ?: this.reminderNote,
            notes = notes?.trim() ?: this.notes,
            quickNotesSkipped = quickNotesSkipped ?: this.quickNotesSkipped
        )
    }
}
