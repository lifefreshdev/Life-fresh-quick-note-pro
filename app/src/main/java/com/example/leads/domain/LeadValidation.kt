package com.example.leads.domain

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Canonical lead draft used by both the manual Lead form and LifeFresh AI.
 *
 * This file is intentionally Android-free so the validation rules can be unit tested
 * without Compose, Room, Firebase, or an Android Context.
 */
data class LeadDraft(
    val id: String? = null,
    val name: String = "",
    val mobile: String = "",
    val diseases: List<String> = emptyList(),
    val otherDisease: String = "",
    val relation: String = "",
    val otherRelation: String = "",
    val status: String = "Pending",
    val reminderDate: String = "",
    val reminderTime: String = "",
    val reminderNote: String = "",
    val notes: String = ""
)

enum class LeadField {
    NAME,
    MOBILE,
    DISEASES,
    OTHER_DISEASE,
    RELATION,
    OTHER_RELATION,
    STATUS,
    REMINDER_DATE,
    REMINDER_TIME
}

data class LeadValidationIssue(
    val field: LeadField,
    val message: String
)

data class LeadValidationResult(
    val normalizedDraft: LeadDraft,
    val issues: List<LeadValidationIssue>
) {
    val isValid: Boolean
        get() = issues.isEmpty()

    fun firstIssueFor(field: LeadField): LeadValidationIssue? =
        issues.firstOrNull { it.field == field }
}

/**
 * Single source of truth for Lead form validation.
 *
 * Important:
 * - Database duplicate checks do not belong here because they require current data.
 * - Reminder scheduling does not belong here because it requires Android services.
 * - This validator only normalizes and validates one draft deterministically.
 */
object LeadValidator {

    private const val MIN_MOBILE_DIGITS = 10
    private const val MAX_MOBILE_DIGITS = 15

    private val allowedStatuses = setOf("Pending", "Complete")

    fun validate(
        draft: LeadDraft,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadValidationResult {
        val normalized = normalize(draft)
        val issues = mutableListOf<LeadValidationIssue>()

        if (normalized.name.isBlank()) {
            issues += LeadValidationIssue(
                field = LeadField.NAME,
                message = "Name is required."
            )
        }

        val rawMobile = draft.mobile.trim()
        val containsUnsupportedMobileCharacters = rawMobile.any { character ->
            !character.isDigit() && character !in setOf(' ', '+', '-', '(', ')')
        }

        if (
            containsUnsupportedMobileCharacters ||
            normalized.mobile.length !in MIN_MOBILE_DIGITS..MAX_MOBILE_DIGITS
        ) {
            issues += LeadValidationIssue(
                field = LeadField.MOBILE,
                message = "Please enter a valid mobile number (10 to 15 digits)."
            )
        }

        if (normalized.diseases.isEmpty()) {
            issues += LeadValidationIssue(
                field = LeadField.DISEASES,
                message = "At least one category is required."
            )
        }

        if (
            normalized.diseases.any { it.equals("Other", ignoreCase = true) } &&
            normalized.otherDisease.isBlank()
        ) {
            issues += LeadValidationIssue(
                field = LeadField.OTHER_DISEASE,
                message = "Please specify the category detail."
            )
        }

        if (normalized.relation.isBlank()) {
            issues += LeadValidationIssue(
                field = LeadField.RELATION,
                message = "Relation is required."
            )
        }

        if (
            normalized.relation.equals("Other", ignoreCase = true) &&
            normalized.otherRelation.isBlank()
        ) {
            issues += LeadValidationIssue(
                field = LeadField.OTHER_RELATION,
                message = "Please specify the relation detail."
            )
        }

        if (normalized.status !in allowedStatuses) {
            issues += LeadValidationIssue(
                field = LeadField.STATUS,
                message = "Status must be Pending or Complete."
            )
        }

        validateReminder(
            date = normalized.reminderDate,
            time = normalized.reminderTime,
            nowMillis = nowMillis,
            issues = issues
        )

        return LeadValidationResult(
            normalizedDraft = normalized,
            issues = issues.toList()
        )
    }

    fun normalize(draft: LeadDraft): LeadDraft {
        val normalizedStatus = when {
            draft.status.trim().equals("pending", ignoreCase = true) -> "Pending"
            draft.status.trim().equals("complete", ignoreCase = true) -> "Complete"
            draft.status.trim().equals("completed", ignoreCase = true) -> "Complete"
            else -> draft.status.trim()
        }

        return draft.copy(
            id = draft.id?.trim()?.takeIf { it.isNotEmpty() },
            name = collapseWhitespace(draft.name),
            mobile = normalizeMobile(draft.mobile),
            diseases = draft.diseases
                .map(::collapseWhitespace)
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ROOT) },
            otherDisease = collapseWhitespace(draft.otherDisease),
            relation = collapseWhitespace(draft.relation),
            otherRelation = collapseWhitespace(draft.otherRelation),
            status = normalizedStatus,
            reminderDate = draft.reminderDate.trim(),
            reminderTime = draft.reminderTime.trim(),
            reminderNote = draft.reminderNote.trim(),
            notes = draft.notes.trim()
        )
    }

    /**
     * Removes only common phone formatting characters and keeps the complete digit string.
     * Examples:
     * +91 98765-43210 -> 919876543210
     * (98765) 43210   -> 9876543210
     */
    fun normalizeMobile(value: String): String = value.filter(Char::isDigit)

    private fun validateReminder(
        date: String,
        time: String,
        nowMillis: Long,
        issues: MutableList<LeadValidationIssue>
    ) {
        val hasDate = date.isNotEmpty()
        val hasTime = time.isNotEmpty()

        if (!hasDate && !hasTime) return

        if (!hasDate) {
            issues += LeadValidationIssue(
                field = LeadField.REMINDER_DATE,
                message = "Please select both date and time."
            )
        }

        if (!hasTime) {
            issues += LeadValidationIssue(
                field = LeadField.REMINDER_TIME,
                message = "Please select both date and time."
            )
        }

        if (!hasDate || !hasTime) return

        val reminderMillis = parseStrictDateTime(date, time)
        if (reminderMillis == null) {
            issues += LeadValidationIssue(
                field = LeadField.REMINDER_DATE,
                message = "Error parsing reminder date and time."
            )
            return
        }

        if (reminderMillis <= nowMillis) {
            issues += LeadValidationIssue(
                field = LeadField.REMINDER_DATE,
                message = "Error: Selected date/time must be in the future."
            )
        }
    }

    private fun parseStrictDateTime(date: String, time: String): Long? {
        val value = "$date $time"
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null

        return parsed.time.takeIf { position.index == value.length }
    }

    private fun collapseWhitespace(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
