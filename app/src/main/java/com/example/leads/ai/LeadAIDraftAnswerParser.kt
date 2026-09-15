package com.example.leads.ai

import com.example.ai.extraction.EntityExtractor
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class LeadAIDraftAnswerStatus {
    ACCEPTED,
    SKIPPED,
    INVALID
}

data class LeadAIDraftPatch(
    val name: String? = null,
    val mobile: String? = null,
    val diseases: List<String>? = null,
    val otherDisease: String? = null,
    val relation: String? = null,
    val otherRelation: String? = null,
    val status: String? = null,
    val reminderRequested: Boolean? = null,
    val reminderDate: String? = null,
    val reminderTime: String? = null,
    val reminderNote: String? = null,
    val notes: String? = null,
    val quickNotesSkipped: Boolean? = null
)

data class LeadAIDraftAnswerResult(
    val status: LeadAIDraftAnswerStatus,
    val patch: LeadAIDraftPatch = LeadAIDraftPatch(),
    val message: String? = null
) {
    val isAccepted: Boolean
        get() = status == LeadAIDraftAnswerStatus.ACCEPTED ||
            status == LeadAIDraftAnswerStatus.SKIPPED
}

object LeadAIDraftAnswerParser {

    /**
     * Injectable clock keeps date parsing deterministic in unit tests.
     */
    var calendarProvider: () -> Calendar = { Calendar.getInstance() }

    private val fullDateFormats = listOf(
        "yyyy-MM-dd",
        "d/M/yyyy",
        "d-M-yyyy",
        "d.M.yyyy",
        "d MMM yyyy",
        "d MMMM yyyy"
    )

    private val dateWithoutYearFormats = listOf(
        "d MMM",
        "d MMMM"
    )

    /*
     * Only explicit cancellation phrases belong here.
     *
     * Ambiguous phrases such as "rehne do" and "chhodo" are intentionally
     * not treated as full-draft cancellation. During reminder/notes steps,
     * users commonly use those phrases to skip only the optional field.
     */
    private val cancelWords = setOf(
        "cancel",
        "cancel karo",
        "cancel kar do",
        "poora cancel karo",
        "lead cancel karo",
        "band karo",
        "रद्द",
        "रद्द करो",
        "पूरा रद्द करो"
    )

    private val skipWords = setOf(
        "skip",
        "skip karo",
        "skip kar do",
        "nahi",
        "nahin",
        "no",
        "none",
        "kuch nahi",
        "kuch nahin",
        "rehne do",
        "chhodo",
        "chhod do",
        "नहीं",
        "छोड़ो",
        "छोड़ दो"
    )

    private val yesWords = setOf(
        "yes",
        "haan",
        "han",
        "ha",
        "ji",
        "haan ji",
        "yes please",
        "laga do",
        "kar do",
        "rakh do",
        "हाँ",
        "हां",
        "जी"
    )

    private val noWords = setOf(
        "no",
        "nahi",
        "nahin",
        "na",
        "mat lagao",
        "reminder nahi",
        "reminder nahin",
        "skip",
        "rehne do",
        "chhodo",
        "chhod do",
        "नहीं",
        "ना",
        "मत लगाओ",
        "रहने दो",
        "छोड़ो",
        "छोड़ दो"
    )

    fun parse(
        step: LeadAIDraftStep,
        rawText: String
    ): LeadAIDraftAnswerResult {
        val clean = rawText.trim()
        if (clean.isEmpty()) {
            return invalid("Jawab khali nahi ho sakta.")
        }

        return when (step) {
            LeadAIDraftStep.NAME -> parseName(clean)
            LeadAIDraftStep.MOBILE -> parseMobile(clean)
            LeadAIDraftStep.WELLNESS_CATEGORIES -> parseCategories(clean)
            LeadAIDraftStep.OTHER_CATEGORY_DETAIL -> accepted(
                LeadAIDraftPatch(otherDisease = clean)
            )
            LeadAIDraftStep.RELATION -> parseRelation(clean)
            LeadAIDraftStep.OTHER_RELATION_DETAIL -> accepted(
                LeadAIDraftPatch(otherRelation = clean)
            )
            LeadAIDraftStep.STATUS -> parseStatus(clean)
            LeadAIDraftStep.REMINDER_CHOICE -> parseReminderChoice(clean)
            LeadAIDraftStep.REMINDER_DATE -> parseReminderDate(clean)
            LeadAIDraftStep.REMINDER_TIME -> parseReminderTime(clean)
            LeadAIDraftStep.REMINDER_NOTE -> accepted(
                LeadAIDraftPatch(reminderNote = clean)
            )
            LeadAIDraftStep.QUICK_NOTES -> parseQuickNotes(clean)
            LeadAIDraftStep.READY_FOR_CONFIRMATION -> invalid(
                "Draft confirmation ke liye ready hai."
            )
        }
    }

