package com.example.ai.language

import java.util.Locale

object LanguageNormalizer {
    fun normalize(text: String): String {
        var normalized = text.lowercase(Locale.getDefault())
        // Replace punctuation
        normalized = normalized.replace(Regex("[!?,.()\\-]+"), " ")
        // Replace multiple spaces
        normalized = normalized.replace(Regex("\\s+"), " ")
        return normalized.trim()
    }
}
