package com.example.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.AiAttachment
import com.example.ai.AttachmentLoader
import com.example.auth.signOutUser
import com.example.data.model.ChatMessage
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraViolet
import com.example.voice.VoiceAndSpeechController
import kotlinx.coroutines.launch

private data class StarterPrompt(
    val title: String,
    val prompt: String,
    val icon: ImageVector
)

private val StarterPrompts = listOf(
    StarterPrompt(
        title = "Explain Simply",
        prompt = "Explain how quantum computing works in simple, everyday terms with a fun analogy.",
        icon = Icons.Rounded.School
    ),
    StarterPrompt(
        title = "Code & Debug",
        prompt = "Write a clean Kotlin coroutine retry helper with exponential backoff and explain how it works.",
        icon = Icons.Rounded.Code
    ),
    StarterPrompt(
        title = "Math Solver",
        prompt = "Solve step-by-step: If a train leaves at 9:15 AM going 72 km/h and another leaves 30 min later at 96 km/h on the same track, when does it catch up?",
        icon = Icons.Rounded.Calculate
    ),
    StarterPrompt(
        title = "Write Email",
        prompt = "Draft a warm, professional follow-up email after a great product design interview.",
        icon = Icons.Rounded.EditNote
    ),
    StarterPrompt(
        title = "Business Plan",
        prompt = "Help me outline a lean launch plan for a local eco-friendly coffee subscription.",
        icon = Icons.Rounded.Work
    ),
    StarterPrompt(
        title = "Translate",
        prompt = "Translate 'Friendly Mind, Helpful Heart! Ready to explore together?' into Spanish, French, Japanese, and Hindi with pronunciation notes.",
        icon = Icons.Rounded.Translate
    ),
    StarterPrompt(
        title = "Creative Story",
        prompt = "Write a short, uplifting sci-fi story about a friendly pocket AI who helps a stargazer.",
        icon = Icons.Rounded.AutoAwesome
    ),
    StarterPrompt(
        title = "Summarize Text",
        prompt = "Give me a crisp framework for summarizing long research articles into 5 actionable takeaways.",
        icon = Icons.Rounded.Summarize
    )
)

