package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DuplicateCandidateReviewTest {
    @Test fun candidateChooserLetsUserInspectEveryBoundedResult() {
        val a = ContactEntry(7, "a", "Alex")
        val b = ContactEntry(8, "b", "A. Example")
        val choices = DuplicateCandidateReview.choices(listOf(a, b))
        assertEquals(listOf(7L, 8L), choices.map { it.contactId })
        assertEquals(listOf("1. Alex", "2. A. Example"), choices.map { it.title })
        assertTrue(DuplicateCandidateReview.heading(listOf(a, b)).contains("2 possible matches"))
    }

    @Test fun chooserDoesNotExposePrivateFields() {
        val contact = ContactEntry(3, "x", "Alice",
            phones = listOf("SECRET-PHONE"), emails = listOf("secret@example.invalid"),
            organization = "SECRET-ORG", notes = listOf("PRIVATE-NOTE"))
        val label = DuplicateCandidateReview.choices(listOf(contact)).single().title
        assertTrue(label.contains("Alice"))
        for (privateText in listOf("SECRET-PHONE", "secret@", "PRIVATE-NOTE", "SECRET-ORG"))
            assertFalse(label.contains(privateText))
    }

    @Test fun namesDoNotInjectExtraLinesIntoChoice() {
        val c = ContactEntry(1, "id", "Test\nAnother contact")
        assertFalse(DuplicateCandidateReview.choices(listOf(c))[0].title.contains('\n'))
    }

    @Test fun largeClustersAreBoundedWithoutChangingSourceList() {
        val originals = (1L..100L).map { ContactEntry(it, "key" + it, "Test " + it) }
        val view = DuplicateCandidateReview.choices(originals)
        assertEquals(20, view.size)
        assertEquals(100, originals.size)
        assertEquals(20L, view.last().contactId)
    }

    @Test fun invalidSelectionBoundsAreRejected() {
        try {
            DuplicateCandidateReview.choices(emptyList(), 0)
            fail("Invalid limit was accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
