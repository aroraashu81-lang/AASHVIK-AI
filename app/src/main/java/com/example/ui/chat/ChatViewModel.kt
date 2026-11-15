package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AashvikAiService
import com.example.ai.AiAttachment
import com.example.ai.AiSafetyBlockedException
import com.example.ai.SafetyCheckResult
import com.example.ai.SafetyGuardrails
import com.example.data.model.AppPolicies
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.SafetyPolicy
import com.example.data.model.UserProfile
import com.example.data.model.ViolationReport
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"

sealed interface ConversationsUiState {
    data object Loading : ConversationsUiState
    data class Success(val conversations: List<Conversation>) : ConversationsUiState
    data class Error(val message: String) : ConversationsUiState
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Thinking : GenerationState
    data class Streaming(val text: String) : GenerationState
    data class Error(val message: String) : GenerationState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val aiService: AashvikAiService,
    private val currentUserId: String,
    private val displayName: String,
    private val isVerifiedAdmin: Boolean = false
) : ViewModel() {

    private var activeGenerationJob: Job? = null
    private var discardPartialOnCancel: Boolean = false

    private var lastFailedPrompt: String? = null
    private var lastFailedAttachment: AiAttachment? = null
    private var lastFailedMessagePersisted: Boolean = false
    private var lastAutoSpeakCallback: ((String, String) -> Unit)? = null

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<AiAttachment?>(null)
    val pendingAttachment: StateFlow<AiAttachment?> = _pendingAttachment.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transientNotice = MutableStateFlow<String?>(null)
    val transientNotice: StateFlow<String?> = _transientNotice.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    val safetyPolicy: StateFlow<SafetyPolicy> = repository.observeGlobalSafetyPolicy()
        .catch { error ->
            if (error is CancellationException && error !is FirebaseFirestoreException) throw error
            emit(SafetyPolicy())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SafetyPolicy()
        )

    val ownReports: StateFlow<List<ViolationReport>> = repository.observeOwnViolationReports(currentUserId)
        .catch { error ->
            if (error is CancellationException && error !is FirebaseFirestoreException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val conversationsState: StateFlow<ConversationsUiState> = repository.observeConversations(currentUserId)
        .map<List<Conversation>, ConversationsUiState> { ConversationsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing conversations", rootError)
            emit(ConversationsUiState.Error(rootError.localizedMessage ?: "Failed to sync conversations"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ConversationsUiState.Loading
        )

    val messages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId == null) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(currentUserId, convId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", rootError)
                        _transientNotice.value = rootError.localizedMessage ?: "Failed to sync messages."
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(displayName)
        }
    }

    fun acceptTerms() {
        viewModelScope.launch {
            val res = repository.acceptTerms(AppPolicies.CURRENT_TERMS_VERSION)
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Could not save Terms acceptance. Please try again."
            } else {
                _transientNotice.value = "Terms & Safety Policy accepted. Welcome to Aashvik AI!"
            }
        }
    }

    suspend fun submitViolationReport(category: String, description: String): Result<String> {
        return repository.submitViolationReport(category = category, description = description)
    }

    fun selectConversation(conversationId: String) {
        if (_activeConversationId.value == conversationId) return
        cancelActiveGeneration(discardPartial = true)
        _generationState.value = GenerationState.Idle
        _activeConversationId.value = conversationId
        clearFailedRetryState()
    }

    fun startNewChat() {
        cancelActiveGeneration(discardPartial = true)
        _generationState.value = GenerationState.Idle
        _activeConversationId.value = null
        _pendingAttachment.value = null
        clearFailedRetryState()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPendingAttachment(attachment: AiAttachment?) {
        _pendingAttachment.value = attachment
    }

    fun dismissNotice() {
        _transientNotice.value = null
    }

    fun dismissGenerationError() {
        if (_generationState.value is GenerationState.Error) {
            _generationState.value = GenerationState.Idle
            clearFailedRetryState()
        }
    }

    fun sendMessage(
        rawPrompt: String,
        onAutoSpeakReply: (messageId: String, replyText: String) -> Unit = { _, _ -> }
    ) {
        val status = userProfile.value?.moderationStatus
        if (status == "banned" || status == "suspended") {
            _transientNotice.value = if (status == "banned") {
                "Your account is permanently banned from Aashvik AI for policy violations."
            } else {
                "Your account is suspended for policy violations. Review Terms & Violation Policy to appeal."
            }
            return
        }
        val attachment = _pendingAttachment.value
        val trimmedPrompt = rawPrompt.trim()
        if (trimmedPrompt.isBlank() && attachment == null) return
        if (_generationState.value is GenerationState.Thinking ||
            _generationState.value is GenerationState.Streaming
        ) {
            return
        }

        _pendingAttachment.value = null
        launchGeneration(
            trimmedPrompt = trimmedPrompt,
            attachment = attachment,
            alreadyPersistedUserMessage = false,
            onAutoSpeakReply = onAutoSpeakReply
        )
    }

    fun retryLastFailedTurn() {
        val status = userProfile.value?.moderationStatus
        if (status == "banned" || status == "suspended") {
            _transientNotice.value = if (status == "banned") {
                "Your account is permanently banned from Aashvik AI."
            } else {
                "Your account is suspended for policy violations."
            }
            return
        }
        val prompt = lastFailedPrompt ?: return
        val attachment = lastFailedAttachment
        val alreadyPersisted = lastFailedMessagePersisted
        val speakCallback = lastAutoSpeakCallback ?: { _, _ -> }
        if (_generationState.value is GenerationState.Thinking ||
            _generationState.value is GenerationState.Streaming
        ) {
            return
        }
        launchGeneration(
            trimmedPrompt = prompt,
            attachment = attachment,
            alreadyPersistedUserMessage = alreadyPersisted,
            onAutoSpeakReply = speakCallback
        )
    }

    private fun launchGeneration(
        trimmedPrompt: String,
        attachment: AiAttachment?,
        alreadyPersistedUserMessage: Boolean,
        onAutoSpeakReply: (messageId: String, replyText: String) -> Unit
    ) {
        val attachmentName = when (attachment) {
            is AiAttachment.ImageAttachment -> attachment.name
            is AiAttachment.DataAttachment -> attachment.name
            null -> null
        }
        val attachmentType = when (attachment) {
            is AiAttachment.ImageAttachment -> "image"
            is AiAttachment.DataAttachment -> "data"
            null -> null
        }

        val userDisplayMessage = when {
            trimmedPrompt.isNotBlank() -> trimmedPrompt
            attachment is AiAttachment.ImageAttachment -> "Analyze this image (${attachment.name})"
            attachment is AiAttachment.DataAttachment -> "Analyze this data file (${attachment.name})"
            else -> ""
        }

        val currentMessages = messages.value
        val priorHistory = if (alreadyPersistedUserMessage && currentMessages.isNotEmpty()) {
            currentMessages.dropLast(1)
        } else {
            currentMessages
        }
        val expectedModelCount = if (alreadyPersistedUserMessage) {
            currentMessages.size + 1L
        } else {
            currentMessages.size + 2L
        }

        val activeModel = userProfile.value?.preferredModel ?: AashvikAiService.MODEL_SMART
        val activePolicy = safetyPolicy.value
        var targetConvId = _activeConversationId.value

        lastFailedPrompt = trimmedPrompt
        lastFailedAttachment = attachment
        lastFailedMessagePersisted = alreadyPersistedUserMessage
        lastAutoSpeakCallback = onAutoSpeakReply
        discardPartialOnCancel = false

        _generationState.value = GenerationState.Thinking

        activeGenerationJob = viewModelScope.launch {
            try {
                if (!alreadyPersistedUserMessage) {
                    targetConvId = if (targetConvId == null) {
                        val title = aiService.buildSmartTitle(trimmedPrompt, attachmentName)
                        val createRes = repository.createConversationWithFirstMessage(
                            title = title,
                            messageText = userDisplayMessage,
                            attachmentName = attachmentName,
                            attachmentType = attachmentType
                        )
                        val newId = createRes.getOrElse { err ->
                            _generationState.value = GenerationState.Error(
                                err.localizedMessage ?: "Could not start conversation."
                            )
                            return@launch
                        }
                        _activeConversationId.value = newId
                        lastFailedMessagePersisted = true
                        newId
                    } else {
                        val addRes = repository.addMessage(
                            conversationId = targetConvId!!,
                            role = "user",
                            text = userDisplayMessage,
                            newMessageCount = currentMessages.size + 1L,
                            attachmentName = attachmentName,
                            attachmentType = attachmentType
                        )
                        if (addRes.isFailure) {
                            val err = addRes.exceptionOrNull()
                            _generationState.value = GenerationState.Error(
                                err?.localizedMessage ?: "Could not save your message."
                            )
                            return@launch
                        }
                        lastFailedMessagePersisted = true
                        targetConvId
                    }
                }

                val convId = targetConvId ?: run {
                    _generationState.value = GenerationState.Error("Conversation unavailable.")
                    return@launch
                }

                // Pre-flight AI Safety Guardrail check
                val guardrailResult = SafetyGuardrails.inspectUserPrompt(trimmedPrompt, activePolicy)
                if (guardrailResult is SafetyCheckResult.Blocked) {
                    repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        text = guardrailResult.refusalMessage,
                        newMessageCount = expectedModelCount
                    )
                    if (activePolicy.autoFlagViolations) {
                        repository.logSafetyViolation(
                            category = guardrailResult.category,
                            reason = guardrailResult.reason,
                            promptSnippet = trimmedPrompt.take(300),
                            severity = guardrailResult.severity
                        )
                    }
                    if (!isVerifiedAdmin) {
                        repository.triggerAutomaticPermanentBan(guardrailResult.reason)
                    }
                    _generationState.value = GenerationState.Idle
                    clearFailedRetryState()
                    _transientNotice.value = if (isVerifiedAdmin) {
                        "Blocked by Aashvik AI safety training policy."
                    } else {
                        "Policy violation detected. Account permanently banned."
                    }
                    return@launch
                }

                var finalReply = ""
                aiService.streamReply(
                    modelName = activeModel,
                    history = priorHistory,
                    userPrompt = trimmedPrompt,
                    attachment = attachment,
                    safetyPolicy = activePolicy
                ).collect { accumulated ->
                    finalReply = accumulated
                    _generationState.value = GenerationState.Streaming(accumulated)
                }

                if (finalReply.isNotBlank()) {
                    val savedMsgRes = repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        text = finalReply,
                        newMessageCount = expectedModelCount
                    )
                    _generationState.value = GenerationState.Idle
                    clearFailedRetryState()
                    val savedMsgId = savedMsgRes.getOrNull() ?: "local_${System.currentTimeMillis()}"
                    if (userProfile.value?.ttsEnabled == true) {
                        onAutoSpeakReply(savedMsgId, finalReply)
                    }
                } else {
                    _generationState.value = GenerationState.Error("Aashvik AI returned an empty response.")
                }
            } catch (e: AiSafetyBlockedException) {
                Log.w(TAG, "AI safety filter blocked response: ${e.reason}")
                val convId = targetConvId
                if (convId != null) {
                    repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        text = e.refusalText,
                        newMessageCount = expectedModelCount
                    )
                }
                if (activePolicy.autoFlagViolations) {
                    repository.logSafetyViolation(
                        category = e.category,
                        reason = e.reason,
                        promptSnippet = trimmedPrompt.take(300),
                        severity = e.severity
                    )
                }
                if (!isVerifiedAdmin) {
                    repository.triggerAutomaticPermanentBan(e.reason)
                }
                _generationState.value = GenerationState.Idle
                clearFailedRetryState()
                _transientNotice.value = if (isVerifiedAdmin) {
                    "Blocked by Aashvik AI safety protection filters."
                } else {
                    "Policy violation detected. Account permanently banned."
                }
            } catch (e: CancellationException) {
                val partial = (_generationState.value as? GenerationState.Streaming)?.text
                val shouldDiscard = discardPartialOnCancel
                _generationState.value = GenerationState.Idle
                if (!shouldDiscard && !partial.isNullOrBlank() && targetConvId != null) {
                    val savedConvId = targetConvId!!
                    viewModelScope.launch {
                        repository.addMessage(
                            conversationId = savedConvId,
                            role = "model",
                            text = partial,
                            newMessageCount = expectedModelCount
                        )
                    }
                }
                clearFailedRetryState()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error generating Aashvik AI reply", e)
                _generationState.value = GenerationState.Error(
                    e.localizedMessage ?: "Aashvik AI ran into an unexpected error."
                )
            }
        }
    }

    fun stopGenerationIfActive() {
        cancelActiveGeneration(discardPartial = false)
    }

    private fun cancelActiveGeneration(discardPartial: Boolean) {
        discardPartialOnCancel = discardPartial
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        if (_generationState.value is GenerationState.Thinking) {
            _generationState.value = GenerationState.Idle
        }
    }

    private fun clearFailedRetryState() {
        lastFailedPrompt = null
        lastFailedAttachment = null
        lastFailedMessagePersisted = false
        lastAutoSpeakCallback = null
    }

    fun clearCurrentConversation() {
        val convId = _activeConversationId.value
        cancelActiveGeneration(discardPartial = true)
        _generationState.value = GenerationState.Idle
        _pendingAttachment.value = null
        clearFailedRetryState()
        if (convId != null) {
            _activeConversationId.value = null
            viewModelScope.launch {
                val res = repository.deleteConversation(convId)
                if (res.isFailure) {
                    _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                        ?: "Failed to clear conversation."
                } else {
                    _transientNotice.value = "Conversation cleared."
                }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        if (_activeConversationId.value == conversationId) {
            cancelActiveGeneration(discardPartial = true)
            _activeConversationId.value = null
            _generationState.value = GenerationState.Idle
            clearFailedRetryState()
        }
        viewModelScope.launch {
            val res = repository.deleteConversation(conversationId)
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to delete conversation."
            }
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            val res = repository.renameConversation(conversationId, newTitle)
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to rename conversation."
            }
        }
    }

    fun clearAllConversations() {
        cancelActiveGeneration(discardPartial = true)
        _activeConversationId.value = null
        _generationState.value = GenerationState.Idle
        clearFailedRetryState()
        viewModelScope.launch {
            val res = repository.clearAllConversations()
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to clear all conversations."
            } else {
                _transientNotice.value = "All conversations cleared."
            }
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            val res = repository.updatePreferences(themeMode = mode)
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to sync theme preference."
            }
        }
    }

    fun updatePreferredModel(model: String) {
        viewModelScope.launch {
            val res = repository.updatePreferences(preferredModel = model)
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to sync model preference."
            }
        }
    }

    fun toggleTtsEnabled() {
        val current = userProfile.value?.ttsEnabled ?: false
        viewModelScope.launch {
            val res = repository.updatePreferences(ttsEnabled = !current)
            if (res.isFailure) {
                _transientNotice.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to sync speech preference."
            }
        }
    }
}
