package com.goreecloud.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposable emulator checks only. Never places a real call or requests phone
 * role/permission, so these tests are safe to run without a SIM.
 */
@RunWith(AndroidJUnit4::class)
class DialerRuntimeSmokeTest {
    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) yieldAll(descendants(root.getChildAt(i)))
        }
    }
    private fun button(root: View, title: String): Button =
        descendants(root).filterIsInstance<Button>()
            .first { it.text.toString() == title }

    private fun textExists(root: View, phrase: String): Boolean =
        descendants(root).filterIsInstance<TextView>()
            .any { it.text.toString().contains(phrase) }

    @Test fun keypadAndNavigationAreAvailableWithoutSensitivePermissions() {
        val scenario = ActivityScenario.launch(DialerActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(textExists(root, "Call a number"))
                assertTrue(textExists(root, "Private by default"))
                button(root, "Contacts").performClick()
                assertTrue(textExists(root, "Contacts stay on your device"))
                button(root, "Recents").performClick()
                assertTrue(textExists(root, "Call history is only read"))
                button(root, "Settings").performClick()
                assertTrue(textExists(root, "Phone app"))
                button(root, "Keypad").performClick()
                assertTrue(textExists(root, "Call a number"))
            }
        } finally {
            scenario.close()
        }
    }

    @Test fun telIntentPopulatesKeypadWithoutPlacingCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+14155550123")).apply {
            setClass(context, DialerActivity::class.java)
        }
        val scenario = ActivityScenario.launch<DialerActivity>(intent)
        try {
            scenario.onActivity { activity ->
                val edit = descendants(activity.window.decorView).filterIsInstance<EditText>().first()
                assertEquals("+14155550123", edit.text.toString())
                assertTrue(textExists(activity.window.decorView, "Call a number"))
            }
        } finally {
            scenario.close()
        }
    }

    @Test fun installedPackageHasNoNetworkOrUnnecessaryCallLogWritePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(requested.contains(Manifest.permission.INTERNET))
        assertFalse(requested.contains(Manifest.permission.WRITE_CALL_LOG))
        assertFalse(requested.contains(Manifest.permission.READ_SMS))
    }
    @Test fun favoritesAreLocalReversibleAndNotRawPhoneNumbers() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FavoritesStore(app)
        val testContactId = Long.MAX_VALUE - 7L
        store.remove(testContactId)
        try {
            assertFalse(store.contains(testContactId))
            assertTrue(store.toggle(testContactId))
            assertTrue(store.contains(testContactId))
            assertFalse(store.all().any { "555" in it || "+" in it })
            assertFalse(store.toggle(testContactId))
            assertFalse(store.contains(testContactId))
            assertTrue(store.toggle(testContactId))
            store.remove(testContactId)
            assertFalse(store.contains(testContactId))
        } finally {
            store.remove(testContactId)
        }
    }

    @Test fun captureOfflineEmptyStateScreensForReview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(DialerActivity::class.java)
        try {
            val pages = listOf("keypad", "recents", "contacts", "settings")
            for (page in pages) {
                scenario.onActivity { activity ->
                    if (page != "keypad") {
                        val root = activity.window.decorView
                        val target = page.replaceFirstChar { it.uppercase() }
                        button(root, target).performClick()
                    }
                }
                instrumentation.waitForIdleSync()
                // A blank image on some accelerated emulators is not a
                // telephony test failure. CI records only available evidence.
                val bitmap: Bitmap? = instrumentation.uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    val context = instrumentation.targetContext
                    val folder = context.getExternalFilesDir("screenshots")
                        ?: continue
                    folder.mkdirs()
                    FileOutputStream(File(folder, "$page.png")).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            scenario.close()
        }
    }

    @Test fun activityRecreationRetainsDraftAndSelectedPage() {
        val scenario = ActivityScenario.launch(DialerActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val keypad = descendants(activity.window.decorView)
                    .filterIsInstance<EditText>().first()
                keypad.setText("+1 (202) 555-0132")
                assertEquals("+1 (202) 555-0132", keypad.text.toString())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = descendants(activity.window.decorView)
                    .filterIsInstance<EditText>().first()
                assertEquals("+1 (202) 555-0132", restored.text.toString())
                button(activity.window.decorView, "Contacts").performClick()
                assertTrue(textExists(activity.window.decorView, "Contacts"))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(textExists(activity.window.decorView, "Contacts stay on your device"))
                button(activity.window.decorView, "Keypad").performClick()
                val restored = descendants(activity.window.decorView)
                    .filterIsInstance<EditText>().first()
                assertEquals("+1 (202) 555-0132", restored.text.toString())
            }
        } finally { scenario.close() }
    }

    @Test fun rotationDoesNotReapplyOriginalTelIntentOverDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:5550100")).apply {
            setClass(context, DialerActivity::class.java)
        }
        val scenario = ActivityScenario.launch<DialerActivity>(intent)
        try {
            scenario.onActivity { activity ->
                val keypad = descendants(activity.window.decorView)
                    .filterIsInstance<EditText>().first()
                assertEquals("5550100", keypad.text.toString())
                keypad.setText("5550199")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val keypad = descendants(activity.window.decorView)
                    .filterIsInstance<EditText>().first()
                assertEquals("5550199", keypad.text.toString())
            }
        } finally { scenario.close() }
    }

}
