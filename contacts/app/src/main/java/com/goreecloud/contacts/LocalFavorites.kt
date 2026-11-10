package com.goreecloud.contacts

import android.content.SharedPreferences

/**
 * Private, user-controlled favorites. Never changes Android Contacts STARRED,
 * writes to the Contacts Provider, or includes contact records in storage.
 */
class LocalFavorites(private val preferences: SharedPreferences) {
    companion object {
        fun keyFor(contact: ContactEntry): String =
            contact.lookupKey.ifBlank { "id:" + contact.id }
    }

    fun keys(): Set<String> =
        preferences.getStringSet("keys", emptySet())?.toSet().orEmpty()

    fun contains(contact: ContactEntry): Boolean = keyFor(contact) in keys()

    fun toggle(contact: ContactEntry) {
        val values = keys().toMutableSet()
        if (!values.add(keyFor(contact))) values.remove(keyFor(contact))
        preferences.edit().putStringSet("keys", values).apply()
    }

    /** Explicit privacy erasure. Preserves all Android system contacts. */
    fun clear() {
        preferences.edit().remove("keys").apply()
    }
}
