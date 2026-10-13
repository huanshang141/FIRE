package app.fireplan.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import app.fireplan.data.assembleMessages
import app.fireplan.data.db.ChatMessageEntity
import app.fireplan.data.retrySelection
import app.fireplan.data.toInput
import app.fireplan.data.userVisibleLlmError
import app.fireplan.domain.FireCalculator
import app.fireplan.domain.buildProgressContext
import app.fireplan.ui.LocalViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FireRepository.get(app)
    private val configResult = runCatching { EncryptedConfig(app) }
    private val config = configResult.getOrNull()
    private val client = LlmClient()
    private var job: Job? = null
    private var generation by mutableIntStateOf(0)

    val messages = repo.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var input by mutableStateOf("")
    var streaming by mutableStateOf(false)
    var streamingText by mutableStateOf<String?>(null)

    fun updateInput(value: String) {
        input = value
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || streaming) return
        input = ""
        if (config == null) {
            viewModelScope.launch {
                repo.insertMessage(role = "user", content = text, error = null)
                repo.insertMessage(
                    role = "assistant",
                    content = "",
                    error = "无法读取加密配置：${configResult.exceptionOrNull()?.message ?: "未知错误"}",
                )
            }
            return
        }
        streaming = true
        val gen = generation
        job = viewModelScope.launch {
            try {
                streamReply(text, gen, explicitHistory = null, persistUser = true)
            } finally {
                if (gen == generation) {
                    streaming = false
                    streamingText = null
                }
            }
        }
    }

    fun retry(errorId: Long) {
        if (streaming || errorId <= 0L) return
        if (config == null) return
        streaming = true
        val gen = generation
        job = viewModelScope.launch {
            try {
                val selection = Conversation.exclusive {
                    if (gen != generation) return@exclusive null
                    val all = repo.listMessages()
                    val chosen = retrySelection(all, errorId) ?: return@exclusive null
                    repo.deleteMessage(errorId)
                    chosen
                } ?: return@launch
                if (gen != generation) return@launch
                streamReply(selection.userText, gen, selection.history, persistUser = false)
            } finally {
                if (gen == generation) {
                    streaming = false
                    streamingText = null
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        val gen = ++generation
        Conversation.bumpEpoch()
        job?.cancel()
        viewModelScope.launch {
            Conversation.exclusive {
                repo.clearMessages()
                if (gen == generation) {
                    streaming = false
                    streamingText = null
                }
            }
        }
    }

    private suspend fun streamReply(
        userText: String,
        gen: Int,
        explicitHistory: List<ChatTurn>?,
        persistUser: Boolean,
    ) {
        val savedConfig = config ?: return
        streaming = true
        streamingText = ""
        val buffer = StringBuilder()
        val seen = Conversation.epoch()
        try {
            Conversation.exclusive {
                if (gen != generation || seen != Conversation.epoch()) return@exclusive
                if (persistUser) {
                    repo.insertMessage(role = "user", content = userText, error = null)
                }
                val history = explicitHistory ?: repo.listMessages()
                    .mapNotNull { message ->
                        if (
                            message.error == null &&
                            message.content.isNotBlank() &&
                            message.role in setOf("user", "assistant")
                        ) {
                            ChatTurn(message.role, message.content)
                        } else {
                            null
                        }
                    }
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
                val reply = client.stream(
                    settings = savedConfig.toSettings(),
                    messages = assembleMessages(
                        systemPrompt = savedConfig.toSettings().systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                        progressContext = buildProgressContext(inputModel, projection),
                        history = history,
                        userInput = userText,
                    ),
                ) { delta ->
                    buffer.append(delta)
                    if (gen == generation) streamingText = buffer.toString()
                }
                if (gen != generation || seen != Conversation.epoch()) return@exclusive
                if (reply.isBlank()) {
                    repo.insertMessage("assistant", "", "请求失败：模型返回了空内容")
                } else {
                    repo.insertMessage("assistant", reply, null)
                }
            }
        } catch (cancel: CancellationException) {
            val partial = buffer.toString()
            if (gen == generation && seen == Conversation.epoch() && partial.isNotBlank()) {
                Conversation.exclusive {
                    if (gen == generation && seen == Conversation.epoch()) {
                        repo.insertMessage("assistant", partial, null)
                    }
                }
            }
            throw cancel
        } catch (error: Exception) {
            if (gen == generation && seen == Conversation.epoch()) {
                Conversation.exclusive {
                    if (gen == generation && seen == Conversation.epoch()) {
                        repo.insertMessage("assistant", buffer.toString(), userVisibleLlmError(error))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(factory = LocalViewModelFactory.current)) {
    val stored by viewModel.messages.collectAsState()
    val streamingText = viewModel.streamingText
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val bubbles = buildList {
        addAll(stored)
        if (streamingText != null) {
            add(
                ChatMessageEntity(
                    id = -1,
                    role = "assistant",
                    content = streamingText,
                    createdAt = Long.MAX_VALUE,
                ),
            )
        }
    }
    LaunchedEffect(bubbles.size, streamingText) {
        if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.lastIndex)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("聊天") },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (stored.isEmpty() && streamingText == null) {
                Text(
                    "每次发送都会带上当前进度，并请求你在设置里填写的模型。失败时只显示错误，不会用本地文案代替回复。",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bubbles, key = { if (it.id == -1L) "streaming" else it.id }) { message ->
                    Bubble(
                        message = message,
                        onRetry = { viewModel.retry(message.id) },
                        showRetry = message.id > 0L && message.error != null && !viewModel.streaming,
                    )
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
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空聊天记录？") },
            text = { Text("这台手机上的对话会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clear()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
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
