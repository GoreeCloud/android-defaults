package com.goreecloud.contacts

import android.content.ContentResolver
import android.os.CancellationSignal
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import java.util.Locale

/**
 * Read-only Android Contacts Provider adapter. Queries must run on a worker.
 * It deliberately has no direct write operations or network activity.
 */
class ContactsRepository(private val resolver: ContentResolver) {
    private data class Builder(
        val id: Long,
        val lookupKey: String,
        val name: String,
        val phones: MutableList<String> = mutableListOf(),
        val emails: MutableList<String> = mutableListOf(),
        var organization: String = "",
        val groups: MutableSet<Long> = mutableSetOf()
    ) {
        fun freeze() = ContactEntry(id, lookupKey, name, phones.toList(),
            emails.toList(), organization, groups.toSet())
    }

    fun load(signal: CancellationSignal): DirectorySnapshot {
        val byId = linkedMapOf<Long, Builder>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ), null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC",
            signal
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                val id = cursor.getLong(idCol)
                byId[id] = Builder(id, cursor.getString(keyCol) ?: "",
                    cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: "Unnamed contact")
            }
        }
        if (byId.isEmpty()) return DirectorySnapshot(emptyList(), emptyList())

        resolver.query(CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.NUMBER),
            null, null, null, signal)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.CONTACT_ID)
            val valueCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                val b = byId[cursor.getLong(idCol)] ?: continue
                val value = cursor.getString(valueCol)?.trim().orEmpty()
                if (value.isNotBlank() && !b.phones.contains(value)) b.phones.add(value)
            }
        }

        resolver.query(CommonDataKinds.Email.CONTENT_URI,
            arrayOf(CommonDataKinds.Email.CONTACT_ID, CommonDataKinds.Email.ADDRESS),
            null, null, null, signal)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Email.CONTACT_ID)
            val valueCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                val b = byId[cursor.getLong(idCol)] ?: continue
                val value = cursor.getString(valueCol)?.trim().orEmpty()
                if (value.isNotBlank() && b.emails.none { it.equals(value, true) }) b.emails.add(value)
            }
        }

        resolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, CommonDataKinds.Organization.COMPANY),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.Organization.CONTENT_ITEM_TYPE), null, signal
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val valueCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Organization.COMPANY)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                val b = byId[cursor.getLong(idCol)] ?: continue
                if (b.organization.isBlank()) b.organization = cursor.getString(valueCol).orEmpty()
            }
        }

        val groups = mutableListOf<GroupEntry>()
        resolver.query(ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            ContactsContract.Groups.DELETED + "=0", null,
            ContactsContract.Groups.TITLE + " COLLATE LOCALIZED ASC", signal
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                val name = cursor.getString(nameCol).orEmpty()
                if (name.isNotBlank()) groups.add(GroupEntry(cursor.getLong(idCol), name))
            }
        }

        resolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE), null, signal
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val groupCol = cursor.getColumnIndexOrThrow(CommonDataKinds.GroupMembership.GROUP_ROW_ID)
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                byId[cursor.getLong(idCol)]?.groups?.add(cursor.getLong(groupCol))
            }
        }

        return DirectorySnapshot(
            byId.values.map { it.freeze() }.sortedWith(compareBy(
                { it.name.lowercase(Locale.ROOT) }, { it.id }
            )), groups
        )
    }
}
