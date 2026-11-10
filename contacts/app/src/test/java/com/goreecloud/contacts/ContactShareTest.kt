package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContactShareTest {
    private val person = ContactEntry(
        id = 1, lookupKey = "synthetic", name = "Renee Example",
        phones = listOf("+1 205 555 0100", "+1 205 555 0200"),
        emails = listOf("renee@example.invalid"),
        organization = "private organization",
        addresses = listOf("private address"),
        importantDates = listOf(ImportantDate("Birthday", "--11-09")),
        notes = listOf("private note")
    )

    @Test fun onlyChosenEmailIsSharedWithNoNameOrPrivateFields() {
        val options = ContactShare.choices(person)
        val message = ContactShare.format(options, setOf(3))
        assertEquals("Email: renee@example.invalid", message)
        assertFalse(message.contains("Renee Example"))
        assertFalse(message.contains("205"))
        assertFalse(message.contains("private"))
        assertFalse(message.contains("Birthday"))
    }

    @Test fun selectedNameAndOnePhonePreserveOrdering() {
        val options = ContactShare.choices(person)
        assertEquals("Name: Renee Example\nPhone: +1 205 555 0200",
            ContactShare.format(options, setOf(2, 0)))
    }

    @Test fun noFieldsArePreselectedAndInvalidSelectionsAreRejected() {
        val options = ContactShare.choices(person)
        for (selection in listOf(emptySet(), setOf(-1), setOf(99))) {
            try {
                ContactShare.format(options, selection)
                fail("Expected invalid selection")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun controlCharactersCannotForgeAdditionalShareLabels() {
        val unsafe = ContactEntry(2, "x", "A\nEmail: injected", phones = listOf("123\r\nNote: stolen"))
        val options = ContactShare.choices(unsafe)
        val text = ContactShare.format(options, setOf(0, 1))
        assertFalse(text.contains("\nEmail: injected"))
        assertFalse(text.contains("\nNote: stolen"))
        assertEquals(2, text.split('\n').size)
    }

    @Test fun rejectsBlankOverlongAndInvalidUnicodeFields() {
        for (name in listOf("", "x".repeat(4097), "bad\uD800")) {
            val options = ContactShare.choices(ContactEntry(4, "id", name))
            try {
                ContactShare.format(options, setOf(0))
                fail("Expected invalid contact field")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun supportsMultibyteUnicodeWithoutDroppingCharacters() {
        val contact = ContactEntry(5, "key", "Zoë 🙂", emails = listOf("zoe@example.invalid"))
        val value = ContactShare.format(ContactShare.choices(contact), setOf(0))
        assertEquals("Name: Zoë 🙂", value)
        assertTrue(value.contains("🙂"))
    }
}
