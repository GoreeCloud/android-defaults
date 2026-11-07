package com.goreecloud.clock.alarms

import java.util.Calendar
import java.util.TimeZone

/** Monday is bit zero; Sunday is bit six. A zero bitmask denotes a one-shot alarm. */
object ClockMath {
    fun nextAlarmAt(now: Long, hour: Int, minute: Int, repeatDays: Int,
                    zone: TimeZone = TimeZone.getDefault()): Long {
        require(hour in 0..23 && minute in 0..59)
        require(repeatDays in 0..127)
        for (offset in 0..7) {
            val candidate = Calendar.getInstance(zone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val mondayIndex = (candidate.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (candidate.timeInMillis > now &&
                (repeatDays == 0 || repeatDays and (1 shl mondayIndex) != 0)) {
                return candidate.timeInMillis
            }
        }
        error("No future occurrence in the next eight days")
    }
}
