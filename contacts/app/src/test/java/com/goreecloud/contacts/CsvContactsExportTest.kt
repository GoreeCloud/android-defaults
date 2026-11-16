package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CsvContactsExportTest {
    @Test fun rejectsMalformedUtf16InEveryCsvColumnBeforeDocumentExport() {
        val high = 0xD83D.toChar()
        val low = 0xDE00.toChar()
        val invalid = listOf(
            String(charArrayOf(high)),
            String(charArrayOf(low)),
            String(charArrayOf('A', high, 'B')),
            String(charArrayOf('A', low, 'B')),
            String(charArrayOf(high, high, low))
        )
        for (value in invalid) {
            val cases = listOf(
                ContactEntry(1L, "synthetic", value),
                ContactEntry(1L, "synthetic", "Safe", phones = listOf("5550100", value)),
                ContactEntry(1L, "synthetic", "Safe", emails = listOf("a@example.test", value)),
                ContactEntry(1L, "synthetic", "Safe", organization = value)
            )
            for (person in cases) {
                try {
                    CsvContactsExport.row(person)
                    fail("Malformed UTF-16 contact field must not reach CSV encoding")
                } catch (_: IllegalArgumentException) { }
                try {
                    CsvContactsExport.validate(listOf(person))
                    fail("Preflight must deny a lossy CSV document before SAF opens")
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test fun validSurrogatePairsRemainLosslessInUtf8Export() {
        val emoji = String(Character.toChars(0x1F600))
        val person = ContactEntry(1L, "synthetic", "Name $emoji",
            phones = listOf("5550100"), organization = "Org $emoji")
        val row = CsvContactsExport.row(person)
        assertTrue(row.contains(emoji))
        assertTrue(row.toByteArray(Charsets.UTF_8).toList()
            .containsAll(emoji.toByteArray(Charsets.UTF_8).toList()))
        assertEquals(CsvContactsExport.header.toByteArray(Charsets.UTF_8).size +
            row.toByteArray(Charsets.UTF_8).size, CsvContactsExport.validate(listOf(person)))
    }

    @Test fun utf8HeaderAndQuotedFields() {
        val person = ContactEntry(1, "a", "Renée \"R\"",
            phones = listOf("2055550100", "2055550200"),
            emails = listOf("one@example.invalid"), organization = "Goree,Cloud")
        val row = CsvContactsExport.row(person)
        assertTrue(row.startsWith("\"Renée \"\"R\"\"\","))
        assertTrue(row.contains("\"2055550100 | 2055550200\""))
        assertTrue(row.endsWith("\"Goree,Cloud\"\r\n"))
        val expected = CsvContactsExport.header.toByteArray(Charsets.UTF_8).size +
            row.toByteArray(Charsets.UTF_8).size
        assertEquals(expected, CsvContactsExport.validate(listOf(person)))
    }

    @Test fun formulasAndInternationalNumbersAreTreatedAsLiteralText() {
        for (value in listOf("=2+2", "+12055550100", "-5+2", "@NOW()",
            "  =HYPERLINK(\"https://example.invalid\")",
            "\t=SUM(1,1)", "\u200B=1+1", "\uFEFF+1",
            "\u200C=HYPERLINK(1)", "\u200D+2", "\u200E=3",
            "\u200F-4", "\u2060@NOW()", "\u2066=SUM(1,2)")) {
            val row = CsvContactsExport.row(ContactEntry(1, "x", value))
            assertTrue(row.startsWith("\"'"))
            assertTrue(row.endsWith("\"\r\n") || row.contains("\","))
        }
    }

    @Test fun harmlessFormatPrefixedTextIsNotModified() {
        val row = CsvContactsExport.row(ContactEntry(1, "x", "\u2060Alice"))
        assertTrue(row.startsWith("\"\u2060Alice\""))
    }

    @Test fun rejectsEmbeddedNulAndOverlongCell() {
        for (name in listOf("Bad\u0000Name", "x".repeat(4097))) {
            try {
                CsvContactsExport.row(ContactEntry(2, "x", name))
                fail("Expected rejected field")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun refusesEmptyAndOversizedExports() {
        try {
            CsvContactsExport.validate(emptyList())
            fail("Expected missing contacts")
        } catch (_: IllegalArgumentException) {}
        try {
            CsvContactsExport.validate(List(CsvContactsExport.MAX_ROWS + 1) {
                ContactEntry(it.toLong(), "key$it", "Synthetic")
            })
            fail("Expected row count refusal")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun csvContainsNoUnquotedFormulaCells() {
        val person = ContactEntry(1, "p", "A,B", emails = listOf("=1+1"))
        val row = CsvContactsExport.row(person)
        assertTrue(row.contains("\"'=1+1\""))
        assertFalse(row.contains(",=1+1,"))
    }

    @Test fun preservesEmbeddedLineBreaksInsideQuotedCsvCell() {
        val text = "Multi\nLine"
        val row = CsvContactsExport.row(ContactEntry(2, "p", text))
        assertTrue(row.startsWith("\"Multi\nLine\""))
    }
}
