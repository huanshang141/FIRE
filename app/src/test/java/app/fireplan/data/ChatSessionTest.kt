package app.fireplan.data

import app.fireplan.data.db.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChatSessionTest {
    @Test
    fun ordinaryTitleUsesTheFirstTwentyCharacters() {
        assertEquals("新的聊天", titleFromFirstUtterance("   "))
        assertEquals("今天去上班 接着说", titleFromFirstUtterance("  今天去上班\n接着说  "))
        val long = "一二三四五六七八九十一二三四五六七八九十多出来"
        assertEquals("一二三四五六七八九十一二三四五六七八九十", titleFromFirstUtterance(long))
        assertEquals(20, titleFromFirstUtterance(long).length)
    }

    @Test
    fun dailyConversationIsNamedForThatDay() {
        val date = LocalDate.of(2026, 10, 13)
        assertEquals("10月13日开工", dailyConversationTitle(date))
        assertEquals("2026-10-13", dailyDayKey(date))
        assertEquals(LEGACY_CHAT_TITLE, "以前的聊天")
    }

    @Test
    fun requestHistoryStaysInsideOneConversation() {
        val messages = listOf(
            message(1, 10, "user", "只要这句"),
            message(2, 11, "user", "别带上"),
            message(3, 10, "assistant", "这句回复"),
            message(4, 10, "assistant", "", error = "请求失败：超时"),
        )
        assertEquals(
            listOf(ChatTurn("user", "只要这句"), ChatTurn("assistant", "这句回复")),
            turnsForRequest(messages, conversationId = 10),
        )
    }

    @Test
    fun replyChipsBelongOnlyToTodaysLatestCard() {
        val today = "2026-10-13"
        val older = message(1, 4, "assistant", "昨天的开工", kind = TODAY_CARD_KIND)
        val latest = message(2, 4, "assistant", "今天的开工", kind = TODAY_CARD_KIND)
        val failed = message(3, 4, "assistant", "", error = "请求失败：HTTP 500", kind = TODAY_CARD_KIND)
        assertTrue(
            showsTodayReplyChips(CONVERSATION_DAILY, today, today, latest, listOf(older, latest)),
        )
        assertFalse(
            showsTodayReplyChips(CONVERSATION_DAILY, today, today, older, listOf(older, latest)),
        )
        assertFalse(
            showsTodayReplyChips(CONVERSATION_DAILY, "2026-10-12", today, older, listOf(older)),
        )
        assertFalse(
            showsTodayReplyChips(CONVERSATION_CHAT, today, today, latest, listOf(latest)),
        )
        assertFalse(
            showsTodayReplyChips(CONVERSATION_DAILY, today, today, failed, listOf(failed)),
        )
        assertEquals(listOf(CHIP_GO_TO_WORK, CHIP_LOW_ENERGY, CHIP_OPEN_PROGRESS), TODAY_REPLY_CHIPS.map { it.label })
        assertFalse(TODAY_REPLY_CHIPS.single { it.label == CHIP_OPEN_PROGRESS }.requestsModel)
        assertTrue(TODAY_REPLY_CHIPS.filter { it.label != CHIP_OPEN_PROGRESS }.all { it.requestsModel })
    }

    private fun message(
        id: Long,
        conversationId: Long,
        role: String,
        content: String,
        error: String? = null,
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
