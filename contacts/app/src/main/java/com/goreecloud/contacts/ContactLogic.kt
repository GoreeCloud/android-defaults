package com.goreecloud.contacts

import java.util.Locale

/** A snapshot of one Android aggregated contact. Never persist this model. */
data class ContactEntry(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organization: String = "",
    val groupIds: Set<Long> = emptySet(),
    val addresses: List<String> = emptyList(),
    val importantDates: List<ImportantDate> = emptyList(),
    val notes: List<String> = emptyList()
)

data class ImportantDate(val label: String, val value: String)

data class GroupEntry(val id: Long, val name: String)
data class DirectorySnapshot(val contacts: List<ContactEntry>, val groups: List<GroupEntry>)

/** Pure, offline transformations; no contact information leaves the device. */
object ContactLogic {
    /** Explicit scope selection, preserving the local directory order. */
    fun favoriteContacts(contacts: List<ContactEntry>, favoriteKeys: Set<String>):
        List<ContactEntry> = contacts.filter {
        (it.lookupKey.ifBlank { "id:" + it.id }) in favoriteKeys
    }

    fun groupContacts(contacts: List<ContactEntry>, groupId: Long):
        List<ContactEntry> = contacts.filter { groupId in it.groupIds }

    fun matches(contact: ContactEntry, search: String): Boolean {
        val terms = search.trim().lowercase(Locale.ROOT).split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val text = listOf(contact.name, contact.organization)
            .plus(contact.phones).plus(contact.emails)
            .plus(contact.addresses).plus(contact.importantDates.map { it.label + " " + it.value })
            .plus(contact.notes)
            .joinToString(" ").lowercase(Locale.ROOT)
        return terms.all { text.contains(it) }
    }

    /** Suggestions only. Never auto-merge contacts or modify the provider. */
    fun duplicateClusters(contacts: List<ContactEntry>): List<List<ContactEntry>> {
        val parent = IntArray(contacts.size) { it }
        fun rootOf(i: Int): Int {
            var n = i
            while (parent[n] != n) {
                parent[n] = parent[parent[n]]
                n = parent[n]
            }
            return n
        }
        fun join(a: Int, b: Int) {
            val aa = rootOf(a)
            val bb = rootOf(b)
            if (aa != bb) parent[bb] = aa
        }
        val seen = mutableMapOf<String, Int>()
        contacts.forEachIndexed { i, c ->
            val keys = c.emails.asSequence()
                .map { "e:" + it.trim().lowercase(Locale.ROOT) }
                .filter { it.length > 2 }
                .plus(c.phones.asSequence().map { number ->
                    val digits = number.filter { it.isDigit() }
                    if (digits.length >= 10) "p:" + digits.takeLast(10)
                    else if (digits.length >= 7) "p:" + digits else ""
                }.filter { it.isNotEmpty() }).distinct()
            keys.forEach { key ->
                val old = seen.putIfAbsent(key, i)
                if (old != null) join(i, old)
            }
        }
        return contacts.indices.groupBy { rootOf(it) }.values
            .filter { it.size > 1 }
            .map { indices -> indices.map { contacts[it] }.sortedBy { it.name.lowercase(Locale.ROOT) } }
            .sortedBy { it.first().name.lowercase(Locale.ROOT) }
    }

    // These limits apply before writing to a user-selected document provider.
    // An oversized record is rejected in full rather than silently truncated.
    const val MAX_EXPORT_CONTACTS = 10000
    const val MAX_EXPORT_BYTES = 10 * 1024 * 1024
    private const val MAX_CARD_BYTES = 128 * 1024
    private const val MAX_FIELD_CHARS = 4096
    private const val MAX_FIELDS_PER_KIND = 100

    /**
     * Two-pass, memory-bounded validation: inspect every card before opening
     * the destination, then stream the exact same immutable contact snapshot.
     * Returns its expected UTF-8 size. No address book data is logged or sent.
     */
    fun validateExport(contacts: List<ContactEntry>): Int {
        require(contacts.isNotEmpty() && contacts.size <= MAX_EXPORT_CONTACTS) {
            "Export item limit exceeded"
        }
        var total = 0L
        for (contact in contacts) {
            val bytes = vCard(contact).toByteArray(Charsets.UTF_8).size
            require(bytes <= MAX_CARD_BYTES) { "A contact exceeds the vCard size limit" }
            total += bytes
            require(total <= MAX_EXPORT_BYTES) { "vCard export size limit exceeded" }
        }
        return total.toInt()
    }

    private fun escapeVCard(text: String): String {
        require(text.length <= MAX_FIELD_CHARS) { "Contact field size limit exceeded" }
        require(text.none { Character.isISOControl(it) && it != '\r' && it != '\n' }) {
            "Contact field has an invalid control character"
        }
        return text
        .replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")
    }

    /**
     * RFC vCard physical lines have a 75-octet folding limit. Never split
     * UTF-8 multi-byte code points; continuation space counts toward 75.
     */
    private fun StringBuilder.appendFoldedVCardLine(property: String) {
        var used = 0
        var offset = 0
        while (offset < property.length) {
            val point = Character.codePointAt(property, offset)
            require(point !in 0xD800..0xDFFF) { "Invalid Unicode surrogate in contact field" }
            val text = String(Character.toChars(point))
            val bytes = text.toByteArray(Charsets.UTF_8).size
            if (used + bytes > 75) {
                append("\r\n ")
                used = 1
            }
            append(text)
            used += bytes
            offset += Character.charCount(point)
        }
        append("\r\n")
    }

    /** Export deliberately includes only fields represented by this MVP snapshot. */
    fun vCard(contact: ContactEntry): String = buildString {
        require(contact.phones.size <= MAX_FIELDS_PER_KIND && contact.emails.size <= MAX_FIELDS_PER_KIND) {
            "Contact has too many phone or email fields"
        }
        appendFoldedVCardLine("BEGIN:VCARD")
        appendFoldedVCardLine("VERSION:3.0")
        appendFoldedVCardLine("FN:" + escapeVCard(contact.name))
        contact.phones.forEach { appendFoldedVCardLine("TEL:" + escapeVCard(it)) }
        contact.emails.forEach { appendFoldedVCardLine("EMAIL:" + escapeVCard(it)) }
        if (contact.organization.isNotBlank()) {
            appendFoldedVCardLine("ORG:" + escapeVCard(contact.organization))
        }
        appendFoldedVCardLine("END:VCARD")
        require(length <= MAX_CARD_BYTES) { "Contact export exceeds the size budget" }
    }
}
