package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryControlsTest {
    private val contacts = listOf(
        ContactEntry(1, "first", "Renée Smith",
            phones = listOf("+1 (205) 555-0100"), organization = "Zephyr",
            groupIds = setOf(7)),
        ContactEntry(2, "second", "Alex Doe",
            emails = listOf("alex@example.invalid"), organization = "Aster",
            groupIds = setOf(7)),
        ContactEntry(3, "", "Taylor",
            phones = listOf("205-555-0222"), organization = "",
            groupIds = setOf(9)),
        ContactEntry(4, "fourth", "Zoë",
            emails = listOf("zoe@example.invalid"), organization = "Aster")
    )

    private fun select(
        search: String = "", favorites: Set<String> = emptySet(),
        onlyFavorites: Boolean = false, group: Long? = null,
        sort: DirectorySort = DirectorySort.NAME,
        filter: DirectoryFilter = DirectoryFilter.ALL
    ) = DirectoryControls.directory(contacts, search, favorites,
        onlyFavorites, group, sort, filter)

    @Test fun namesAreAccentInsensitiveAndPhoneDigitsIgnoreFormatting() {
        assertTrue(DirectoryControls.matches(contacts[0], "renee"))
        assertTrue(DirectoryControls.matches(contacts[3], "zoe"))
        assertTrue(DirectoryControls.matches(contacts[0], "2055550100"))
        assertTrue(DirectoryControls.matches(contacts[0], "205-555"))
        assertFalse(DirectoryControls.matches(contacts[0], "2055550999"))
        assertFalse(DirectoryControls.matches(contacts[0], "missing"))
    }

    @Test fun searchTermsCanMatchDifferentLocalFields() {
        assertEquals(listOf(1L), select("Renee Zephyr").map { it.id })
        assertEquals(listOf(2L), select("alex example.invalid").map { it.id })
    }

    @Test fun sortByOrganizationKeepsEmptyOrganizationsLast() {
        assertEquals(listOf(2L, 4L, 1L, 3L),
            select(sort = DirectorySort.ORGANIZATION).map { it.id })
    }

    @Test fun favoriteFirstSortIsDeterministicAndNonDestructive() {
        assertEquals(listOf(3L, 4L, 2L, 1L),
            select(favorites = setOf("id:3", "fourth"),
                sort = DirectorySort.FAVORITES_FIRST).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), contacts.map { it.id })
    }

    @Test fun filterAndGroupScopeNeverIncludeOtherContacts() {
        assertEquals(listOf(1L), select(group = 7,
            filter = DirectoryFilter.WITH_PHONE).map { it.id })
        assertEquals(listOf(2L), select(group = 7,
            filter = DirectoryFilter.WITH_EMAIL).map { it.id })
        assertEquals(listOf(3L), select(favorites = setOf("id:3"),
            onlyFavorites = true).map { it.id })
        assertTrue(select(favorites = emptySet(), onlyFavorites = true).isEmpty())
        assertTrue(select(group = 999).isEmpty())
    }

    @Test fun visibleExtendedDetailsSearchButPrivateNotesRequireReveal() {
        val privateContact = ContactEntry(5, "private", "Morgan",
            notes = listOf("private project"), addresses = listOf("Test Square"),
            importantDates = listOf(ImportantDate("Birthday", "--11-09")))
        assertFalse(DirectoryControls.matches(privateContact, "project"))
        assertFalse(DirectoryControls.matches(privateContact, "private"))
        assertTrue(DirectoryControls.matches(privateContact, "morgan"))
        assertTrue(DirectoryControls.matches(privateContact, "birthday"))
        assertTrue(DirectoryControls.matches(privateContact, "square"))
        assertFalse(DirectoryControls.matches(privateContact, "other"))
    }
}
