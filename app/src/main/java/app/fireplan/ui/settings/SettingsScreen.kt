package app.fireplan.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fireplan.alarm.AlarmScheduler
import app.fireplan.data.DEFAULT_SYSTEM_PROMPT
import app.fireplan.data.EncryptedConfig
import app.fireplan.ui.LocalViewModelFactory
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val config = EncryptedConfig(app)

    var baseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var model by mutableStateOf("")
    var temperature by mutableStateOf(0.7f)
    var systemPrompt by mutableStateOf(DEFAULT_SYSTEM_PROMPT)
    var hour by mutableStateOf(8)
    var minute by mutableStateOf(0)
    var saved by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            try {
                baseUrl = config.baseUrl
                apiKey = config.apiKey
                model = config.model
                temperature = config.temperature
                systemPrompt = config.systemPrompt
                hour = config.reminderHour
                minute = config.reminderMinute
            } catch (error: Throwable) {
                loadError = "无法读取加密配置：${error.message ?: "未知错误"}"
            }
        }
    }

    fun restorePrompt() {
        systemPrompt = DEFAULT_SYSTEM_PROMPT
    }

    fun save() {
        try {
            config.baseUrl = baseUrl
            config.apiKey = apiKey
            config.model = model
            config.temperature = temperature
            config.systemPrompt = systemPrompt
            config.reminderHour = hour
            config.reminderMinute = minute
            AlarmScheduler.schedule(getApplication(), hour, minute)
            saved = true
            loadError = null
        } catch (error: Throwable) {
            saved = false
            loadError = "保存失败：${error.message ?: "未知错误"}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = LocalViewModelFactory.current)) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var exact by remember { mutableStateOf(AlarmScheduler.canExact(context)) }
    var showClock by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exact = AlarmScheduler.canExact(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text("设置") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            viewModel.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                value = viewModel.baseUrl,
                onValueChange = { viewModel.baseUrl = it; viewModel.saved = false },
                label = { Text("接口根地址") },
                placeholder = { Text("https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (viewModel.baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    "当前使用明文 HTTP，只建议连接本机或局域网里的模型。",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = viewModel.apiKey,
                onValueChange = { viewModel.apiKey = it; viewModel.saved = false },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = viewModel.model,
                onValueChange = { viewModel.model = it; viewModel.saved = false },
                label = { Text("模型名") },
                placeholder = { Text("gpt-4o-mini") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("温度 ${String.format(Locale.CHINA, "%.2f", viewModel.temperature)}")
            Slider(
                value = viewModel.temperature,
                onValueChange = { viewModel.temperature = it; viewModel.saved = false },
                valueRange = 0f..2f,
            )
            OutlinedTextField(
                value = viewModel.systemPrompt,
                onValueChange = { viewModel.systemPrompt = it; viewModel.saved = false },
                label = { Text("系统提示词") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            TextButton(onClick = { viewModel.restorePrompt(); viewModel.saved = false }) {
                Text("恢复默认")
            }
            Text("每日提醒时间 ${String.format(Locale.CHINA, "%02d:%02d", viewModel.hour, viewModel.minute)}")
            TextButton(onClick = { showClock = true }) { Text("选择时间") }
            if (!exact) {
                Text(
                    "精确闹钟权限未授予，已改用非精确闹钟。到点仍会请求模型。",
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                    }) { Text("授予精确闹钟权限") }
                }
            }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
            if (viewModel.saved) {
                Text("设置已保存", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showClock) {
        val state = rememberTimePickerState(viewModel.hour, viewModel.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showClock = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hour = state.hour
                    viewModel.minute = state.minute
                    viewModel.saved = false
                    showClock = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClock = false }) { Text("取消") }
            },
            text = { TimePicker(state) },
        )
    }
}
