package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactLogicTest {
    private val alex = ContactEntry(1L, "a", "Alex Rivera",
        listOf("+1 (205) 555-0100"), listOf("ALEX@example.com"), "GoreeCloud")
    private val other = ContactEntry(2L, "b", "Alex R.",
        listOf("2055550100"), listOf("other@example.com"))
    private val unrelated = ContactEntry(3L, "c", "Morgan Lee",
        listOf("555-9000"), listOf("morgan@example.com"))

    @Test fun searchMatchesNameCompanyPhoneAndEmail() {
        assertTrue(ContactLogic.matches(alex, "alex"))
        assertTrue(ContactLogic.matches(alex, "goreecloud"))
        assertTrue(ContactLogic.matches(alex, "205"))
        assertTrue(ContactLogic.matches(alex, "example.com"))
        assertFalse(ContactLogic.matches(alex, "unrelated"))
    }

    @Test fun duplicatesAreSuggestionsFromPhonesOrEmails() {
        val groups = ContactLogic.duplicateClusters(listOf(alex, other, unrelated))
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.first().map { it.id }.toSet())
    }

    @Test fun shortSharedNumbersDoNotCreateFalseMatches() {
        val x = ContactEntry(4, "d", "First", listOf("1234"))
        val y = ContactEntry(5, "e", "Second", listOf("1234"))
        assertTrue(ContactLogic.duplicateClusters(listOf(x, y)).isEmpty())
    }

    @Test fun vCardEscapesInjectedLinesAndSpecialCharacters() {
        val item = ContactEntry(1, "x", "Lee\nBEGIN:VCARD", listOf("123;456"),
            listOf("a@example.com"), "A,B")
        val card = ContactLogic.vCard(item)
        assertTrue(card.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(card.contains("FN:Lee\\nBEGIN:VCARD\r\n"))
        assertTrue(card.contains("TEL:123\\;456\r\n"))
        assertTrue(card.contains("ORG:A\\,B\r\n"))
        assertEquals(1, Regex("(?m)^BEGIN:VCARD").findAll(card).count())
    }

    @Test fun duplicateKeysAreCaseInsensitive() {
        val copy = ContactEntry(9L, "y", "Name", emails = listOf("alex@example.com"))
        assertEquals(1, ContactLogic.duplicateClusters(listOf(alex, copy)).size)
    }
}
