package com.example.ai.intent

import com.example.ai.extraction.EntityExtractor
import com.example.ai.language.LanguageNormalizer
import java.util.Locale

object IntentParser {

    fun parseCommand(originalText: String): ParsedCommand {
        val normalized = LanguageNormalizer.normalize(originalText)
        val entities = EntityExtractor.extractEntities(originalText)

        val intent = detectIntent(normalized, entities)
        
        val missingRequiredFields = mutableListOf<String>()
        var validationStatus = "VALID"
        var clarificationQuestion: String? = null
        var requiresConfirmation = false

        when (intent) {
            AIIntent.CREATE_LEAD -> {
                requiresConfirmation = true
                if (entities.name == null) {
                    missingRequiredFields.add("name")
                    validationStatus = "INCOMPLETE"
                    clarificationQuestion = "Please tell me the name of the lead."
                }
                
                // If there is an invalid phone number
                if (entities.phone != null && entities.phone.length != 10 && entities.phone.length != 12) {
                    validationStatus = "INVALID"
                }
            }
            AIIntent.CREATE_REMINDER -> {
                val isTimeAmbiguous = entities.time?.startsWith("AMBIGUOUS_") == true
                requiresConfirmation = !isTimeAmbiguous
                if (entities.name == null) {
                    missingRequiredFields.add("name")
                }
                if (entities.resolvedDate == null) {
                    missingRequiredFields.add("date")
                }
                if (entities.time == null || isTimeAmbiguous) {
                    missingRequiredFields.add("time")
                }

                if (missingRequiredFields.isNotEmpty()) {
                    validationStatus = "INCOMPLETE"
                    clarificationQuestion = when {
                        entities.name == null -> "Kiske liye reminder set karna hai? (Please provide the person's name)"
                        entities.resolvedDate == null -> "Reminder kis din ke liye lagana hai? (Please provide a date like aaj or kal)"
                        isTimeAmbiguous -> {
                            val timeVal = entities.time!!
                            if (timeVal.startsWith("AMBIGUOUS_RAAT_EARLY_HOUR_")) {
                                val h = timeVal.substringAfter("AMBIGUOUS_RAAT_EARLY_HOUR_")
                                "Kya aap raat $h:00 AM, yani midnight ke baad ka samay keh rahe hain?"
                            } else {
                                val h = timeVal.substringAfter("AMBIGUOUS_NO_DAYPART_")
                                val dayName = if (normalized.contains("tomorrow") || normalized.contains("kal") || normalized.contains("कल")) "Kal" else if (normalized.contains("today") || normalized.contains("aaj") || normalized.contains("आज")) "Aaj" else "Subah"
                                "$dayName subah $h baje ya raat $h baje?"
                            }
                        }
                        entities.time == null -> "Kis samay ka reminder set karna hai? (Please provide a specific time, as guessing is disabled for safety)"
                        else -> "Please provide the missing details: ${missingRequiredFields.joinToString(", ")}"
                    }
                }
            }
            AIIntent.UPDATE_LEAD_STATUS -> {
                requiresConfirmation = true
                if (entities.name == null) {
                    missingRequiredFields.add("name")
                }
                if (entities.status == null) {
                    missingRequiredFields.add("status")
                }

                if (missingRequiredFields.isNotEmpty()) {
                    validationStatus = "INCOMPLETE"
                    clarificationQuestion = when {
                        entities.name == null -> "Kiska status update karna hai? Please provide the name."
                        entities.status == null -> "Kya status set karna hai? (Pending or Complete)"
                        else -> "Please specify the missing information."
                    }
                }
            }
            AIIntent.ADD_LEAD_NOTE -> {
                requiresConfirmation = true
                if (entities.name == null) {
                    missingRequiredFields.add("name")
                }
                if (entities.noteText == null) {
                    missingRequiredFields.add("noteText")
                }

                if (missingRequiredFields.isNotEmpty()) {
                    validationStatus = "INCOMPLETE"
                    clarificationQuestion = "Please provide the missing note details."
                }
            }
            AIIntent.SHOW_PENDING_LEADS, AIIntent.SHOW_TODAY_REMINDERS, AIIntent.SHOW_WEEKLY_REPORT, AIIntent.OPEN_ADD_LEAD -> {
                requiresConfirmation = false
            }
            AIIntent.UNKNOWN -> {
                requiresConfirmation = false
                validationStatus = "INVALID"
            }
        }

        return ParsedCommand(
            intent = intent,
            entities = entities,
            missingRequiredFields = missingRequiredFields,
            validationStatus = validationStatus,
            originalText = originalText,
            normalizedText = normalized,
            requiresConfirmation = requiresConfirmation,
            clarificationQuestion = clarificationQuestion
        )
    }

