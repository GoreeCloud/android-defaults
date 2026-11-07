package com.goreecloud.clock.alarms

import java.util.Calendar
import java.util.TimeZone

/** Monday is bit zero; Sunday is bit six. A zero bitmask denotes a one-shot alarm. */
object ClockMath {
    /** Only persisted, positive deadlines qualify as an elapsed countdown. */
    fun timerElapsed(deadline: Long, now: Long): Boolean =
        deadline > 0L && now >= deadline

    /** Full 64-bit identity; requestCode (Int) and Intent extras alone can collide. */
    fun alarmRequestData(id: Long): String = "goreecloud-clock://alarm/$id"

    /** Avoid overwriting an existing alarm if two creations share one clock millisecond. */
    fun uniqueAlarmId(now: Long, existingIds: Collection<Long>): Long {
        val seen = existingIds.toHashSet()
        var candidate = now.coerceAtLeast(1L)
        while (candidate in seen) {
            candidate = if (candidate == Long.MAX_VALUE) 1L else candidate + 1L
        }
        return candidate
    }

    /**
     * Returns null for invalid or incomplete two-digit timer input.
     * Never silently clamp a user-requested duration to a different value.
     */
    fun timerDurationMillis(hours: String, minutes: String, seconds: String): Long? {
        fun component(value: String, max: Int): Long? {
            if (value.isEmpty() || value.length > 2 || !value.all { it in '0'..'9' }) return null
            val parsed = value.toLongOrNull() ?: return null
            return parsed.takeIf { it <= max }
        }
        val h = component(hours, 99) ?: return null
        val m = component(minutes, 59) ?: return null
        val s = component(seconds, 59) ?: return null
        return (h * 3600L + m * 60L + s) * 1000L
    }

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
