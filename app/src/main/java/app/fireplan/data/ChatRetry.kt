package app.fireplan.data

import app.fireplan.data.db.ChatMessageEntity

data class RetrySelection(
    val userText: String,
    val history: List<ChatTurn>,
)

fun retrySelection(messages: List<ChatMessageEntity>, errorId: Long): RetrySelection? {
    val error = messages.find { it.id == errorId && it.error != null } ?: return null
    val prior = messages.filter { it.id < error.id }
    val lastUser = prior.lastOrNull { it.role == "user" }
    val between = if (lastUser == null) prior else prior.filter { it.id > lastUser.id }
    val user = lastUser
    val historySource: List<ChatMessageEntity>
    val userText: String
    if (user != null && between.none { it.isSuccessfulAssistant() }) {
        historySource = messages.filter { it.id < user.id }
        userText = user.content
    } else {
        historySource = prior
        userText = DAILY_ENCOURAGEMENT_REQUEST
    }
    return RetrySelection(
        userText = userText,
        history = historySource.mapNotNull { it.toTurnOrNull() },
    )
}

private fun ChatMessageEntity.isSuccessfulAssistant(): Boolean {
    return role == "assistant" && error == null && content.isNotBlank()
}

private fun ChatMessageEntity.toTurnOrNull(): ChatTurn? {
    if (error != null || content.isBlank() || role !in setOf("user", "assistant")) return null
    return ChatTurn(role, content)
}
