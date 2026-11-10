package com.goreecloud.contacts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFavoritesTest {
    @Test fun privateFavoriteKeysToggleAndClearWithoutProviderAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "synthetic_favorite_store_test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = LocalFavorites(prefs)
            val first = ContactEntry(42L, "synthetic-lookup", "Synthetic Alice")
            val second = ContactEntry(43L, "", "Synthetic Bob")
            assertTrue(store.keys().isEmpty())
            store.toggle(first)
            store.toggle(second)
            assertEquals(setOf("synthetic-lookup", "id:43"), store.keys())
            assertTrue(store.contains(first))
            assertTrue(store.contains(second))
            store.toggle(first)
            assertFalse(store.contains(first))
            assertTrue(store.contains(second))
            store.clear()
            assertTrue(store.keys().isEmpty())
            assertFalse(prefs.contains("keys"))
            // Clearing changes only our app-local preference key and never
            // invokes Android Contacts Provider or requests READ_CONTACTS.
        } finally {
            prefs.edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }
}
