package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TextToSpeechManager(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to Indian English or US English
                val fallbackResult = tts?.setLanguage(Locale("en", "IN"))
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onStart()
                }

                override fun onDone(utteranceId: String?) {
                    onDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onError("Error speaking utterance")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError("Error speaking utterance: $errorCode")
                }
            })

            isInitialized = true
            pendingText?.let {
                speak(it)
                pendingText = null
            }
        } else {
            isInitialized = false
            onError("TTS Initialization failed with status $status")
        }
    }

    fun speak(text: String) {
        val cleanedText = sanitizeForSpeech(text)
        if (cleanedText.isBlank()) return

        if (!isInitialized) {
            pendingText = cleanedText
            return
        }

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ai_response_utterance")
        }
        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, "ai_response_utterance")
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TTS", "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e("TTS", "Error shutting down TTS", e)
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    companion object {
        /**
         * Removes visual/technical formatting from text before it is spoken.
         * Kept as a pure internal function so production behavior can be tested
         * without constructing Android's TextToSpeech service.
         */
        internal fun sanitizeForSpeech(text: String): String {
            var clean = text

            clean = clean.replace(Regex("\\*\\*|\\*|__|_|`"), "")
            clean = clean.replace(
                Regex("^[\\s*-]*\\d+\\.\\s+", RegexOption.MULTILINE),
                ""
            )
            clean = clean.replace(
                Regex("^[\\s*-]+\\s*", RegexOption.MULTILINE),
                ""
            )
            clean = clean.replace(
                Regex("actionCardType\\s*=\\s*\\w+"),
                ""
            )
            clean = clean.replace(Regex("id\\s*=\\s*\\w+"), "")

            if (
                clean.contains("Exception:", ignoreCase = true) ||
                clean.contains("Error:", ignoreCase = true)
            ) {
                val lines = clean.split('\n')
                clean = lines.firstOrNull {
                    !it.contains("Exception", ignoreCase = true) &&
                        !it.contains("at ", ignoreCase = true)
                } ?: "An error occurred."
            }

            return clean.replace(Regex("\\s+"), " ").trim()
        }
    }
}
