package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechRecognitionManager(
    private val context: Context,
    private val onPartialResults: (String) -> Unit,
    private val onFinalResults: (String) -> Unit,
    private val onError: (Int, String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var hasResultSent = false

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                        onError(-1, "Speech recognition is not available on this device")
                        return@post
                    }
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(SpeechListener())
                    }
                }

                hasResultSent = false
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    
                    // Try to configure Indian languages
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged(true)
            } catch (e: Exception) {
                Log.e("SpeechRecognition", "Error starting listening", e)
                isListening = false
                onListeningStateChanged(false)
                onError(-2, e.localizedMessage ?: "Unknown error starting recognizer")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                }
            } catch (e: Exception) {
                Log.e("SpeechRecognition", "Error stopping listening", e)
            } finally {
                isListening = false
                onListeningStateChanged(false)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("SpeechRecognition", "Error cancelling recognizer", e)
            } finally {
                isListening = false
                onListeningStateChanged(false)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("SpeechRecognition", "Error destroying recognizer", e)
            } finally {
                isListening = false
                onListeningStateChanged(false)
            }
        }
    }

    private inner class SpeechListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("SpeechRecognition", "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d("SpeechRecognition", "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d("SpeechRecognition", "onEndOfSpeech")
            isListening = false
            onListeningStateChanged(false)
        }

        override fun onError(error: Int) {
            isListening = false
            onListeningStateChanged(false)
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                else -> "Speech recognition error: $error"
            }
            Log.e("SpeechRecognition", "onError: $message ($error)")
            onError(error, message)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            onListeningStateChanged(false)
            if (hasResultSent) return

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val resultText = matches?.firstOrNull()?.trim() ?: ""
            if (resultText.isNotEmpty()) {
                hasResultSent = true
                onFinalResults(resultText)
            } else {
                onError(SpeechRecognizer.ERROR_NO_MATCH, "No speech recognized")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotEmpty() && !hasResultSent) {
                onPartialResults(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
