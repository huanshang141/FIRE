package app.fireplan.data

import app.fireplan.data.db.ChatMessageEntity

data class RetrySelection(
    val userText: String,
    val history: List<ChatTurn>,
    val replyKind: String,
)

fun retrySelection(messages: List<ChatMessageEntity>, errorId: Long): RetrySelection? {
    val error = messages.find { it.id == errorId && it.error != null } ?: return null
    val scoped = messages.filter { it.conversationId == error.conversationId }
    val prior = scoped.filter { it.id < error.id }
    val lastUser = prior.lastOrNull { it.role == "user" }
    val between = if (lastUser == null) prior else prior.filter { it.id > lastUser.id }
    val user = lastUser
    val historySource: List<ChatMessageEntity>
    val userText: String
    if (user != null && between.none { it.isSuccessfulAssistant() }) {
        historySource = scoped.filter { it.id < user.id }
        userText = user.content
    } else {
        historySource = prior
        userText = DAILY_ENCOURAGEMENT_REQUEST
    }
    val replyKind = if (error.kind == TODAY_CARD_KIND) TODAY_CARD_KIND else MESSAGE_KIND
    return RetrySelection(
        userText = userText,
        history = historySource.mapNotNull { it.toRequestTurn() },
        replyKind = replyKind,
    )
}

private fun ChatMessageEntity.isSuccessfulAssistant(): Boolean {
    return role == "assistant" && error == null && content.isNotBlank()
}
