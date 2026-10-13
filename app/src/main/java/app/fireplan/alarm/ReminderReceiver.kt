package app.fireplan.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import app.fireplan.data.EncryptedConfig
import app.fireplan.data.userVisibleLlmError

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fireplan:reminder")
            .apply { acquire(90_000L) }
        Thread({
            try {
                DailyReminder.execute(app)
            } catch (error: Throwable) {
                Notifier.show(app, userVisibleLlmError(error), openChat = true)
            } finally {
                try {
                    val config = EncryptedConfig(app)
                    AlarmScheduler.schedule(app, config.reminderHour, config.reminderMinute)
                } catch (_: Throwable) {
                    AlarmScheduler.schedule(app, 8, 0)
                }
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }, "fireplan-reminder").start()
    }
}