    fun isCancelCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return normalized in cancelWords
    }

    fun isSkipCommand(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return normalized in skipWords
    }

    private fun parseName(text: String): LeadAIDraftAnswerResult {
        val candidate = text
            .replace(
                Regex(
                    """^(?:naam|name)\s*(?:hai|is|:|-)?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()

        if (candidate.isBlank()) {
            return invalid("Lead ka sahi naam batayein.")
        }

        if (!candidate.any { it.isLetter() }) {
            return invalid("Naam mein kam se kam ek letter hona chahiye.")
        }

        return accepted(
            LeadAIDraftPatch(name = candidate)
        )
    }

    private fun parseMobile(text: String): LeadAIDraftAnswerResult {
        val allowed = text.all {
            it.isDigit() ||
                it.isWhitespace() ||
                it == '+' ||
                it == '-' ||
                it == '(' ||
                it == ')'
        }

        if (!allowed) {
            return invalid("Mobile number mein sirf digits aur normal separators use karein.")
        }

        val digits = text.filter(Char::isDigit)
        if (digits.length !in 10..15) {
            return invalid("Mobile number 10 se 15 digits ka hona chahiye.")
        }

        return accepted(
            LeadAIDraftPatch(mobile = digits)
        )
    }

    private fun parseCategories(text: String): LeadAIDraftAnswerResult {
        val categories = text
            .split(
                Regex(
                    """\s*(?:,|&|\band\b|\baur\b|\bor\b)\s*""",
                    RegexOption.IGNORE_CASE
                )
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }

        if (categories.isEmpty()) {
            return invalid("Kam se kam ek wellness category batayein.")
        }

        return accepted(
            LeadAIDraftPatch(diseases = categories)
        )
    }

    private fun parseRelation(text: String): LeadAIDraftAnswerResult {
        val candidate = text
            .replace(
                Regex(
                    """^(?:relation|rishta)\s*(?:hai|is|:|-)?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()

        if (candidate.isBlank()) {
            return invalid("Relation batayein.")
        }

        return accepted(
            LeadAIDraftPatch(relation = candidate)
        )
    }

    private fun parseStatus(text: String): LeadAIDraftAnswerResult {
        val normalized = normalize(text)

        val status = when {
            normalized.contains("complete") ||
                normalized.contains("completed") ||
                normalized.contains("done") ||
                normalized.contains("khatam") ||
                normalized.contains("poora") ||
                normalized.contains("पूरा") -> "Complete"

            normalized.contains("pending") ||
                normalized.contains("baki") ||
                normalized.contains("baaki") ||
                normalized.contains("active") ||
                normalized.contains("बाकी") -> "Pending"

            else -> null
        }

        return if (status == null) {
            invalid("Status Pending ya Complete batayein.")
        } else {
            accepted(
                LeadAIDraftPatch(status = status)
            )
        }
    }

    private fun parseReminderChoice(text: String): LeadAIDraftAnswerResult {
        val normalized = normalize(text)

        return when {
            normalized in yesWords ||
                normalized.startsWith("haan ") ||
                normalized.startsWith("han ") ||
                normalized.startsWith("yes ") -> accepted(
                LeadAIDraftPatch(reminderRequested = true)
            )

            normalized in noWords ||
                normalized.startsWith("nahi ") ||
                normalized.startsWith("nahin ") ||
                normalized.startsWith("no ") -> accepted(
                LeadAIDraftPatch(reminderRequested = false)
            )

            else -> invalid("Reminder ke liye Haan ya Nahi batayein.")
        }
    }

    private fun parseReminderDate(text: String): LeadAIDraftAnswerResult {
        val normalized = normalize(text)

        val resolvedDate = when {
            normalized == "parso" ||
                normalized == "day after tomorrow" ||
                normalized == "परसों" -> {
                calendarProvider().apply {
                    add(Calendar.DAY_OF_YEAR, 2)
                }.toIsoDate()
            }

            else -> {
                EntityExtractor.extractEntities(text).resolvedDate
                    ?: parseExplicitDate(text)
            }
        }

        if (resolvedDate.isNullOrBlank()) {
            return invalid(
                "Date samajh nahi aayi. Aaj, kal, parso, 2026-07-25 " +
                    "ya 25 July 2026 jaise date batayein."
            )
        }

        if (isPastDate(resolvedDate)) {
            return invalid("Reminder ke liye aaj ya future date batayein.")
        }

        return accepted(
            LeadAIDraftPatch(reminderDate = resolvedDate)
        )
    }

    private fun parseReminderTime(text: String): LeadAIDraftAnswerResult {
        val entities = EntityExtractor.extractEntities(text)
        val parsedTime = entities.time

        if (parsedTime.isNullOrBlank()) {
            return invalid("Time samajh nahi aaya. Jaise: subah 9 baje.")
        }

        if (parsedTime.startsWith("AMBIGUOUS_")) {
            val hour = parsedTime.substringAfterLast("_")
            return invalid("Time clear karein: subah $hour baje ya shaam/raat $hour baje?")
        }

        return accepted(
            LeadAIDraftPatch(reminderTime = parsedTime)
        )
    }

    private fun parseQuickNotes(text: String): LeadAIDraftAnswerResult {
        return if (isSkipCommand(text)) {
            LeadAIDraftAnswerResult(
                status = LeadAIDraftAnswerStatus.SKIPPED,
                patch = LeadAIDraftPatch(quickNotesSkipped = true)
            )
        } else {
            accepted(
                LeadAIDraftPatch(
                    notes = text,
                    quickNotesSkipped = false
                )
            )
        }
    }

    private fun parseExplicitDate(text: String): String? {
        val clean = text
            .trim()
            .replace(
                Regex(
                    """^(?:date|reminder date|tarikh|tareekh|तारीख)\s*(?:hai|is|:|-)?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()

        fullDateFormats.forEach { pattern ->
            parseStrictDate(clean, pattern)?.let { parsed ->
                return parsed.toIsoDate()
            }
        }

        /*
         * A date such as "25 July" uses the current year when it is still
         * upcoming; otherwise it rolls forward to the next year.
         */
        dateWithoutYearFormats.forEach { pattern ->
            val currentYear = calendarProvider().get(Calendar.YEAR)
            val parsed = parseStrictDate(
                value = "$clean $currentYear",
                pattern = "$pattern yyyy"
            )

            if (parsed != null) {
                var candidate: Date = parsed

                if (candidate.toIsoDate() < todayIsoDate()) {
                    val calendar = calendarProvider().apply {
                        time = candidate
                        add(Calendar.YEAR, 1)
                    }
                    candidate = calendar.time
                }

                return candidate.toIsoDate()
            }
        }

        return null
    }

    private fun parseStrictDate(
        value: String,
        pattern: String
    ): Date? {
        val formatter = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null

        return parsed.takeIf { position.index == value.length }
    }

    private fun isPastDate(isoDate: String): Boolean {
        return isoDate < todayIsoDate()
    }

    private fun todayIsoDate(): String {
        return calendarProvider().toIsoDate()
    }

    private fun Calendar.toIsoDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(time)
    }

    private fun Date.toIsoDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(this)
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""[!?,.]+"""), "")
            .replace(Regex("""\s+"""), " ")
    }

    private fun accepted(
        patch: LeadAIDraftPatch
    ): LeadAIDraftAnswerResult {
        return LeadAIDraftAnswerResult(
            status = LeadAIDraftAnswerStatus.ACCEPTED,
            patch = patch
        )
    }

    private fun invalid(
        message: String
    ): LeadAIDraftAnswerResult {
        return LeadAIDraftAnswerResult(
            status = LeadAIDraftAnswerStatus.INVALID,
            message = message
        )
    }
}
