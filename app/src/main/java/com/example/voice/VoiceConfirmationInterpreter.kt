package com.example.voice

import java.util.Locale

enum class VoiceConfirmationDecision {
    CONFIRM,
    CANCEL
}

/**
 * Interprets only short, explicit confirmation replies.
 *
 * This intentionally does not use loose substring matching. A database-changing
 * lead action must never run because an unrelated sentence happened to contain
 * words such as "save" or "yes".
 */
object VoiceConfirmationInterpreter {

    private val confirmPhrases = setOf(
        "haan",
        "han",
        "ha",
        "ji haan",
        "yes",
        "yes confirm",
        "yes save",
        "confirm",
        "confirm karo",
        "confirm kar do",
        "save",
        "save karo",
        "save kar do",
        "haan kar do",
        "han kar do",
        "haan save karo",
        "haan save kar do",
        "theek hai",
        "theek hai save kar do",
        "thik hai",
        "thik hai save kar do",
        "ok",
        "okay",
        "ok save kar do",
        "okay save kar do",
        "हाँ",
        "हां",
        "जी हाँ",
        "जी हां",
        "कन्फर्म",
        "कन्फर्म कर दो",
        "सेव",
        "सेव कर दो",
        "ठीक है"
    )

    private val cancelPhrases = setOf(
        "nahi",
        "nahin",
        "na",
        "no",
        "no cancel",
        "nahi cancel kar do",
        "nahin cancel kar do",
        "cancel",
        "cancel karo",
        "cancel kar do",
        "mat karo",
        "save mat karo",
        "rehne do",
        "rahne do",
        "chhodo",
        "chhod do",
        "chod do",
        "nahi chahiye",
        "nahin chahiye",
        "नहीं",
        "नही",
        "ना",
        "कैंसल",
        "कैंसल कर दो",
        "मत करो",
        "रहने दो",
        "छोड़ दो"
    )

    fun parse(rawText: String): VoiceConfirmationDecision? {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) return null

        return when (normalized) {
            in confirmPhrases -> VoiceConfirmationDecision.CONFIRM
            in cancelPhrases -> VoiceConfirmationDecision.CANCEL
            else -> null
        }
    }

    internal fun normalize(rawText: String): String {
        return rawText
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
