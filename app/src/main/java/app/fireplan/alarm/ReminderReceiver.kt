package app.fireplan.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import app.fireplan.data.EncryptedConfig
import app.fireplan.data.userVisibleLlmError

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        scheduleNext(app)
        val pending = goAsync()
        val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fireplan:reminder")
            .apply { acquire(90_000L) }
        Thread({
            try {
                val result = DailyReminder.execute(app)
                if (result != null) Notifier.show(app, result.text, result.conversationId)
            } catch (error: Throwable) {
                Notifier.show(app, userVisibleLlmError(error))
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }, "fireplan-reminder").start()
    }

    private fun scheduleNext(app: Context) {
        try {
            val config = EncryptedConfig(app)
            AlarmScheduler.schedule(app, config.reminderHour, config.reminderMinute)
        } catch (_: Throwable) {
            AlarmScheduler.schedule(app, 8, 0)
        }
    }
}
