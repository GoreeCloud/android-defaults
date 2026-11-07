package com.goreecloud.clock.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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

    @Test fun alarmIdentityDistinguishesCollidingLongHashCodes() {
        // Java Long.hashCode() returns 1 for both of these distinct IDs.
        val first = 1L
        val second = 1L shl 32
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(ClockMath.alarmRequestData(first), ClockMath.alarmRequestData(second))
    }

    @Test fun alarmIdGenerationSkipsMillisecondCollision() {
        assertEquals(1_002L, ClockMath.uniqueAlarmId(1_000L, listOf(1_000L, 1_001L)))
        assertEquals(1L, ClockMath.uniqueAlarmId(0L, emptyList()))
    }

    @Test fun alarmIdGenerationWrapsAfterMaximumLong() {
        assertEquals(2L, ClockMath.uniqueAlarmId(Long.MAX_VALUE, listOf(Long.MAX_VALUE, 1L)))
    }

    @Test fun timerDurationUsesExactEnteredValues() {
        assertEquals(3_723_000L, ClockMath.timerDurationMillis("01", "02", "03"))
        assertEquals(359_999_000L, ClockMath.timerDurationMillis("99", "59", "59"))
        assertEquals(0L, ClockMath.timerDurationMillis("00", "00", "00"))
    }

    @Test fun timerDurationRejectsOutOfRangeFields() {
        assertNull(ClockMath.timerDurationMillis("01", "60", "00"))
        assertNull(ClockMath.timerDurationMillis("01", "00", "60"))
        assertNull(ClockMath.timerDurationMillis("100", "00", "00"))
    }

    @Test fun timerDurationRejectsMalformedFields() {
        assertNull(ClockMath.timerDurationMillis("", "01", "00"))
        assertNull(ClockMath.timerDurationMillis("00", "3x", "00"))
        assertNull(ClockMath.timerDurationMillis("-1", "00", "00"))
    }

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

    @Test fun exactRepeatTimeRollsToNextWeek() {
        // November 9, 2026 is Monday. A same-minute Monday alarm recurs Nov 16.
        assertEquals(instant(2026, 11, 16, 7, 0),
            ClockMath.nextAlarmAt(instant(2026, 11, 9, 7, 0), 7, 0, 1 shl 0, zone))
    }

    @Test fun yearBoundaryOneShot() {
        assertEquals(instant(2027, 1, 1, 6, 45),
            ClockMath.nextAlarmAt(instant(2026, 12, 31, 9, 0), 6, 45, 0, zone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMinutesRejected() {
        ClockMath.nextAlarmAt(0L, 12, 60, 0, zone)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHoursRejected() {
        ClockMath.nextAlarmAt(0L, 24, 0, 0, zone)
    }
}
