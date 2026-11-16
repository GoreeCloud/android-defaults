package com.goreecloud.clock.alarms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ClockTimeFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun localizedAmPmAnd24HourLabels() {
        assertEquals("12:05 AM", ClockTimeFormat.alarmTime(0, 5, false, Locale.US))
        assertEquals("12:05 PM", ClockTimeFormat.alarmTime(12, 5, false, Locale.US))
        assertEquals("11:07 PM", ClockTimeFormat.alarmTime(23, 7, false, Locale.US))
        assertEquals("23:07", ClockTimeFormat.alarmTime(23, 7, true, Locale.US))
    }

    @Test fun nextAlarmUsesAndroidHourPreference() {
        val at = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 6, 18, 5)
        }.timeInMillis
        assertEquals("Friday · Nov 6, 6:05 PM",
            ClockTimeFormat.nextAlarm(at, utc, false, Locale.US))
        assertEquals("Friday · Nov 6, 18:05",
            ClockTimeFormat.nextAlarm(at, utc, true, Locale.US))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectInvalidHour() { ClockTimeFormat.alarmTime(24, 0, true, Locale.US) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectInvalidMinute() { ClockTimeFormat.alarmTime(10, 60, false, Locale.US) }
}
