package com.goreecloud.contacts

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenProtectionPreferenceTest {
    @Test fun optionIsOffByDefaultAndCanBeEnabledAndClearedWithoutContactAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "synthetic_screen_privacy_test"
        val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val setting = ScreenProtectionPreference(prefs)
            assertFalse(setting.isEnabled())
            setting.setEnabled(true)
            assertTrue(setting.isEnabled())
            assertTrue(prefs.getBoolean("secure_screen_enabled", false))
            setting.setEnabled(false)
            assertFalse(setting.isEnabled())
            assertFalse(prefs.contains("secure_screen_enabled"))
        } finally {
            prefs.edit().clear().commit()
            context.deleteSharedPreferences(file)
        }
    }

    @Test fun activityWindowFollowsExplicitPreferenceOnDisposableEmulator() {
        // Never inspect owner contacts or mutate a user's persisted window mode.
        // This check is exclusively for hosted Android emulators with no
        // Contacts Provider access granted to the development package.
        val hardware = Build.HARDWARE.lowercase()
        assumeTrue("Disposable Android emulator only",
            hardware.contains("ranchu") || hardware.contains("goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("No real Contacts Provider access",
            context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED)
        val prefs = context.getSharedPreferences("screen_privacy_local", Context.MODE_PRIVATE)
        val hadSetting = prefs.contains("secure_screen_enabled")
        val previous = prefs.getBoolean("secure_screen_enabled", false)
        try {
            assertTrue(prefs.edit().remove("secure_screen_enabled").commit())
            ActivityScenario.launch(ContactsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse((activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }
            }
            assertTrue(prefs.edit().putBoolean("secure_screen_enabled", true).commit())
            ActivityScenario.launch(ContactsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue((activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }
            }
            assertTrue(prefs.edit().remove("secure_screen_enabled").commit())
            ActivityScenario.launch(ContactsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse((activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }
            }
        } finally {
            val edit = prefs.edit()
            if (hadSetting) edit.putBoolean("secure_screen_enabled", previous)
            else edit.remove("secure_screen_enabled")
            edit.commit()
        }
    }
}
