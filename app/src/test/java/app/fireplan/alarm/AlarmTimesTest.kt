package app.fireplan.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AlarmTimesTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun schedulesLaterTheSameDay() {
        val now = LocalDateTime.of(2026, 10, 13, 7, 15).toInstant(zone).toEpochMilli()
        val trigger = nextDailyTriggerMillis(8, 0, now, zone)
        val expected = LocalDateTime.of(2026, 10, 13, 8, 0).toInstant(zone).toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun rollsToTheNextDayOnceTheMinuteHasPassed() {
        val now = LocalDateTime.of(2026, 10, 13, 8, 0).toInstant(zone).toEpochMilli()
        val trigger = nextDailyTriggerMillis(8, 0, now, zone)
        val expected = LocalDateTime.of(2026, 10, 14, 8, 0).toInstant(zone).toEpochMilli()
        assertEquals(expected, trigger)
    }
}
