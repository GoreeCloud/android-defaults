package com.goreecloud.contacts

import android.content.Context
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
}
