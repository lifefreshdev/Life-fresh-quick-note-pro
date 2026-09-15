package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ui.screens.MockMessage
import com.example.ui.screens.Sender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceConversationManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onSendText: (String) -> Unit
) {
    private val _state = MutableStateFlow(VoiceConversationState.IDLE)
    val state: StateFlow<VoiceConversationState> = _state.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _isVoiceModeEnabled = MutableStateFlow(false)
    val isVoiceModeEnabled: StateFlow<Boolean> = _isVoiceModeEnabled.asStateFlow()

    private var speechRecognizerManager: SpeechRecognitionManager? = null
    private var textToSpeechManager: TextToSpeechManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastSpokenMessageSignature: String? = null
    private var consecutiveErrorCount = 0
    private val maxConsecutiveErrors = 3

    init {
        initSpeechAndTts()
    }

    private fun initSpeechAndTts() {
        speechRecognizerManager = SpeechRecognitionManager(
            context = context,
            onPartialResults = { partialText ->
                if (_state.value == VoiceConversationState.LISTENING) {
                    _liveSpokenText.value = partialText
                }
            },
            onFinalResults = { finalResults ->
                _liveSpokenText.value = finalResults
                handleSpokenInputSubmitted(finalResults)
            },
            onError = { errorCode, errorMsg ->
                handleSpeechError(errorCode, errorMsg)
            },
            onListeningStateChanged = { isListening ->
                if (isListening) {
                    _state.value = VoiceConversationState.LISTENING
                } else if (_state.value == VoiceConversationState.LISTENING) {
                    _state.value = VoiceConversationState.PROCESSING
                }
            }
        )

        textToSpeechManager = TextToSpeechManager(
            context = context,
            onStart = {
                _state.value = VoiceConversationState.SPEAKING
            },
            onDone = {
                _state.value = VoiceConversationState.IDLE
                consecutiveErrorCount = 0 // Reset error count on successful speech
                mainHandler.postDelayed({
                    if (_isVoiceModeEnabled.value) {
                        startListeningInternal()
                    }
                }, 800) // Small delay to prevent catching own TTS echo or clipping
            },
            onError = { errorMsg ->
                Log.e("VoiceConversation", "TTS Error: $errorMsg")
                _state.value = VoiceConversationState.IDLE
                if (_isVoiceModeEnabled.value) {
                    startListeningInternal()
                }
            }
        )
    }

    fun setInitialLastSpokenMessage(message: MockMessage?) {
        if (lastSpokenMessageSignature == null && message != null) {
            lastSpokenMessageSignature = buildMessageSignature(message)
            Log.d(
                "VoiceConversation",
                "Muted initial historical AI message: ${message.id}"
            )
        }
    }

    fun onMessagesUpdated(messages: List<MockMessage>, isThinking: Boolean, isConfirmationExecuting: Boolean) {
        val latestAiMessage = messages.lastOrNull { it.sender == Sender.AI }
            ?: return
        val latestSignature = buildMessageSignature(latestAiMessage)

        // Text-mode replies must stay silent. Keep the signature current so
        // enabling voice mode later never speaks an old historical response.
        if (!_isVoiceModeEnabled.value) {
            lastSpokenMessageSignature = latestSignature
            return
        }

        // The first message observed after manager creation is history, not a
        // new answer. This also prevents speaking on initial screen entry.
        if (lastSpokenMessageSignature == null) {
            lastSpokenMessageSignature = latestSignature
            return
        }

        if (latestSignature == lastSpokenMessageSignature) return

        // Do not mark the changed message as spoken yet. The same message is
        // re-evaluated when thinking/execution finishes, so the real final
        // result is not lost.
        if (isThinking || isConfirmationExecuting) return

        lastSpokenMessageSignature = latestSignature
        speakResponse(latestAiMessage.text)
    }

    fun toggleVoiceMode() {
        if (_isVoiceModeEnabled.value) {
            stopVoiceMode()
        } else {
            startVoiceMode()
        }
    }

    fun startVoiceMode() {
        // Check permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = VoiceConversationState.PERMISSION_REQUIRED
            _isVoiceModeEnabled.value = true
            android.widget.Toast.makeText(
                context,
                "Microphone permission is required to record voice notes.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        _isVoiceModeEnabled.value = true
        consecutiveErrorCount = 0
        _liveSpokenText.value = ""
        
        // Ensure TTS is stopped before listening
        textToSpeechManager?.stop()
        
        startListeningInternal()
    }

    fun stopVoiceMode() {
        _isVoiceModeEnabled.value = false
        _state.value = VoiceConversationState.IDLE
        _liveSpokenText.value = ""
        consecutiveErrorCount = 0
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizerManager?.cancel()
        textToSpeechManager?.stop()
    }

    private fun startListeningInternal() {
        if (!_isVoiceModeEnabled.value) return
        if (textToSpeechManager?.isSpeaking() == true) {
            Log.d("VoiceConversation", "TTS is speaking, delay start listening")
            return
        }

        _state.value = VoiceConversationState.LISTENING
        speechRecognizerManager?.startListening()
    }

    private fun handleSpokenInputSubmitted(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            // Ignore empty results
            if (_isVoiceModeEnabled.value) {
                startListeningInternal()
            }
            return
        }

        consecutiveErrorCount = 0 // Reset on successful user input
        _state.value = VoiceConversationState.PROCESSING
        speechRecognizerManager?.stopListening()
        
        // Auto-send
        onSendText(trimmed)
    }

    private fun speakResponse(text: String) {
        // Protect feedback-loop: Stop recognition before speaking
        speechRecognizerManager?.stopListening()
        _state.value = VoiceConversationState.SPEAKING
        textToSpeechManager?.speak(text)
    }

    private fun handleSpeechError(errorCode: Int, errorMsg: String) {
        Log.e("VoiceConversation", "Speech Error: $errorMsg ($errorCode)")
        
        if (errorCode == 9) { // ERROR_INSUFFICIENT_PERMISSIONS
            _state.value = VoiceConversationState.PERMISSION_REQUIRED
            _isVoiceModeEnabled.value = false
            return
        }

        consecutiveErrorCount++
        if (consecutiveErrorCount >= maxConsecutiveErrors) {
            Log.e("VoiceConversation", "Too many consecutive speech errors, stopping voice mode")
            _state.value = VoiceConversationState.ERROR
            _isVoiceModeEnabled.value = false
            return
        }

        if (_isVoiceModeEnabled.value) {
            // Wait a small delay before restarting
            mainHandler.postDelayed({
                if (_isVoiceModeEnabled.value) {
                    startListeningInternal()
                }
            }, 1500)
        } else {
            _state.value = VoiceConversationState.IDLE
        }
    }

    fun onPause() {
        // Pause listening but keep mode enabled if we return
        speechRecognizerManager?.cancel()
    }

    fun onDestroy() {
        stopVoiceMode()
        speechRecognizerManager?.destroy()
        textToSpeechManager?.shutdown()
    }

    companion object {
        internal fun buildMessageSignature(message: MockMessage): String {
            return listOf(
                message.id,
                message.text,
                message.sender.name,
                message.isError.toString(),
                message.isConfirmation.toString(),
                message.actionCardType.orEmpty()
            ).joinToString(separator = "\u001F")
        }
    }
}
