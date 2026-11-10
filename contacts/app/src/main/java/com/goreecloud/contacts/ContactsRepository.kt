package com.goreecloud.contacts

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import java.util.Locale

/**
 * Read-only Android Contacts Provider adapter. Queries must run on a worker.
 * It deliberately has no direct write operations or network activity.
 */
class ContactsRepository(
    private val resolver: ContentResolver,
    private val maxContactRows: Int = 10000,
    private val maxRelatedRows: Int = 60000,
    private val readCursor: (
        Uri, Array<String>, String?, Array<String>?, String?, CancellationSignal
    ) -> Cursor? = { uri, projection, selection, arguments, sort, signal ->
        resolver.query(uri, projection, selection, arguments, sort, signal)
    }
) {
    init {
        require(maxContactRows > 0 && maxRelatedRows > 0)
    }

    companion object {
        const val MAX_INLINE_FIELD_CHARS = 4096
        const val MAX_CHANNELS_PER_CONTACT = 100
        const val MAX_GROUPS_PER_CONTACT = 256
    }

    private fun checkedValue(value: String?, limit: Int = MAX_INLINE_FIELD_CHARS): String {
        val text = value?.trim().orEmpty()
        if (text.length > limit)
            throw IllegalStateException("Contact detail exceeds the safe read budget")
        return text
    }

    /** A missing cursor is a provider failure, never a verified empty directory. */
    private fun requiredQuery(
        uri: Uri, columns: Array<String>, selection: String?,
        selectionArgs: Array<String>?, sort: String?, signal: CancellationSignal
    ): Cursor {
        signal.throwIfCanceled()
        return readCursor(uri, columns, selection, selectionArgs, sort, signal)
            ?: throw IllegalStateException("Contacts provider did not return a cursor")
    }

    private inline fun eachRow(
        cursor: Cursor, signal: CancellationSignal, maximum: Int, row: () -> Unit
    ) {
        var count = 0
        while (cursor.moveToNext()) {
            signal.throwIfCanceled()
            if (++count > maximum)
                throw IllegalStateException("Contacts provider exceeded the read safety budget")
            row()
        }
        // A provider can cancel while returning end-of-cursor. The last row's
        // earlier check does not authorize publishing the completed snapshot.
        signal.throwIfCanceled()
    }

    private data class Builder(
        val id: Long,
        val lookupKey: String,
        val name: String,
        val phones: MutableList<String> = mutableListOf(),
        val emails: MutableList<String> = mutableListOf(),
        var organization: String = "",
        val groups: MutableSet<Long> = mutableSetOf(),
        val addresses: MutableList<String> = mutableListOf(),
        val importantDates: MutableList<ImportantDate> = mutableListOf(),
        val notes: MutableList<String> = mutableListOf()
    ) {
        fun freeze() = ContactEntry(
            id = id, lookupKey = lookupKey, name = name, phones = phones.toList(),
            emails = emails.toList(), organization = organization,
            groupIds = groups.toSet(), addresses = addresses.toList(),
            importantDates = importantDates.toList(), notes = notes.toList()
        )
    }

    fun load(signal: CancellationSignal): DirectorySnapshot {
        val byId = linkedMapOf<Long, Builder>()
        requiredQuery(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ), null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC",
            signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            eachRow(cursor, signal, maxContactRows) {
                signal.throwIfCanceled()
                val id = cursor.getLong(idCol)
                if (id <= 0L || byId.containsKey(id))
                    throw IllegalStateException("Contacts provider returned an invalid or duplicate ID")
                byId[id] = Builder(id, checkedValue(cursor.getString(keyCol)),
                    checkedValue(cursor.getString(nameCol)).ifEmpty { "Unnamed contact" })
            }
        }
        requiredQuery(CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.NUMBER),
            null, null, null, signal).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.CONTACT_ID)
            val valueCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.NUMBER)
            eachRow(cursor, signal, maxRelatedRows) {
                signal.throwIfCanceled()
                val b = byId[cursor.getLong(idCol)] ?: return@eachRow
                val value = checkedValue(cursor.getString(valueCol))
                if (value.isNotBlank() && !b.phones.contains(value)) {
                    if (b.phones.size >= MAX_CHANNELS_PER_CONTACT)
                        throw IllegalStateException("Too many phone numbers on one contact")
                    b.phones.add(value)
                }
            }
        }

        requiredQuery(CommonDataKinds.Email.CONTENT_URI,
            arrayOf(CommonDataKinds.Email.CONTACT_ID, CommonDataKinds.Email.ADDRESS),
            null, null, null, signal).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Email.CONTACT_ID)
            val valueCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Email.ADDRESS)
            eachRow(cursor, signal, maxRelatedRows) {
                signal.throwIfCanceled()
                val b = byId[cursor.getLong(idCol)] ?: return@eachRow
                val value = checkedValue(cursor.getString(valueCol))
                if (value.isNotBlank() && b.emails.none { it.equals(value, true) }) {
                    if (b.emails.size >= MAX_CHANNELS_PER_CONTACT)
                        throw IllegalStateException("Too many email addresses on one contact")
                    b.emails.add(value)
                }
            }
        }

        requiredQuery(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, CommonDataKinds.Organization.COMPANY),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.Organization.CONTENT_ITEM_TYPE), null, signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val valueCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Organization.COMPANY)
            eachRow(cursor, signal, maxRelatedRows) {
                signal.throwIfCanceled()
                val b = byId[cursor.getLong(idCol)] ?: return@eachRow
                if (b.organization.isBlank()) b.organization = checkedValue(cursor.getString(valueCol))
            }
        }

        val groups = mutableListOf<GroupEntry>()
        requiredQuery(ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            ContactsContract.Groups.DELETED + "=0", null,
            ContactsContract.Groups.TITLE + " COLLATE LOCALIZED ASC", signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
            eachRow(cursor, signal, maxRelatedRows) {
                signal.throwIfCanceled()
                val name = checkedValue(cursor.getString(nameCol))
                if (name.isNotBlank()) groups.add(GroupEntry(cursor.getLong(idCol), name))
            }
        }

        requiredQuery(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE), null, signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val groupCol = cursor.getColumnIndexOrThrow(CommonDataKinds.GroupMembership.GROUP_ROW_ID)
            eachRow(cursor, signal, maxRelatedRows) {
                signal.throwIfCanceled()
                val contact = byId[cursor.getLong(idCol)] ?: return@eachRow
                val groupId = cursor.getLong(groupCol)
                if (groupId !in contact.groups) {
                    if (contact.groups.size >= MAX_GROUPS_PER_CONTACT)
                        throw IllegalStateException("Too many group memberships")
                    contact.groups.add(groupId)
                }
            }
        }

        // Extended Android Data rows remain read-only and memory-bounded.
        requiredQuery(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID,
                CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE), null, signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val addressCol = cursor.getColumnIndexOrThrow(
                CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
            eachRow(cursor, signal, maxRelatedRows) {
                val contact = byId[cursor.getLong(idCol)] ?: return@eachRow
                val address = checkedValue(cursor.getString(addressCol))
                if (address.isNotEmpty() && address !in contact.addresses) {
                    if (contact.addresses.size >= 16)
                        throw IllegalStateException("Too many contact addresses")
                    contact.addresses.add(address)
                }
            }
        }
        requiredQuery(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, CommonDataKinds.Event.START_DATE,
                CommonDataKinds.Event.TYPE, CommonDataKinds.Event.LABEL),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.Event.CONTENT_ITEM_TYPE), null, signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val dateCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Event.START_DATE)
            val typeCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Event.TYPE)
            val labelCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Event.LABEL)
            eachRow(cursor, signal, maxRelatedRows) {
                val contact = byId[cursor.getLong(idCol)] ?: return@eachRow
                val value = checkedValue(cursor.getString(dateCol), 128)
                if (value.isNotEmpty()) {
                    val kind = if (cursor.isNull(typeCol))
                        CommonDataKinds.Event.TYPE_OTHER else cursor.getInt(typeCol)
                    val label = when (kind) {
                        CommonDataKinds.Event.TYPE_BIRTHDAY -> "Birthday"
                        CommonDataKinds.Event.TYPE_ANNIVERSARY -> "Anniversary"
                        CommonDataKinds.BaseTypes.TYPE_CUSTOM ->
                            checkedValue(cursor.getString(labelCol), 128).ifEmpty { "Date" }
                        else -> "Date"
                    }
                    val entry = ImportantDate(label, value)
                    if (entry !in contact.importantDates) {
                        if (contact.importantDates.size >= 16)
                            throw IllegalStateException("Too many contact dates")
                        contact.importantDates.add(entry)
                    }
                }
            }
        }
        requiredQuery(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, CommonDataKinds.Note.NOTE),
            ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(CommonDataKinds.Note.CONTENT_ITEM_TYPE), null, signal
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val noteCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Note.NOTE)
            eachRow(cursor, signal, maxRelatedRows) {
                val contact = byId[cursor.getLong(idCol)] ?: return@eachRow
                val value = checkedValue(cursor.getString(noteCol))
                if (value.isNotEmpty() && value !in contact.notes) {
                    if (contact.notes.size >= 8)
                        throw IllegalStateException("Too many contact notes")
                    contact.notes.add(value)
                }
            }
        }

        // The final provider cursor may cancel during close() even after the
        // last row was successfully mapped. Never publish stale data on cancel.
        signal.throwIfCanceled()
        return DirectorySnapshot(
            byId.values.map { it.freeze() }.sortedWith(compareBy(
                { it.name.lowercase(Locale.ROOT) }, { it.id }
            )), groups
        )
    }
}
