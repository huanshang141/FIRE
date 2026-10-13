package app.fireplan.alarm

import android.content.Context
import app.fireplan.data.ChatTurn
import app.fireplan.data.DAILY_ENCOURAGEMENT_REQUEST
import app.fireplan.data.DEFAULT_SYSTEM_PROMPT
import app.fireplan.data.EncryptedConfig
import app.fireplan.data.FireRepository
import app.fireplan.data.LlmClient
import app.fireplan.data.MissingConfigException
import app.fireplan.data.assembleMessages
import app.fireplan.data.missingSettings
import app.fireplan.data.notificationText
import app.fireplan.data.toInput
import app.fireplan.data.userVisibleLlmError
import app.fireplan.domain.FireCalculator
import app.fireplan.domain.buildProgressContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object DailyReminder {
    fun execute(context: Context) {
        val app = context.applicationContext
        val config = EncryptedConfig(app)
        val repo = FireRepository.get(app)
        val result = runCatching {
            runBlocking(Dispatchers.IO) {
                val settings = config.toSettings()
                val missing = missingSettings(settings)
                if (missing.isNotEmpty()) throw MissingConfigException(missing)
                val goal = repo.getGoal()
                val input = goal?.toInput()
                val projection = input?.let { FireCalculator.calculate(it) }
                val history = repo.listMessages()
                    .filter { message ->
                        message.error == null &&
                            message.content.isNotBlank() &&
                            message.role in setOf("user", "assistant")
                    }
                    .map { ChatTurn(it.role, it.content) }
                val messages = assembleMessages(
                    systemPrompt = settings.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                    progressContext = buildProgressContext(input, projection),
                    history = history,
                    userInput = DAILY_ENCOURAGEMENT_REQUEST,
                )
                val reply = LlmClient().stream(settings, messages) { }
                if (reply.isBlank()) error("模型返回了空内容")
                repo.insertMessage(role = "assistant", content = reply, error = null)
                reply
            }
        }
        result.onFailure { error ->
            runBlocking(Dispatchers.IO) {
                repo.insertMessage(role = "assistant", content = "", error = userVisibleLlmError(error))
            }
        }
        Notifier.show(app, notificationText(result), openChat = true)
    }
}
