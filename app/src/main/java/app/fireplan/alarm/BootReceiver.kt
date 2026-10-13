package app.fireplan.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fireplan.data.EncryptedConfig

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val app = context.applicationContext
        try {
            val config = EncryptedConfig(app)
            AlarmScheduler.schedule(app, config.reminderHour, config.reminderMinute)
        } catch (_: Throwable) {
            AlarmScheduler.schedule(app, 8, 0)
        }
    }
}
