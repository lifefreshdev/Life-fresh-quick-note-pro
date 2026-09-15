package com.example.leads.operation

import android.content.Context
import com.example.audio.ReminderScheduler
import com.example.data.database.LeadEntity
import com.example.data.repository.LeadRepository
import com.example.leads.domain.LeadDraft
import com.example.leads.domain.LeadValidationIssue
import com.example.leads.domain.LeadValidator
import org.json.JSONArray
import java.util.UUID

enum class LeadSaveStatus {
    SUCCESS,
    VALIDATION_FAILED,
    DUPLICATE_MOBILE,
    DUPLICATE_REMINDER,
    LEAD_NOT_FOUND,
    DATABASE_ERROR
}

data class LeadSaveResult(
    val status: LeadSaveStatus,
    val entity: LeadEntity? = null,
    val validationIssues: List<LeadValidationIssue> = emptyList(),
    val warnings: List<String> = emptyList(),
    val message: String
) {
    val isSuccess: Boolean
        get() = status == LeadSaveStatus.SUCCESS && entity != null
}

/**
 * Shared save operation for both the manual Leads form and LifeFresh AI.
 *
 * This service owns:
 * - canonical LeadValidator execution
 * - duplicate mobile checks
 * - duplicate reminder checks
 * - create/update LeadEntity mapping
 * - preservation of existing non-form fields
 * - local Room save
 * - reminder scheduling/cancellation
 * - optional post-save cloud sync callback
 *
 * The caller must provide a current leads snapshot.
 * This file does not change Room schema and does not delete user data.
 */
