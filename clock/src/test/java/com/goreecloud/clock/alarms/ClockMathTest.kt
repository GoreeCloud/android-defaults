package com.goreecloud.clock.alarms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClockMathTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test fun sameDayFuture() {
        assertEquals(instant(2026, 11, 6, 7, 30),
            ClockMath.nextAlarmAt(instant(2026, 11, 6, 6, 0), 7, 30, 0, zone))
    }

    @Test fun oncePastRollsToTomorrow() {
        assertEquals(instant(2026, 11, 7, 7, 30),
            ClockMath.nextAlarmAt(instant(2026, 11, 6, 8, 0), 7, 30, 0, zone))
    }

    @Test fun weekendSkipsToMonday() {
        // November 6, 2026 is Friday; Monday is November 9.
        assertEquals(instant(2026, 11, 9, 7, 0),
            ClockMath.nextAlarmAt(instant(2026, 11, 6, 8, 0), 7, 0, 1 shl 0, zone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHoursRejected() {
        ClockMath.nextAlarmAt(0L, 24, 0, 0, zone)
    }
}
