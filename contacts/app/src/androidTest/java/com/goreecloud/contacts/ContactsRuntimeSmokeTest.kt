package com.goreecloud.contacts

import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsRuntimeSmokeTest {
    @Test fun activityStartsWithPermissionGateOrLocalDirectory() {
        ActivityScenario.launch(ContactsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val window = activity.findViewById<ViewGroup>(android.R.id.content)
                assertTrue(window.childCount > 0)
                assertTrue(activity.packageName == "com.goreecloud.contacts")
            }
        }
    }
}