class LeadOperationService(
    context: Context,
    private val repository: LeadRepository,
    private val onLeadSaved: (LeadEntity) -> Unit = {}
) {
    private val appContext = context.applicationContext

    suspend fun saveLead(
        draft: LeadDraft,
        currentLeads: List<LeadEntity>,
        ownerUid: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LeadSaveResult {
        val validationResult = LeadValidator.validate(
            draft = draft,
            nowMillis = nowMillis
        )

        if (!validationResult.isValid) {
            return LeadSaveResult(
                status = LeadSaveStatus.VALIDATION_FAILED,
                validationIssues = validationResult.issues,
                message = validationResult.issues
                    .firstOrNull()
                    ?.message
                    ?: "Lead details valid nahi hain."
            )
        }

        val normalizedDraft = validationResult.normalizedDraft
        val requestedId = normalizedDraft.id
        val existingLead = requestedId?.let { id ->
            currentLeads.firstOrNull { it.id == id }
        }

        if (requestedId != null && existingLead == null) {
            return LeadSaveResult(
                status = LeadSaveStatus.LEAD_NOT_FOUND,
                message = "Update ke liye target lead nahi mila."
            )
        }

        val duplicateMobile = currentLeads.any { lead ->
            lead.id != requestedId &&
                LeadValidator.normalizeMobile(lead.mobile) == normalizedDraft.mobile
        }

        if (duplicateMobile) {
            return LeadSaveResult(
                status = LeadSaveStatus.DUPLICATE_MOBILE,
                message = "Is mobile number ka lead pehle se maujood hai."
            )
        }

        val duplicateReminder = hasDuplicateReminder(
            excludeId = requestedId,
            date = normalizedDraft.reminderDate,
            time = normalizedDraft.reminderTime,
            currentLeads = currentLeads
        )

        if (duplicateReminder) {
            return LeadSaveResult(
                status = LeadSaveStatus.DUPLICATE_REMINDER,
                message = "Isi date aur time par doosra active reminder maujood hai."
            )
        }

        val leadId = requestedId ?: UUID.randomUUID().toString()
        val reminderStatus = resolveReminderStatus(
            existingLead = existingLead,
            draft = normalizedDraft
        )

        val notesUpdatedAt = when {
            existingLead == null && normalizedDraft.notes.isNotBlank() -> nowMillis
            existingLead != null && existingLead.notes != normalizedDraft.notes -> nowMillis
            else -> existingLead?.notesUpdatedAt ?: 0L
        }

        val reminderChanged = existingLead == null ||
            existingLead.reminderDate != normalizedDraft.reminderDate ||
            existingLead.reminderTime != normalizedDraft.reminderTime ||
            existingLead.reminderNote != normalizedDraft.reminderNote

        val reminderUpdatedAt = when {
            normalizedDraft.reminderDate.isBlank() -> 0L
            reminderChanged -> nowMillis
            else -> existingLead?.reminderUpdatedAt ?: 0L
        }

        val activeUid = ownerUid?.takeIf { it.isNotBlank() } ?: try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        } ?: existingLead?.ownerUid ?: ""
        val entity = LeadEntity(
            id = leadId,
            name = normalizedDraft.name,
            mobile = normalizedDraft.mobile,
            diseases = JSONArray(normalizedDraft.diseases).toString(),
            otherDisease = normalizedDraft.otherDisease,
            relation = normalizedDraft.relation,
            otherRelation = normalizedDraft.otherRelation,
            status = normalizedDraft.status,
            reminderDate = normalizedDraft.reminderDate,
            reminderTime = normalizedDraft.reminderTime,
            reminderNote = normalizedDraft.reminderNote,
            reminderStatus = reminderStatus,
            notes = normalizedDraft.notes,
            archived = existingLead?.archived ?: false,
            lastCall = existingLead?.lastCall,
            timestamp = existingLead?.timestamp ?: nowMillis,
            notesUpdatedAt = notesUpdatedAt,
            reminderUpdatedAt = reminderUpdatedAt,
            ownerUid = activeUid
        )

        return try {
            repository.insertLead(entity)

            val warnings = mutableListOf<String>()

            try {
                if (entity.archived) {
                    ReminderScheduler.cancelReminder(appContext, entity.ownerUid, entity.id)
                } else {
                    val result = ReminderScheduler.scheduleReminder(appContext, entity)
                    if (result == com.example.audio.ScheduleResult.INEXACT_FALLBACK_SCHEDULED) {
                        warnings += "Exact alarm permission na hone ki wajah se approximate reminder schedule hua."
                    } else if (result == com.example.audio.ScheduleResult.PERMISSION_REQUIRED) {
                        warnings += "Exact alarm permission zaroori hai reminder ke liye."
                    }
                }
            } catch (error: Exception) {
                warnings += "Lead save hua, lekin reminder sync nahi ho saka: ${error.message.orEmpty()}"
            }

            try {
                onLeadSaved(entity)
            } catch (error: Exception) {
                warnings += "Lead local database mein save hua, lekin cloud sync start nahi ho saka: ${error.message.orEmpty()}"
            }

            LeadSaveResult(
                status = LeadSaveStatus.SUCCESS,
                entity = entity,
                warnings = warnings.toList(),
                message = if (existingLead == null) {
                    "Lead '${entity.name}' successfully create hua."
                } else {
                    "Lead '${entity.name}' successfully update hua."
                }
            )
        } catch (error: Exception) {
            LeadSaveResult(
                status = LeadSaveStatus.DATABASE_ERROR,
                message = "Lead save nahi ho saka: ${error.message.orEmpty()}"
            )
        }
    }

    fun hasDuplicateReminder(
        excludeId: String?,
        date: String,
        time: String,
        currentLeads: List<LeadEntity>
    ): Boolean {
        if (date.isBlank() || time.isBlank()) return false

        return currentLeads.any { lead ->
            lead.id != excludeId &&
                lead.reminderDate == date &&
                lead.reminderTime == time &&
                !lead.reminderStatus.equals("Completed", ignoreCase = true) &&
                !lead.archived
        }
    }

    private fun resolveReminderStatus(
        existingLead: LeadEntity?,
        draft: LeadDraft
    ): String {
        if (existingLead == null) {
            return if (
                draft.status == "Complete" &&
                draft.reminderDate.isEmpty()
            ) {
                "Completed"
            } else {
                "Pending"
            }
        }

        val reminderChanged =
            existingLead.reminderDate != draft.reminderDate ||
                existingLead.reminderTime != draft.reminderTime ||
                existingLead.reminderNote != draft.reminderNote

        if (reminderChanged) {
            return "Pending"
        }

        return if (
            draft.status == "Complete" &&
            existingLead.status != "Complete"
        ) {
            "Completed"
        } else {
            existingLead.reminderStatus
        }
    }
}
