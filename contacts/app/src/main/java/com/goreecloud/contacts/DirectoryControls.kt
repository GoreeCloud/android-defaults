package com.goreecloud.contacts

import java.text.Normalizer
import java.util.Locale

/** Local-only view controls. Does not persist or transmit any contact fields. */
enum class DirectorySort(val title: String) {
    NAME("Name"), ORGANIZATION("Organization"), FAVORITES_FIRST("Favorites first")
}

enum class DirectoryFilter(val title: String) {
    ALL("All"), WITH_PHONE("With phone"), WITH_EMAIL("With email")
}

object DirectoryControls {
    private val marks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    private fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)

    private fun favoriteKey(contact: ContactEntry) =
        contact.lookupKey.ifBlank { "id:" + contact.id }

    /** Search visible fields only; private note text is reveal-gated, not indexed. */
    fun matches(contact: ContactEntry, search: String): Boolean {
        val words = fold(search.trim()).split(whitespace).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val fields = fold(buildString {
            append(contact.name).append(' ').append(contact.organization)
            contact.phones.forEach { append(' ').append(it) }
            contact.emails.forEach { append(' ').append(it) }
            contact.addresses.forEach { append(' ').append(it) }
            contact.importantDates.forEach {
                append(' ').append(it.label).append(' ').append(it.value)
            }
        })
        val phoneDigits = contact.phones.map { number ->
            number.filter { it.isDigit() }
        }
        return words.all { word ->
            fields.contains(word) || run {
                val number = word.filter { it.isDigit() }
                number.length >= 3 &&
                    word.all { it.isDigit() || it in "+-()." } &&
                    phoneDigits.any { it.contains(number) }
            }
        }
    }

    /** Preserve favorite/group boundaries; sorting never mutates the device provider. */
    fun directory(
        contacts: List<ContactEntry>,
        search: String,
        favoriteKeys: Set<String>,
        onlyFavorites: Boolean,
        groupId: Long?,
        sort: DirectorySort,
        filter: DirectoryFilter
    ): List<ContactEntry> {
        val selected = contacts.filter { contact ->
            (!onlyFavorites || favoriteKey(contact) in favoriteKeys) &&
                (groupId == null || groupId in contact.groupIds) &&
                (when (filter) {
                    DirectoryFilter.ALL -> true
                    DirectoryFilter.WITH_PHONE -> contact.phones.any { it.isNotBlank() }
                    DirectoryFilter.WITH_EMAIL -> contact.emails.any { it.isNotBlank() }
                }) && matches(contact, search)
        }
        return when (sort) {
            DirectorySort.NAME -> selected.sortedWith(compareBy(
                { fold(it.name) }, { it.id }
            ))
            DirectorySort.ORGANIZATION -> selected.sortedWith(compareBy(
                { if (it.organization.isBlank()) 1 else 0 },
                { fold(it.organization) }, { fold(it.name) }, { it.id }
            ))
            DirectorySort.FAVORITES_FIRST -> selected.sortedWith(compareBy(
                { if (favoriteKey(it) in favoriteKeys) 0 else 1 },
                { fold(it.name) }, { it.id }
            ))
        }
    }
}
