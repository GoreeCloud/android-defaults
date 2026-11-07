package com.goreecloud.clock

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import com.goreecloud.clock.alarms.AlarmEngine
import com.goreecloud.clock.alarms.ClockMath
import com.goreecloud.clock.data.ClockAlarm
import com.goreecloud.clock.data.ClockStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)
    private var activityStarted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmEngine.createNotificationChannel(this)
        setContent { ClockApp(refreshTick, activityStarted) }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
    }

    override fun onStop() {
        activityStarted = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        AlarmEngine.reconcileSchedules(this)
        refreshTick++
    }
}

private enum class ClockTab(val label: String, val icon: ImageVector) {
    ALARMS("Alarms", Icons.Outlined.Alarm),
    TIMER("Timer", Icons.Outlined.Timer),
    STOPWATCH("Stopwatch", Icons.Outlined.Timelapse),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

private val gcLight = lightColorScheme(
    primary = Color(0xFF5145D9), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E6FF), onPrimaryContainer = Color(0xFF2C227C),
    background = Color(0xFFECF0F8), onBackground = Color(0xFF20243A),
    surface = Color(0xFFF9FAFD), onSurface = Color(0xFF20243A),
    surfaceVariant = Color(0xFFE5EBF6), onSurfaceVariant = Color(0xFF5F657B),
    outline = Color(0xFF8990A7),
)
private val gcDark = darkColorScheme(
    primary = Color(0xFFC5BFFF), onPrimary = Color(0xFF241B66),
    primaryContainer = Color(0xFF39316B), onPrimaryContainer = Color(0xFFEBE8FF),
    background = Color(0xFF10131E), onBackground = Color(0xFFF2F2FA),
    surface = Color(0xFF1D2231), onSurface = Color(0xFFF2F2FA),
    surfaceVariant = Color(0xFF292F40), onSurfaceVariant = Color(0xFFB3B9CF),
    outline = Color(0xFF747C99),
)

