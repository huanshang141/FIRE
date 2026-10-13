package app.fireplan.alarm

import android.content.Context
import app.fireplan.data.ChatTurn
import app.fireplan.data.Conversation
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
import kotlinx.coroutines.runBlocking

object DailyReminder {
    fun execute(context: Context) {
        val app = context.applicationContext
        val seen = Conversation.epoch()
        val config = EncryptedConfig(app)
        val repo = FireRepository.get(app)
        val result = runCatching {
            runBlocking {
                Conversation.exclusive {
                    if (seen != Conversation.epoch()) return@exclusive ""
                    val settings = config.toSettings()
                    val missing = missingSettings(settings)
                    if (missing.isNotEmpty()) throw MissingConfigException(missing)
                    val goal = repo.getGoal()
                    val input = goal?.toInput()
                    val projection = input?.let { FireCalculator.calculate(it) }
                    val history = repo.listMessages().mapNotNull { message ->
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
                    val messages = assembleMessages(
                        systemPrompt = settings.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                        progressContext = buildProgressContext(input, projection),
                        history = history,
                        userInput = DAILY_ENCOURAGEMENT_REQUEST,
                    )
                    val reply = LlmClient().stream(settings, messages) { }
                    if (seen != Conversation.epoch()) return@exclusive ""
                    if (reply.isBlank()) error("模型返回了空内容")
                    repo.insertMessage(role = "assistant", content = reply, error = null)
                    reply
                }
            }
        }
        if (seen != Conversation.epoch()) return
        if (result.getOrNull().isNullOrEmpty() && result.isSuccess) return
        result.onFailure { error ->
            runBlocking {
                Conversation.exclusive {
                    if (seen != Conversation.epoch()) return@exclusive
                    repo.insertMessage(role = "assistant", content = "", error = userVisibleLlmError(error))
                }
            }
        }
        if (seen != Conversation.epoch()) return
        Notifier.show(app, notificationText(result), openChat = true)
    }
}
