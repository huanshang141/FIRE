package app.fireplan

import android.app.Application
import app.fireplan.alarm.AlarmScheduler
import app.fireplan.alarm.Notifier
import app.fireplan.data.EncryptedConfig

class FireApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        try {
            val config = EncryptedConfig(this)
            AlarmScheduler.schedule(this, config.reminderHour, config.reminderMinute)
        } catch (_: Throwable) {
            AlarmScheduler.schedule(this, 8, 0)
        }
    }
}
