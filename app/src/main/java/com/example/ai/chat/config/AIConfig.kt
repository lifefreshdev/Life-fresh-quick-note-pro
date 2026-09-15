package com.example.ai.chat.config

import com.example.BuildConfig

/**
 * Provides access to AI API configurations securely.
 * Keys are loaded from BuildConfig (populated via .env / Secrets panel in AI Studio)
 * and never hardcoded in source files.
 */
object AIConfig {

    @Volatile
    var customGeminiApiKeyProvider: (() -> String)? = null

    val groqApiKey: String
        get() = try {
            val key = BuildConfig.GROQ_API_KEY
            if (key.isNotBlank() && key != "DEFAULT_GROQ_API_KEY" && key != "null") key.trim() else ""
        } catch (e: Throwable) {
            ""
        }

    val geminiApiKey: String
        get() {
            val custom = try {
                customGeminiApiKeyProvider?.invoke()?.trim().orEmpty()
            } catch (e: Throwable) {
                ""
            }
            if (custom.isNotBlank()) return custom

            return try {
                val key = BuildConfig.GEMINI_API_KEY
                if (key.isNotBlank() && key != "DEFAULT_GEMINI_API_KEY" && key != "null") key.trim() else ""
            } catch (e: Throwable) {
                ""
            }
        }

    val isGroqConfigured: Boolean
        get() = groqApiKey.isNotBlank()

    val isGeminiConfigured: Boolean
        get() = geminiApiKey.isNotBlank()

    const val DEFAULT_SYSTEM_INSTRUCTION =
        "You are LifeFresh AI, the conversational assistant inside LifeFresh QuickNote Pro.\n" +
        "Respond naturally in English, Hindi, or Hinglish depending on the user's language.\n" +
        "Format your responses cleanly for a mobile chat screen: prefer clear paragraphs and simple bullet points.\n" +
        "Avoid unnecessary Markdown heading markers (such as '#', '##'), ASCII/pipe tables ('|'), horizontal divider lines, or excessive decorative symbols.\n" +
        "When explaining concepts, present structured information as clean bulleted or numbered lists rather than markdown tables.\n" +
        "When sharing code or commands, use standard markdown code blocks with language tags.\n" +
        "At this stage you are a text conversational assistant only.\n" +
        "Do not claim to have modified CRM data, reminders, files or device state.\n" +
        "Do not provide medical diagnosis or treatment."
}
