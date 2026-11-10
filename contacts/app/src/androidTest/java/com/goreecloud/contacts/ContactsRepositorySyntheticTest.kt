package com.goreecloud.contacts

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Entirely synthetic Android Cursor fixtures. This suite never queries real
 * contacts, accounts or personal data and never modifies the Contacts Provider.
 * Values and URI routing are test-owned in-memory objects only.
 */
@RunWith(AndroidJUnit4::class)
class ContactsRepositorySyntheticTest {
    private fun key(uri: Uri, arguments: Array<String>?): String = when (uri) {
        ContactsContract.Contacts.CONTENT_URI -> "contacts"
        CommonDataKinds.Phone.CONTENT_URI -> "phones"
        CommonDataKinds.Email.CONTENT_URI -> "emails"
        ContactsContract.Groups.CONTENT_URI -> "groups"
        ContactsContract.Data.CONTENT_URI ->
            if (arguments?.contains(CommonDataKinds.Organization.CONTENT_ITEM_TYPE) == true)
                "organizations"
            else "memberships"
        else -> throw IllegalArgumentException("Unexpected URI in synthetic test")
    }

    private fun repository(
        rows: Map<String, List<Array<Any?>>> = emptyMap(),
        nullAt: String? = null,
        throwAt: String? = null,
        maxContacts: Int = 10000,
        maxRelated: Int = 60000,
        malformedAt: String? = null
    ): ContactsRepository {
        val fake: (
            Uri, Array<String>, String?, Array<String>?, String?, CancellationSignal
        ) -> Cursor? = { uri, projection, _, args, _, signal ->
            val target = key(uri, args)
            signal.throwIfCanceled()
            if (target == throwAt) throw IllegalStateException("synthetic provider failure")
            if (target == nullAt) null
            else {
                val columns = if (target == malformedAt) arrayOf("missing_column") else projection
                MatrixCursor(columns).apply {
                    rows[target].orEmpty().forEach { addRow(it) }
                }
            }
        }
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        return ContactsRepository(resolver, maxContacts, maxRelated, fake)
    }

    private fun fails(action: () -> Unit) {
        try {
            action()
            fail("Expected provider read refusal")
        } catch (_: IllegalStateException) {
            // Rejected snapshots must not be mistaken for an empty address book.
        } catch (_: IllegalArgumentException) {
            // Missing mandatory columns also fail closed.
        }
    }

    @Test fun completeEmptyDirectoryIsValid() {
        val snap = repository().load(CancellationSignal())
        assertTrue(snap.contacts.isEmpty())
        assertTrue(snap.groups.isEmpty())
    }

    @Test fun emptyContactsStillReturnsValidGroups() {
        val rows: Map<String, List<Array<Any?>>> =
            mapOf("groups" to listOf(arrayOf<Any?>(9L, "Empty circle")))
        val snap = repository(rows).load(CancellationSignal())
        assertTrue(snap.contacts.isEmpty())
        assertEquals(listOf(GroupEntry(9, "Empty circle")), snap.groups)
    }

    @Test fun syntheticDirectoryJoinsFieldsAndDeduplicatesEntries() {
        val rows: Map<String, List<Array<Any?>>> = mapOf(
            "contacts" to listOf(arrayOf(22L, "lookup-z", "Zeta"),
                arrayOf(11L, "lookup-a", "Alpha")),
            "phones" to listOf(arrayOf(11L, "555-0100"), arrayOf(11L, "555-0100"),
                arrayOf(22L, "555-0200")),
            "emails" to listOf(arrayOf(11L, "a@example.invalid"),
                arrayOf(11L, "A@EXAMPLE.INVALID")),
            "organizations" to listOf(arrayOf(11L, "Synthetic Co.")),
            "groups" to listOf(arrayOf(4L, "Friends")),
            "memberships" to listOf(arrayOf(11L, 4L))
        )
        val snap = repository(rows).load(CancellationSignal())
        assertEquals(listOf("Alpha", "Zeta"), snap.contacts.map { it.name })
        assertEquals("lookup-a", snap.contacts[0].lookupKey)
        assertEquals(listOf("555-0100"), snap.contacts[0].phones)
        assertEquals(listOf("a@example.invalid"), snap.contacts[0].emails)
        assertEquals("Synthetic Co.", snap.contacts[0].organization)
        assertEquals(setOf(4L), snap.contacts[0].groupIds)
        assertEquals(listOf(GroupEntry(4L, "Friends")), snap.groups)
    }

    @Test fun nullPrimaryCursorMustNotLookLikeEmptyDirectory() {
        fails { repository(nullAt = "contacts").load(CancellationSignal()) }
    }

    @Test fun nullRelatedCursorMustNotPublishIncompleteSnapshot() {
        fails {
            repository(mapOf("contacts" to listOf(arrayOf(11L, "key-a", "A"))),
                nullAt = "phones").load(CancellationSignal())
        }
    }

    @Test fun providerExceptionAfterContactReadDiscardsEntireResult() {
        fails {
            repository(mapOf("contacts" to listOf(arrayOf(11L, "key-a", "A"))),
                throwAt = "emails").load(CancellationSignal())
        }
    }

    @Test fun malformedRequiredColumnIsRejected() {
        fails { repository(malformedAt = "contacts").load(CancellationSignal()) }
    }

    @Test fun contactCountBudgetRejectsTruncation() {
        fails {
            repository(
                mapOf("contacts" to listOf(arrayOf(11L, "k1", "A"),
                    arrayOf(12L, "k2", "B"))),
                maxContacts = 1
            ).load(CancellationSignal())
        }
    }

    @Test fun relatedRowBudgetRejectsTruncation() {
        fails {
            repository(
                mapOf("contacts" to listOf(arrayOf(11L, "k1", "A")),
                    "phones" to listOf(arrayOf(11L, "555-0100"), arrayOf(11L, "555-0101"))),
                maxRelated = 1
            ).load(CancellationSignal())
        }
    }

    @Test fun cancellationBeforeReadPreventsAnyProviderAccess() {
        val signal = CancellationSignal().apply { cancel() }
        try {
            repository().load(signal)
            fail("Canceled provider request must not return a directory")
        } catch (_: OperationCanceledException) {
            // No provider access occurred because requiredQuery checks first.
        }
    }
}
