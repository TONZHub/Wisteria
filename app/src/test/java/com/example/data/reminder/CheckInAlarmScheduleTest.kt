package com.example.data.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInAlarmScheduleTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `future time schedules for today`() {
        val now = at(2026, 8, 24, 8, 30)

        val next = CheckInAlarmSchedule.nextDailyTriggerMillis(
            hour = 9,
            minute = 15,
            nowMillis = now,
            zoneId = zone
        )

        assertEquals(at(2026, 8, 24, 9, 15), next)
    }

    @Test
    fun `past time schedules for tomorrow`() {
        val now = at(2026, 8, 24, 18, 30)

        val next = CheckInAlarmSchedule.nextDailyTriggerMillis(
            hour = 9,
            minute = 0,
            nowMillis = now,
            zoneId = zone
        )

        assertEquals(at(2026, 8, 25, 9, 0), next)
    }

    @Test
    fun `same instant schedules the next daily occurrence`() {
        val now = at(2026, 8, 24, 12, 0)

        val next = CheckInAlarmSchedule.nextDailyTriggerMillis(
            hour = 12,
            minute = 0,
            nowMillis = now,
            zoneId = zone
        )

        assertEquals(at(2026, 8, 25, 12, 0), next)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
