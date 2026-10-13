package app.fireplan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRequestTest {
    @Test
    fun baseUrlDropsTrailingSlashesBeforeChatCompletions() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            chatCompletionsUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            chatCompletionsUrl("  https://api.openai.com/v1///  "),
        )
        assertEquals(
            "http://192.168.1.8:11434/v1/chat/completions",
            chatCompletionsUrl("http://192.168.1.8:11434/v1/"),
        )
    }

    @Test
    fun messageOrderIsSystemThenProgressThenHistoryThenUser() {
        val messages = assembleMessages(
            systemPrompt = "SYS",
            progressContext = "PROGRESS",
            history = listOf(
                ChatTurn("user", "上一句"),
                ChatTurn("assistant", "上一答"),
            ),
            userInput = "这一轮",
        )
        assertEquals(
            listOf(
                ChatTurn("system", "SYS"),
                ChatTurn("system", "PROGRESS"),
                ChatTurn("user", "上一句"),
                ChatTurn("assistant", "上一答"),
                ChatTurn("user", "这一轮"),
            ),
            messages,
        )
    }

    @Test
    fun dailyEncouragementIsAUserRequestNotALocalReply() {
        val messages = assembleMessages(
            systemPrompt = DEFAULT_SYSTEM_PROMPT,
            progressContext = "当前进度：净资产 1 元",
            history = emptyList(),
            userInput = DAILY_ENCOURAGEMENT_REQUEST,
        )
        assertEquals("user", messages.last().role)
        assertEquals(DAILY_ENCOURAGEMENT_REQUEST, messages.last().content)
        assertEquals("system", messages[0].role)
        assertEquals("system", messages[1].role)
    }

    @Test
    fun requestJsonKeepsOrderAndLeavesTheKeyOut() {
        val json = buildRequestJson(
            model = "demo",
            temperature = 0.7,
            messages = assembleMessages("SYS", "PROGRESS", listOf(ChatTurn("user", "hi")), "next"),
        )
        assertTrue(json.contains("\"stream\":true"))
        assertTrue(json.contains("\"temperature\":0.7"))
        assertFalse(json.contains("sk-"))
        val sys = json.indexOf("SYS")
        val progress = json.indexOf("PROGRESS")
        val history = json.indexOf("hi")
        val next = json.indexOf("next")
        assertTrue(sys in 0 until progress)
        assertTrue(progress < history)
        assertTrue(history < next)
        assertEquals("Bearer secret", authorizationHeader("secret"))
    }

    @Test
    fun missingSettingsBlocksTheCallList() {
        val missing = missingSettings(
            LlmSettings(baseUrl = "  ", apiKey = "", model = "", temperature = 0.7, systemPrompt = ""),
        )
        assertEquals(listOf("baseUrl", "apiKey", "model"), missing)
        val ready = missingSettings(
            LlmSettings("http://127.0.0.1:8080/v1", "key", "local", 0.7, DEFAULT_SYSTEM_PROMPT),
        )
        assertTrue(ready.isEmpty())
    }

    @Test
    fun failureNoticeReportsTheErrorAndNotAnEncouragementTemplate() {
        val http = notificationText(Result.failure(LlmException(401, "HTTP 401：invalid api key")))
        assertEquals("请求失败：HTTP 401：invalid api key", http)
        assertEquals(http, userVisibleLlmError(LlmException(401, "HTTP 401：invalid api key")))
        assertEquals(MISSING_CONFIG_MESSAGE, notificationText(Result.failure(MissingConfigException(listOf("model")))))
        assertFalse(http.contains("开工鼓励"))
        assertFalse(MISSING_CONFIG_MESSAGE.contains("开工鼓励"))
        assertEquals("模型原话", notificationText(Result.success("模型原话")))
        assertEquals("请求失败：模型返回了空内容", notificationText(Result.success("  ")))
    }

    @Test
    fun sseParserReadsDeltaUntilDone() {
        assertNull(sseData("event: ping"))
        assertEquals("[DONE]", sseData("data: [DONE]"))
        assertEquals("你", deltaContent("""{"choices":[{"delta":{"content":"你"}}]}"""))
        assertEquals("a\"b\n", deltaContent("""{"choices":[{"delta":{"content":"a\"b\n"}}]}"""))
        assertEquals("bad key", extractErrorMessage("""{"error":{"message":"bad key","type":"auth"}}"""))
        assertEquals("HTTP 401：bad key", formatHttpError(401, """{"error":{"message":"bad key"}}"""))
        val incomplete = assertThrows(LlmException::class.java) { requireCompletedStream(false) }
        assertEquals(INCOMPLETE_STREAM_MESSAGE, incomplete.message)
        requireCompletedStream(true)
    }
}