@Composable
private fun ClockApp(refreshToken: Int, activityStarted: Boolean) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ClockTab.ALARMS) }
    var alarms by remember { mutableStateOf(ClockStore.alarms(context)) }
    var themeMode by remember { mutableStateOf(ClockStore.theme(context)) }
    var notificationsReady by remember { mutableStateOf(AlarmEngine.notificationsAllowed(context)) }
    var exactReady by remember { mutableStateOf(AlarmEngine.exactAllowed(context)) }
    var deadline by remember { mutableLongStateOf(ClockStore.timerDeadline(context)) }
    var pausedRemaining by remember { mutableLongStateOf(ClockStore.timerRemaining(context)) }
    var stopwatchAccumulated by remember { mutableLongStateOf(ClockStore.stopwatchAccumulated(context)) }
    var stopwatchStart by remember { mutableLongStateOf(ClockStore.stopwatchStart(context)) }
    var laps by remember { mutableStateOf(emptyList<Long>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editingRepeat by remember { mutableStateOf<ClockAlarm?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Compose survives activity stops. Update only while visible; stopwatch gets the
    // faster foreground tick, while all other tabs update once per second.
    LaunchedEffect(activityStarted, tab, stopwatchStart) {
        if (activityStarted) {
            while (true) {
                now = System.currentTimeMillis()
                elapsedNow = SystemClock.elapsedRealtime()
                delay(if (tab == ClockTab.STOPWATCH && stopwatchStart > 0L) 50L else 1000L)
            }
        }
    }
    // Keep the foreground countdown in sync with its persisted broadcast completion.
    LaunchedEffect(now / 1000L, deadline) {
        if (deadline > 0L && now >= deadline && ClockStore.timerDeadline(context) == 0L) {
            deadline = 0L
            pausedRemaining = 0L
        }
    }
    LaunchedEffect(refreshToken) {
        alarms = ClockStore.alarms(context)
        notificationsReady = AlarmEngine.notificationsAllowed(context)
        exactReady = AlarmEngine.exactAllowed(context)
        deadline = ClockStore.timerDeadline(context)
        pausedRemaining = ClockStore.timerRemaining(context)
        themeMode = ClockStore.theme(context)
        stopwatchAccumulated = ClockStore.stopwatchAccumulated(context)
        stopwatchStart = ClockStore.stopwatchStart(context)
        if (stopwatchStart > SystemClock.elapsedRealtime()) {
            stopwatchStart = 0L
            ClockStore.setStopwatch(context, stopwatchAccumulated, 0L)
        }
    }

    fun updateAlarm(alarm: ClockAlarm) {
        AlarmEngine.cancelAlarm(context, alarm.id)
        val scheduled = !alarm.enabled || AlarmEngine.scheduleAlarm(context, alarm)
        val saved = if (scheduled) alarm else alarm.copy(enabled = false)
        ClockStore.upsertAlarm(context, saved)
        alarms = ClockStore.alarms(context)
        if (!scheduled) notice = "Enable notifications and exact alarms in Settings before turning on alarms."
    }

    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // System bars follow the same verified GC UI appearance as the app canvas.
    SideEffect {
        val window = (context as? ComponentActivity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT < 35) {
                val color = if (dark) android.graphics.Color.rgb(16, 19, 30)
                    else android.graphics.Color.rgb(236, 240, 248)
                window.statusBarColor = color
                window.navigationBarColor = color
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) gcDark else gcLight) {
        val scheme = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(scheme.background)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GOREECLOUD  /  UTILITIES", fontSize = 11.sp, letterSpacing = 2.sp,
                            color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Text("Clock", style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold, color = scheme.onBackground)
                        Text(
                            when (tab) {
                                ClockTab.ALARMS -> "Your day, right on time."
                                ClockTab.TIMER -> "Make every second count."
                                ClockTab.STOPWATCH -> "A moment worth measuring."
                                ClockTab.SETTINGS -> "Make it your own."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = CircleShape, color = scheme.primaryContainer) {
                        Icon(Icons.Outlined.Alarm, "Clock", Modifier.padding(15.dp).size(27.dp),
                            tint = scheme.onPrimaryContainer)
                    }
                }
                if (notice != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = scheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(notice ?: "", Modifier.weight(1f), color = scheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { notice = null }) { Text("Dismiss") }
                        }
                    }
                }
                when (tab) {
                    ClockTab.ALARMS -> AlarmPage(
                        alarms = alarms, now = now,
                        canAlert = notificationsReady && exactReady,
                        onOpenSettings = { tab = ClockTab.SETTINGS },
                        onAdd = {
                            val time = Calendar.getInstance()
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    if (alarms.size >= 100) {
                                        notice = "You can save up to 100 alarms."
                                    } else {
                                        updateAlarm(ClockAlarm(System.currentTimeMillis(), hour, minute))
                                    }
                                },
                                time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE),
                                DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        onEditTime = { alarm ->
                            TimePickerDialog(
                                context, { _, hour, minute ->
                                    updateAlarm(alarm.copy(hour = hour, minute = minute))
                                },
                                alarm.hour, alarm.minute, DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        onToggle = { alarm, enabled -> updateAlarm(alarm.copy(enabled = enabled)) },
                        onEditDays = { editingRepeat = it },
                        onDelete = {
                            AlarmEngine.cancelAlarm(context, it.id)
                            ClockStore.removeAlarm(context, it.id)
                            alarms = ClockStore.alarms(context)
                        },
                    )
                    ClockTab.TIMER -> TimerPage(
                        now = now, deadline = deadline, pausedRemaining = pausedRemaining,
                        onStart = { duration ->
                            val at = System.currentTimeMillis() + duration
                            if (duration <= 0L || at <= System.currentTimeMillis()) {
                                notice = "Enter a countdown longer than zero."
                            } else if (!AlarmEngine.scheduleTimer(context, at)) {
                                notice = "Enable notifications and exact alarms in Settings to start a timer."
                            } else {
                                ClockStore.setTimer(context, at, 0L)
                                deadline = at
                                pausedRemaining = 0L
                            }
                        },
                        onPause = {
                            pausedRemaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                            AlarmEngine.cancelTimer(context)
                            deadline = 0L
                            ClockStore.setTimer(context, 0L, pausedRemaining)
                        },
                        onReset = {
                            AlarmEngine.cancelTimer(context)
                            ClockStore.setTimer(context, 0L, 0L)
                            deadline = 0L
                            pausedRemaining = 0L
                        },
                    )
                    ClockTab.STOPWATCH -> StopwatchPage(
                        elapsed = stopwatchAccumulated +
                            if (stopwatchStart > 0L) (elapsedNow - stopwatchStart).coerceAtLeast(0L) else 0L,
                        running = stopwatchStart > 0L,
                        laps = laps,
                        onStartPause = {
                            if (stopwatchStart == 0L) stopwatchStart = SystemClock.elapsedRealtime()
                            else {
                                stopwatchAccumulated += (SystemClock.elapsedRealtime() - stopwatchStart).coerceAtLeast(0L)
                                stopwatchStart = 0L
                            }
                            ClockStore.setStopwatch(context, stopwatchAccumulated, stopwatchStart)
                        },
                        onLap = {
                            laps = laps + (stopwatchAccumulated +
                                (SystemClock.elapsedRealtime() - stopwatchStart).coerceAtLeast(0L))
                        },
                        onReset = {
                            stopwatchStart = 0L
                            stopwatchAccumulated = 0L
                            laps = emptyList()
                            ClockStore.setStopwatch(context, 0L, 0L)
                        },
                    )
                    ClockTab.SETTINGS -> SettingsPage(
                        theme = themeMode,
                        onTheme = {
                            themeMode = it
                            ClockStore.setTheme(context, it)
                        },
                        notificationsAllowed = notificationsReady,
                        exactAllowed = exactReady,
                        onPermissionsChanged = {
                            notificationsReady = AlarmEngine.notificationsAllowed(context)
                            exactReady = AlarmEngine.exactAllowed(context)
                        },
                    )
                }
            }
            FloatingDock(
                selected = tab,
                onSelect = { tab = it; notice = null },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
        val edited = editingRepeat
        if (edited != null) {
            RepeatEditor(edited, onDismiss = { editingRepeat = null }, onSave = { days ->
                updateAlarm(edited.copy(repeatDays = days))
                editingRepeat = null
            })
        }
    }
}

@Composable
private fun FloatingDock(selected: ClockTab, onSelect: (ClockTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        color = scheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.22f)),
        shadowElevation = 16.dp,
    ) {
        Row(Modifier.padding(7.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ClockTab.entries.forEach { item ->
                val active = item == selected
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 61.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (active) scheme.primaryContainer else Color.Transparent)
                        .selectable(selected = active, role = Role.Tab) { onSelect(item) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(23.dp),
                        tint = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, fontSize = 12.sp, maxLines = 1,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 2.dp,
    ) { Box(Modifier.padding(20.dp)) { content() } }
}

@Composable
private fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlarmPage(
    alarms: List<ClockAlarm>,
    now: Long,
    canAlert: Boolean,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onEditTime: (ClockAlarm) -> Unit,
    onToggle: (ClockAlarm, Boolean) -> Unit,
    onEditDays: (ClockAlarm) -> Unit,
    onDelete: (ClockAlarm) -> Unit,
) {
    val context = LocalContext.current
    val currentZone = java.util.TimeZone.getDefault()
    val next = remember(alarms, now / 60000L, currentZone.id) {
        alarms.filter { it.enabled }.map {
            ClockMath.nextAlarmAt(now, it.hour, it.minute, it.repeatDays, currentZone)
        }.minOrNull()
    }
    val dateFormat = remember(context, currentZone.id) {
        SimpleDateFormat("EEEE · MMM d, h:mm a", Locale.getDefault()).apply {
            timeZone = currentZone
        }
    }
    if (!canAlert && alarms.any { it.enabled }) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Alarm alerts need attention", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                Text("Some alarms are enabled but cannot reliably notify. Check Android notification and exact-alarm access.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onOpenSettings) { Text("Review settings") }
            }
        }
    }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NEXT ALARM", fontSize = 11.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (next != null) dateFormat.format(Date(next)) else "Nothing scheduled",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(if (next == null) "Add your first alarm below."
                else "Ready when you need it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SectionHeading("Alarms", "Tap repeat to choose weekdays.") }
        Button(onClick = onAdd, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add")
        }
    }
    if (alarms.isEmpty()) {
        Panel {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("A fresh start", fontWeight = FontWeight.Bold)
                Text("Your alarms will live here. Create one to get started.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                OutlinedButton(onClick = onAdd) { Text("Create an alarm") }
            }
        }
    } else alarms.forEach { alarm ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val format = if (DateFormat.is24HourFormat(context)) "%02d:%02d" else "%d:%02d %s"
                    val time = if (DateFormat.is24HourFormat(context))
                        String.format(Locale.getDefault(), format, alarm.hour, alarm.minute)
                    else String.format(Locale.getDefault(), format, (alarm.hour + 11) % 12 + 1,
                        alarm.minute, if (alarm.hour < 12) "AM" else "PM")
                    Text(time, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(if (alarm.repeatDays == 0) "Once" else weekdaysLabel(alarm.repeatDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEditTime(alarm) }) {
                            Icon(Icons.Outlined.Alarm, contentDescription = "Change alarm time")
                        }
                        IconButton(onClick = { onEditDays(alarm) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit repeat days")
                        }
                        IconButton(onClick = { onDelete(alarm) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete alarm")
                        }
                    }
                }
                Switch(checked = alarm.enabled, onCheckedChange = { onToggle(alarm, it) })
            }
        }
    }
}

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private fun weekdaysLabel(mask: Int): String =
    if (mask == 127) "Every day" else weekdays.filterIndexed { index, _ ->
        mask and (1 shl index) != 0
    }.joinToString(" · ")

