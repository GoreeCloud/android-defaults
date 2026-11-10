package com.goreecloud.contacts

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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

    @Test fun activityWindowFollowsOptInPreferenceAcrossRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("screen_privacy_local", Context.MODE_PRIVATE)
        val key = "secure_screen_enabled"
        val priorExists = prefs.contains(key)
        val priorValue = prefs.getBoolean(key, false)
        try {
            // Exercise only app-private synthetic preferences, not the Contacts provider.
            assertTrue(prefs.edit().remove(key).commit())
            ActivityScenario.launch(ContactsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse((activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }
                assertTrue(prefs.edit().putBoolean(key, true).commit())
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertTrue((activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }
                assertTrue(prefs.edit().remove(key).commit())
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertFalse((activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE) != 0)
                }
            }
        } finally {
            val editor = prefs.edit()
            if (priorExists) editor.putBoolean(key, priorValue) else editor.remove(key)
            editor.commit()
        }
    }

}