    private fun detectIntent(normalizedText: String, entities: ExtractedEntities): AIIntent {
        // Checking for Show Weekly Report
        if (normalizedText.contains("weekly") || normalizedText.contains("report") || normalizedText.contains("conversion") || normalizedText.contains("banao")) {
            if (normalizedText.contains("weekly") || normalizedText.contains("report")) {
                // To distinguish from "Ramesh ka weekly status" or similar, check if report is prominent
                return AIIntent.SHOW_WEEKLY_REPORT
            }
        }

        // Checking for Show Pending Leads
        if (normalizedText.contains("pending") || normalizedText.contains("baaki") || normalizedText.contains("baki") || normalizedText.contains("बाकी")) {
            if (normalizedText.contains("lead") || normalizedText.contains("leads") || normalizedText.contains("follow-up") || normalizedText.contains("followup") || normalizedText.contains("dikhao") || normalizedText.contains("show")) {
                if (entities.name == null) {
                    return AIIntent.SHOW_PENDING_LEADS
                }
            }
        }

        // Checking for Show Today's Reminders
        if (normalizedText.contains("reminder") || normalizedText.contains("alert") || normalizedText.contains("alarm")) {
            if (normalizedText.contains("today") || normalizedText.contains("aaj") || normalizedText.contains("batao") || normalizedText.contains("dikhao") || normalizedText.contains("आज")) {
                if (entities.name == null) {
                    return AIIntent.SHOW_TODAY_REMINDERS
                }
            }
        }

        // Checking for Update Lead Status
        if (normalizedText.contains("status") || normalizedText.contains("stage") || normalizedText.contains("complete") || normalizedText.contains("completed") || normalizedText.contains("pending")) {
            if (entities.name != null && entities.status != null) {
                return AIIntent.UPDATE_LEAD_STATUS
            }
        }

        // Checking for Add Lead Note
        if (normalizedText.contains("note") || normalizedText.contains("notes") || normalizedText.contains("comment") || normalizedText.contains("observation")) {
            if (entities.name != null) {
                return AIIntent.ADD_LEAD_NOTE
            }
        }

        // Checking for Create Reminder
        if (normalizedText.contains("reminder") || normalizedText.contains("remindar") || normalizedText.contains("yaad") || normalizedText.contains("alert") || normalizedText.contains("alarm") || normalizedText.contains("laga do") || normalizedText.contains("set")) {
            if (entities.name != null || entities.relativeDate != null || entities.time != null) {
                return AIIntent.CREATE_REMINDER
            }
        }

        // Checking for Create Lead vs Open Add Lead Form
        if (normalizedText.contains("add") || normalizedText.contains("create") || normalizedText.contains("jodo") || normalizedText.contains("banao") || normalizedText.contains("naya") || normalizedText.contains("new") || normalizedText.contains("nayi") || normalizedText.contains("register")) {
            if (normalizedText.contains("lead") || normalizedText.contains("client") || normalizedText.contains("customer") || normalizedText.contains("prospect") || normalizedText.contains("grahak") || normalizedText.contains("mariz")) {
                if (entities.name != null || entities.phone != null) {
                    return AIIntent.CREATE_LEAD
                } else {
                    return AIIntent.OPEN_ADD_LEAD
                }
            }
        }

        // Fallbacks for direct commands like "Ramesh ka status completed kar do"
        if (entities.name != null && entities.status != null && (normalizedText.contains("kar do") || normalizedText.contains("karo") || normalizedText.contains("status"))) {
            return AIIntent.UPDATE_LEAD_STATUS
        }

        // Fallbacks for direct reminder commands like "Rahul ka reminder kal subah 9 baje..."
        if (entities.name != null && (normalizedText.contains("reminder") || normalizedText.contains("remindar") || normalizedText.contains("yaad") || normalizedText.contains("baje") || normalizedText.contains("laga"))) {
            return AIIntent.CREATE_REMINDER
        }

        return AIIntent.UNKNOWN
    }
}