@Composable
private fun RepeatEditor(alarm: ClockAlarm, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var mask by remember(alarm.id) { mutableIntStateOf(alarm.repeatDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose days, or leave all off for a one-time alarm.",
                    style = MaterialTheme.typography.bodyMedium)
                weekdays.chunked(4).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEachIndexed { index, name ->
                            val dayIndex = rowIndex * 4 + index
                            val selected = mask and (1 shl dayIndex) != 0
                            FilterChip(
                                selected = selected,
                                onClick = { mask = mask xor (1 shl dayIndex) },
                                label = { Text(name.take(2), fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(mask) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimerPage(
    now: Long,
    deadline: Long,
    pausedRemaining: Long,
    onStart: (Long) -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    var hours by rememberSaveable { mutableStateOf("00") }
    var minutes by rememberSaveable { mutableStateOf("05") }
    var seconds by rememberSaveable { mutableStateOf("00") }
    val remaining = if (deadline > 0L) (deadline - now).coerceAtLeast(0L) else pausedRemaining
    val running = deadline > now
    val hasSession = deadline > 0L || pausedRemaining > 0L
    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (running) "COUNTING DOWN" else if (hasSession) "TIMER PAUSED OR FINISHED"
                    else "READY WHEN YOU ARE",
                fontSize = 11.sp, letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatDuration(remaining),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 52.sp, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(if (running) "Your timer continues in the background."
                else "Set a duration and press Start.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
    if (!hasSession) {
        SectionHeading("Duration", "Hours, minutes and seconds")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("Hours", hours, { value: String -> hours = value }),
                Triple("Min", minutes, { value: String -> minutes = value }),
                Triple("Sec", seconds, { value: String -> seconds = value })).forEach { (label, value, change) ->
                OutlinedTextField(
                    value = value, onValueChange = { text ->
                        if (text.length <= 2 && text.all { it.isDigit() }) change(text)
                    },
                    label = { Text(label) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (running) {
            Button(onClick = onPause, Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Pause, null)
                Spacer(Modifier.size(8.dp))
                Text("Pause")
            }
        } else {
            Button(
                onClick = {
                    val duration = if (pausedRemaining > 0L) pausedRemaining else {
                        val h = hours.toLongOrNull()?.coerceIn(0L, 99L) ?: 0L
                        val m = minutes.toLongOrNull()?.coerceIn(0L, 59L) ?: 0L
                        val s = seconds.toLongOrNull()?.coerceIn(0L, 59L) ?: 0L
                        (h * 3600L + m * 60L + s) * 1000L
                    }
                    onStart(duration)
                },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.size(8.dp))
                Text(if (pausedRemaining > 0L) "Resume" else "Start")
            }
        }
        OutlinedButton(onClick = onReset, Modifier.weight(1f).heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.Refresh, null)
            Spacer(Modifier.size(8.dp))
            Text("Reset")
        }
    }
    Panel {
        Text("The countdown is scheduled with Android's alarm service, so it can notify you after leaving Clock.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StopwatchPage(
    elapsed: Long,
    running: Boolean,
    laps: List<Long>,
    onStartPause: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (running) "MEASURING TIME" else "STOPWATCH", fontSize = 11.sp, letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatStopwatch(elapsed), style = MaterialTheme.typography.displayLarge,
                fontSize = 53.sp, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center)
            Text(if (running) "Time is moving. Capture a lap any moment."
                else "Start whenever you're ready.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onStartPause, Modifier.weight(1f).heightIn(min = 52.dp)) {
            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.size(8.dp))
            Text(if (running) "Pause" else if (elapsed > 0L) "Resume" else "Start")
        }
        OutlinedButton(
            onClick = if (running) onLap else onReset,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(if (running) Icons.Filled.Add else Icons.Outlined.Refresh, null)
            Spacer(Modifier.size(8.dp))
            Text(if (running) "Lap" else "Reset")
        }
    }
    SectionHeading("Laps", if (laps.isEmpty()) "Your lap splits will appear here." else "Recorded in this session")
    if (laps.isEmpty()) {
        Panel { Text("No laps yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                laps.reversed().forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lap " + (laps.size - index), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatStopwatch(value), fontWeight = FontWeight.SemiBold)
                    }
                    if (index != laps.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    theme: String, onTheme: (String) -> Unit,
    notificationsAllowed: Boolean, exactAllowed: Boolean,
    onPermissionsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val requestNotification = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onPermissionsChanged() }
    SectionHeading("Appearance", "Pick the feel that fits your day.")
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, name) ->
                    FilterChip(selected = key == theme, onClick = { onTheme(key) },
                        label = { Text(name) })
                }
            }
            Text("Uses the GoreeCloud UI light and dark palettes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
    SectionHeading("Alerts & permissions", "Android controls precise timing and notifications.")
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PermissionRow(
                "Notifications", notificationsAllowed,
                if (notificationsAllowed) "Allowed" else "Permission needed"
            ) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            HorizontalDivider()
            PermissionRow("Exact alarms", exactAllowed,
                if (exactAllowed) "Allowed" else "Access needed") {
                if (Build.VERSION.SDK_INT >= 31) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + context.packageName))
                    runCatching { context.startActivity(intent) }.onFailure {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + context.packageName)))
                    }
                }
            }
        }
    }
    SectionHeading("About")
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("GoreeCloud Clock", fontWeight = FontWeight.Bold)
            Text("Development preview · v0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Private by design. Stored locally on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRow(title: String, allowed: Boolean, status: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = if (allowed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) { Text(if (allowed) "Manage" else "Allow") }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val total = milliseconds.coerceAtLeast(0L) / 1000L
    return String.format(Locale.getDefault(), "%02d:%02d:%02d",
        total / 3600L, (total / 60L) % 60L, total % 60L)
}

private fun formatStopwatch(milliseconds: Long): String {
    val centiseconds = milliseconds.coerceAtLeast(0L) / 10L
    return String.format(Locale.getDefault(), "%02d:%02d.%02d",
        centiseconds / 6000L, (centiseconds / 100L) % 60L, centiseconds % 100L)
}
