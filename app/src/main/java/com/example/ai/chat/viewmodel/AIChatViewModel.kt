package com.example.ai.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.chat.model.ChatUiState
import com.example.ai.chat.repository.AIChatRepository
import com.example.ai.chat.repository.DefaultAIChatRepository
import com.example.ui.viewmodel.CRMViewModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AIChatViewModel(
    private val repository: AIChatRepository = DefaultAIChatRepository()
) : ViewModel() {
    val uiState: StateFlow<ChatUiState> = repository.uiState
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    private var activeRequestJob: Job? = null
    private var crmViewModel: CRMViewModel? = null

    fun attachCrmViewModel(viewModel: CRMViewModel?) {
        crmViewModel = viewModel
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage(textOverride: String? = null) {
        val query = textOverride ?: _inputText.value
        val trimmed = query.trim()
        if (trimmed.isBlank() || uiState.value.isThinking || activeRequestJob?.isActive == true) return
        if (textOverride == null) _inputText.value = ""

        val job = viewModelScope.launch {
            val crm = crmViewModel
            if (crm != null) {
                val responseId = java.util.UUID.randomUUID().toString()
                val crmResult = crm.processAICommand(trimmed, responseId, {})
                if (crmResult.handled) {
                    coroutineContext.ensureActive()
                    repository.appendExternalResult(trimmed, crmResult.text, responseId, crmResult.isError, "CRM")
                    return@launch
                }
            }
            repository.sendMessage(trimmed)
        }
        activeRequestJob = job
        job.invokeOnCompletion { if (activeRequestJob === job) activeRequestJob = null }
    }

    fun retry() {
        if (!uiState.value.canRetry || uiState.value.isThinking || activeRequestJob?.isActive == true) return
        val job = viewModelScope.launch { repository.retry() }
        activeRequestJob = job
        job.invokeOnCompletion { if (activeRequestJob === job) activeRequestJob = null }
    }

    fun clearConversation() {
        activeRequestJob?.cancel()
        activeRequestJob = null
        _inputText.value = ""
        repository.clearConversation()
    }
}
