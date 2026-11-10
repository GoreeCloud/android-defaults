package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrivateNotesTest {
    @Test fun hiddenProfileLabelContainsNoPrivateContent() {
        val note = "Do not show on contact profile"
        val text = PrivateNotes.hiddenLabel(listOf(note))
        assertEquals("1 private note hidden", text)
        assertFalse(text.contains(note))
    }

    @Test fun disclosurePreservesTextOnlyOnExplicitCall() {
        val values = listOf("Private meeting detail", "Follow-up for Tuesday")
        assertEquals("2 private notes hidden", PrivateNotes.hiddenLabel(values))
        assertEquals("Private meeting detail\n\nFollow-up for Tuesday",
            PrivateNotes.reveal(values))
    }

    @Test fun emptyOversizedAndTooManyNotesFailClosed() {
        for (notes in listOf(emptyList(), listOf(""), listOf("x".repeat(4097)),
            List(PrivateNotes.MAX_NOTES + 1) { "synthetic" })) {
            try {
                PrivateNotes.reveal(notes)
                fail("Expected invalid or oversized notes")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun unicodeAndMultiLineNotesRemainLiteralInDialog() {
        val v = "日本語 ✨\nmeeting"
        assertTrue(PrivateNotes.reveal(listOf(v)).contains(v))
    }
}
