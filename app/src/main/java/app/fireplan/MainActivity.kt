package app.fireplan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.fireplan.alarm.AlarmScheduler
import app.fireplan.data.EncryptedConfig
import app.fireplan.ui.AppViewModelFactory
import app.fireplan.ui.FireApp
import app.fireplan.ui.theme.FirePlanTheme

class MainActivity : ComponentActivity() {
    private var openChatToken by mutableIntStateOf(0)
    private var lastExactAlarmAllowed: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.wantsChat()) openChatToken++
        val factory = AppViewModelFactory(application)
        setContent {
            FirePlanTheme {
                FireApp(openChatToken = openChatToken, factory = factory)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val allowed = AlarmScheduler.canExact(this)
        val previous = lastExactAlarmAllowed
        lastExactAlarmAllowed = allowed
        if (previous != null && previous != allowed) {
            try {
                val config = EncryptedConfig(this)
                AlarmScheduler.schedule(this, config.reminderHour, config.reminderMinute)
            } catch (_: Throwable) {
                AlarmScheduler.schedule(this, 8, 0)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.wantsChat()) openChatToken++
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "app.fireplan.OPEN_CHAT"
    }
}

private fun Intent?.wantsChat(): Boolean = this?.getBooleanExtra(MainActivity.EXTRA_OPEN_CHAT, false) == true
