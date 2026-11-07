package com.goreecloud.clock.alarms

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.goreecloud.clock.MainActivity
import com.goreecloud.clock.R
import com.goreecloud.clock.data.ClockAlarm
import com.goreecloud.clock.data.ClockStore

object AlarmEngine {
    private const val ACTION_ALARM = "com.goreecloud.clock.ALARM"
    private const val ACTION_TIMER = "com.goreecloud.clock.TIMER"
    private const val EXTRA_ID = "alarm_id"
    private const val CHANNEL_ID = "clock_alerts"

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun exactAllowed(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    }

    fun ready(context: Context): Boolean = notificationsAllowed(context) && exactAllowed(context)

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarmIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.hashCode(),
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun timerIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, -1,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleAlarm(context: Context, alarm: ClockAlarm): Boolean {
        if (!alarm.enabled) return false
        if (!ready(context)) return false
        val at = ClockMath.nextAlarmAt(System.currentTimeMillis(), alarm.hour, alarm.minute, alarm.repeatDays)
        return try {
            context.getSystemService(AlarmManager::class.java).setAlarmClock(
                AlarmManager.AlarmClockInfo(at, openApp(context)),
                alarmIntent(context, alarm.id),
            )
            true
        } catch (_: SecurityException) { false }
    }

    fun cancelAlarm(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
    }

    fun scheduleTimer(context: Context, deadline: Long): Boolean {
        if (deadline <= System.currentTimeMillis() || !ready(context)) return false
        return try {
            context.getSystemService(AlarmManager::class.java).setAlarmClock(
                AlarmManager.AlarmClockInfo(deadline, openApp(context)),
                timerIntent(context),
            )
            true
        } catch (_: SecurityException) { false }
    }

    fun cancelTimer(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(timerIntent(context))
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Clock alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm and timer completion notifications"
                enableVibration(true)
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notifyAlert(context: Context, title: String, message: String, id: Int) {
        if (!notificationsAllowed(context)) return
        createNotificationChannel(context)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    internal fun isAlarmAction(action: String?) = action == ACTION_ALARM
    internal fun isTimerAction(action: String?) = action == ACTION_TIMER
    internal fun alarmId(intent: Intent) = intent.getLongExtra(EXTRA_ID, 0L)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when {
            AlarmEngine.isAlarmAction(intent.action) -> {
                val id = AlarmEngine.alarmId(intent)
                val alarm = ClockStore.alarms(context).firstOrNull { it.id == id && it.enabled } ?: return
                if (alarm.repeatDays == 0) ClockStore.upsertAlarm(context, alarm.copy(enabled = false))
                else AlarmEngine.scheduleAlarm(context, alarm)
                AlarmEngine.notifyAlert(context, "Alarm", "It's time.", id.hashCode())
            }
            AlarmEngine.isTimerAction(intent.action) -> {
                val deadline = ClockStore.timerDeadline(context)
                if (deadline <= 0L || System.currentTimeMillis() + 1500L < deadline) return
                ClockStore.setTimer(context, 0L, 0L)
                AlarmEngine.notifyAlert(context, "Timer complete", "Your countdown has finished.", -1)
            }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val approvedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        if (intent.action !in approvedActions) return
        ClockStore.alarms(context).filter { it.enabled }.forEach {
            AlarmEngine.scheduleAlarm(context, it)
        }
        val deadline = ClockStore.timerDeadline(context)
        if (deadline > System.currentTimeMillis()) AlarmEngine.scheduleTimer(context, deadline)
        else if (deadline != 0L) ClockStore.setTimer(context, 0L, 0L)
    }
}
