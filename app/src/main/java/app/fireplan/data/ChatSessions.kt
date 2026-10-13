package app.fireplan.data

import app.fireplan.data.db.ChatMessageEntity
import java.time.LocalDate

const val CONVERSATION_CHAT = "chat"
const val CONVERSATION_DAILY = "daily"
const val MESSAGE_KIND = "message"
const val TODAY_CARD_KIND = "today_card"
const val NEW_CHAT_TITLE = "新的聊天"
const val LEGACY_CHAT_TITLE = "以前的聊天"

const val CHIP_GO_TO_WORK = "今天去上班"
const val CHIP_LOW_ENERGY = "今天有点不想动"
const val CHIP_OPEN_PROGRESS = "想对一下进度"

data class ReplyChip(val label: String, val requestsModel: Boolean)

val TODAY_REPLY_CHIPS = listOf(
    ReplyChip(CHIP_GO_TO_WORK, requestsModel = true),
    ReplyChip(CHIP_LOW_ENERGY, requestsModel = true),
    ReplyChip(CHIP_OPEN_PROGRESS, requestsModel = false),
)

fun dailyConversationTitle(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日开工"

fun dailyDayKey(date: LocalDate): String = date.toString()

fun titleFromFirstUtterance(text: String): String {
    val compact = text.trim().replace(Regex("\\s+"), " ")
    if (compact.isEmpty()) return NEW_CHAT_TITLE
    return compact.take(20)
}

fun turnsForRequest(messages: List<ChatMessageEntity>, conversationId: Long): List<ChatTurn> {
    return messages.filter { it.conversationId == conversationId }.mapNotNull { it.toRequestTurn() }
}

fun showsTodayReplyChips(
    conversationKind: String,
    conversationDayKey: String?,
    todayKey: String,
    message: ChatMessageEntity,
    conversationMessages: List<ChatMessageEntity>,
): Boolean {
    if (conversationKind != CONVERSATION_DAILY || conversationDayKey != todayKey) return false
    if (message.kind != TODAY_CARD_KIND || message.error != null || message.content.isBlank()) return false
    val latestCardId = conversationMessages
        .filter { it.kind == TODAY_CARD_KIND }
        .maxOfOrNull { it.id }
        ?: return false
    return message.id == latestCardId
}

fun ChatMessageEntity.toRequestTurn(): ChatTurn? {
    if (error != null || content.isBlank() || role !in setOf("user", "assistant")) return null
    return ChatTurn(role, content)
}
