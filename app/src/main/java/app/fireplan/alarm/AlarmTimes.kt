package app.fireplan.alarm

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

fun nextDailyTriggerMillis(hour: Int, minute: Int, nowMillis: Long, zone: ZoneId): Long {
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) {
        next = next.plusDays(1)
    }
    return next.toInstant().toEpochMilli()
}
