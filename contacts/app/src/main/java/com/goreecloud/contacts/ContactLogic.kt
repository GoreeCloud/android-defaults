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
    val groupIds: Set<Long> = emptySet()
)

data class GroupEntry(val id: Long, val name: String)
data class DirectorySnapshot(val contacts: List<ContactEntry>, val groups: List<GroupEntry>)

/** Pure, offline transformations; no contact information leaves the device. */
object ContactLogic {
    fun matches(contact: ContactEntry, search: String): Boolean {
        val terms = search.trim().lowercase(Locale.ROOT).split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val text = listOf(contact.name, contact.organization)
            .plus(contact.phones).plus(contact.emails)
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

    private fun escapeVCard(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")

    /** Export deliberately includes only fields represented by this MVP snapshot. */
    fun vCard(contact: ContactEntry): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        append("FN:").append(escapeVCard(contact.name)).append("\r\n")
        contact.phones.forEach { append("TEL:").append(escapeVCard(it)).append("\r\n") }
        contact.emails.forEach { append("EMAIL:").append(escapeVCard(it)).append("\r\n") }
        if (contact.organization.isNotBlank()) {
            append("ORG:").append(escapeVCard(contact.organization)).append("\r\n")
        }
        append("END:VCARD\r\n")
    }
}
