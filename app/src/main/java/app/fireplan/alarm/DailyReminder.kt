package app.fireplan.alarm

import android.content.Context
import app.fireplan.data.Conversation
import app.fireplan.data.DAILY_ENCOURAGEMENT_REQUEST
import app.fireplan.data.DEFAULT_SYSTEM_PROMPT
import app.fireplan.data.EncryptedConfig
import app.fireplan.data.FireRepository
import app.fireplan.data.LlmClient
import app.fireplan.data.MissingConfigException
import app.fireplan.data.TODAY_CARD_KIND
import app.fireplan.data.assembleMessages
import app.fireplan.data.missingSettings
import app.fireplan.data.notificationText
import app.fireplan.data.toInput
import app.fireplan.data.turnsForRequest
import app.fireplan.data.userVisibleLlmError
import app.fireplan.domain.FireCalculator
import app.fireplan.domain.buildProgressContext
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

data class ReminderResult(val text: String, val conversationId: Long)

object DailyReminder {
    fun execute(context: Context): ReminderResult? {
        val app = context.applicationContext
        val config = EncryptedConfig(app)
        val repo = FireRepository.get(app)
        var conversationId = 0L
        var seen = 0L
        val result = runCatching {
            runBlocking {
                Conversation.exclusive {
                    val conversation = repo.ensureDailyConversation(LocalDate.now())
                    conversationId = conversation.id
                    seen = Conversation.generation(conversation.id)
                    if (seen != Conversation.generation(conversation.id)) return@exclusive ""
                    val settings = config.toSettings()
                    val missing = missingSettings(settings)
                    if (missing.isNotEmpty()) throw MissingConfigException(missing)
                    val goal = repo.getGoal()
                    val input = goal?.toInput()
                    val projection = input?.let { FireCalculator.calculate(it) }
                    val history = turnsForRequest(repo.listMessages(conversation.id), conversation.id)
                    val messages = assembleMessages(
                        systemPrompt = settings.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                        progressContext = buildProgressContext(input, projection),
                        history = history,
                        userInput = DAILY_ENCOURAGEMENT_REQUEST,
                    )
                    val reply = LlmClient().stream(settings, messages) { }
                    if (seen != Conversation.generation(conversation.id)) return@exclusive ""
                    if (!repo.conversationExists(conversation.id)) return@exclusive ""
                    if (reply.isBlank()) error("模型返回了空内容")
                    repo.insertMessage(
                        conversationId = conversation.id,
                        role = "assistant",
                        content = reply,
                        error = null,
                        kind = TODAY_CARD_KIND,
                    )
                    reply
                }
            }
        }
        if (conversationId == 0L || seen != Conversation.generation(conversationId)) return null
        if (result.getOrNull().isNullOrEmpty() && result.isSuccess) return null
        result.onFailure { error ->
            runBlocking {
                Conversation.exclusive {
                    if (seen != Conversation.generation(conversationId)) return@exclusive
                    if (!repo.conversationExists(conversationId)) return@exclusive
                    repo.insertMessage(
                        conversationId = conversationId,
                        role = "assistant",
                        content = "",
                        error = userVisibleLlmError(error),
                        kind = TODAY_CARD_KIND,
                    )
                }
            }
        }
        if (seen != Conversation.generation(conversationId)) return null
        return ReminderResult(notificationText(result), conversationId)
    }
}
