package com.goreecloud.dialer

import android.content.Context

/**
 * Optional, strictly local favorite contact IDs.
 *
 * Never stores phone numbers, names, raw contact records or account details.
 * Contacts are only resolved through Android's ContactsProvider after the user
 * grants READ_CONTACTS. Application backup and device transfer are disabled.
 */
class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "goreecloud_dialer_favorites", Context.MODE_PRIVATE
    )

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun contains(contactId: Long): Boolean = contactId > 0 && contactId.toString() in all()

    /** @return whether the contact is now a favorite */
    fun toggle(contactId: Long): Boolean {
        require(contactId > 0L) { "Invalid contact ID" }
        val ids = all().toMutableSet()
        val added = ids.add(contactId.toString())
        if (!added) ids.remove(contactId.toString())
        prefs.edit().putStringSet(KEY, ids).apply()
        return added
    }

    fun remove(contactId: Long) {
        if (contactId <= 0L) return
        val ids = all().toMutableSet()
        if (ids.remove(contactId.toString())) prefs.edit().putStringSet(KEY, ids).apply()
    }

    fun clear() { prefs.edit().remove(KEY).apply() }

    private companion object { const val KEY = "contact_ids" }
}
