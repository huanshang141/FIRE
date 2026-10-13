package app.fireplan.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fireplan.data.ChatTurn
import app.fireplan.data.Conversation
import app.fireplan.data.DEFAULT_SYSTEM_PROMPT
import app.fireplan.data.EncryptedConfig
import app.fireplan.data.FireRepository
import app.fireplan.data.LlmClient
import app.fireplan.data.MESSAGE_KIND
import app.fireplan.data.TODAY_CARD_KIND
import app.fireplan.data.TODAY_REPLY_CHIPS
import app.fireplan.data.assembleMessages
import app.fireplan.data.dailyDayKey
import app.fireplan.data.db.ChatMessageEntity
import app.fireplan.data.db.ConversationEntity
import app.fireplan.data.retrySelection
import app.fireplan.data.toInput
import app.fireplan.data.turnsForRequest
import app.fireplan.data.userVisibleLlmError
import app.fireplan.domain.FireCalculator
import app.fireplan.domain.buildProgressContext
import app.fireplan.ui.LocalViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FireRepository.get(app)
    private val configResult = runCatching { EncryptedConfig(app) }
    private val config = configResult.getOrNull()
    private val client = LlmClient()
    private val selectedId = MutableStateFlow<Long?>(null)
    private var job: Job? = null
    private var requestGeneration by mutableIntStateOf(0)

    val conversations = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var currentId by mutableStateOf<Long?>(null)
        private set
    var input by mutableStateOf("")
    var streaming by mutableStateOf(false)
    var streamingText by mutableStateOf<String?>(null)
    var streamingConversationId by mutableStateOf<Long?>(null)
    var streamingKind by mutableStateOf(MESSAGE_KIND)

    init {
        viewModelScope.launch {
            val latest = repo.listConversations().maxByOrNull { it.updatedAt }?.id
            if (selectedId.value == null) select(latest)
        }
    }

    fun updateInput(value: String) {
        input = value
    }

    fun openConversation(id: Long) {
        viewModelScope.launch {
            if (repo.conversationExists(id)) select(id)
        }
    }

    fun selectConversation(id: Long) {
        select(id)
    }

    fun createConversation() {
        viewModelScope.launch {
            select(repo.createChat())
        }
    }

    fun rename(id: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.renameConversation(id, trimmed) }
    }

    fun deleteConversation(id: Long) {
        Conversation.bump(id)
        val deletingActive = streamingConversationId == id
        if (deletingActive) {
            requestGeneration++
            job?.cancel()
        }
        viewModelScope.launch {
            Conversation.exclusive {
                repo.deleteConversation(id)
                if (deletingActive) {
                    streaming = false
                    streamingText = null
                    streamingConversationId = null
                }
            }
            if (selectedId.value == id) {
                select(repo.listConversations().maxByOrNull { it.updatedAt }?.id)
            }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || streaming) return
        input = ""
        val requestedId = selectedId.value
        if (config == null) {
            viewModelScope.launch { recordConfigFailure(requestedId, text) }
            return
        }
        beginJob { gen ->
            val id = resolveConversation(requestedId)
            streamReply(id, text, gen, explicitHistory = null, persistUser = true, replyKind = MESSAGE_KIND)
        }
    }

    fun sendChip(label: String) {
        val chip = TODAY_REPLY_CHIPS.find { it.label == label && it.requestsModel } ?: return
        if (streaming) return
        val requestedId = selectedId.value ?: return
        if (config == null) {
            viewModelScope.launch { recordConfigFailure(requestedId, chip.label) }
            return
        }
        beginJob { gen ->
            if (!repo.conversationExists(requestedId)) return@beginJob
            streamReply(requestedId, chip.label, gen, explicitHistory = null, persistUser = true, replyKind = MESSAGE_KIND)
        }
    }

    fun retry(errorId: Long) {
        if (streaming || errorId <= 0L || config == null) return
        val conversationId = selectedId.value ?: return
        beginJob { gen ->
            val selection = Conversation.exclusive {
                if (gen != requestGeneration) return@exclusive null
                val all = repo.listMessages(conversationId)
                val chosen = retrySelection(all, errorId) ?: return@exclusive null
                repo.deleteMessage(errorId)
                chosen
            } ?: return@beginJob
            if (gen != requestGeneration) return@beginJob
            streamReply(
                conversationId,
                selection.userText,
                gen,
                selection.history,
                persistUser = false,
                replyKind = selection.replyKind,
            )
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun select(id: Long?) {
        currentId = id
        selectedId.value = id
    }

    private suspend fun resolveConversation(requestedId: Long?): Long {
        if (requestedId != null && repo.conversationExists(requestedId)) return requestedId
        val id = repo.createChat()
        select(id)
        return id
    }

    private suspend fun recordConfigFailure(requestedId: Long?, text: String) {
        val id = resolveConversation(requestedId)
        repo.insertMessage(id, "user", text, null, MESSAGE_KIND)
        repo.maybeTitleFromFirstMessage(id, text)
        repo.insertMessage(
            conversationId = id,
            role = "assistant",
            content = "",
            error = "无法读取加密配置：${configResult.exceptionOrNull()?.message ?: "未知错误"}",
            kind = MESSAGE_KIND,
        )
    }

    private fun beginJob(block: suspend (gen: Int) -> Unit) {
        if (streaming) return
        streaming = true
        val gen = requestGeneration
        job = viewModelScope.launch {
            try {
                block(gen)
            } finally {
                if (gen == requestGeneration) {
                    streaming = false
                    streamingText = null
                    streamingConversationId = null
                }
            }
        }
    }

    private suspend fun streamReply(
        conversationId: Long,
        userText: String,
        gen: Int,
        explicitHistory: List<ChatTurn>?,
        persistUser: Boolean,
        replyKind: String,
    ) {
        val savedConfig = config ?: return
        streamingConversationId = conversationId
        streamingKind = replyKind
        streamingText = ""
        val buffer = StringBuilder()
        val seen = Conversation.generation(conversationId)
        try {
            Conversation.exclusive {
                if (!stillCurrent(conversationId, gen, seen)) return@exclusive
                if (persistUser) {
                    repo.insertMessage(conversationId, "user", userText, null, MESSAGE_KIND)
                    repo.maybeTitleFromFirstMessage(conversationId, userText)
                }
                val history = explicitHistory ?: turnsForRequest(repo.listMessages(conversationId), conversationId)
                    .let { turns ->
                        if (turns.lastOrNull()?.role == "user" && turns.last().content == userText) {
                            turns.dropLast(1)
                        } else {
                            turns
                        }
                    }
                val goal = repo.getGoal()
                val inputModel = goal?.toInput()
                val projection = inputModel?.let { FireCalculator.calculate(it) }
                val settings = savedConfig.toSettings()
                val reply = client.stream(
                    settings = settings,
                    messages = assembleMessages(
                        systemPrompt = settings.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                        progressContext = buildProgressContext(inputModel, projection),
                        history = history,
                        userInput = userText,
                    ),
                ) { delta ->
                    buffer.append(delta)
                    if (gen == requestGeneration && streamingConversationId == conversationId) {
                        streamingText = buffer.toString()
                    }
                }
                if (!stillCurrent(conversationId, gen, seen)) return@exclusive
                if (reply.isBlank()) {
                    repo.insertMessage(conversationId, "assistant", "", "请求失败：模型返回了空内容", replyKind)
                } else {
                    repo.insertMessage(conversationId, "assistant", reply, null, replyKind)
                }
            }
        } catch (cancel: CancellationException) {
            val partial = buffer.toString()
            if (partial.isNotBlank() && stillCurrent(conversationId, gen, seen)) {
                Conversation.exclusive {
                    if (stillCurrent(conversationId, gen, seen)) {
                        repo.insertMessage(conversationId, "assistant", partial, null, replyKind)
                    }
                }
            }
            throw cancel
        } catch (error: Exception) {
            if (stillCurrent(conversationId, gen, seen)) {
                Conversation.exclusive {
                    if (stillCurrent(conversationId, gen, seen)) {
                        repo.insertMessage(
                            conversationId,
                            "assistant",
                            buffer.toString(),
                            userVisibleLlmError(error),
                            replyKind,
                        )
                    }
                }
            }
        }
    }

    private suspend fun stillCurrent(conversationId: Long, gen: Int, seen: Long): Boolean {
        return gen == requestGeneration &&
            seen == Conversation.generation(conversationId) &&
            repo.conversationExists(conversationId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    openToken: Int,
    openConversationId: Long,
    onOpenProgress: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val stored by viewModel.messages.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val current = conversations.find { it.id == viewModel.currentId }
    val streamingHere = viewModel.streamingText != null && viewModel.streamingConversationId == viewModel.currentId
    var showSessions by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val todayKey = dailyDayKey(LocalDate.now())
    val bubbles = buildList {
        addAll(stored)
        if (streamingHere) {
            add(
                ChatMessageEntity(
                    id = -1,
                    conversationId = viewModel.currentId ?: 0L,
                    role = "assistant",
                    content = viewModel.streamingText.orEmpty(),
                    createdAt = Long.MAX_VALUE,
                    kind = viewModel.streamingKind,
                ),
            )
        }
    }
    LaunchedEffect(openToken) {
        if (openToken > 0 && openConversationId > 0L) viewModel.openConversation(openConversationId)
    }
    LaunchedEffect(bubbles.size, viewModel.streamingText, viewModel.currentId) {
        if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.lastIndex)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = { showSessions = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "会话")
                    }
                },
                title = {
                    Text(
                        current?.title ?: "聊天",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::createConversation) {
                        Icon(Icons.Filled.Add, contentDescription = "新建会话")
                    }
                    IconButton(
                        onClick = { current?.let { confirmDeleteId = it.id } },
                        enabled = current != null,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除会话")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (stored.isEmpty() && !streamingHere) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("从一句想说的话开始。", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "发送时会带上当前进度，失败只显示错误。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bubbles, key = { if (it.id == -1L) "streaming" else it.id }) { message ->
                    val showChips = current != null && showsChips(current, todayKey, message, stored)
                    if (message.kind == TODAY_CARD_KIND && message.role == "assistant") {
                        TodayCard(
                            message = message,
                            showChips = showChips,
                            showRetry = message.id > 0L && message.error != null && !viewModel.streaming,
                            onRetry = { viewModel.retry(message.id) },
                            onChip = { label, requestsModel ->
                                if (requestsModel) viewModel.sendChip(label) else onOpenProgress()
                            },
                        )
                    } else {
                        Bubble(
                            message = message,
                            onRetry = { viewModel.retry(message.id) },
                            showRetry = message.id > 0L && message.error != null && !viewModel.streaming,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = viewModel.input,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("说点什么") },
                    maxLines = 4,
                )
                if (viewModel.streaming) {
                    IconButton(onClick = viewModel::stop) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止")
                    }
                } else {
                    IconButton(onClick = viewModel::send, enabled = viewModel.input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
    if (showSessions) {
        ModalBottomSheet(
            onDismissRequest = { showSessions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SessionList(
                conversations = conversations,
                currentId = viewModel.currentId,
                onCreate = {
                    viewModel.createConversation()
                    showSessions = false
                },
                onSelect = {
                    viewModel.selectConversation(it)
                    showSessions = false
                },
                onRename = {
                    renameTarget = it
                    renameText = it.title
                },
                onDelete = { confirmDeleteId = it.id },
            )
        }
    }
    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("删除这个会话？") },
            text = { Text("里面的消息也会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteId = null
                    showSessions = false
                    viewModel.deleteConversation(id)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("取消") }
            },
        )
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(target.id, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.trim().isNotEmpty(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SessionList(
    conversations: List<ConversationEntity>,
    currentId: Long?,
    onCreate: () -> Unit,
    onSelect: (Long) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("会话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("新建会话") }
        if (conversations.isEmpty()) {
            Text(
                "还没有会话",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        conversations.forEach { conversation ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onSelect(conversation.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        conversation.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (conversation.id == currentId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = { onRename(conversation) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "重命名")
                }
                IconButton(onClick = { onDelete(conversation) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

private fun showsChips(
    conversation: ConversationEntity,
    todayKey: String,
    message: ChatMessageEntity,
    conversationMessages: List<ChatMessageEntity>,
): Boolean = app.fireplan.data.showsTodayReplyChips(
    conversationKind = conversation.kind,
    conversationDayKey = conversation.dayKey,
    todayKey = todayKey,
    message = message,
    conversationMessages = conversationMessages,
)

@Composable
private fun TodayCard(
    message: ChatMessageEntity,
    showChips: Boolean,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onChip: (label: String, requestsModel: Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今日开工", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (message.content.isNotBlank() || message.error == null) {
                    Text(
                        message.content.ifBlank { "…" },
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        message.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
            if (showRetry) {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("重试") }
            }
        }
        if (showChips) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TODAY_REPLY_CHIPS.forEach { chip ->
                    OutlinedButton(onClick = { onChip(chip.label, chip.requestsModel) }) {
                        Text(chip.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessageEntity, onRetry: () -> Unit, showRetry: Boolean) {
    val mine = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (message.content.isNotBlank() || message.error == null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = message.content.ifBlank { "…" },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        message.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
            if (showRetry) {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("重试") }
            }
        }
    }
}
