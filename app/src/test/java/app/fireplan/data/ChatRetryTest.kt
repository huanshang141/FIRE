package app.fireplan.data

import app.fireplan.data.db.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRetryTest {
    @Test
    fun retriesTheFailedUserTurnWithoutLaterMessages() {
        val messages = listOf(
            turn(1, "user", "第一问"),
            turn(2, "assistant", "第一答"),
            turn(3, "user", "第二问"),
            turn(4, "assistant", "", error = "请求失败：HTTP 500：boom"),
            turn(5, "user", "第三问"),
            turn(6, "assistant", "第三答"),
        )
        val selection = retrySelection(messages, errorId = 4)
        assertEquals("第二问", selection!!.userText)
        assertEquals(
            listOf(ChatTurn("user", "第一问"), ChatTurn("assistant", "第一答")),
            selection.history,
        )
    }

    @Test
    fun reminderErrorRetriesTheDailyRequest() {
        val messages = listOf(
            turn(1, "user", "你好"),
            turn(2, "assistant", "在的"),
            turn(3, "assistant", "", error = "请求失败：HTTP 401：invalid"),
        )
        val selection = retrySelection(messages, errorId = 3)
        assertEquals(DAILY_ENCOURAGEMENT_REQUEST, selection!!.userText)
        assertEquals(
            listOf(ChatTurn("user", "你好"), ChatTurn("assistant", "在的")),
            selection.history,
        )
        assertEquals(MESSAGE_KIND, selection.replyKind)
    }

    @Test
    fun todayCardRetryStaysInThatConversation() {
        val messages = listOf(
            turn(1, "assistant", "昨天的话", conversationId = 2),
            turn(2, "assistant", "", error = "请求失败：HTTP 500：boom", conversationId = 7, kind = TODAY_CARD_KIND),
            turn(3, "user", "另一件事", conversationId = 2),
        )
        val selection = retrySelection(messages, errorId = 2)
        assertEquals(DAILY_ENCOURAGEMENT_REQUEST, selection!!.userText)
        assertEquals(TODAY_CARD_KIND, selection.replyKind)
        assertTrue(selection.history.isEmpty())
    }

    @Test
    fun missingErrorIsNotRetried() {
        assertNull(retrySelection(listOf(turn(1, "user", "你好")), errorId = 9))
    }

    private fun turn(
        id: Long,
        role: String,
        content: String,
        error: String? = null,
        conversationId: Long = 0,
        kind: String = MESSAGE_KIND,
    ) = ChatMessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        createdAt = id,
        error = error,
        kind = kind,
    )
}
