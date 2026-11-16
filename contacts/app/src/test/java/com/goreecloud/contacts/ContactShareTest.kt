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

    @Test fun selectedBidiFormattedContactFieldsFailClosedBeforeExternalShare() {
        val controls = listOf(0x061C, 0x200E, 0x200F, 0x202A, 0x202B,
            0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069)
        for (code in controls) {
            val mark = String(Character.toChars(code))
            val examples = listOf(
                ContactEntry(6, "synthetic", "Renee " + mark + "Example"),
                ContactEntry(6, "synthetic", "Safe", phones = listOf("555" + mark + "0100")),
                ContactEntry(6, "synthetic", "Safe", emails = listOf("r" + mark + "@example.invalid"))
            )
            for ((i, contact) in examples.withIndex()) {
                try {
                    ContactShare.format(ContactShare.choices(contact), setOf(if (i == 0) 0 else 1))
                    fail("Selected U+${code.toString(16)} must be refused before share chooser")
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test fun unselectedUnsafeFieldDoesNotLeakOrBlockSelectedSafeDetails() {
        val contact = ContactEntry(7, "synthetic", "Hidden " + 0x202E.toChar() + "name",
            phones = listOf("+1 205 555 0100"),
            emails = listOf("safe@example.invalid"))
        val value = ContactShare.format(ContactShare.choices(contact), setOf(2))
        assertEquals("Email: safe@example.invalid", value)
    }

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
