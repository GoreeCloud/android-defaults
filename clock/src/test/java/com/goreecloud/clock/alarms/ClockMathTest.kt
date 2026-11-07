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

    @Test fun sundayRepeatCrossesWeekBoundary() {
        val sunday = 1 shl 6
        assertEquals(instant(2026, 11, 8, 7, 45),
            ClockMath.nextAlarmAt(instant(2026, 11, 6, 10, 0), 7, 45, sunday, zone))
    }

    @Test fun exactCurrentMinuteMeansTomorrowForOneShot() {
        assertEquals(instant(2026, 11, 7, 7, 30),
            ClockMath.nextAlarmAt(instant(2026, 11, 6, 7, 30), 7, 30, 0, zone))
    }

    @Test fun everyDayRollsForwardFromLateNight() {
        assertEquals(instant(2026, 11, 7, 0, 15),
            ClockMath.nextAlarmAt(instant(2026, 11, 6, 23, 59), 0, 15, 127, zone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWeekdayMaskRejected() {
        ClockMath.nextAlarmAt(0L, 8, 0, 128, zone)
    }

    @Test fun springForwardUsesNextValidLocalTime() {
        val ny = TimeZone.getTimeZone("America/New_York")
        fun local(month: Int, day: Int, hour: Int, minute: Int) =
            Calendar.getInstance(ny).apply {
                clear()
                set(2026, month - 1, day, hour, minute)
            }.timeInMillis
        // The 02:30 local clock time does not exist on March 8, 2026.
        assertEquals(local(3, 8, 3, 30),
            ClockMath.nextAlarmAt(local(3, 7, 23, 0), 2, 30, 1 shl 6, ny))
    }

    @Test fun leapYearOneShotRollsIntoMarch() {
        assertEquals(instant(2028, 3, 1, 6, 15),
            ClockMath.nextAlarmAt(instant(2028, 2, 29, 8, 0), 6, 15, 0, zone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHoursRejected() {
        ClockMath.nextAlarmAt(0L, 24, 0, 0, zone)
    }
}
