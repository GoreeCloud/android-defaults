package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    @Test fun validUnicodeExportIsBoundedBeforeWrite() {
        val content = listOf(ContactEntry(11L, "synthetic", "Renée ☀",
            listOf("+1 555 0100"), listOf("test@example.invalid")))
        val bytes = ContactLogic.validateExport(content)
        assertEquals(ContactLogic.vCard(content[0]).toByteArray(Charsets.UTF_8).size, bytes)
        assertTrue(bytes > 0)
    }

    @Test fun vCardExportRefusesDirectionalSpoofingInEveryFieldBeforeDocumentWrite() {
        val controls = listOf(0x061C, 0x200E, 0x200F, 0x2028, 0x2029,
            0x202A, 0x202E, 0x2066, 0x2069)
        for (code in controls) {
            val marker = String(Character.toChars(code))
            val cases = listOf(
                ContactEntry(1L, "synthetic", "QA" + marker + "Name"),
                ContactEntry(2L, "synthetic", "QA", phones = listOf("+1205555" + marker + "0100")),
                ContactEntry(3L, "synthetic", "QA", emails = listOf("qa" + marker + "@example.invalid")),
                ContactEntry(4L, "synthetic", "QA", organization = "Company" + marker + "QA")
            )
            for (person in cases) {
                try {
                    ContactLogic.validateExport(listOf(person))
                    fail("Spoofable Unicode must be rejected before selecting a destination")
                } catch (_: IllegalArgumentException) { }
                try {
                    ContactLogic.vCard(person)
                    fail("Spoofable Unicode must be rejected before vCard creation")
                } catch (_: IllegalArgumentException) { }
            }
        }
        val normal = ContactEntry(5L, "safe", "محمد Renée 🪻",
            phones = listOf("+12055550100"), organization = "会社")
        val card = ContactLogic.vCard(normal)
        assertTrue(card.contains("محمد"))
        assertTrue(card.contains("Renée"))
        assertEquals(ContactLogic.validateExport(listOf(normal)), card.toByteArray(Charsets.UTF_8).size)
    }

    @Test fun vCardRejectsEmbeddedControlCharacters() {
        val contact = ContactEntry(2L, "key", "No\u0000Name")
        try {
            ContactLogic.vCard(contact)
            fail("Expected NUL rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun vCardRejectsOversizedFieldBeforeAllocatingExport() {
        val contact = ContactEntry(3L, "key", "x".repeat(4097))
        try {
            ContactLogic.validateExport(listOf(contact))
            fail("Expected field size rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun vCardRejectsExcessiveRepeatedPhoneFields() {
        val contact = ContactEntry(4L, "key", "Synthetic",
            phones = List(101) { "555000$it" })
        try {
            ContactLogic.vCard(contact)
            fail("Expected field count rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun exportRejectsOverlongAggregateBeforeOpeningDocument() {
        val contact = ContactEntry(5L, "key", "Synthetic",
            phones = List(70) { "9".repeat(3000) })
        try {
            ContactLogic.validateExport(listOf(contact))
            fail("Expected per-card bytes rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun exportRejectsTooManyContactsAndEmptySelections() {
        for (contacts in listOf(emptyList(), List(10001) {
            ContactEntry(it.toLong(), "k$it", "Synthetic")
        })) {
            try {
                ContactLogic.validateExport(contacts)
                fail("Expected invalid export count")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun favoriteAndGroupExportScopesExcludeUnselectedContacts() {
        val a = ContactEntry(1, "key-a", "A", groupIds = setOf(10))
        val b = ContactEntry(2, "key-b", "B", groupIds = setOf(11))
        val c = ContactEntry(3, "", "C", groupIds = setOf(10))
        val all = listOf(a, b, c)
        assertEquals(listOf(a, c),
            ContactLogic.favoriteContacts(all, setOf("key-a", "id:3")))
        assertEquals(listOf(a, c), ContactLogic.groupContacts(all, 10))
        assertTrue(ContactLogic.groupContacts(all, 99).isEmpty())
        assertTrue(ContactLogic.favoriteContacts(all, emptySet()).isEmpty())
    }

    @Test fun visibleDetailsRemainSearchableWithoutRevealingPrivateNotes() {
        val entry = ContactEntry(
            id = 33, lookupKey = "synthetic", name = "Taylor",
            addresses = listOf("Memphis, Tennessee"),
            importantDates = listOf(ImportantDate("Birthday", "--11-09")),
            notes = listOf("private research context")
        )
        assertTrue(ContactLogic.matches(entry, "memphis"))
        assertTrue(ContactLogic.matches(entry, "birthday"))
        assertFalse(ContactLogic.matches(entry, "research"))
        assertFalse(ContactLogic.matches(entry, "private"))
        assertTrue(ContactLogic.matches(entry, "taylor"))
        assertFalse(ContactLogic.matches(entry, "nonmatching"))
        val card = ContactLogic.vCard(entry)
        assertFalse(card.contains("private research context"))
        assertFalse(card.contains("Memphis"))
    }

    @Test fun longUnicodeVCardFieldsFoldAtUtf8OctetBoundaryAndRoundTrip() {
        val name = "Renée✨".repeat(15)
        val organization = "公司🙂".repeat(20)
        val contact = ContactEntry(45L, "key", name,
            emails = listOf("person@example.invalid"), organization = organization)
        val card = ContactLogic.vCard(contact)
        assertTrue(card.contains("\r\n "))
        val physicalLines = card.split("\r\n").filter { it.isNotEmpty() }
        assertTrue(physicalLines.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        val parsed = VCardImport.parse(card.toByteArray(Charsets.UTF_8))
        assertEquals(name, parsed.name)
        assertEquals(organization, parsed.organization)
        assertEquals(ContactLogic.validateExport(listOf(contact)),
            card.toByteArray(Charsets.UTF_8).size)
    }

    @Test fun vCardRejectsUnpairedSurrogateInsteadOfReplacingContactText() {
        try {
            ContactLogic.vCard(ContactEntry(67L, "bad", "Invalid\uD800"))
            fail("Expected malformed Unicode rejection")
        } catch (_: IllegalArgumentException) {}
    }


    @Test fun duplicatesDoNotCollapseDifferentInternationalCountryCodes() {
        val a = ContactEntry(100L, "a", "Country A",
            phones = listOf("+44 234 567 89012"))
        val b = ContactEntry(101L, "b", "Country B",
            phones = listOf("+61 234 567 89012"))
        assertTrue(ContactLogic.duplicateClusters(listOf(a, b)).isEmpty())
    }

    @Test fun duplicateMatchingRejectsExtensionAmbiguity() {
        val withExtension = ContactEntry(102L, "ext", "Extension",
            phones = listOf("+1 205 555 0100 ext 12"))
        val noExtension = ContactEntry(103L, "basic", "Basic",
            phones = listOf("(205) 555-0100"))
        assertTrue(ContactLogic.duplicateClusters(listOf(withExtension, noExtension)).isEmpty())
    }

    @Test fun duplicateReviewMatchesNanpEquivalentDialStringsWithoutWriting() {
        val international = ContactEntry(104L, "int", "International",
            phones = listOf("+1 (205) 555-0100"))
        val national = ContactEntry(105L, "nat", "National",
            phones = listOf("2055550100"))
        assertEquals(1, ContactLogic.duplicateClusters(listOf(international, national)).size)
    }


}
