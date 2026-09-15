package com.example.ai.action

import android.content.Context
import com.example.data.database.LeadEntity
import com.example.data.repository.LeadRepository
import com.example.audio.ReminderScheduler
import com.example.ai.intent.ExtractedEntities
import java.text.SimpleDateFormat
import java.util.Locale

class ActionDispatcher(
    private val context: Context,
    private val repository: LeadRepository
) {

    suspend fun resolveLeads(name: String, allLeads: List<LeadEntity>): List<LeadEntity> {
        val query = name.lowercase(Locale.getDefault()).trim()
        return allLeads.filter {
            it.name.lowercase(Locale.getDefault()).contains(query)
        }
    }

    suspend fun executeAction(request: ActionRequest, allLeads: List<LeadEntity>): ActionResult {
        return when (request.tool) {
            AITool.CREATE_LEAD -> executeCreateLead(request.entities, allLeads)
            AITool.CREATE_REMINDER -> executeCreateReminder(request.entities, allLeads)
            AITool.UPDATE_LEAD_STATUS -> executeUpdateLeadStatus(request.entities, allLeads)
            AITool.ADD_LEAD_NOTE -> executeAddLeadNote(request.entities, allLeads)
        }
    }

    private suspend fun executeCreateLead(entities: ExtractedEntities, allLeads: List<LeadEntity>): ActionResult {
        val name = entities.name?.trim() ?: ""
        if (name.isEmpty()) {
            return ActionResult(false, "Lead name is required.")
        }
        val phone = entities.phone?.trim() ?: ""
        if (phone.isEmpty()) {
            return ActionResult(false, "Phone number is required.")
        }
        if (phone.length != 10 && phone.length != 12) {
            return ActionResult(false, "Invalid phone number length.")
        }

        val isDuplicate = allLeads.any { it.mobile == phone }
        if (isDuplicate) {
            return ActionResult(false, "A lead with phone number $phone already exists.")
        }

        val leadId = java.util.UUID.randomUUID().toString()
        val activeUid = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        } catch (_: Exception) {
            ""
        }
        val lead = LeadEntity(
            id = leadId,
            name = name,
            mobile = phone,
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            notes = "",
            archived = false,
            timestamp = System.currentTimeMillis(),
            ownerUid = activeUid
        )

        return try {
            repository.insertLead(lead)
            ActionResult(
                success = true,
                message = "Lead '$name' was created successfully.",
                details = mapOf("leadId" to leadId, "name" to name, "phone" to phone)
            )
        } catch (e: Exception) {
            ActionResult(false, "Database insertion failed: ${e.message}")
        }
    }

    private suspend fun executeCreateReminder(entities: ExtractedEntities, allLeads: List<LeadEntity>): ActionResult {
        val name = entities.name ?: return ActionResult(false, "Lead name is missing.")
        val date = entities.resolvedDate ?: return ActionResult(false, "Reminder date is missing.")
        val time = entities.time ?: return ActionResult(false, "Reminder time is missing.")

        val matches = resolveLeads(name, allLeads)
        if (matches.isEmpty()) {
            return ActionResult(false, "No lead matches name '$name'.")
        }
        if (matches.size > 1) {
            val names = matches.joinToString(", ") { it.name }
            return ActionResult(false, "Ambiguous query. Multiple leads match: $names.")
        }

        val lead = matches.first()
        
        // Validate date/time is usable and not in the past
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val triggerTimeMs = try {
            val triggerDate = sdf.parse("$date $time")
            triggerDate?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
        if (triggerTimeMs == 0L) {
            return ActionResult(false, "Invalid date/time format.")
        }
        if (triggerTimeMs <= System.currentTimeMillis()) {
            return ActionResult(false, "Cannot schedule reminder in the past ($date $time).")
        }

        // Save reminder to database
        val note = entities.noteText ?: entities.reminderDescription ?: "AI Reminder"
        val actionLabel = if (note.lowercase(Locale.US).contains("call")) "Call reminder" else if (note.lowercase(Locale.US).contains("meeting")) "Meeting reminder" else if (note.lowercase(Locale.US).contains("payment")) "Payment reminder" else "Reminder"
        val reminderEntry = "$actionLabel: ${lead.name} — $date $time"
        
        val updatedNotes = if (lead.notes.contains(reminderEntry)) {
            lead.notes
        } else {
            if (lead.notes.isEmpty()) reminderEntry else "${lead.notes}\n$reminderEntry"
        }

        val updatedLead = lead.copy(
            reminderDate = date,
            reminderTime = time,
            reminderNote = note,
            reminderStatus = "Pending",
            notes = updatedNotes
        )

        return try {
            repository.insertLead(updatedLead)
            
            // Register Android alarm
            var scheduled = false
            var scheduleErr: String? = null
            try {
                ReminderScheduler.scheduleReminder(context, updatedLead)
                scheduled = true
            } catch (e: Exception) {
                scheduleErr = e.message
            }

            if (!scheduled) {
                ActionResult(
                    success = false,
                    message = "Reminder saved, but alarm scheduling failed: $scheduleErr",
                    details = mapOf("leadId" to lead.id, "partialFailure" to true)
                )
            } else {
                val dateParsed = try {
                    val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
                    SimpleDateFormat("d MMMM yyyy", Locale.US).format(parsedDate)
                } catch(e: Exception) {
                    date
                }
                val timeParsed = try {
                    val parsedTime = SimpleDateFormat("HH:mm", Locale.US).parse(time)
                    SimpleDateFormat("h:mm a", Locale.US).format(parsedTime).uppercase(Locale.US)
                } catch(e: Exception) {
                    time
                }
                ActionResult(
                    success = true,
                    message = "$actionLabel for ${lead.name} scheduled for $dateParsed at $timeParsed successfully.",
                    details = mapOf("leadId" to lead.id, "name" to lead.name, "date" to date, "time" to time)
                )
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to update lead reminder in database: ${e.message}")
        }
    }

    private suspend fun executeUpdateLeadStatus(entities: ExtractedEntities, allLeads: List<LeadEntity>): ActionResult {
        val name = entities.name ?: return ActionResult(false, "Lead name is missing.")
        val targetStatus = entities.status ?: return ActionResult(false, "Status is missing.")

        val matches = resolveLeads(name, allLeads)
        if (matches.isEmpty()) {
            return ActionResult(false, "No lead matches name '$name'.")
        }
        if (matches.size > 1) {
            val names = matches.joinToString(", ") { it.name }
            return ActionResult(false, "Ambiguous query. Multiple leads match: $names.")
        }

        val lead = matches.first()
        val normalized = targetStatus.lowercase(Locale.getDefault()).trim()
        val mappedStatus = when {
            normalized.contains("complete") || normalized.contains("done") || normalized.contains("sarthak") || normalized.contains("khatam") -> "Complete"
            normalized.contains("pending") || normalized.contains("active") || normalized.contains("baaki") || normalized.contains("baki") -> "Pending"
            else -> null
        }

        if (mappedStatus == null) {
            return ActionResult(false, "Unsupported status '$targetStatus'. Status must be 'Pending' or 'Complete'.")
        }

        val updatedLead = lead.copy(
            status = mappedStatus,
            reminderStatus = if (mappedStatus == "Complete") "Completed" else lead.reminderStatus
        )

        return try {
            repository.insertLead(updatedLead)
            ActionResult(
                success = true,
                message = "Lead '${lead.name}' status updated from '${lead.status}' to '$mappedStatus' successfully.",
                details = mapOf("leadId" to lead.id, "oldStatus" to lead.status, "newStatus" to mappedStatus)
            )
        } catch (e: Exception) {
            ActionResult(false, "Failed to update lead status: ${e.message}")
        }
    }

    private suspend fun executeAddLeadNote(entities: ExtractedEntities, allLeads: List<LeadEntity>): ActionResult {
        val name = entities.name ?: return ActionResult(false, "Lead name is missing.")
        val noteText = entities.noteText ?: return ActionResult(false, "Note text is missing.")

        if (noteText.trim().isEmpty()) {
            return ActionResult(false, "Note text cannot be blank.")
        }

        val matches = resolveLeads(name, allLeads)
        if (matches.isEmpty()) {
            return ActionResult(false, "No lead matches name '$name'.")
        }
        if (matches.size > 1) {
            val names = matches.joinToString(", ") { it.name }
            return ActionResult(false, "Ambiguous query. Multiple leads match: $names.")
        }

        val lead = matches.first()
        val updatedNotes = if (lead.notes.trim().isEmpty()) {
            noteText
        } else {
            "${lead.notes}\n$noteText"
        }

        val updatedLead = lead.copy(notes = updatedNotes)

        return try {
            repository.insertLead(updatedLead)
            ActionResult(
                success = true,
                message = "Note added to '${lead.name}' successfully.",
                details = mapOf("leadId" to lead.id, "noteAdded" to noteText)
            )
        } catch (e: Exception) {
            ActionResult(false, "Failed to add note: ${e.message}")
        }
    }
}
