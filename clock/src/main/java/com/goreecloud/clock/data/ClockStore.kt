package com.goreecloud.clock.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClockAlarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0,
    val enabled: Boolean = true,
)

object ClockStore {
    private const val FILE = "clock_preferences"
    private const val ALARMS = "alarms"
    private const val TIMER_DEADLINE = "timer_deadline"
    private const val TIMER_REMAINING = "timer_remaining"
    private const val THEME = "theme"
    private const val STOPWATCH_ACCUMULATED = "stopwatch_accumulated"
    private const val STOPWATCH_START = "stopwatch_start"
    private const val UNDELIVERED_ALARM = "undelivered_alarm_notice"
    private const val MISSED_ONE_SHOT = "missed_one_shot_notice"
    private const val ONE_SHOT_DUE_PREFIX = "one_shot_due_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun alarms(context: Context): List<ClockAlarm> {
        val raw = prefs(context).getString(ALARMS, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val hour = json.optInt("hour", -1)
                val minute = json.optInt("minute", -1)
                if (hour !in 0..23 || minute !in 0..59) continue
                add(ClockAlarm(
                    id = json.optLong("id", 0L),
                    hour = hour,
                    minute = minute,
                    repeatDays = json.optInt("repeatDays", 0) and 127,
                    enabled = json.optBoolean("enabled", false),
                ))
            }
        }.filter { it.id != 0L }.distinctBy { it.id }.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    /** A scheduled PendingIntent must never outlive an unwritten alarm record.
     * Commit synchronously so the UI can refuse to change Android scheduling
     * when a disk write fails or the process is interrupted.
     */
    private fun writeAlarms(context: Context, alarms: List<ClockAlarm>): Boolean {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(JSONObject()
                .put("id", alarm.id)
                .put("hour", alarm.hour)
                .put("minute", alarm.minute)
                .put("repeatDays", alarm.repeatDays)
                .put("enabled", alarm.enabled))
        }
        return prefs(context).edit().putString(ALARMS, array.toString()).commit()
    }

    fun upsertAlarm(context: Context, alarm: ClockAlarm): Boolean {
        val current = alarms(context).filterNot { it.id == alarm.id }
        return writeAlarms(context, current + alarm)
    }

    fun removeAlarm(context: Context, id: Long): Boolean {
        if (!writeAlarms(context, alarms(context).filterNot { it.id == id })) return false
        clearOneShotDue(context, id)
        return true
    }

    fun timerDeadline(context: Context): Long = prefs(context).getLong(TIMER_DEADLINE, 0L)
    fun timerRemaining(context: Context): Long = prefs(context).getLong(TIMER_REMAINING, 0L).coerceAtLeast(0L)
    /**
     * A timer receiver runs in a separate, short-lived process lifecycle. A
     * scheduled PendingIntent must not outlive an unwritten timer deadline.
     * Commit both fields together and tell callers when storage refused them.
     */
    fun setTimer(context: Context, deadline: Long, remaining: Long): Boolean =
        prefs(context).edit().putLong(TIMER_DEADLINE, deadline.coerceAtLeast(0L))
            .putLong(TIMER_REMAINING, remaining.coerceAtLeast(0L)).commit()

    /** Minimal local acknowledgement flag, without alarm identifiers or times. */
    fun hasUndeliveredAlarm(context: Context): Boolean =
        prefs(context).getBoolean(UNDELIVERED_ALARM, false)

    /** Synchronous write protects a short-lived broadcast's notice from process exit. */
    fun recordUndeliveredAlarm(context: Context): Boolean =
        prefs(context).edit().putBoolean(UNDELIVERED_ALARM, true).commit()

    fun acknowledgeUndeliveredAlarm(context: Context) {
        prefs(context).edit().remove(UNDELIVERED_ALARM).apply()
    }

    /**
     * Keep the original due instant outside the alarm record so the existing
     * edit/upsert path cannot accidentally replace it with an outdated snapshot.
     * A missing key means a pre-migration alarm; never assume it was missed.
     */
    fun oneShotDue(context: Context, id: Long): Long =
        prefs(context).getLong(ONE_SHOT_DUE_PREFIX + id, 0L).coerceAtLeast(0L)

    fun recordOneShotDue(context: Context, id: Long, whenMillis: Long): Boolean =
        whenMillis > 0L && prefs(context).edit()
            .putLong(ONE_SHOT_DUE_PREFIX + id, whenMillis).commit()

    fun clearOneShotDue(context: Context, id: Long) {
        prefs(context).edit().remove(ONE_SHOT_DUE_PREFIX + id).apply()
    }

    fun hasMissedOneShot(context: Context): Boolean =
        prefs(context).getBoolean(MISSED_ONE_SHOT, false)

    /** Persist before clearing an overdue alarm so its possible miss is visible. */
    fun recordMissedOneShot(context: Context): Boolean =
        prefs(context).edit().putBoolean(MISSED_ONE_SHOT, true).commit()

    fun acknowledgeMissedOneShot(context: Context) {
        prefs(context).edit().remove(MISSED_ONE_SHOT).apply()
    }

    fun theme(context: Context): String = prefs(context).getString(THEME, "system") ?: "system"
    fun setTheme(context: Context, mode: String) {
        if (mode in setOf("system", "light", "dark")) prefs(context).edit().putString(THEME, mode).apply()
    }

    fun stopwatchAccumulated(context: Context): Long =
        prefs(context).getLong(STOPWATCH_ACCUMULATED, 0L).coerceAtLeast(0L)
    fun stopwatchStart(context: Context): Long = prefs(context).getLong(STOPWATCH_START, 0L)
    fun setStopwatch(context: Context, accumulated: Long, startElapsed: Long) {
        prefs(context).edit().putLong(STOPWATCH_ACCUMULATED, accumulated.coerceAtLeast(0L))
            .putLong(STOPWATCH_START, startElapsed.coerceAtLeast(0L)).apply()
    }
}