@Composable
fun MainChatScreen(
    viewModel: ChatViewModel,
    userDisplayName: String,
    isAdmin: Boolean,
    onOpenTerms: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val voiceController = remember(context) { VoiceAndSpeechController(context) }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val transientNotice by viewModel.transientNotice.collectAsStateWithLifecycle()
    val voiceState by voiceController.state.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showClearCurrentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(transientNotice) {
        transientNotice?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissNotice()
        }
    }

    LaunchedEffect(voiceState.errorMessage) {
        voiceState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            voiceController.clearError()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = AttachmentLoader.loadImageAttachment(context, uri)
                res.onSuccess { attachment ->
                    viewModel.setPendingAttachment(attachment)
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: "Failed to load selected image."
                    )
                }
            }
        }
    }

    val dataFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = AttachmentLoader.loadDataAttachment(context, uri)
                res.onSuccess { attachment ->
                    viewModel.setPendingAttachment(attachment)
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: "Failed to read selected data file."
                    )
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening { spokenText ->
                inputText = if (inputText.isBlank()) spokenText else "$inputText $spokenText"
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed for voice input.")
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        BackHandler(enabled = !isWideScreen && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        val drawerContent: @Composable () -> Unit = {
            ChatDrawerContent(
                conversationsState = conversationsState,
                activeConversationId = activeConversationId,
                searchQuery = searchQuery,
                userProfile = userProfile,
                userDisplayName = userDisplayName,
                isAdmin = isAdmin,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onNewChat = {
                    viewModel.startNewChat()
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onRenameConversation = viewModel::renameConversation,
                onDeleteConversation = viewModel::deleteConversation,
                onClearAllConversations = viewModel::clearAllConversations,
                onThemeModeChange = viewModel::updateThemeMode,
                onModelChange = viewModel::updatePreferredModel,
                onToggleTts = viewModel::toggleTtsEnabled,
                onOpenTerms = {
                    if (!isWideScreen) scope.launch { drawerState.close() }
                    onOpenTerms()
                },
                onOpenAdmin = {
                    if (!isWideScreen) scope.launch { drawerState.close() }
                    onOpenAdmin()
                },
                onSignOut = {
                    voiceController.stopSpeaking()
                    voiceController.stopListening()
                    signOutUser(credentialManager, scope)
                }
            )
        }

        val moderationStatus = userProfile?.moderationStatus ?: "active"
        val warningCount = userProfile?.warningCount ?: 0L

        val chatBody: @Composable () -> Unit = {
            ChatWorkspaceScaffold(
                isWideScreen = isWideScreen,
                isAdmin = isAdmin,
                moderationStatus = moderationStatus,
                warningCount = warningCount,
                activeTitle = run {
                    val list = (conversationsState as? ConversationsUiState.Success)?.conversations
                    list?.firstOrNull { it.id == activeConversationId }?.title
                },
                hasActiveConversation = activeConversationId != null || messages.isNotEmpty(),
                messages = messages,
                generationState = generationState,
                pendingAttachment = pendingAttachment,
                inputText = inputText,
                voiceState = voiceState,
                snackbarHostState = snackbarHostState,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onNewChat = { viewModel.startNewChat() },
                onClearConversationClick = { showClearCurrentDialog = true },
                onInputTextChange = { inputText = it },
                onSend = { promptToSend ->
                    val textToSend = promptToSend
                    inputText = ""
                    viewModel.sendMessage(textToSend) { msgId, replyText ->
                        voiceController.speak(msgId, replyText)
                    }
                },
                onStopGeneration = { viewModel.stopGenerationIfActive() },
                onRetryError = { viewModel.retryLastFailedTurn() },
                onDismissError = { viewModel.dismissGenerationError() },
                onAttachImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onAttachDataFile = {
                    dataFileLauncher.launch(
                        arrayOf("text/plain", "text/csv", "application/json", "text/*")
                    )
                },
                onClearAttachment = { viewModel.setPendingAttachment(null) },
                onMicClick = {
                    if (voiceState.isListening) {
                        voiceController.stopListening()
                    } else {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            voiceController.startListening { spoken ->
                                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onToggleSpeakMessage = { msgId, msgText ->
                    if (voiceState.isSpeaking && voiceState.speakingMessageId == msgId) {
                        voiceController.stopSpeaking()
                    } else {
                        voiceController.speak(msgId, msgText)
                    }
                },
                onCopiedMessage = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Copied response to clipboard")
                    }
                },
                onOpenTerms = onOpenTerms,
                onOpenAdmin = onOpenAdmin
            )
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = drawerContent,
                content = chatBody
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = drawerContent,
                content = chatBody
            )
        }
    }

    if (showClearCurrentDialog) {
        AlertDialog(
            onDismissRequest = { showClearCurrentDialog = false },
            title = { Text("Clear this conversation?") },
            text = { Text("All messages in this chat will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCurrentDialog = false
                        viewModel.clearCurrentConversation()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCurrentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatWorkspaceScaffold(
    isWideScreen: Boolean,
    isAdmin: Boolean,
    moderationStatus: String,
    warningCount: Long,
    activeTitle: String?,
    hasActiveConversation: Boolean,
    messages: List<ChatMessage>,
    generationState: GenerationState,
    pendingAttachment: AiAttachment?,
    inputText: String,
    voiceState: com.example.voice.VoiceUiState,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onClearConversationClick: () -> Unit,
    onInputTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRetryError: () -> Unit,
    onDismissError: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachDataFile: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onToggleSpeakMessage: (String, String) -> Unit,
    onCopiedMessage: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val streamingText = (generationState as? GenerationState.Streaming)?.text
    val isGenerating = generationState is GenerationState.Thinking ||
        generationState is GenerationState.Streaming

    LaunchedEffect(messages.size, streamingText, generationState is GenerationState.Thinking) {
        val totalItems = messages.size +
            (if (generationState is GenerationState.Thinking || streamingText != null) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                navigationIcon = {
                    if (!isWideScreen) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Open chat history"
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(listOf(AuroraCyan, AuroraViolet))
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_aashvik_logo),
                                contentDescription = "Aashvik AI Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = activeTitle?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_admin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = "Admin Panel",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenTerms,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_terms_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Policy,
                            contentDescription = "Terms & Violation Policy"
                        )
                    }
                    if (hasActiveConversation) {
                        IconButton(
                            onClick = onClearConversationClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_conversation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.clear_conversation)
                            )
                        }
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.new_chat)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    WelcomeEmptyState(
                        onStarterPromptClick = { starter ->
                            keyboardController?.hide()
                            onSend(starter)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages, key = { it.id.ifBlank { "${it.role}_${it.text.hashCode()}" } }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                isStreaming = false,
                                isSpeakingThisMessage = voiceState.isSpeaking &&
                                    voiceState.speakingMessageId == msg.id,
                                onToggleSpeak = { onToggleSpeakMessage(msg.id, msg.text) },
                                onCopied = onCopiedMessage
                            )
                        }

                        if (generationState is GenerationState.Thinking) {
                            item(key = "thinking_bubble") {
                                AiThinkingBubble()
                            }
                        } else if (generationState is GenerationState.Streaming) {
                            item(key = "streaming_bubble") {
                                ChatMessageBubble(
                                    message = ChatMessage(
                                        id = "streaming",
                                        role = "model",
                                        text = generationState.text
                                    ),
                                    isStreaming = true
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = generationState is GenerationState.Error) {
                val errorMsg = (generationState as? GenerationState.Error)?.message.orEmpty()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRetryError,
                            modifier = Modifier.testTag("retry_generation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = voiceState.isListening,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val animatedRms by animateFloatAsState(
                    targetValue = voiceState.rmsLevel.coerceIn(0.08f, 1f),
                    label = "rms_level"
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = voiceState.partialTranscript.ifBlank {
                                        stringResource(R.string.listening_now)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedRms },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
            }

            AnimatedVisibility(visible = pendingAttachment != null) {
                pendingAttachment?.let { attachment ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (attachment) {
                                is AiAttachment.ImageAttachment -> {
                                    Image(
                                        bitmap = attachment.bitmap.asImageBitmap(),
                                        contentDescription = attachment.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = attachment.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                is AiAttachment.DataAttachment -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${attachment.name} (${attachment.textContent.length} chars)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onClearAttachment,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove attachment",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (moderationStatus == "suspended" || moderationStatus == "warned") {
                val isSuspended = moderationStatus == "suspended"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSuspended) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReportProblem,
                            contentDescription = null,
                            tint = if (isSuspended) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSuspended) {
                                "Account suspended due to policy violations."
                            } else {
                                "Account notice: $warningCount policy warning(s)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuspended) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenTerms) {
                            Text("Policy / Appeal")
                        }
                    }
                }
            }

            ComposerBar(
                inputText = inputText,
                isGenerating = isGenerating,
                isListening = voiceState.isListening,
                hasAttachment = pendingAttachment != null,
                isSuspended = moderationStatus == "suspended",
                onInputTextChange = onInputTextChange,
                onSendClick = {
                    keyboardController?.hide()
                    onSend(inputText)
                },
                onStopClick = onStopGeneration,
                onMicClick = onMicClick,
                onAttachImage = onAttachImage,
                onAttachDataFile = onAttachDataFile
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeEmptyState(
    onStarterPromptClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            tonalElevation = 6.dp,
            border = BorderStroke(
                2.dp,
                Brush.linearGradient(listOf(AuroraCyan, AuroraViolet))
            ),
            modifier = Modifier.size(84.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_aashvik_logo),
                contentDescription = "Aashvik AI Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Try asking Aashvik AI",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StarterPrompts.forEach { starter ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onStarterPromptClick(starter.prompt) }
                        .testTag("starter_chip_${starter.title.lowercase().replace(" ", "_")}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = starter.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = starter.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    inputText: String,
    isGenerating: Boolean,
    isListening: Boolean,
    hasAttachment: Boolean,
    isSuspended: Boolean,
    onInputTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onMicClick: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachDataFile: () -> Unit
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val canSend = (inputText.isNotBlank() || hasAttachment) && !isGenerating && !isSuspended

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("attach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = stringResource(R.string.attach_file),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attach_image)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Image, contentDescription = null)
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onAttachImage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attach_data)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Description, contentDescription = null)
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onAttachDataFile()
                                }
                            )
                        }
                    }

                    TextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        enabled = !isSuspended,
                        placeholder = {
                            Text(
                                text = if (isSuspended) {
                                    "Messaging disabled (account suspended)"
                                } else {
                                    stringResource(R.string.type_message_hint)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend) onSendClick() }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input")
                    )

                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("voice_input_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.voice_input),
                            tint = if (isListening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (isGenerating) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onStopClick)
                                .testTag("stop_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = stringResource(R.string.stop_generating),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .padding(4.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canSend, onClick = onSendClick)
                                .testTag("send_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.send_message),
                                    tint = if (canSend) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
