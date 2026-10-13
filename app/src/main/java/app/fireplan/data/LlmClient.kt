package app.fireplan.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

const val DEFAULT_SYSTEM_PROMPT =
    "你是中文 FIRE 陪跑。鼓励用户当天去上班，并把储蓄坚持下去。只使用给出的数字，不要编造净资产、收入、支出或日期。不要把建议说成投资承诺或收益保证。回答简短、具体、务实。"

const val DAILY_ENCOURAGEMENT_REQUEST =
    "根据当前进度写一段 80 字以内的开工鼓励，并写进具体数字"

const val MISSING_CONFIG_MESSAGE = "请先在设置中填写接口地址、API Key 和模型名"

data class ChatTurn(val role: String, val content: String)

data class LlmSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double,
    val systemPrompt: String,
)

class MissingConfigException(val missing: List<String>) : Exception(MISSING_CONFIG_MESSAGE)

class LlmException(val statusCode: Int?, message: String) : Exception(message)

fun chatCompletionsUrl(baseUrl: String): String {
    val root = baseUrl.trim().trimEnd('/')
    return "$root/chat/completions"
}

fun missingSettings(settings: LlmSettings): List<String> = buildList {
    if (settings.baseUrl.isBlank()) add("baseUrl")
    if (settings.apiKey.isBlank()) add("apiKey")
    if (settings.model.isBlank()) add("model")
}

fun assembleMessages(
    systemPrompt: String,
    progressContext: String,
    history: List<ChatTurn>,
    userInput: String,
): List<ChatTurn> = buildList {
    add(ChatTurn("system", systemPrompt))
    add(ChatTurn("system", progressContext))
    addAll(history)
    add(ChatTurn("user", userInput))
}

fun authorizationHeader(apiKey: String): String = "Bearer $apiKey"

fun buildRequestJson(model: String, temperature: Double, messages: List<ChatTurn>): String {
    val encoded = messages.joinToString(",") { turn ->
        """{"role":${jsonString(turn.role)},"content":${jsonString(turn.content)}}"""
    }
    return """{"model":${jsonString(model)},"temperature":${formatJsonNumber(temperature)},"stream":true,"messages":[$encoded]}"""
}

fun userVisibleLlmError(error: Throwable): String = when (error) {
    is MissingConfigException -> MISSING_CONFIG_MESSAGE
    is LlmException -> prefixFailure(error.message ?: "未知错误")
    else -> prefixFailure(error.message ?: "未知错误")
}

private fun prefixFailure(message: String): String {
    return if (message.startsWith("请求失败")) message else "请求失败：$message"
}

fun notificationText(result: Result<String>): String {
    result.fold(
        onSuccess = { text ->
            return if (text.isBlank()) "请求失败：模型返回了空内容" else text
        },
        onFailure = { error ->
            return userVisibleLlmError(error)
        },
    )
}

fun sseData(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("data:")) return null
    return trimmed.removePrefix("data:").trim().ifEmpty { null }
}

fun deltaContent(json: String): String? {
    val deltaAt = json.indexOf("\"delta\"")
    if (deltaAt < 0) return null
    return jsonStringAfter(json, "\"content\"", deltaAt)
}

fun extractErrorMessage(json: String): String? {
    val errorAt = json.indexOf("\"error\"")
    val from = if (errorAt >= 0) errorAt else 0
    return jsonStringAfter(json, "\"message\"", from)
}

fun formatHttpError(code: Int, raw: String): String {
    val detail = extractErrorMessage(raw) ?: raw.trim().take(800)
    return if (detail.isBlank()) "HTTP $code" else "HTTP $code：$detail"
}

class LlmClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun stream(
        settings: LlmSettings,
        messages: List<ChatTurn>,
        onDelta: (String) -> Unit,
    ): String {
        val missing = missingSettings(settings)
        if (missing.isNotEmpty()) throw MissingConfigException(missing)
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(chatCompletionsUrl(settings.baseUrl))
                .header("Authorization", authorizationHeader(settings.apiKey))
                .header("Accept", "text/event-stream")
                .post(
                    buildRequestJson(settings.model, settings.temperature, messages)
                        .toRequestBody(JSON_MEDIA),
                )
                .build()
            val call = http.newCall(request)
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause is kotlinx.coroutines.CancellationException) call.cancel()
            }
            call.execute().use { response ->
                val payload = response.body ?: throw LlmException(response.code, formatHttpError(response.code, ""))
                if (!response.isSuccessful) {
                    throw LlmException(response.code, formatHttpError(response.code, payload.string()))
                }
                val source = payload.source()
                val accumulated = StringBuilder()
                while (true) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.startsWith("{") && trimmed.contains("\"error\"")) {
                        throw LlmException(response.code, formatHttpError(response.code, trimmed))
                    }
                    val data = sseData(line) ?: continue
                    if (data == "[DONE]") break
                    if (data.contains("\"error\"") && deltaContent(data) == null) {
                        throw LlmException(response.code, formatHttpError(response.code, data))
                    }
                    val piece = deltaContent(data) ?: continue
                    if (piece.isEmpty()) continue
                    accumulated.append(piece)
                    onDelta(piece)
                }
                accumulated.toString()
            }
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun jsonString(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            else -> if (char.code < 0x20) {
                out.append("\\u")
                out.append(char.code.toString(16).padStart(4, '0'))
            } else {
                out.append(char)
            }
        }
    }
    out.append('"')
    return out.toString()
}

internal fun formatJsonNumber(value: Double): String {
    if (!value.isFinite()) return "0"
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

private fun jsonStringAfter(json: String, key: String, from: Int): String? {
    val at = json.indexOf(key, from)
    if (at < 0) return null
    var index = at + key.length
    while (index < json.length && json[index].isWhitespace()) index++
    if (index >= json.length || json[index] != ':') return null
    index++
    while (index < json.length && json[index].isWhitespace()) index++
    if (index >= json.length) return null
    if (json.startsWith("null", index)) return null
    if (json[index] != '"') return null
    index++
    val out = StringBuilder()
    while (index < json.length) {
        val char = json[index]
        if (char == '\\') {
            if (index + 1 >= json.length) return null
            when (val next = json[index + 1]) {
                '"', '\\', '/' -> out.append(next)
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (index + 5 >= json.length) return null
                    val hex = json.substring(index + 2, index + 6)
                    val code = hex.toIntOrNull(16) ?: return null
                    out.append(code.toChar())
                    index += 6
                    continue
                }
                else -> out.append(next)
            }
            index += 2
            continue
        }
        if (char == '"') return out.toString()
        out.append(char)
        index++
    }
    return null
}
