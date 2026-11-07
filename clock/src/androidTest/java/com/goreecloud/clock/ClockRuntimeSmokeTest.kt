package com.goreecloud.clock

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goreecloud.clock.alarms.AlarmReceiver
import com.goreecloud.clock.alarms.ClockMath
import com.goreecloud.clock.alarms.RescheduleReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable-emulator smoke coverage; not physical alarm/audibility acceptance. */
@RunWith(AndroidJUnit4::class)
class ClockRuntimeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun floatingNavigationOpensAllFourPages() {
        compose.onNode(hasText("Alarms") and hasClickAction()).assertExists()
        compose.onNodeWithText("NEXT ALARM").assertExists()
        compose.onNode(hasText("Timer") and hasClickAction()).performClick()
        compose.onNodeWithText("Duration").assertExists()
        compose.onNode(hasText("Stopwatch") and hasClickAction()).performClick()
        compose.onNodeWithText("No laps yet").assertExists()
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Appearance").assertExists()
    }

    @Test fun distinct64BitAlarmIdsNeverAliasInAndroidPendingIntentIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val aId = 1L
        val bId = 1L shl 32
        assertEquals(aId.hashCode(), bId.hashCode())
        fun request(id: Long): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java)
                .setAction("com.goreecloud.clock.ALARM")
                .setData(Uri.parse(ClockMath.alarmRequestData(id)))
                .putExtra("alarm_id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val first = request(aId)
        val second = request(bId)
        try {
            assertNotEquals(first, second)
        } finally {
            first.cancel()
            second.cancel()
        }
    }

    @Test fun installedAppHasNoInternetOrExportedReceivers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        assertEquals(PackageManager.PERMISSION_DENIED,
            pm.checkPermission(Manifest.permission.INTERNET, context.packageName))
        val info = pm.getApplicationInfo(context.packageName, 0)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        for (receiver in listOf(AlarmReceiver::class.java, RescheduleReceiver::class.java)) {
            assertFalse(pm.getReceiverInfo(ComponentName(context, receiver), 0).exported)
        }
    }
}
