package app.fireplan.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.math.roundToInt

class EncryptedConfig(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value.coerceIn(0f, 2f)).apply()

    var systemPrompt: String
        get() = if (prefs.contains(KEY_SYSTEM_PROMPT)) {
            prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty()
        } else {
            DEFAULT_SYSTEM_PROMPT
        }
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var reminderHour: Int
        get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        set(value) = prefs.edit().putInt(KEY_HOUR, value.coerceIn(0, 23)).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value.coerceIn(0, 59)).apply()

    fun saveSnapshot(
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Float,
        systemPrompt: String,
        reminderHour: Int,
        reminderMinute: Int,
    ) {
        val wrote = prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .putFloat(KEY_TEMPERATURE, temperature.coerceIn(0f, 2f))
            .putString(KEY_SYSTEM_PROMPT, systemPrompt)
            .putInt(KEY_HOUR, reminderHour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, reminderMinute.coerceIn(0, 59))
            .commit()
        if (!wrote) error("设置没有写入")
    }

    fun toSettings(): LlmSettings = LlmSettings(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        temperature = (temperature * 100f).roundToInt() / 100.0,
        systemPrompt = systemPrompt,
    )

    fun isComplete(): Boolean = missingSettings(toSettings()).isEmpty()

    private companion object {
        const val FILE_NAME = "fireplan_secure_config"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_API_KEY = "apiKey"
        const val KEY_MODEL = "model"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_SYSTEM_PROMPT = "systemPrompt"
        const val KEY_HOUR = "reminderHour"
        const val KEY_MINUTE = "reminderMinute"
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 0

        @Suppress("DEPRECATION")
        fun createPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
