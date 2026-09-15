package com.example.ai.extraction

import com.example.ai.intent.ExtractedEntities
import com.example.ai.language.LanguageNormalizer
import java.text.SimpleDateFormat
import java.util.*

object EntityExtractor {

    var calendarProvider: () -> Calendar = { Calendar.getInstance() }

    fun extractEntities(originalText: String): ExtractedEntities {
        val normalized = LanguageNormalizer.normalize(originalText)
        
        val name = extractName(originalText)
        val phone = extractPhone(originalText)
        
        val relativeCal = parseRelativeDuration(normalized)
        
        val datePair = if (relativeCal != null) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(relativeCal.time)
            Pair("today", dateStr)
        } else {
            resolveRelativeDate(normalized)
        }
        
        val relativeDate = if (relativeCal != null) {
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendarProvider().time)
            val calTomorrow = calendarProvider().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calTomorrow.time)
            val calDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(relativeCal.time)
            if (calDate == todayDate) "today" else if (calDate == tomorrowDate) "tomorrow" else "relative"
        } else {
            datePair?.first
        }
        
        val resolvedDate = datePair?.second
        
        val time = if (relativeCal != null) {
            SimpleDateFormat("HH:mm", Locale.US).format(relativeCal.time)
        } else {
            extractTime(normalized)
        }
        
        val status = extractStatus(normalized)
        val noteText = extractNoteText(originalText)
        val reminderDescription = extractReminderDescription(originalText, name)

        return ExtractedEntities(
            name = name,
            phone = phone,
            relativeDate = relativeDate,
            resolvedDate = resolvedDate,
            time = time,
            status = status,
            noteText = noteText,
            reminderDescription = reminderDescription
        )
    }

    fun parseRelativeDuration(normalizedText: String): Calendar? {
        val cal = calendarProvider()
        val text = normalizedText.lowercase(Locale.US)
        
        if (text.contains("aadhe ghante") || text.contains("half an hour") || text.contains("aadhe gante") || text.contains("aadhe ghanta") || text.contains("aadha ghanta")) {
            cal.add(Calendar.MINUTE, 30)
            return cal
        }
        
        val minRegex = Regex("(\\d+)\\s*(?:minute|minutes|min|mins|मिनट|मन्तर)\\s*(?:baad|later|baad me|baad mein|bad)")
        val minMatch = minRegex.find(text)
        if (minMatch != null) {
            val minutes = minMatch.groupValues[1].toInt()
            cal.add(Calendar.MINUTE, minutes)
            return cal
        }
        
        val hourRegex = Regex("(\\d+)\\s*(?:ghante|ghanta|hour|hours|घंटे|घंटा|gante|ganta)\\s*(?:baad|later|baad me|baad mein|bad)")
        val hourMatch = hourRegex.find(text)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toInt()
            cal.add(Calendar.HOUR_OF_DAY, hours)
            return cal
        }
        
        return null
    }

    private fun extractName(text: String): String? {
        val clean = text.trim()

        // Pattern 1: <Name> naam ka/ki/ke/ko
        val naamKaMatch = Regex("(\\b[a-zA-Z\\u0900-\\u097F]+)\\s+naam\\s+(?:ka|ki|ke|ko|da|la)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (naamKaMatch != null) {
            val nameCandidate = naamKaMatch.groupValues[1]
            if (!isKeyword(nameCandidate)) return capitalize(nameCandidate)
        }

        // Pattern 2: naam <Name>
        val naamMatch = Regex("naam\\s+(?:ka|ki|ke|ko|da|la)?\\s*(\\b[a-zA-Z\\u0900-\\u097F]+)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (naamMatch != null) {
            val nameCandidate = naamMatch.groupValues[1]
            if (!isKeyword(nameCandidate)) return capitalize(nameCandidate)
        }

        // Pattern 3: <Name> ka/ki/ke/da/la
        val kaMatch = Regex("(\\b[a-zA-Z\\u0900-\\u097F]+)\\s+(?:ka|ki|ke|ko|da|la)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (kaMatch != null) {
            val nameCandidate = kaMatch.groupValues[1]
            if (!isKeyword(nameCandidate)) return capitalize(nameCandidate)
        }

        // Pattern 4: lead/client/grahak/mariz/patient followed by Name
        val leadNameMatch = Regex("(?:lead|client|customer|patient|member|grahak|mariz|mrz|bimar)\\s+(\\b[a-zA-Z\\u0900-\\u097F]+)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (leadNameMatch != null) {
            val nameCandidate = leadNameMatch.groupValues[1]
            if (!isKeyword(nameCandidate)) return capitalize(nameCandidate)
        }

        // Pattern 5: naya/add/create followed by Name
        val addNameMatch = Regex("(?:add|create|jodo|banao|register|naya|new|nayi)\\s+(\\b[a-zA-Z\\u0900-\\u097F]+)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (addNameMatch != null) {
            val nameCandidate = addNameMatch.groupValues[1]
            if (!isKeyword(nameCandidate)) return capitalize(nameCandidate)
        }

        // Pattern 6: First word if it's not a keyword and is followed by some text
        val parts = clean.split(Regex("\\s+"))
        if (parts.isNotEmpty()) {
            val firstWord = parts[0]
            if (firstWord.isNotEmpty() && !isKeyword(firstWord) && firstWord.matches(Regex("[a-zA-Z\\u0900-\\u097F]+"))) {
                return capitalize(firstWord)
            }
        }

        return null
    }

    private fun extractPhone(text: String): String? {
        val matches = Regex("\\d+").findAll(text).toList()
        for (match in matches) {
            val digits = match.value
            if (digits.length >= 5) {
                return digits
            }
        }
        return null
    }

    private fun resolveRelativeDate(normalizedText: String): Pair<String, String>? {
        val cal = calendarProvider()
        return when {
            normalizedText.contains("today") || normalizedText.contains("aaj") || normalizedText.contains("आज") -> {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                Pair("today", dateStr)
            }
            normalizedText.contains("tomorrow") || normalizedText.contains("kal") || normalizedText.contains("कल") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                Pair("tomorrow", dateStr)
            }
            else -> null
        }
    }

    private fun extractTime(normalizedText: String): String? {
        val text = normalizedText.lowercase(Locale.US)
        
        val hhmmMatch = Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(text)
        var parsedHour: Int? = null
        var parsedMinute: Int? = null
        var isExplicit24Hour = false
        
        if (hhmmMatch != null) {
            val h = hhmmMatch.groupValues[1].toInt()
            val m = hhmmMatch.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) {
                parsedHour = h
                parsedMinute = m
                if (h > 12) {
                    isExplicit24Hour = true
                }
            }
        } else {
            val bajeMatch = Regex("\\b(\\d{1,2})\\s*(?:baje|o'clock)?\\b").find(text)
            if (bajeMatch != null) {
                val h = bajeMatch.groupValues[1].toInt()
                if (h in 0..23) {
                    parsedHour = h
                    parsedMinute = 0
                    if (h > 12) {
                        isExplicit24Hour = true
                    }
                }
            }
        }
        
        if (parsedHour == null || parsedMinute == null) {
            return null
        }
        
        if (parsedHour !in 0..23 || parsedMinute !in 0..59) {
            return null
        }
        
        val hasAm = Regex("\\b(?:am|a\\.m\\.)\\b").containsMatchIn(text)
        val hasPm = Regex("\\b(?:pm|p\\.m\\.)\\b").containsMatchIn(text)
        
        val hasMorning = text.contains("subah") || text.contains("subha") || text.contains("savere") || text.contains("savera") || text.contains("morning") || text.contains("सुबह") || text.contains("सवेरे")
        val hasAfternoon = text.contains("dopahar") || text.contains("dupehar") || text.contains("afternoon") || text.contains("noon") || text.contains("दोपहर")
        val hasEvening = text.contains("shaam") || text.contains("sham") || text.contains("saam") || text.contains("evening") || text.contains("शाम")
        val hasNight = text.contains("raat") || text.contains("rat") || text.contains("night") || text.contains("रात")
        val hasAadhiRaat = text.contains("aadhi raat") || text.contains("आधी रात") || text.contains("midnight")
        
        if (isExplicit24Hour) {
            return String.format(Locale.US, "%02d:%02d", parsedHour, parsedMinute)
        }
        
        if (hasAm || hasPm) {
            var h = parsedHour
            if (hasPm) {
                if (h < 12) h += 12
            } else if (hasAm) {
                if (h == 12) h = 0
            }
            return String.format(Locale.US, "%02d:%02d", h, parsedMinute)
        }
        
        if (hasAadhiRaat) {
            return "00:00"
        }
        
        if (hasMorning) {
            var h = parsedHour
            if (h == 12) h = 0
            return String.format(Locale.US, "%02d:%02d", h, parsedMinute)
        }
        
        if (hasAfternoon) {
            var h = parsedHour
            if (h < 12) h += 12
            return String.format(Locale.US, "%02d:%02d", h, parsedMinute)
        }
        
        if (hasEvening) {
            var h = parsedHour
            if (h < 12) h += 12
            return String.format(Locale.US, "%02d:%02d", h, parsedMinute)
        }
        
        if (hasNight) {
            if (parsedHour in 1..4) {
                return "AMBIGUOUS_RAAT_EARLY_HOUR_$parsedHour"
            }
            var h = parsedHour
            if (h < 12) h += 12
            if (h == 12) h = 0
            return String.format(Locale.US, "%02d:%02d", h, parsedMinute)
        }
        
        if (parsedHour in 1..11) {
            return "AMBIGUOUS_NO_DAYPART_$parsedHour"
        }
        
        return String.format(Locale.US, "%02d:%02d", parsedHour, parsedMinute)
    }

    private fun extractStatus(normalizedText: String): String? {
        return when {
            normalizedText.contains("completed") || normalizedText.contains("complete") || normalizedText.contains("done") || normalizedText.contains("ho gaya") -> "Complete"
            normalizedText.contains("pending") || normalizedText.contains("baki") || normalizedText.contains("baaki") -> "Pending"
            else -> null
        }
    }

    private fun extractNoteText(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null

        val commandPatterns = listOf(
            // Salman ke note mein likho Friday ko dobara call karna
            Regex(
                """\bnotes?\s*(?:mein|me|में)\s*(?:likho|likh\s*do|add\s*karo|jodo|jod\s*do|daalo|dalo|daal\s*do|dal\s*do|karo|kar\s*do|लिखो|लिख\s*दो|जोड़ो|डालो|डाल\s*दो)\s*[:\-]?\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ),

            // Salman ke note likho Friday ko dobara call karna
            Regex(
                """\bnotes?\s*(?:likho|likh\s*do|add\s*karo|jodo|jod\s*do|daalo|dalo|daal\s*do|dal\s*do|karo|kar\s*do|लिखो|लिख\s*दो|जोड़ो|डालो|डाल\s*दो)\s*[:\-]?\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ),

            // Salman ke note mein Friday ko dobara call karna
            Regex(
                """\bnotes?\s*(?:mein|me|में)\s*[:\-]?\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ),

            // Note: Friday ko dobara call karna
            Regex(
                """\bnotes?\s*[:\-]\s*(.+)$""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in commandPatterns) {
            val extracted = pattern.find(clean)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trimStart(':', '-', ' ')
                ?.trim()

            if (!extracted.isNullOrBlank()) {
                return extracted
            }
        }

        return null
    }

    private fun extractReminderDescription(text: String, name: String?): String? {
        val lower = text.lowercase(Locale.getDefault())
        val safeName = name ?: "Client"
        
        val isCall = lower.contains("call") || lower.contains("phone") || lower.contains("baat")
        val isPayment = lower.contains("payment") || lower.contains("paisa") || lower.contains("pay") || lower.contains("fees")
        val isFollowUp = lower.contains("follow-up") || lower.contains("followup") || lower.contains("dobara")

        return when {
            isCall && isPayment && isFollowUp -> "Payment follow-up with $safeName"
            isCall && isPayment -> "Call $safeName for payment"
            isPayment && isFollowUp -> "Payment follow-up with $safeName"
            isCall && isFollowUp -> "Follow-up call with $safeName"
            isCall -> "Call $safeName"
            isPayment -> "Payment reminder for $safeName"
            lower.contains("meeting") || lower.contains("milna") -> "Meeting with $safeName"
            lower.contains("appointment") -> "Appointment for $safeName"
            lower.contains("birthday") || lower.contains("janamdin") -> "Birthday of $safeName"
            lower.contains("medicine") || lower.contains("dawai") -> "Medicine reminder for $safeName"
            lower.contains("visit") -> "Visit $safeName"
            lower.contains("message") || lower.contains("whatsapp") -> "Message $safeName"
            lower.contains("email") || lower.contains("mail") -> "Email $safeName"
            else -> {
                var actionText = text
                if (name != null) {
                    actionText = actionText.replace(Regex("\\b$name\\s*(?:ko|ka|ki|ke|ko)?\\b", RegexOption.IGNORE_CASE), "")
                }
                val wordsToRemove = listOf(
                    "kal", "aaj", "today", "tomorrow", "subah", "subha", "shaam", "sham", "dopahar", "dupehar", "raat", "rat", "night", "morning", "evening", "afternoon", "noon", "baje", "minute", "minutes", "min", "mins", "hours", "hour", "later", "baad", "aadhe", "ghante", "ghanta", "aadhi"
                )
                for (word in wordsToRemove) {
                    actionText = actionText.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), "")
                }
                actionText = actionText.replace(Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|am|pm)?\\b", RegexOption.IGNORE_CASE), "")
                
                val endingsToRemove = listOf(
                    "yaad dilana", "yaad dilana.", "yaad dilana!", "yaad dila", "reminder", "laga do", "laga", "set karo", "set", "karo", "karna", "krna", "kar do", "dila dena", "ki", "ka", "ko", "ke", "liye"
                )
                for (ending in endingsToRemove) {
                    actionText = actionText.replace(Regex("\\b$ending\\b", RegexOption.IGNORE_CASE), "")
                }
                
                actionText = actionText.replace(Regex("\\s+"), " ").trim()
                
                if (actionText.isNotEmpty()) {
                    if (actionText.lowercase(Locale.getDefault()).contains("project quotation bhejne") || actionText.lowercase(Locale.getDefault()).contains("project quotation bhej")) {
                        "Send project quotation to $safeName"
                    } else {
                        actionText.substring(0, 1).uppercase(Locale.getDefault()) + actionText.substring(1)
                    }
                } else {
                    "Reminder for $safeName"
                }
            }
        }
    }

    private fun isKeyword(word: String): Boolean {
        val lower = word.lowercase(Locale.US)
        val keywords = setOf(
            "lead", "client", "customer", "patient", "member", "prospect", "grahak", "mariz", "bimar",
            "add", "jodo", "banao", "create", "register", "naya", "new", "nayi", "karo", "kar", "do",
            "pending", "follow-up", "followup", "baaki", "baki", "status", "stage", "state", "weekly",
            "report", "summary", "stats", "analytics", "reminder", "remindar", "yaad", "dilana", "alert",
            "alarm", "today", "aaj", "tomorrow", "kal", "completed", "complete", "done", "status", "number",
            "num", "no", "mobile", "phone", "ph", "laga", "rakho", "set", "batao", "dikhao", "bana", "shuru",
            "ka", "ki", "ke", "ko", "da", "la", "naam", "baje"
        )
        return keywords.contains(lower)
    }

    private fun capitalize(word: String): String {
        if (word.isEmpty()) return word
        return word.substring(0, 1).uppercase(Locale.US) + word.substring(1).lowercase(Locale.US)
    }
}
