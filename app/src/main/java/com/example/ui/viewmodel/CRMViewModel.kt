package com.example.ui.viewmodel

import com.example.BuildConfig
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.example.audio.AlarmSynthesizer
import com.example.audio.ReminderScheduler
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import com.example.data.database.AIChatSessionEntity
import com.example.data.database.AIChatMessageEntity
import com.example.data.repository.LeadRepository
import com.example.data.repository.AIChatRepository
import com.example.ui.screens.Sender
import com.example.ui.screens.MockMessage
import com.example.ui.screens.ChatSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

sealed interface AICommandState {
    object Idle : AICommandState
    object Loading : AICommandState
    object Success : AICommandState
    object Empty : AICommandState
    data class Error(val message: String) : AICommandState
    object UnsupportedCommand : AICommandState
}

data class AICommandResult(
    val text: String,
    val actionCardType: String?,
    val isConfirmation: Boolean = false,
    val isError: Boolean = false,
    val isOfflineWarning: Boolean = false,
    val handled: Boolean = true
)

class CRMViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("CRMViewModel", "Caught unhandled coroutine exception: ${throwable.message}", throwable)
    }

    private val _aiCommandState = MutableStateFlow<AICommandState>(AICommandState.Idle)
    val aiCommandState: StateFlow<AICommandState> = _aiCommandState.asStateFlow()

    fun registerPendingConfirmation(messageId: String, tool: com.example.ai.action.AITool, entities: com.example.ai.intent.ExtractedEntities) {
        val request = com.example.ai.action.ActionRequest(tool, entities, messageId)
        val pending = com.example.ai.action.PendingConfirmation(request, com.example.ai.action.ConfirmationStatus.PENDING)
        _pendingConfirmations.update { it + (messageId to pending) }
    }

    fun confirmAction(messageId: String) {
        viewModelScope.launch {
            val leadAIState = leadAIViewModelBridge.getConfirmationState(messageId)
            if (leadAIState != null) {
                val result = leadAIViewModelBridge.confirm(
                    messageId = messageId,
                    currentLeads = allLeadsList.value
                )
                persistLeadAIConfirmationResult(
                    messageId = messageId,
                    result = result
                )
                return@launch
            }

            val pending = _pendingConfirmations.value[messageId] ?: return@launch
            if (pending.status == com.example.ai.action.ConfirmationStatus.EXECUTING) return@launch

            _pendingConfirmations.update { map ->
                map + (messageId to pending.copy(status = com.example.ai.action.ConfirmationStatus.EXECUTING))
            }

            val result = actionDispatcher.executeAction(pending.request, allLeadsList.value)

            val messages = activeSessionMessages.value
            val currentMessage = messages.find { it.id == messageId }
            if (currentMessage != null) {
                val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                if (uid.isNotBlank()) {
                    val updatedEntity = AIChatMessageEntity(
                        ownerUid = uid,
                        id = currentMessage.id,
                        sessionId = activeSessionId.value ?: "",
                        text = if (result.success) result.message else "Error: ${result.message}",
                        sender = "AI",
                        timestamp = currentMessage.timestamp,
                        isError = !result.success,
                        isOfflineWarning = false,
                        isConfirmation = false,
                        actionCardType = "confirmation"
                    )
                    aiChatRepository.insertMessage(updatedEntity)
                }
            }

            if (result.success) {
                _pendingConfirmations.update { map ->
                    map + (messageId to pending.copy(
                        status = com.example.ai.action.ConfirmationStatus.SUCCESS,
                        successText = result.message
                    ))
                }
            } else {
                _pendingConfirmations.update { map ->
                    map + (messageId to pending.copy(
                        status = com.example.ai.action.ConfirmationStatus.FAILED,
                        errorText = result.message
                    ))
                }
            }
        }
    }

    fun cancelAction(messageId: String) {
        viewModelScope.launch {
            val leadAIState = leadAIViewModelBridge.getConfirmationState(messageId)
            if (leadAIState != null) {
                val result = leadAIViewModelBridge.cancelConfirmation(messageId)
                persistLeadAIConfirmationResult(
                    messageId = messageId,
                    result = result
                )
                return@launch
            }

            val pending = _pendingConfirmations.value[messageId] ?: return@launch
            if (pending.status == com.example.ai.action.ConfirmationStatus.EXECUTING) return@launch

            val messages = activeSessionMessages.value
            val currentMessage = messages.find { it.id == messageId }
            if (currentMessage != null) {
                val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                if (uid.isNotBlank()) {
                    val updatedEntity = AIChatMessageEntity(
                        ownerUid = uid,
                        id = currentMessage.id,
                        sessionId = activeSessionId.value ?: "",
                        text = "Action cancelled.",
                        sender = "AI",
                        timestamp = currentMessage.timestamp,
                        isError = false,
                        isOfflineWarning = false,
                        isConfirmation = false,
                        actionCardType = "confirmation"
                    )
                    aiChatRepository.insertMessage(updatedEntity)
                }
            }

            _pendingConfirmations.update { map ->
                map + (messageId to pending.copy(
                    status = com.example.ai.action.ConfirmationStatus.CANCELLED
                ))
            }
        }
    }

    private suspend fun persistLeadAIConfirmationResult(
        messageId: String,
        result: com.example.leads.ai.LeadAIChatResult
    ) {
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        val currentMessage = activeSessionMessages.value
            .firstOrNull { it.id == messageId }
            ?: return

        val remainsInteractive = when (result.status) {
            com.example.leads.ai.LeadAIChatStatus.EXECUTION_FAILED,
            com.example.leads.ai.LeadAIChatStatus.REJECTED,
            com.example.leads.ai.LeadAIChatStatus.ALREADY_RUNNING -> true

            else -> false
        }

        val updatedEntity = AIChatMessageEntity(
            ownerUid = uid,
            id = currentMessage.id,
            sessionId = activeSessionId.value ?: return,
            text = result.text,
            sender = "AI",
            timestamp = System.currentTimeMillis(),
            isError = result.isError,
            isOfflineWarning = false,
            isConfirmation = remainsInteractive,
            actionCardType = if (remainsInteractive) {
                "lead_ai_confirmation"
            } else {
                null
            }
        )

        aiChatRepository.insertMessage(updatedEntity)
        aiChatRepository.updateSessionTimestamp(uid, updatedEntity.sessionId)
    }

    private fun latestActionableLeadAIConfirmation():
        Pair<String, com.example.leads.ai.LeadAIChatConfirmationState>? {
        val states = leadAIConfirmationStates.value

        for (message in activeSessionMessages.value.asReversed()) {
            val state = states[message.id] ?: continue
            if (state.canConfirm || state.canCancel) {
                return message.id to state
            }
        }

        return null
    }

    private suspend fun handlePendingLeadAIConfirmationReply(
        rawText: String
    ): com.example.leads.ai.LeadAIChatResult? {
        val decision = com.example.voice.VoiceConfirmationInterpreter.parse(rawText)
            ?: return null
        val (confirmationMessageId, state) =
            latestActionableLeadAIConfirmation() ?: return null

        val result = when (decision) {
            com.example.voice.VoiceConfirmationDecision.CONFIRM -> {
                if (!state.canConfirm) return null
                leadAIViewModelBridge.confirm(
                    messageId = confirmationMessageId,
                    currentLeads = allLeadsList.value
                )
            }

            com.example.voice.VoiceConfirmationDecision.CANCEL -> {
                if (!state.canCancel) return null
                leadAIViewModelBridge.cancelConfirmation(confirmationMessageId)
            }
        }

        persistLeadAIConfirmationResult(
            messageId = confirmationMessageId,
            result = result
        )
        return result
    }

    suspend fun processAICommand(
        query: String,
        messageId: String,
        onAddLeadTrigger: () -> Unit,
        sourceMessageId: String? = null
    ): AICommandResult {
        if (!BuildConfig.AI_FEATURES_ENABLED) {
            return AICommandResult(
                text = "This feature is not available in the current release.",
                actionCardType = null,
                isError = true
            )
        }

        // A ready data-changing action must be explicitly resolved before a
        // new command can start. Explicit confirm/cancel replies are handled
        // in sendAIMessage before this router is called.
        if (latestActionableLeadAIConfirmation() != null) {
            return AICommandResult(
                text = "Lead details confirmation ke liye ready hain. Save karne ke liye 'haan save kar do' ya rokne ke liye 'cancel' boliye.",
                actionCardType = null
            )
        }

        val parsed = kotlinx.coroutines.withContext(Dispatchers.Default) {
            com.example.ai.intent.IntentParser.parseCommand(query)
        }

        val sanitizedParsed = if (
            parsed.intent == com.example.ai.intent.AIIntent.CREATE_LEAD
        ) {
            val normalizedPhone = parsed.entities.phone
                ?.filter(Char::isDigit)
                ?.takeIf { it.length in 10..15 }

            parsed.copy(
                entities = parsed.entities.copy(phone = normalizedPhone)
            )
        } else {
            parsed
        }

        val leadAIResult = leadAIViewModelBridge.handleMessage(
            rawText = query,
            parsedCommand = sanitizedParsed,
            responseMessageId = messageId,
            sourceMessageId = sourceMessageId,
            nowMillis = System.currentTimeMillis()
        )

        if (leadAIResult.handled) {
            return AICommandResult(
                text = leadAIResult.text,
                actionCardType = leadAIResult.actionCardType,
                isConfirmation = leadAIResult.isConfirmation,
                isError = leadAIResult.isError
            )
        }

        val todayStr = getSystemTodayDateStr()
        
        return when (parsed.intent) {
            com.example.ai.intent.AIIntent.SHOW_PENDING_LEADS -> {
                val pendingCount = allLeadsList.value.count { it.status == "Pending" && !it.archived }
                val responseText = if (pendingCount > 0) {
                    "Aapke business ke pending follow-up leads ki live report niche generate ki gayi hai. Inhe check karein:"
                } else {
                    "Aapke local database mein koi pending follow-up leads nahi mile. Sab kuch up-to-date hai!"
                }
                AICommandResult(
                    text = responseText,
                    actionCardType = "leads"
                )
            }
            com.example.ai.intent.AIIntent.SHOW_TODAY_REMINDERS -> {
                val todayRemindersCount = allLeadsList.value.count { it.reminderDate == todayStr && it.reminderDate.isNotEmpty() }
                val responseText = if (todayRemindersCount > 0) {
                    "Aaj ke active reminders scheduled alerts list niche di gayi hai:"
                } else {
                    "No readable reminders found for today."
                }
                AICommandResult(
                    text = responseText,
                    actionCardType = "reminders"
                )
            }
            com.example.ai.intent.AIIntent.OPEN_ADD_LEAD -> {
                onAddLeadTrigger()
                AICommandResult(
                    text = "Opening the real New Lead registration form for you. Please enter client details to save.",
                    actionCardType = null
                )
            }
            com.example.ai.intent.AIIntent.SHOW_WEEKLY_REPORT -> {
                AICommandResult(
                    text = "Weekly Lead Conversion status and reports calculated from actual database records:",
                    actionCardType = "report"
                )
            }
            com.example.ai.intent.AIIntent.CREATE_LEAD -> {
                if (parsed.validationStatus == "INCOMPLETE") {
                    AICommandResult(
                        text = parsed.clarificationQuestion ?: "Please provide lead details.",
                        actionCardType = "unsupported"
                    )
                } else if (parsed.validationStatus == "INVALID") {
                    AICommandResult(
                        text = "The phone number provided (${parsed.entities.phone}) is invalid. Action execution aborted.",
                        actionCardType = "unsupported",
                        isError = true
                    )
                } else {
                    val name = parsed.entities.name?.trim() ?: ""
                    val phone = parsed.entities.phone?.trim() ?: ""
                    val isDuplicate = allLeadsList.value.any { it.mobile == phone }
                    if (isDuplicate) {
                        AICommandResult(
                            text = "A lead with phone number $phone already exists in the system.",
                            actionCardType = "unsupported",
                            isError = true
                        )
                    } else {
                        registerPendingConfirmation(messageId, com.example.ai.action.AITool.CREATE_LEAD, parsed.entities)
                        AICommandResult(
                            text = "I understood that you want to create a lead for $name with phone $phone. Action execution will require confirmation.",
                            actionCardType = "confirmation",
                            isConfirmation = true
                        )
                    }
                }
            }
            com.example.ai.intent.AIIntent.CREATE_REMINDER -> {
                if (parsed.validationStatus == "INCOMPLETE") {
                    AICommandResult(
                        text = parsed.clarificationQuestion ?: "Please provide reminder details.",
                        actionCardType = "unsupported"
                    )
                } else {
                    val name = parsed.entities.name
                    if (name == null) {
                        AICommandResult(
                            text = "Kiske liye reminder set karna hai? (Please provide the person's name)",
                            actionCardType = "unsupported"
                        )
                    } else {
                        val matches = actionDispatcher.resolveLeads(name, allLeadsList.value)
                        when {
                            matches.isEmpty() -> {
                                AICommandResult(
                                    text = "Mujhe aapke database mein '$name' naam ka koi lead nahi mila. Kripya pahle lead banayein.",
                                    actionCardType = "unsupported"
                                )
                            }
                            matches.size > 1 -> {
                                val namesStr = matches.joinToString(", ") { it.name }
                                AICommandResult(
                                    text = "Aapke database mein '$name' naam ke multiple matches hain: $namesStr. Kiske liye action perform karna hai? Please clarify.",
                                    actionCardType = "unsupported"
                                )
                            }
                            else -> {
                                val matchedLead = matches.first()
                                if (parsed.entities.resolvedDate == null) {
                                    AICommandResult(
                                        text = "Reminder kis din ke liye lagana hai? (Please provide a date like aaj or kal)",
                                        actionCardType = "unsupported"
                                    )
                                } else if (parsed.entities.time == null) {
                                    AICommandResult(
                                        text = "Kis samay ka reminder set karna hai? (Please provide a specific time, as guessing is disabled for safety)",
                                        actionCardType = "unsupported"
                                    )
                                } else {
                                    val date = parsed.entities.resolvedDate
                                    val time = parsed.entities.time
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                    val triggerTimeMs = try {
                                        val triggerDate = sdf.parse("$date $time")
                                        triggerDate?.time ?: 0L
                                    } catch (e: Exception) {
                                        0L
                                    }
                                    if (triggerTimeMs == 0L) {
                                        AICommandResult(
                                            text = "Provided reminder date or time format is invalid.",
                                            actionCardType = "unsupported"
                                        )
                                    } else if (triggerTimeMs <= System.currentTimeMillis()) {
                                        AICommandResult(
                                            text = "Reminder cannot be scheduled in the past ($date $time).",
                                            actionCardType = "unsupported"
                                        )
                                    } else {
                                        registerPendingConfirmation(messageId, com.example.ai.action.AITool.CREATE_REMINDER, parsed.entities)
                                        val relDate = parsed.entities.relativeDate ?: "tomorrow"
                                        AICommandResult(
                                            text = "I understood that you want to create a reminder for ${matchedLead.name} $relDate at $time. Action execution will require confirmation.",
                                            actionCardType = "confirmation",
                                            isConfirmation = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            com.example.ai.intent.AIIntent.UPDATE_LEAD_STATUS -> {
                val name = parsed.entities.name
                if (name == null) {
                    AICommandResult(
                        text = "Kiska status update karna hai? Please provide the name.",
                        actionCardType = "unsupported"
                    )
                } else {
                    val matches = actionDispatcher.resolveLeads(name, allLeadsList.value)
                    when {
                        matches.isEmpty() -> {
                            AICommandResult(
                                text = "Mujhe aapke database mein '$name' naam ka koi lead nahi mila.",
                                actionCardType = "unsupported"
                            )
                        }
                        matches.size > 1 -> {
                            val namesStr = matches.joinToString(", ") { it.name }
                            AICommandResult(
                                text = "Aapke database mein '$name' naam ke multiple matches hain: $namesStr. Kiske liye action perform karna hai? Please clarify.",
                                actionCardType = "unsupported"
                            )
                        }
                        else -> {
                            val matchedLead = matches.first()
                            val targetStatus = parsed.entities.status
                            if (targetStatus == null) {
                                AICommandResult(
                                    text = "Kya status set karna hai? (Pending or Complete)",
                                    actionCardType = "unsupported"
                                )
                            } else {
                                val normalized = targetStatus.lowercase(Locale.getDefault()).trim()
                                val mappedStatus = when {
                                    normalized.contains("complete") || normalized.contains("done") || normalized.contains("sarthak") || normalized.contains("khatam") -> "Complete"
                                    normalized.contains("pending") || normalized.contains("active") || normalized.contains("baaki") || normalized.contains("baki") -> "Pending"
                                    else -> null
                                }
                                if (mappedStatus == null) {
                                    AICommandResult(
                                        text = "Unsupported status '$targetStatus'. Status must be 'Pending' or 'Complete'.",
                                        actionCardType = "unsupported"
                                    )
                                } else {
                                    registerPendingConfirmation(messageId, com.example.ai.action.AITool.UPDATE_LEAD_STATUS, parsed.entities)
                                    AICommandResult(
                                        text = "I understood that you want to update status of ${matchedLead.name} from '${matchedLead.status}' to '$mappedStatus'. Action execution will require confirmation.",
                                        actionCardType = "confirmation",
                                        isConfirmation = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
            com.example.ai.intent.AIIntent.ADD_LEAD_NOTE -> {
                val name = parsed.entities.name
                if (name == null) {
                    AICommandResult(
                        text = "Please provide the lead's name for adding a note.",
                        actionCardType = "unsupported"
                    )
                } else {
                    val matches = actionDispatcher.resolveLeads(name, allLeadsList.value)
                    when {
                        matches.isEmpty() -> {
                            AICommandResult(
                                text = "Mujhe aapke database mein '$name' naam ka koi lead nahi mila.",
                                actionCardType = "unsupported"
                            )
                        }
                        matches.size > 1 -> {
                            val namesStr = matches.joinToString(", ") { it.name }
                            AICommandResult(
                                text = "Aapke database mein '$name' naam ke multiple matches hain: $namesStr. Kiske liye action perform karna hai? Please clarify.",
                                actionCardType = "unsupported"
                            )
                        }
                        else -> {
                            val matchedLead = matches.first()
                            val noteText = parsed.entities.noteText
                            if (noteText == null || noteText.trim().isEmpty()) {
                                AICommandResult(
                                    text = "Please specify the note text to add.",
                                    actionCardType = "unsupported"
                                )
                            } else {
                                registerPendingConfirmation(messageId, com.example.ai.action.AITool.ADD_LEAD_NOTE, parsed.entities)
                                AICommandResult(
                                    text = "I understood that you want to add note '$noteText' for ${matchedLead.name}. Action execution will require confirmation.",
                                    actionCardType = "confirmation",
                                    isConfirmation = true
                                )
                            }
                        }
                    }
                }
            }
            com.example.ai.intent.AIIntent.UNKNOWN -> {
                AICommandResult(
                    text = "",
                    actionCardType = null,
                    handled = false
                )
            }
        }
    }

    private val sharedPrefs: SharedPreferences =
        application.getSharedPreferences("lifefresh_prefs", Context.MODE_PRIVATE)
    private val preservedGuestOwnerKey = "preserved_guest_owner_uid"

    private val database = AppDatabase.getDatabase(application)
    private val leadSyncMutationCoordinator = com.example.sync.LeadSyncMutationCoordinator(
        context = application,
        database = database,
        leadDao = database.leadDao,
        metadataDao = database.leadSyncMetadataDao,
        syncDao = database.syncDao
    )
    private val repository = LeadRepository(database.leadDao, leadSyncMutationCoordinator)
    val aiChatRepository = AIChatRepository(database.aiChatDao)

    private val _currentUidFlow = MutableStateFlow<String?>(FirebaseAuth.getInstance().currentUser?.uid)
    val currentUidFlow: StateFlow<String?> = _currentUidFlow.asStateFlow()

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val newUid = auth.currentUser?.uid
            if (newUid != _currentUidFlow.value) {
                _currentUidFlow.value = newUid
                if (newUid == null) {
                    clearInMemoryStateOnSignOut()
                }
            }
        }
    }

    private suspend fun claimLegacyLeadsIfNecessary(uid: String) {
        if (uid.isBlank()) return
        val isClaimed = sharedPrefs.getBoolean("legacy_unowned_leads_claimed", false)
        if (!isClaimed) {
            try {
                repository.claimUnownedLeads(uid)
                sharedPrefs.edit().putBoolean("legacy_unowned_leads_claimed", true).apply()
            } catch (e: Exception) {
                android.util.Log.e("CRMViewModel", "Error claiming legacy unowned leads", e)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val accountScopedLeadsFlow: Flow<List<LeadEntity>> = _currentUidFlow.flatMapLatest { uid ->
        if (uid.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            flow {
                claimLegacyLeadsIfNecessary(uid)
                emitAll(repository.getAllLeads(uid))
            }
        }
    }

    fun getGuestLeadCount(guestOwnerUid: String, onComplete: (Int) -> Unit) {
        if (guestOwnerUid.isBlank()) {
            onComplete(0)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val count = runCatching { database.leadDao.getAllLeadsList(guestOwnerUid).size }.getOrDefault(0)
            withContext(Dispatchers.Main) { onComplete(count) }
        }
    }

    fun keepGuestDataSeparate(guestOwnerUid: String) {
        if (guestOwnerUid.isBlank()) return
        sharedPrefs.edit().putString(preservedGuestOwnerKey, guestOwnerUid).apply()
    }

    fun moveGuestDataToAccount(
        guestOwnerUid: String,
        targetOwnerUid: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (guestOwnerUid.isBlank() || targetOwnerUid.isBlank() || guestOwnerUid == targetOwnerUid) {
            onComplete(false, "Guest or account identity is invalid.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val guestLeads = database.leadDao.getAllLeadsList(guestOwnerUid)
                if (guestLeads.isEmpty()) {
                    sharedPrefs.edit().remove(preservedGuestOwnerKey).apply()
                    withContext(Dispatchers.Main) { onComplete(true, "No guest data needed to be moved.") }
                    return@launch
                }

                ReminderScheduler.cancelAllRemindersForUser(getApplication(), guestOwnerUid)

                val movedLeads = guestLeads.map { lead ->
                    val existing = database.leadDao.getLeadById(lead.id, targetOwnerUid)
                    if (existing == null) {
                        lead.copy(ownerUid = targetOwnerUid)
                    } else {
                        lead.copy(
                            ownerUid = targetOwnerUid,
                            id = java.util.UUID.randomUUID().toString()
                        )
                    }
                }

                // Route through the repository so authenticated writes enter the normal outbox/sync path.
                repository.insertLeads(movedLeads)
                database.leadDao.clearLeadsForUser(guestOwnerUid)
                ReminderScheduler.rescheduleAllReminders(getApplication(), movedLeads)
                sharedPrefs.edit().remove(preservedGuestOwnerKey).apply()

                withContext(Dispatchers.Main) {
                    onComplete(true, "Moved ${movedLeads.size} guest lead${if (movedLeads.size == 1) "" else "s"} to your account.")
                }
            } catch (e: Exception) {
                android.util.Log.e("GuestDataTransfer", "Failed to move guest data", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Failed to move guest data to the signed-in account.")
                }
            }
        }
    }

    fun restorePreservedGuestDataIfNeeded(
        newGuestOwnerUid: String,
        transitionGuestOwnerUid: String? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (newGuestOwnerUid.isBlank()) {
            onComplete?.invoke(false, "Guest identity is invalid.")
            return
        }

        val sourceOwnerUid = transitionGuestOwnerUid?.takeIf { it.isNotBlank() }
            ?: sharedPrefs.getString(preservedGuestOwnerKey, null)

        if (sourceOwnerUid.isNullOrBlank() || sourceOwnerUid == newGuestOwnerUid) {
            onComplete?.invoke(true, "Guest workspace is ready.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldGuestLeads = database.leadDao.getAllLeadsList(sourceOwnerUid)
                if (oldGuestLeads.isEmpty()) {
                    sharedPrefs.edit().remove(preservedGuestOwnerKey).apply()
                    withContext(Dispatchers.Main) { onComplete?.invoke(true, "Guest workspace is ready.") }
                    return@launch
                }

                ReminderScheduler.cancelAllRemindersForUser(getApplication(), sourceOwnerUid)
                val restoredLeads = oldGuestLeads.map { it.copy(ownerUid = newGuestOwnerUid) }
                // Guest sessions stay local-only, so write directly to Room and do not enqueue cloud mutations.
                database.leadDao.insertLeads(restoredLeads)
                database.leadDao.clearLeadsForUser(sourceOwnerUid)
                ReminderScheduler.rescheduleAllReminders(getApplication(), restoredLeads)
                sharedPrefs.edit().remove(preservedGuestOwnerKey).apply()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, "Restored ${restoredLeads.size} guest lead${if (restoredLeads.size == 1) "" else "s"} on this device.")
                }
            } catch (e: Exception) {
                android.util.Log.e("GuestDataTransfer", "Failed to restore preserved guest data", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.localizedMessage ?: "Failed to restore guest data.")
                }
            }
        }
    }

    fun clearInMemoryStateOnSignOut() {
        _showCloudRestoreDialog.value = false
        pendingCloudLeads = emptyList()
        hasCheckedCloudBackupForUser = null
        lastUserId = null
        _lastSyncTime.value = null
        _totalCloudCustomers.value = null
        ringingLead.value = null
        searchQuery.value = ""
        currentFilter.value = "all"
        _aiCommandState.value = AICommandState.Idle
        _pendingConfirmations.value = emptyMap()
        _isThinking.value = false
        setActiveSession(null)
        _voiceManager?.stopVoiceMode()
        dismissActiveAlarm()
    }

    private val _pendingConfirmations = MutableStateFlow<Map<String, com.example.ai.action.PendingConfirmation>>(emptyMap())
    val pendingConfirmations: StateFlow<Map<String, com.example.ai.action.PendingConfirmation>> = _pendingConfirmations.asStateFlow()

    private val actionDispatcher by lazy {
        com.example.ai.action.ActionDispatcher(application, repository)
    }

    // Voice Conversation Support
    private var activeAddLeadTrigger: (() -> Unit)? = null
    private var _voiceManager: com.example.voice.VoiceConversationManager? = null

    val voiceManager: com.example.voice.VoiceConversationManager
        get() {
            check(BuildConfig.AI_FEATURES_ENABLED) {
                "AI and voice features are disabled in this release."
            }
            if (_voiceManager == null) {
                val vm = com.example.voice.VoiceConversationManager(
                    context = getApplication(),
                    coroutineScope = viewModelScope,
                    onSendText = { text ->
                        sendAIMessage(text, activeAddLeadTrigger ?: {})
                    }
                )
                _voiceManager = vm
                
                vm.setInitialLastSpokenMessage(
                    activeSessionMessages.value.lastOrNull { it.sender == Sender.AI }
                )

                // One combined observer prevents races between a message
                // update, thinking completion, and either confirmation engine.
                viewModelScope.launch {
                    combine(
                        activeSessionMessages,
                        isThinking,
                        pendingConfirmations,
                        leadAIConfirmationStates
                    ) { messages, thinking, legacyConfirmations, leadConfirmations ->
                        val isConfirmationExecuting =
                            legacyConfirmations.values.any {
                                it.status == com.example.ai.action.ConfirmationStatus.EXECUTING
                            } || leadConfirmations.values.any {
                                it.lifecycle ==
                                    com.example.leads.ai.LeadAIConfirmationLifecycle.EXECUTING
                            }

                        Triple(messages, thinking, isConfirmationExecuting)
                    }.collect { (messages, thinking, isConfirmationExecuting) ->
                        vm.onMessagesUpdated(
                            messages = messages,
                            isThinking = thinking,
                            isConfirmationExecuting = isConfirmationExecuting
                        )
                    }
                }
            }
            return _voiceManager!!
        }

    val voiceState: StateFlow<com.example.voice.VoiceConversationState>
        get() = voiceManager.state

    val liveSpokenText: StateFlow<String>
        get() = voiceManager.liveSpokenText

    val isVoiceModeEnabled: StateFlow<Boolean>
        get() = voiceManager.isVoiceModeEnabled

    private val leadOperationService by lazy {
        com.example.leads.operation.LeadOperationService(
            context = application,
            repository = repository
        )
    }

    private val leadAIController by lazy {
        com.example.leads.ai.LeadAIControllerFactory.create(
            context = application,
            repository = repository
        )
    }

    private val leadAIViewModelBridge by lazy {
        com.example.leads.ai.LeadAIViewModelBridge(leadAIController)
    }

    val leadAIConfirmationStates:
        StateFlow<Map<String, com.example.leads.ai.LeadAIChatConfirmationState>>
        by lazy {
            leadAIViewModelBridge.confirmationStates
        }

    fun getActiveLeadAIDraft(): com.example.leads.ai.LeadAIDraft? {
        return leadAIViewModelBridge.getActiveDraft()
    }

    fun cancelActiveLeadAIDraft(): com.example.leads.ai.LeadAIChatResult {
        return leadAIViewModelBridge.cancelActiveDraft()
    }

    val activeSessionId = savedStateHandle.getStateFlow<String?>("active_session_id", null)

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeSessionMessages: StateFlow<List<MockMessage>> = combine(
        _currentUidFlow,
        activeSessionId
    ) { uid, sessionId ->
        uid to sessionId
    }.flatMapLatest { (uid, sessionId) ->
        if (uid.isNullOrBlank() || sessionId.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            aiChatRepository.getMessagesForSession(uid, sessionId).map { list ->
                list.map { msg ->
                    MockMessage(
                        id = msg.id,
                        text = msg.text,
                        sender = if (msg.sender == "USER") Sender.USER else Sender.AI,
                        timestamp = msg.timestamp,
                        isError = msg.isError,
                        isOfflineWarning = msg.isOfflineWarning,
                        isConfirmation = msg.isConfirmation,
                        actionCardType = msg.actionCardType
                    )
                }.sortedBy { it.timestamp }
            }
        }
    }
    .flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeSession: StateFlow<ChatSession?> = combine(
        _currentUidFlow,
        activeSessionId
    ) { uid, sessionId ->
        uid to sessionId
    }.flatMapLatest { (uid, sessionId) ->
        if (uid.isNullOrBlank() || sessionId.isNullOrBlank()) {
            flowOf(null)
        } else {
            aiChatRepository.getAllSessionsWithMessages(uid).map { list ->
                list.firstOrNull { it.session.id == sessionId }?.let { swm ->
                    ChatSession(
                        id = swm.session.id,
                        title = swm.session.title,
                        messages = swm.messages.map { msg ->
                            MockMessage(
                                id = msg.id,
                                text = msg.text,
                                sender = if (msg.sender == "USER") Sender.USER else Sender.AI,
                                timestamp = msg.timestamp,
                                isError = msg.isError,
                                isOfflineWarning = msg.isOfflineWarning,
                                isConfirmation = msg.isConfirmation,
                                actionCardType = msg.actionCardType
                            )
                        }.sortedBy { it.timestamp },
                        timestamp = swm.session.updatedTimestamp,
                        isPinned = swm.session.isPinned
                    )
                }
            }
        }
    }
    .flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val isNewChatEmpty: StateFlow<Boolean> = activeSessionId
        .map { it == null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun setActiveSession(sessionId: String?) {
        savedStateHandle["active_session_id"] = sessionId
    }

    init {
        if (BuildConfig.AI_FEATURES_ENABLED) {
            viewModelScope.launch {
                val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid
                val sessionId = activeSessionId.value
                if (uid != null && sessionId != null) {
                    val exists = aiChatRepository.getSessionById(uid, sessionId)
                    if (exists == null) {
                        setActiveSession(null)
                    }
                } else if (uid == null) {
                    setActiveSession(null)
                }
            }
        } else {
            setActiveSession(null)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dbChatSessions: StateFlow<List<ChatSession>> = _currentUidFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                aiChatRepository.getAllSessionsWithMessages(uid).map { list ->
                    list.map { swm ->
                        ChatSession(
                            id = swm.session.id,
                            title = swm.session.title,
                            messages = swm.messages.map { msg ->
                                MockMessage(
                                    id = msg.id,
                                    text = msg.text,
                                    sender = if (msg.sender == "USER") Sender.USER else Sender.AI,
                                    timestamp = msg.timestamp,
                                    isError = msg.isError,
                                    isOfflineWarning = msg.isOfflineWarning,
                                    isConfirmation = msg.isConfirmation,
                                    actionCardType = msg.actionCardType
                                )
                            }.sortedBy { it.timestamp },
                            timestamp = swm.session.updatedTimestamp,
                            isPinned = swm.session.isPinned
                        )
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createSession(sessionId: String, title: String) {
        if (!BuildConfig.AI_FEATURES_ENABLED) return
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val session = AIChatSessionEntity(
                ownerUid = uid,
                id = sessionId,
                title = title,
                createdTimestamp = System.currentTimeMillis(),
                updatedTimestamp = System.currentTimeMillis(),
                isPinned = false
            )
            aiChatRepository.insertSession(session)
        }
    }

    fun saveMessage(sessionId: String, message: MockMessage) {
        if (!BuildConfig.AI_FEATURES_ENABLED) return
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val msgEntity = AIChatMessageEntity(
                ownerUid = uid,
                id = message.id,
                sessionId = sessionId,
                text = message.text,
                sender = message.sender.name,
                timestamp = message.timestamp,
                isError = message.isError,
                isOfflineWarning = message.isOfflineWarning,
                isConfirmation = message.isConfirmation,
                actionCardType = message.actionCardType
            )
            aiChatRepository.insertMessage(msgEntity)
            aiChatRepository.updateSessionTimestamp(uid, sessionId)
        }
    }

    fun sendAIMessage(
        text: String,
        onAddLeadTrigger: () -> Unit
    ) {
        if (!BuildConfig.AI_FEATURES_ENABLED) return
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        activeAddLeadTrigger = onAddLeadTrigger
        viewModelScope.launch {
            // 1. If there's no current session ID, create one!
            var sessionId = activeSessionId.value
            if (sessionId == null) {
                val newId = "session_${System.currentTimeMillis()}"
                val title = if (text.length > 25) text.substring(0, 22) + "..." else text
                
                val session = AIChatSessionEntity(
                    ownerUid = uid,
                    id = newId,
                    title = title,
                    createdTimestamp = System.currentTimeMillis(),
                    updatedTimestamp = System.currentTimeMillis(),
                    isPinned = false
                )
                aiChatRepository.insertSession(session)
                setActiveSession(newId)
                sessionId = newId
            }

            // 2. Add User query
            val userMsg = AIChatMessageEntity(
                ownerUid = uid,
                id = "user_${System.currentTimeMillis()}",
                sessionId = sessionId!!,
                text = text,
                sender = "USER",
                timestamp = System.currentTimeMillis(),
                isError = false,
                isOfflineWarning = false,
                isConfirmation = false,
                actionCardType = null
            )
            aiChatRepository.insertMessage(userMsg)
            aiChatRepository.updateSessionTimestamp(uid, sessionId)

            // 3. Trigger async co-pilot thinking block
            _isThinking.value = true
            _aiCommandState.value = AICommandState.Loading

            val confirmationResult =
                handlePendingLeadAIConfirmationReply(text)
            if (confirmationResult != null) {
                _isThinking.value = false
                _aiCommandState.value = if (confirmationResult.isError) {
                    AICommandState.Error(confirmationResult.text)
                } else {
                    AICommandState.Success
                }
                return@launch
            }

            delay(300)
            if (_currentUidFlow.value != uid) {
                _isThinking.value = false
                return@launch
            }
            _isThinking.value = false

            // 4. Smart command routing delegation to ViewModel
            val responseMsgId = "ai_${System.currentTimeMillis()}"
            val commandResult = processAICommand(
                query = text,
                messageId = responseMsgId,
                onAddLeadTrigger = onAddLeadTrigger,
                sourceMessageId = userMsg.id
            )
            
            if (_currentUidFlow.value != uid) return@launch

            val responseMsg = AIChatMessageEntity(
                ownerUid = uid,
                id = responseMsgId,
                sessionId = sessionId,
                text = commandResult.text,
                sender = "AI",
                timestamp = System.currentTimeMillis(),
                isError = commandResult.isError,
                isOfflineWarning = commandResult.isOfflineWarning,
                isConfirmation = commandResult.isConfirmation,
                actionCardType = commandResult.actionCardType
            )
            aiChatRepository.insertMessage(responseMsg)
            aiChatRepository.updateSessionTimestamp(uid, sessionId)

            // Update AI command state
            val state = when (commandResult.actionCardType) {
                "unsupported" -> AICommandState.UnsupportedCommand
                "leads" -> {
                    val pendingCount = allLeadsList.value.count { it.status == "Pending" && !it.archived }
                    if (pendingCount > 0) AICommandState.Success else AICommandState.Empty
                }
                "reminders" -> {
                    val todayStr = getSystemTodayDateStr()
                    val todayCount = allLeadsList.value.count { it.reminderDate == todayStr && it.reminderDate.isNotEmpty() }
                    if (todayCount > 0) AICommandState.Success else AICommandState.Empty
                }
                "report" -> {
                    if (allLeadsList.value.isNotEmpty()) AICommandState.Success else AICommandState.Empty
                }
                else -> AICommandState.Success
            }
            _aiCommandState.value = state
        }
    }

    fun updateSessionPin(sessionId: String, isPinned: Boolean) {
        if (!BuildConfig.AI_FEATURES_ENABLED) return
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            aiChatRepository.updateSessionPinStatus(uid, sessionId, isPinned)
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (!BuildConfig.AI_FEATURES_ENABLED) return
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            aiChatRepository.updateSessionTitle(uid, sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: String) {
        if (!BuildConfig.AI_FEATURES_ENABLED) return
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            aiChatRepository.deleteSessionById(uid, sessionId)
        }
    }

    // Application state
    val searchQuery = MutableStateFlow("")
    val currentFilter = MutableStateFlow("all") // "all", "pending", "complete", "archived", "rem-today", "rem-upcoming", "rem-overdue", "rem-all"

    // Theme state
    val isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("lifefresh_theme", false))

    // App Language state
    val appLanguage = MutableStateFlow(com.example.data.AppLanguageManager.getLanguage(application))
    val activeLanguageMetadata = MutableStateFlow(com.example.data.AppLanguageManager.getActiveLanguageMetadata(application))
    val languagePacks: StateFlow<List<com.example.data.LanguagePackMetadata>> = com.example.data.LanguagePackManager.languagePacksFlow

    val playLanguageDeliveryManager = com.example.data.PlayLanguageDeliveryManager.getInstance(application)
    val playLanguageInstallState: StateFlow<com.example.data.PlayLanguageInstallState> = playLanguageDeliveryManager.installState

    fun setAppLanguage(language: com.example.data.AppLanguage) {
        com.example.data.AppLanguageManager.setLanguage(getApplication(), language)
        appLanguage.value = language
        activeLanguageMetadata.value = language.toMetadata()
    }

    fun selectOrInstallLanguage(
        language: com.example.data.AppLanguage,
        onInstallInitiated: ((Int) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        if (playLanguageDeliveryManager.isLanguageAvailable(language)) {
            setAppLanguage(language)
        } else {
            playLanguageDeliveryManager.requestInstallLanguage(
                language = language,
                onSuccess = { sessionId -> onInstallInitiated?.invoke(sessionId) },
                onError = { ex -> onError?.invoke(ex) }
            )
        }
    }

    fun cancelLanguageInstall(sessionId: Int) {
        playLanguageDeliveryManager.cancelInstall(sessionId)
    }

    fun resetPlayLanguageInstallState() {
        playLanguageDeliveryManager.resetState()
    }

    fun setAppLanguage(metadata: com.example.data.LanguagePackMetadata) {
        com.example.data.AppLanguageManager.setLanguage(getApplication(), metadata)
        appLanguage.value = com.example.data.AppLanguage.fromCode(metadata.code)
        activeLanguageMetadata.value = metadata
    }

    fun downloadLanguagePack(
        metadata: com.example.data.LanguagePackMetadata,
        onResult: (Boolean, String?) -> Unit
    ) {
        com.example.data.LanguagePackManager.downloadPack(
            context = getApplication(),
            packMetadata = metadata,
            onResult = onResult
        )
    }

    fun removeLanguagePack(metadata: com.example.data.LanguagePackMetadata): Boolean {
        val success = com.example.data.LanguagePackManager.removePack(getApplication(), metadata.code)
        if (success) {
            val currentActiveCode = com.example.data.AppLanguageManager.getLanguageCode(getApplication())
            appLanguage.value = com.example.data.AppLanguage.fromCode(currentActiveCode)
            activeLanguageMetadata.value = com.example.data.AppLanguageManager.getActiveLanguageMetadata(getApplication())
        }
        return success
    }

    // Alarm settings states
    val alarmUseCustom = MutableStateFlow(sharedPrefs.getBoolean("alarm_use_custom", false))
    val alarmSound = MutableStateFlow(
        sharedPrefs.getString("alarm_sound", null)?.let { saved ->
            if (saved in listOf("classic", "bell", "notification", "digital", "gentle")) "holiday" else saved
        } ?: "holiday"
    )
    val alarmVolume = MutableStateFlow(sharedPrefs.getString("alarm_volume", "medium") ?: "medium")
    val customAudioFilename = MutableStateFlow(sharedPrefs.getString("alarm_custom_filename", "No file selected") ?: "No file selected")
    val reminderRingMode = MutableStateFlow(sharedPrefs.getString("reminder_ring_mode", "continuous") ?: "continuous")

    // Alarm ringing state
    val ringingLead = MutableStateFlow<LeadEntity?>(null)
    val isExactAlarmGrantedState = MutableStateFlow(false)
    val showExactAlarmPrompt = MutableStateFlow(false)
    val alarmModalTitle = MutableStateFlow("")
    private val triggeredOverdueIds = mutableSetOf<String>()

    // Authoritative reactive account-scoped leads list
    val allLeadsList: StateFlow<List<LeadEntity>> = accountScopedLeadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Testing alarm state in settings
    val isTestingAlarm = MutableStateFlow(false)

    // Changelog state
    val showChangelogDialog = MutableStateFlow(false)

    fun checkAndShowChangelog() {
        val lastViewedVersion = sharedPrefs.getString("last_viewed_version", null)
        val currentVersion = com.example.BuildConfig.VERSION_NAME
        if (lastViewedVersion != currentVersion) {
            showChangelogDialog.value = true
        }
    }

    fun forceShowChangelog() {
        showChangelogDialog.value = true
    }

    fun dismissChangelog() {
        showChangelogDialog.value = false
        sharedPrefs.edit().putString("last_viewed_version", com.example.BuildConfig.VERSION_NAME).apply()
    }

    // Cloud Restore Dialog State
    private val _showCloudRestoreDialog = MutableStateFlow(false)
    val showCloudRestoreDialog = _showCloudRestoreDialog.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String?>(sharedPrefs.getString("cloud_last_sync_time", null))
    val lastSyncTime = _lastSyncTime.asStateFlow()

    private val _totalCloudCustomers = MutableStateFlow<Int?>(if (sharedPrefs.contains("cloud_total_customers")) sharedPrefs.getInt("cloud_total_customers", 0) else null)
    val totalCloudCustomers = _totalCloudCustomers.asStateFlow()

    fun setLastSyncTime(time: String?) {
        if (time != null) {
            sharedPrefs.edit().putString("cloud_last_sync_time", time).apply()
        } else {
            sharedPrefs.edit().remove("cloud_last_sync_time").apply()
        }
        _lastSyncTime.value = time
    }

    fun setTotalCloudCustomers(count: Int?) {
        if (count != null) {
            sharedPrefs.edit().putInt("cloud_total_customers", count).apply()
        } else {
            sharedPrefs.edit().remove("cloud_total_customers").apply()
        }
        _totalCloudCustomers.value = count
    }

    fun backupAllToCloud(onComplete: (Boolean, String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid.isBlank()) {
            onComplete(false, "Cloud backup is available for authenticated non-anonymous users only.")
            return
        }
        val uid = currentUser.uid
        val syncRepo = com.example.sync.SyncRuntimeFactory.getSyncRepository(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            val leads = database.leadDao.getAllLeadsList(uid)
            if (leads.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onComplete(true, "No leads to backup.")
                }
                return@launch
            }

            for (lead in leads) {
                val meta = database.leadSyncMetadataDao.getByLeadId(lead.id, uid)
                val pending = database.syncDao.getPendingMutationsForEntity(uid, "LEAD", lead.id)
                if (meta == null || pending.isEmpty()) {
                    leadSyncMutationCoordinator.upsertLead(lead, com.example.sync.LeadWriteOrigin.LOCAL_USER)
                }
            }

            val result = syncRepo.sync(com.example.sync.model.SyncTrigger.MANUAL, uid)
            withContext(Dispatchers.Main) {
                when (result) {
                    is com.example.sync.model.SyncRunResult.Success -> {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val nowStr = sdf.format(java.util.Date())
                        setLastSyncTime(nowStr)
                        queryCloudBackupStatus { _, _, count ->
                            setTotalCloudCustomers(count ?: leads.size)
                        }
                        onComplete(true, "Successfully uploaded ${result.summary.pushed} leads to cloud.")
                    }
                    is com.example.sync.model.SyncRunResult.Partial -> {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val nowStr = sdf.format(java.util.Date())
                        setLastSyncTime(nowStr)
                        onComplete(true, "Backup completed with warnings: ${result.message}")
                    }
                    is com.example.sync.model.SyncRunResult.AuthRequired -> {
                        onComplete(false, "Backup failed: ${result.message}")
                    }
                    is com.example.sync.model.SyncRunResult.AutomaticSyncDisabled -> {
                        onComplete(false, "Backup failed: ${result.message}")
                    }
                    is com.example.sync.model.SyncRunResult.RetryableFailure -> {
                        onComplete(false, "Backup failed: ${result.message}")
                    }
                    is com.example.sync.model.SyncRunResult.PermanentFailure -> {
                        onComplete(false, "Backup failed: ${result.message}")
                    }
                }
            }
        }
    }

    fun deleteCloudBackup(onComplete: (Boolean, String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid.isBlank()) {
            onComplete(false, "Authenticated non-anonymous user required.")
            return
        }
        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()
        val syncPrefs = com.example.sync.SyncPreferences(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDocument = db.collection("users").document(uid)
                val leadsCollection = userDocument.collection("leads")

                // Step 1: Delete all documents in cloud leads collection from Firestore
                while (true) {
                    val snapshot = com.google.android.gms.tasks.Tasks.await(
                        leadsCollection.limit(400).get(com.google.firebase.firestore.Source.SERVER)
                    )
                    if (snapshot.isEmpty) break
                    val batch = db.batch()
                    snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                    com.google.android.gms.tasks.Tasks.await(db.waitForPendingWrites())
                }

                // Step 2: Delete parent user document if present
                com.google.android.gms.tasks.Tasks.await(userDocument.delete())
                com.google.android.gms.tasks.Tasks.await(db.waitForPendingWrites())

                // Step 3: Clear local sync outbox, conflicts, and checkpoint so local leads are not automatically re-uploaded
                database.syncDao.clearOutboxForUser(uid)
                database.syncDao.clearConflictsForUser(uid)
                database.syncDao.clearCheckpointByScope("leads:$uid")

                // Step 4: Mark local lead metadata as SYNCED without deleting any local leads
                val localLeads = database.leadDao.getAllLeadsList(uid)
                val now = System.currentTimeMillis()
                for (lead in localLeads) {
                    database.leadSyncMetadataDao.markSynced(lead.id, uid, now)
                }

                // Step 5: Reset sync preferences timestamps and error status
                syncPrefs.setLastSuccessfulSyncAt(0L)
                syncPrefs.setLastSyncAttemptAt(0L)
                syncPrefs.setLastSyncError(null)

                withContext(Dispatchers.Main) {
                    setLastSyncTime(null)
                    setTotalCloudCustomers(0)
                    queryCloudBackupStatus { _, _, _ -> }
                    onComplete(true, "Cloud backup data has been permanently deleted.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to delete cloud backup: ${e.localizedMessage}")
                }
            }
        }
    }

    fun queryCloudBackupStatus(onComplete: (Boolean, String, Int?) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid.isBlank()) {
            onComplete(false, "Authenticated non-anonymous user required.", null)
            return
        }
        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("leads")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val count = querySnapshot.size()
                    setTotalCloudCustomers(count)
                    onComplete(true, "Found $count cloud customers.", count)
                } else {
                    setTotalCloudCustomers(0)
                    onComplete(true, "No cloud backup found.", 0)
                }
            }
            .addOnFailureListener { e ->
                onComplete(false, "Failed to query cloud backup: ${e.localizedMessage}", null)
            }
    }

    fun manualRestoreFromCloud(onComplete: (Boolean, String) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid.isBlank()) {
            onComplete(false, "Authenticated non-anonymous user required.")
            return
        }
        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).collection("leads")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val leads = mutableListOf<LeadEntity>()
                    for (doc in querySnapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val name = doc.getString("name") ?: ""
                            val mobile = doc.getString("mobile") ?: ""
                            val diseases = doc.getString("diseases") ?: "[]"
                            val otherDisease = doc.getString("otherDisease") ?: ""
                            val relation = doc.getString("relation") ?: ""
                            val otherRelation = doc.getString("otherRelation") ?: ""
                            val status = doc.getString("status") ?: "Pending"
                            val reminderDate = doc.getString("reminderDate") ?: ""
                            val reminderTime = doc.getString("reminderTime") ?: ""
                            val reminderNote = doc.getString("reminderNote") ?: ""
                            val reminderStatus = doc.getString("reminderStatus") ?: "Pending"
                            val notes = doc.getString("notes") ?: ""
                            val archived = doc.getBoolean("archived") ?: false
                            val lastCall = doc.getString("lastCall")
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                            leads.add(
                                LeadEntity(
                                    id = id,
                                    name = name,
                                    mobile = mobile,
                                    diseases = diseases,
                                    otherDisease = otherDisease,
                                    relation = relation,
                                    otherRelation = otherRelation,
                                    status = status,
                                    reminderDate = reminderDate,
                                    reminderTime = reminderTime,
                                    reminderNote = reminderNote,
                                    reminderStatus = reminderStatus,
                                    notes = notes,
                                    archived = archived,
                                    lastCall = lastCall,
                                    timestamp = timestamp,
                                    ownerUid = uid
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FirestoreSync", "Error parsing doc ${doc.id}", e)
                        }
                    }
                    if (leads.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val activeUser = FirebaseAuth.getInstance().currentUser
                                if (activeUser == null || activeUser.isAnonymous || activeUser.uid != uid) {
                                    withContext(Dispatchers.Main) {
                                        onComplete(false, "Restore aborted due to user sign-out or account change.")
                                    }
                                    return@launch
                                }
                                val filteredLeads = leads.filterNot { lead ->
                                    val meta = database.leadSyncMetadataDao.getByLeadId(lead.id, uid)
                                    val pending = database.syncDao.getPendingMutationsForEntity(uid, "LEAD", lead.id)
                                    (meta != null && meta.deleted) || pending.any { it.operation == "DELETE" }
                                }
                                if (filteredLeads.isNotEmpty()) {
                                    repository.insertLeads(filteredLeads, com.example.sync.LeadWriteOrigin.REMOTE_RESTORE)
                                    ReminderScheduler.rescheduleAllReminders(getApplication(), filteredLeads)
                                }
                                withContext(Dispatchers.Main) {
                                    _showCloudRestoreDialog.value = false
                                    pendingCloudLeads = emptyList()
                                    hasCheckedCloudBackupForUser = uid
                                    sharedPrefs.edit().putBoolean("cloud_restore_completed_$uid", true).apply()
                                    onComplete(true, "Successfully restored ${filteredLeads.size} records from cloud backup.")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FirestoreSync", "Failed to insert restored leads", e)
                                withContext(Dispatchers.Main) {
                                    onComplete(false, "Failed to restore cloud records: ${e.localizedMessage}")
                                }
                            }
                        }
                    } else {
                        onComplete(false, "No cloud backup found.")
                    }
                } else {
                    onComplete(false, "No cloud backup found.")
                }
            }
            .addOnFailureListener { e ->
                onComplete(false, "Failed to read cloud backup: ${e.localizedMessage}")
            }
    }

    private var hasCheckedCloudBackupForUser: String? = null
    private var pendingCloudLeads: List<LeadEntity> = emptyList()
    private var lastUserId: String? = null

    fun checkForCloudBackup(userId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid != userId) {
            return
        }
        lastUserId = userId
        val isRestored = sharedPrefs.getBoolean("cloud_restore_completed_$userId", false)
        if (isRestored || hasCheckedCloudBackupForUser == userId) {
            hasCheckedCloudBackupForUser = userId
            return
        }
        
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId).collection("leads")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val leads = mutableListOf<LeadEntity>()
                    for (doc in querySnapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val name = doc.getString("name") ?: ""
                            val mobile = doc.getString("mobile") ?: ""
                            val diseases = doc.getString("diseases") ?: "[]"
                            val otherDisease = doc.getString("otherDisease") ?: ""
                            val relation = doc.getString("relation") ?: ""
                            val otherRelation = doc.getString("otherRelation") ?: ""
                            val status = doc.getString("status") ?: "Pending"
                            val reminderDate = doc.getString("reminderDate") ?: ""
                            val reminderTime = doc.getString("reminderTime") ?: ""
                            val reminderNote = doc.getString("reminderNote") ?: ""
                            val reminderStatus = doc.getString("reminderStatus") ?: "Pending"
                            val notes = doc.getString("notes") ?: ""
                            val archived = doc.getBoolean("archived") ?: false
                            val lastCall = doc.getString("lastCall")
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                            leads.add(
                                LeadEntity(
                                    id = id,
                                    name = name,
                                    mobile = mobile,
                                    diseases = diseases,
                                    otherDisease = otherDisease,
                                    relation = relation,
                                    otherRelation = otherRelation,
                                    status = status,
                                    reminderDate = reminderDate,
                                    reminderTime = reminderTime,
                                    reminderNote = reminderNote,
                                    reminderStatus = reminderStatus,
                                    notes = notes,
                                    archived = archived,
                                    lastCall = lastCall,
                                    timestamp = timestamp,
                                    ownerUid = userId
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FirestoreSync", "Error parsing doc ${doc.id}", e)
                        }
                    }
                    if (leads.isNotEmpty()) {
                        pendingCloudLeads = leads
                        hasCheckedCloudBackupForUser = userId
                        _showCloudRestoreDialog.value = true
                    } else {
                        hasCheckedCloudBackupForUser = userId
                        android.util.Log.d("FirestoreSync", "No cloud backup found for $userId")
                    }
                } else {
                    hasCheckedCloudBackupForUser = userId
                    android.util.Log.d("FirestoreSync", "No cloud backup found for $userId")
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FirestoreSync", "Error checking cloud backup", e)
            }
    }

    fun performCloudRestore() {
        val leadsToRestore = pendingCloudLeads
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = lastUserId ?: currentUser?.uid ?: ""
        if (currentUser == null || currentUser.isAnonymous || currentUser.uid != uid) {
            _showCloudRestoreDialog.value = false
            pendingCloudLeads = emptyList()
            return
        }

        if (leadsToRestore.isNotEmpty() && uid.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val activeUser = FirebaseAuth.getInstance().currentUser
                    if (activeUser == null || activeUser.isAnonymous || activeUser.uid != uid) {
                        return@launch
                    }
                    val filteredLeads = leadsToRestore.filterNot { lead ->
                        val meta = database.leadSyncMetadataDao.getByLeadId(lead.id, uid)
                        val pending = database.syncDao.getPendingMutationsForEntity(uid, "LEAD", lead.id)
                        (meta != null && meta.deleted) || pending.any { it.operation == "DELETE" }
                    }
                    if (filteredLeads.isNotEmpty()) {
                        repository.insertLeads(filteredLeads, com.example.sync.LeadWriteOrigin.REMOTE_RESTORE)
                        ReminderScheduler.rescheduleAllReminders(getApplication(), filteredLeads)
                    }
                    withContext(Dispatchers.Main) {
                        android.util.Log.d("FirestoreSync", "Cloud restore completed successfully. Restored ${filteredLeads.size} leads.")
                        android.widget.Toast.makeText(getApplication(), "Restore Completed Successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FirestoreSync", "Failed to insert restored leads", e)
                } finally {
                    withContext(Dispatchers.Main) {
                        _showCloudRestoreDialog.value = false
                        pendingCloudLeads = emptyList()
                        lastUserId?.let { userId ->
                            sharedPrefs.edit().putBoolean("cloud_restore_completed_$userId", true).apply()
                        }
                    }
                }
            }
        } else {
            _showCloudRestoreDialog.value = false
        }
    }

    fun skipCloudRestore() {
        _showCloudRestoreDialog.value = false
        pendingCloudLeads = emptyList()
        val userId = lastUserId ?: FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { uid ->
            hasCheckedCloudBackupForUser = uid
            sharedPrefs.edit().putBoolean("cloud_restore_completed_$uid", true).apply()
        }
    }

    fun resetCloudRestoreCheck() {
        sharedPrefs.all.keys.filter { it.startsWith("cloud_restore_completed_") || it.startsWith("cloud_restore_handled_") }.forEach { key ->
            sharedPrefs.edit().remove(key).apply()
        }
        hasCheckedCloudBackupForUser = null
        pendingCloudLeads = emptyList()
        _showCloudRestoreDialog.value = false
    }

    // Combined filtered leads list
    val filteredLeads: StateFlow<List<LeadEntity>> = combine(
        allLeadsList,
        searchQuery,
        currentFilter
    ) { leads, query, filter ->
        val todayStr = getSystemTodayDateStr()

        leads.filter { lead ->
            // Filter out archived unless explicitly viewing archive
            if (filter == "archived") {
                if (!lead.archived) return@filter false
            } else {
                if (lead.archived) return@filter false
            }

            // General status filtering
            when (filter) {
                "pending" -> if (lead.status != "Pending") return@filter false
                "complete" -> if (lead.status != "Complete") return@filter false
            }

            // Reminders filtering
            val reminderStatus = lead.reminderStatus
            val cat = getReminderCategory(lead, todayStr)

            when (filter) {
                "rem-today" -> if (cat != "today" || reminderStatus != "Pending") return@filter false
                "rem-upcoming" -> if (cat != "upcoming" || reminderStatus != "Pending") return@filter false
                "rem-overdue" -> {
                    if (reminderStatus != "Overdue" && (cat != "overdue" || reminderStatus != "Pending")) return@filter false
                }
                "rem-all" -> if (lead.reminderDate.isEmpty()) return@filter false
            }

            // Search query filter
            if (query.isNotEmpty()) {
                val q = query.lowercase(Locale.getDefault())
                val diseasesStr = lead.diseases.lowercase(Locale.getDefault())
                val match = lead.name.lowercase(Locale.getDefault()).contains(q) ||
                        lead.mobile.contains(q) ||
                        lead.relation.lowercase(Locale.getDefault()).contains(q) ||
                        lead.otherRelation.lowercase(Locale.getDefault()).contains(q) ||
                        diseasesStr.contains(q) ||
                        lead.otherDisease.lowercase(Locale.getDefault()).contains(q) ||
                        lead.notes.lowercase(Locale.getDefault()).contains(q) ||
                        lead.reminderNote.lowercase(Locale.getDefault()).contains(q)
                if (!match) return@filter false
            }

            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active polling thread job for checking reminders
    private var reminderCheckJob: Job? = null

    init {
        com.example.data.LanguagePackManager.init(getApplication())
        viewModelScope.launch {
            playLanguageDeliveryManager.installState.collect { state ->
                if (state is com.example.data.PlayLanguageInstallState.Installed) {
                    setAppLanguage(state.language)
                }
            }
        }
        ReminderScheduler.startChecking(getApplication())
        checkAndShowChangelog()
        if (BuildConfig.AI_FEATURES_ENABLED) {
            viewModelScope.launch(Dispatchers.IO) {
                val activeUid = FirebaseAuth.getInstance().currentUser?.uid ?: com.example.data.ActiveAccountStore.getActiveUid(getApplication())
                if (activeUid.isNotBlank()) {
                    val sessionCount = database.aiChatDao.getAllSessionsList(activeUid).size
                    if (sessionCount == 0) {
                    val session1Id = "session_1"
                    database.aiChatDao.insertSession(
                        AIChatSessionEntity(
                            ownerUid = activeUid,
                            id = session1Id,
                            title = "Lead Follow-up Strategy",
                            createdTimestamp = System.currentTimeMillis() - 3600000 * 3,
                            updatedTimestamp = System.currentTimeMillis() - 3600000 * 3,
                            isPinned = false
                        )
                    )
                    database.aiChatDao.insertMessage(
                        AIChatMessageEntity(
                            ownerUid = activeUid,
                            id = "user_init_1",
                            sessionId = session1Id,
                            text = "Strategy review for followups",
                            sender = "USER",
                            timestamp = System.currentTimeMillis() - 3600000 * 3,
                            isError = false,
                            isOfflineWarning = false,
                            isConfirmation = false,
                            actionCardType = null
                        )
                    )
                    database.aiChatDao.insertMessage(
                        AIChatMessageEntity(
                            ownerUid = activeUid,
                            id = "ai_init_1",
                            sessionId = session1Id,
                            text = "Aapke business ke pending follow-up leads ki live report niche generate ki gayi hai. Inhe check karein:",
                            sender = "AI",
                            timestamp = System.currentTimeMillis() - 3600000 * 3 + 1000,
                            isError = false,
                            isOfflineWarning = false,
                            isConfirmation = false,
                            actionCardType = "leads"
                        )
                    )

                    val session2Id = "session_2"
                    database.aiChatDao.insertSession(
                        AIChatSessionEntity(
                            ownerUid = activeUid,
                            id = session2Id,
                            title = "Weekly Conversion Analysis",
                            createdTimestamp = System.currentTimeMillis() - 3600000 * 2,
                            updatedTimestamp = System.currentTimeMillis() - 3600000 * 2,
                            isPinned = false
                        )
                    )
                    database.aiChatDao.insertMessage(
                        AIChatMessageEntity(
                            ownerUid = activeUid,
                            id = "user_init_2",
                            sessionId = session2Id,
                            text = "Report for last week",
                            sender = "USER",
                            timestamp = System.currentTimeMillis() - 3600000 * 2,
                            isError = false,
                            isOfflineWarning = false,
                            isConfirmation = false,
                            actionCardType = null
                        )
                    )
                    database.aiChatDao.insertMessage(
                        AIChatMessageEntity(
                            ownerUid = activeUid,
                            id = "ai_init_2",
                            sessionId = session2Id,
                            text = "Weekly Lead Conversion status and reports summarized perfectly.",
                            sender = "AI",
                            timestamp = System.currentTimeMillis() - 3600000 * 2 + 1000,
                            isError = false,
                            isOfflineWarning = false,
                            isConfirmation = false,
                            actionCardType = "report"
                        )
                    )

                    val session3Id = "session_3"
                    database.aiChatDao.insertSession(
                        AIChatSessionEntity(
                            ownerUid = activeUid,
                            id = session3Id,
                            title = "Today's Sync reminders",
                            createdTimestamp = System.currentTimeMillis() - 3600000,
                            updatedTimestamp = System.currentTimeMillis() - 3600000,
                            isPinned = false
                        )
                    )
                    database.aiChatDao.insertMessage(
                        AIChatMessageEntity(
                            ownerUid = activeUid,
                            id = "user_init_3",
                            sessionId = session3Id,
                            text = "Reminders check",
                            sender = "USER",
                            timestamp = System.currentTimeMillis() - 3600000,
                            isError = false,
                            isOfflineWarning = false,
                            isConfirmation = false,
                            actionCardType = null
                        )
                    )
                    database.aiChatDao.insertMessage(
                        AIChatMessageEntity(
                            ownerUid = activeUid,
                            id = "ai_init_3",
                            sessionId = session3Id,
                            text = "Aaj ke active reminders scheduled alerts list niche di gayi hai.",
                            sender = "AI",
                            timestamp = System.currentTimeMillis() - 3600000 + 1000,
                            isError = false,
                            isOfflineWarning = false,
                            isConfirmation = false,
                            actionCardType = "reminders"
                        )
                    )
                    }
                }
            }
        }
        viewModelScope.launch {
            ReminderScheduler.activeRingingLead.collect { lead ->
                if (lead != null) {
                    ringingLead.value = lead
                    alarmModalTitle.value = "Reminder Due Today"
                } else {
                    ringingLead.value = null
                }
            }
        }
    }

    /* --- BASIC ACTIONS & CRUD --- */

    enum class SaveLeadResult {
        SUCCESS,
        DUPLICATE_MOBILE,
        DUPLICATE_REMINDER,
        VALIDATION_FAILED,
        LEAD_NOT_FOUND,
        SAVE_FAILED
    }

    fun hasDuplicateReminder(
        excludeId: String?,
        date: String,
        time: String
    ): Boolean {
        return leadOperationService.hasDuplicateReminder(
            excludeId = excludeId,
            date = date,
            time = time,
            currentLeads = allLeadsList.value
        )
    }

    suspend fun saveLead(
        id: String?,
        name: String,
        mobile: String,
        diseases: List<String>,
        otherDisease: String,
        relation: String,
        otherRelation: String,
        status: String,
        reminderDate: String,
        reminderTime: String,
        reminderNote: String,
        notes: String
    ): SaveLeadResult {
        val draft = com.example.leads.domain.LeadDraft(
            id = id,
            name = name,
            mobile = mobile,
            diseases = diseases,
            otherDisease = otherDisease,
            relation = relation,
            otherRelation = otherRelation,
            status = status,
            reminderDate = reminderDate,
            reminderTime = reminderTime,
            reminderNote = reminderNote,
            notes = notes
        )

        val result = leadOperationService.saveLead(
            draft = draft,
            currentLeads = allLeadsList.value
        )

        if (result.isSuccess) {
            result.entity?.let { entity ->
                triggeredOverdueIds.remove(entity.id)
            }
        }

        return when (result.status) {
            com.example.leads.operation.LeadSaveStatus.SUCCESS ->
                SaveLeadResult.SUCCESS

            com.example.leads.operation.LeadSaveStatus.DUPLICATE_MOBILE ->
                SaveLeadResult.DUPLICATE_MOBILE

            com.example.leads.operation.LeadSaveStatus.DUPLICATE_REMINDER ->
                SaveLeadResult.DUPLICATE_REMINDER

            com.example.leads.operation.LeadSaveStatus.VALIDATION_FAILED ->
                SaveLeadResult.VALIDATION_FAILED

            com.example.leads.operation.LeadSaveStatus.LEAD_NOT_FOUND ->
                SaveLeadResult.LEAD_NOT_FOUND

            com.example.leads.operation.LeadSaveStatus.DATABASE_ERROR ->
                SaveLeadResult.SAVE_FAILED
        }
    }

    fun deleteLead(lead: LeadEntity) {
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: lead.ownerUid
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.deleteLeadById(lead.id, uid)
            ReminderScheduler.cancelReminder(getApplication(), uid, lead.id)
            if (ringingLead.value?.id == lead.id) {
                dismissActiveAlarm()
            }
        }
    }

    fun toggleArchive(lead: LeadEntity) {
        viewModelScope.launch {
            val updated = lead.copy(archived = !lead.archived)
            repository.insertLead(updated)
            if (updated.archived) {
                ReminderScheduler.cancelReminder(getApplication(), updated.ownerUid, updated.id)
            } else {
                ReminderScheduler.scheduleReminder(getApplication(), updated)
            }
        }
    }

    fun markCallInitiated(lead: LeadEntity) {
        viewModelScope.launch {
            val updated = lead.copy(lastCall = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            repository.insertLead(updated)
        }
    }

    fun reactivateReminder(leadId: String, newDate: String, newTime: String): Boolean {
        android.util.Log.d("DUPLICATE_REMINDER_CHECK", "Checking duplicate reminder in reactivateReminder for leadId: $leadId, date: $newDate, time: $newTime")
        if (hasDuplicateReminder(leadId, newDate, newTime)) {
            android.util.Log.d("DUPLICATE_REMINDER_BLOCKED", "Reactivation blocked due to duplicate reminder at $newDate $newTime")
            return false
        }
        android.util.Log.d("DUPLICATE_REMINDER_ALLOWED", "Reactivation allowed for leadId: $leadId, no blocking duplicate reminder.")

        viewModelScope.launch {
            val list = allLeadsList.value
            val item = list.find { it.id == leadId }
            if (item != null) {
                val updated = item.copy(
                    reminderDate = newDate,
                    reminderTime = newTime,
                    reminderStatus = "Pending",
                    status = "Pending"
                )
                triggeredOverdueIds.remove(leadId)
                repository.insertLead(updated)
                ReminderScheduler.scheduleReminder(getApplication(), updated)
            }
        }
        return true
    }

    fun resetAllData(onResult: ((Boolean, String) -> Unit)? = null) {
        val uid = _currentUidFlow.value ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pendingOutboxCount = database.syncDao.getUnsyncedOutboxCount(uid)
            val unresolvedConflictCount = database.syncDao.countUnresolvedConflicts(uid)
            val pendingMetadataItems = database.leadSyncMetadataDao.getPendingSyncItems(uid)

            if (pendingOutboxCount > 0 || unresolvedConflictCount > 0 || pendingMetadataItems.isNotEmpty()) {
                val msg = "Local data cannot be cleared while cloud changes are pending. Sync or resolve them first."
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, msg)
                }
                return@launch
            }

            val userLeads = repository.getAllLeadsList(uid)
            for (lead in userLeads) {
                ReminderScheduler.cancelReminder(getApplication(), uid, lead.id)
            }
            ReminderScheduler.cancelAllRemindersForUser(getApplication(), uid)
            repository.clearLeadsForUser(uid)
            aiChatRepository.clearChatHistoryForUser(uid)

            withContext(Dispatchers.Main) {
                dismissActiveAlarm()
                triggeredOverdueIds.clear()
                setActiveSession(null)
                onResult?.invoke(true, "All local client records and AI chats have been wiped successfully.")
            }
        }
    }

    /* --- THEME CONTROLLER --- */

    fun toggleTheme(enabled: Boolean) {
        isDarkMode.value = enabled
        sharedPrefs.edit().putBoolean("lifefresh_theme", enabled).apply()
    }

    /* --- ALARM ENGINE CONTROLLER --- */

    fun setAlarmUseCustom(useCustom: Boolean) {
        alarmUseCustom.value = useCustom
        sharedPrefs.edit().putBoolean("alarm_use_custom", useCustom).apply()
        stopAlarmAndTesting()
    }

    fun setAlarmSound(sound: String) {
        alarmSound.value = sound
        sharedPrefs.edit().putString("alarm_sound", sound).apply()
    }

    fun setAlarmVolume(volume: String) {
        alarmVolume.value = volume
        sharedPrefs.edit().putString("alarm_volume", volume).apply()
    }

    fun setReminderRingMode(mode: String) {
        reminderRingMode.value = mode
        sharedPrefs.edit().putString("reminder_ring_mode", mode).apply()
    }

    fun updateExactAlarmStatus() {
        android.util.Log.d("EXACT_ALARM_CHECK", "Checking exact alarm permission status")
        val isGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
        isExactAlarmGrantedState.value = isGranted
        if (isGranted) {
            android.util.Log.d("EXACT_ALARM_GRANTED", "Exact alarm permission is granted")
        } else {
            android.util.Log.d("EXACT_ALARM_DENIED", "Exact alarm permission is denied")
        }
        android.util.Log.d("EXACT_ALARM_STATUS_UPDATED", "Exact alarm permission status updated")
    }

    fun triggerExactAlarmPrompt() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            updateExactAlarmStatus()
            if (!isExactAlarmGrantedState.value) {
                showExactAlarmPrompt.value = true
            }
        }
    }

    fun dismissExactAlarmPrompt() {
        showExactAlarmPrompt.value = false
    }

    fun registerCustomAudioFile(context: Context, uri: Uri): Boolean {
        // Copy audio file safely to Sandbox to guarantee proper offline plays!
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "custom_alarm.wav"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }

            val sandboxFile = File(context.filesDir, "custom_alarm.audio")
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream: OutputStream = sandboxFile.outputStream()
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            customAudioFilename.value = name
            sharedPrefs.edit()
                .putString("alarm_custom_filename", name)
                .putString("alarm_custom_path", sandboxFile.absolutePath)
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeCustomAudio(context: Context) {
        val sandboxFile = File(context.filesDir, "custom_alarm.audio")
        if (sandboxFile.exists()) {
            sandboxFile.delete()
        }
        customAudioFilename.value = "No file selected"
        sharedPrefs.edit()
            .remove("alarm_custom_filename")
            .remove("alarm_custom_path")
            .apply()
        stopAlarmAndTesting()
    }

    fun playAlarmSound(soundName: String, volumeLevel: String, loop: Boolean) {
        android.util.Log.d("CRMViewModel", "playAlarmSound called: soundName=$soundName, volumeLevel=$volumeLevel, loop=$loop")
        stopAlarmSound()

        val assetName = com.example.audio.AlarmSoundResolver.getSoundAssetPath(soundName)

        try {
            val fd = getApplication<Application>().assets.openFd(assetName)
            customMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()

                setAudioStreamType(AudioManager.STREAM_MUSIC)
                isLooping = loop
                val volumeFactor = com.example.audio.AlarmSoundResolver.getVolumeFactor(volumeLevel)
                setVolume(volumeFactor, volumeFactor)
                prepare()
                start()
            }
            android.util.Log.d("CRMViewModel", "MediaPlayer playing asset ringtone: $assetName")
        } catch (e: Exception) {
            android.util.Log.e("CRMViewModel", "Failed to play asset ringtone: $assetName, falling back to legacy synthesizers.", e)
            try {
                AlarmSynthesizer.playAlarmSound(soundName, volumeLevel, loop)
            } catch (ex: Exception) {
                android.util.Log.e("CRMViewModel", "Legacy synthesizer fallback failed too", ex)
            }
        }
    }

    fun stopAlarmSound() {
        android.util.Log.d("CRMViewModel", "stopAlarmSound called")
        try {
            AlarmSynthesizer.stopAlarmSound()
        } catch (e: Exception) {
            android.util.Log.e("CRMViewModel", "Error in stopAlarmSound: ${e.message}", e)
        }

        try {
            customMediaPlayer?.stop()
            customMediaPlayer?.release()
        } catch (e: Exception) {}
        customMediaPlayer = null

        try {
            com.example.audio.ReminderScheduler.stopRingingSound()
        } catch (e: Exception) {}
    }

    fun toggleTestAlarm(context: Context) {
        if (isTestingAlarm.value) {
            stopAlarmAndTesting()
        } else {
            isTestingAlarm.value = true
            triggerSoundOutput(context, isLoop = true)
        }
    }

    private fun stopAlarmAndTesting() {
        isTestingAlarm.value = false
        stopAlarmSound()
        try {
            com.example.audio.AlarmService.stopService(getApplication())
        } catch (e: Exception) {
            android.util.Log.e("CRMViewModel", "Failed to stop AlarmService in stopAlarmAndTesting", e)
        }
    }

    private var customMediaPlayer: android.media.MediaPlayer? = null

    private fun triggerSoundOutput(context: Context, isLoop: Boolean) {
        android.util.Log.d("CRMViewModel", "triggerSoundOutput: useCustom=${alarmUseCustom.value}, isLoop=$isLoop")
        if (alarmUseCustom.value) {
            val customPath = sharedPrefs.getString("alarm_custom_path", null)
            if (customPath != null && File(customPath).exists()) {
                try {
                    customMediaPlayer?.stop()
                    customMediaPlayer?.release()
                } catch (e: Exception) {}

                try {
                    customMediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(customPath)
                        setAudioStreamType(AudioManager.STREAM_MUSIC)
                        isLooping = isLoop
                        val volumeFactor = AlarmSynthesizer.getVolumeFactor(alarmVolume.value)
                        setVolume(volumeFactor, volumeFactor)
                        prepare()
                        start()
                    }
                    android.util.Log.d("CRMViewModel", "MediaPlayer playing custom sound: $customPath")
                } catch (e: Exception) {
                    android.util.Log.e("CRMViewModel", "MediaPlayer failed playing custom audio, falling back.", e)
                    // Fall back to synthesizers if media player fails to load file structures
                    playAlarmSound(alarmSound.value, alarmVolume.value, isLoop)
                }
            } else {
                android.util.Log.w("CRMViewModel", "Custom file path missing, falling back to synthesizers.")
                // Fall back if file missing
                playAlarmSound(alarmSound.value, alarmVolume.value, isLoop)
            }
        } else {
            playAlarmSound(alarmSound.value, alarmVolume.value, isLoop)
        }
    }

    /* --- IN-APP ACTIVE ALARM SCHEDULING ENGINE --- */

    fun snoozeActiveAlarm(minutes: Int) {
        val lead = ringingLead.value ?: return
        stopAlarmAndTesting()

        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, minutes)

            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sdfTime = SimpleDateFormat("HH:mm", Locale.US)

            val snoozeDate = sdfDate.format(calendar.time)
            val snoozeTime = sdfTime.format(calendar.time)

            val updated = lead.copy(
                reminderDate = snoozeDate,
                reminderTime = snoozeTime,
                reminderStatus = "Pending"
            )
            triggeredOverdueIds.remove(lead.id)
            repository.insertLead(updated)
            ReminderScheduler.scheduleReminder(getApplication(), updated)
            ringingLead.value = null
        }
    }

    fun dismissActiveAlarm() {
        val lead = ringingLead.value ?: return
        stopAlarmAndTesting()

        viewModelScope.launch {
            val updated = lead.copy(reminderStatus = "Dismissed")
            repository.insertLead(updated)
            ReminderScheduler.scheduleReminder(getApplication(), updated)
            ringingLead.value = null
        }
    }

    fun markActiveAlarmComplete() {
        val lead = ringingLead.value ?: return
        stopAlarmAndTesting()

        viewModelScope.launch {
            val updated = lead.copy(
                status = "Complete",
                reminderStatus = "Completed"
            )
            repository.insertLead(updated)
            ReminderScheduler.scheduleReminder(getApplication(), updated)
            ringingLead.value = null
        }
    }

    fun closeActiveAlarmPopupOnly() {
        stopAlarmAndTesting()
        ringingLead.value = null
    }

    /* --- HELPERS & COMPILATION --- */

    fun getSystemTodayDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun getReminderCategory(lead: LeadEntity, todayStr: String): String? {
        if (lead.reminderDate.isEmpty()) return null
        return if (lead.reminderDate == todayStr) {
            if (lead.reminderTime.isNotEmpty()) {
                val currentTimeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                if (lead.reminderTime < currentTimeStr) {
                    "overdue"
                } else {
                    "today"
                }
            } else {
                "today"
            }
        } else if (lead.reminderDate < todayStr) {
            "overdue"
        } else {
            "upcoming"
        }
    }

    override fun onCleared() {
        super.onCleared()
        _voiceManager?.onDestroy()
        reminderCheckJob?.cancel()
        stopAlarmAndTesting()
    }

    /* --- BACKUP STRATEGY: PARSING JSON NATIVELY --- */

    fun exportBackupJson(): String {
        val array = JSONArray()
        val activeList = allLeadsList.value
        for (lead in activeList) {
            val obj = JSONObject().apply {
                put("id", lead.id)
                put("name", lead.name)
                put("mobile", lead.mobile)
                put("diseases", JSONArray(lead.diseases))
                put("otherDisease", lead.otherDisease)
                put("relation", lead.relation)
                put("otherRelation", lead.otherRelation)
                put("status", lead.status)
                put("reminderDate", lead.reminderDate)
                put("reminderTime", lead.reminderTime)
                put("reminderNote", lead.reminderNote)
                put("reminderStatus", lead.reminderStatus)
                put("notes", lead.notes)
                put("archived", lead.archived)
                put("lastCall", lead.lastCall ?: JSONObject.NULL)
                put("timestamp", lead.timestamp)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun importLeadsFromJson(jsonString: String): String {
        return try {
            val array = JSONArray(jsonString)
            var added = 0
            var skipped = 0

            val currentList = allLeadsList.value
            val listToInsert = mutableListOf<LeadEntity>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val mobile = obj.optString("mobile", "").trim()
                val status = obj.optString("status", "Pending")

                if (name.isNotEmpty() && mobile.isNotEmpty()) {
                    val exists = currentList.any { it.mobile == mobile } || listToInsert.any { it.mobile == mobile }
                    if (!exists) {
                        val diseasesArray = obj.optJSONArray("diseases")
                        val diseasesList = mutableListOf<String>()
                        if (diseasesArray != null) {
                            for (j in 0 until diseasesArray.length()) {
                                val disease = diseasesArray.optString(j, "").trim()
                                if (disease.isNotEmpty()) {
                                    diseasesList.add(disease)
                                }
                            }
                        }

                        val activeUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        val lead = LeadEntity(
                            id = UUID.randomUUID().toString() + "_" + (1000..9999).random(),
                            name = name,
                            mobile = mobile,
                            diseases = JSONArray(diseasesList).toString(),
                            otherDisease = obj.optString("otherDisease", ""),
                            relation = obj.optString("relation", "Self"),
                            otherRelation = obj.optString("otherRelation", ""),
                            status = status,
                            reminderDate = obj.optString("reminderDate", ""),
                            reminderTime = obj.optString("reminderTime", ""),
                            reminderNote = obj.optString("reminderNote", ""),
                            reminderStatus = obj.optString("reminderStatus", "Pending"),
                            notes = obj.optString("notes", ""),
                            archived = obj.optBoolean("archived", false),
                            lastCall = if (obj.isNull("lastCall") || !obj.has("lastCall")) null else obj.optString("lastCall", "").takeIf { it.isNotEmpty() && it != "null" },
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            ownerUid = activeUid
                        )
                        listToInsert.add(lead)
                        added++
                    } else {
                        skipped++
                    }
                }
            }

            if (listToInsert.isNotEmpty()) {
                viewModelScope.launch {
                    runCatching {
                        repository.insertLeads(listToInsert, com.example.sync.LeadWriteOrigin.LOCAL_IMPORT)
                        ReminderScheduler.rescheduleAllReminders(getApplication(), listToInsert)
                    }.onSuccess {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "Imported $added lead(s) successfully",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }.onFailure { e ->
                        android.util.Log.e("CRMViewModel", "Failed to insert imported leads", e)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "Failed to save imported leads: ${e.localizedMessage ?: "Database error"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            "Import Complete!\nAdded: $added records\nSkipped (Duplicates): $skipped records"
        } catch (e: Exception) {
            android.util.Log.e("CRMViewModel", "Error parsing import JSON payload", e)
            "Error parsing file structure payload."
        }
    }

    fun importBackupJson(jsonString: String): String = importLeadsFromJson(jsonString)
}
