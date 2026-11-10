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

    private fun writeAlarms(context: Context, alarms: List<ClockAlarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(JSONObject()
                .put("id", alarm.id)
                .put("hour", alarm.hour)
                .put("minute", alarm.minute)
                .put("repeatDays", alarm.repeatDays)
                .put("enabled", alarm.enabled))
        }
        prefs(context).edit().putString(ALARMS, array.toString()).apply()
    }

    fun upsertAlarm(context: Context, alarm: ClockAlarm) {
        val current = alarms(context).filterNot { it.id == alarm.id }
        writeAlarms(context, current + alarm)
    }

    fun removeAlarm(context: Context, id: Long) {
        writeAlarms(context, alarms(context).filterNot { it.id == id })
    }

    fun timerDeadline(context: Context): Long = prefs(context).getLong(TIMER_DEADLINE, 0L)
    fun timerRemaining(context: Context): Long = prefs(context).getLong(TIMER_REMAINING, 0L).coerceAtLeast(0L)
    fun setTimer(context: Context, deadline: Long, remaining: Long) {
        prefs(context).edit().putLong(TIMER_DEADLINE, deadline.coerceAtLeast(0L))
            .putLong(TIMER_REMAINING, remaining.coerceAtLeast(0L)).apply()
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
