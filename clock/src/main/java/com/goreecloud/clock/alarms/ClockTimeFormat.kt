package com.goreecloud.clock.alarms

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Localized, time-zone-safe presentation of alarm times for the Clock UI. */
object ClockTimeFormat {
    fun alarmTime(hour: Int, minute: Int, is24Hour: Boolean,
                  locale: Locale = Locale.getDefault()): String {
        require(hour in 0..23 && minute in 0..59)
        // A fixed UTC date prevents DST gaps/folds from changing the user's
        // selected wall-clock time when rendering the row or accessibility label.
        val utc = TimeZone.getTimeZone("UTC")
        val calendar = Calendar.getInstance(utc, locale).apply {
            clear()
            set(2020, Calendar.JANUARY, 1, hour, minute)
        }
        return SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", locale).apply {
            timeZone = utc
        }.format(calendar.time)
    }

    fun nextAlarm(at: Long, zone: TimeZone, is24Hour: Boolean,
                  locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat(
            if (is24Hour) "EEEE · MMM d, HH:mm" else "EEEE · MMM d, h:mm a",
            locale,
        ).apply { timeZone = zone }.format(at)
}
