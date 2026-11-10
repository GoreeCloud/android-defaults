package com.goreecloud.contacts

import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.SystemClock
import android.util.Log
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
            when {
                arguments?.contains(CommonDataKinds.Organization.CONTENT_ITEM_TYPE) == true ->
                    "organizations"
                arguments?.contains(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE) == true ->
                    "addresses"
                arguments?.contains(CommonDataKinds.Event.CONTENT_ITEM_TYPE) == true ->
                    "events"
                arguments?.contains(CommonDataKinds.Note.CONTENT_ITEM_TYPE) == true ->
                    "notes"
                else -> "memberships"
            }
        else -> throw IllegalArgumentException("Unexpected URI in synthetic test")
    }

    private fun repository(
        rows: Map<String, List<Array<Any?>>> = emptyMap(),
        nullAt: String? = null,
        throwAt: String? = null,
        maxContacts: Int = 10000,
        maxRelated: Int = 60000,
        malformedAt: String? = null,
        cancelOnExhaustionAt: String? = null,
        cancelOnCloseAt: String? = null
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
                val data = MatrixCursor(columns).apply {
                    rows[target].orEmpty().forEach { addRow(it) }
                }
                when (target) {
                    cancelOnExhaustionAt -> object : CursorWrapper(data) {
                        override fun moveToNext(): Boolean {
                            val available = super.moveToNext()
                            if (!available) signal.cancel()
                            return available
                        }
                    }
                    cancelOnCloseAt -> object : CursorWrapper(data) {
                        override fun close() {
                            super.close()
                            signal.cancel()
                        }
                    }
                    else -> data
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
            "memberships" to listOf(arrayOf(11L, 4L)),
            "addresses" to listOf(arrayOf(11L, "123 Test Rd, Exampleville")),
            "events" to listOf(arrayOf(11L, "--11-09",
                CommonDataKinds.Event.TYPE_BIRTHDAY, null)),
            "notes" to listOf(arrayOf(11L, "Synthetic note"))
        )
        val snap = repository(rows).load(CancellationSignal())
        assertEquals(listOf("Alpha", "Zeta"), snap.contacts.map { it.name })
        assertEquals("lookup-a", snap.contacts[0].lookupKey)
        assertEquals(listOf("555-0100"), snap.contacts[0].phones)
        assertEquals(listOf("a@example.invalid"), snap.contacts[0].emails)
        assertEquals("Synthetic Co.", snap.contacts[0].organization)
        assertEquals(setOf(4L), snap.contacts[0].groupIds)
        assertEquals(listOf("123 Test Rd, Exampleville"), snap.contacts[0].addresses)
        assertEquals(listOf(ImportantDate("Birthday", "--11-09")),
            snap.contacts[0].importantDates)
        assertEquals(listOf("Synthetic note"), snap.contacts[0].notes)
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
    @Test fun missingExtendedCursorRejectsIncompleteContactDetails() {
        fails {
            repository(mapOf("contacts" to listOf(arrayOf(11L, "lookup-a", "A"))),
                nullAt = "addresses").load(CancellationSignal())
        }
    }

    @Test fun oversizedContactNoteFailsClosedWithoutTruncation() {
        fails {
            repository(mapOf(
                "contacts" to listOf(arrayOf(11L, "lookup-a", "A")),
                "notes" to listOf(arrayOf(11L, "x".repeat(4097)))
            )).load(CancellationSignal())
        }
    }

    @Test fun customDatesAndPostalRowsStayInOwnAggregate() {
        val snap = repository(mapOf(
            "contacts" to listOf(arrayOf(11L, "lookup-a", "A"),
                arrayOf(12L, "lookup-b", "B")),
            "addresses" to listOf(arrayOf(11L, "Post Office"), arrayOf(99L, "Unknown")),
            "events" to listOf(arrayOf(12L, "2026-12-01",
                CommonDataKinds.BaseTypes.TYPE_CUSTOM, "Graduation"))
        )).load(CancellationSignal())
        assertEquals(listOf("Post Office"), snap.contacts[0].addresses)
        assertTrue(snap.contacts[1].addresses.isEmpty())
        assertEquals(listOf(ImportantDate("Graduation", "2026-12-01")),
            snap.contacts[1].importantDates)
    }


    @Test fun oversizedIdentityOrCommunicationFieldRejectsEntireSnapshot() {
        val huge = "x".repeat(ContactsRepository.MAX_INLINE_FIELD_CHARS + 1)
        val base = listOf(arrayOf<Any?>(11L, "lookup-a", "Alice"))
        val scenarios: List<Map<String, List<Array<Any?>>>> = listOf(
            mapOf("contacts" to listOf(arrayOf<Any?>(11L, "lookup-a", huge))),
            mapOf("contacts" to listOf(arrayOf<Any?>(11L, huge, "Alice"))),
            mapOf("contacts" to base, "phones" to listOf(arrayOf<Any?>(11L, huge))),
            mapOf("contacts" to base, "emails" to listOf(arrayOf<Any?>(11L, huge))),
            mapOf("contacts" to base, "organizations" to listOf(arrayOf<Any?>(11L, huge))),
            mapOf("contacts" to base, "groups" to listOf(arrayOf<Any?>(1L, huge)))
        )
        scenarios.forEach { rows ->
            fails { repository(rows).load(CancellationSignal()) }
        }
    }

    @Test fun perContactPhoneEmailAndGroupCapsRejectTruncation() {
        val base = listOf(arrayOf<Any?>(11L, "lookup-a", "Alice"))
        val n = ContactsRepository.MAX_CHANNELS_PER_CONTACT + 1
        val phones = (0 until n).map {
            arrayOf<Any?>(11L, "555" + it.toString().padStart(4, '0'))
        }
        val emails = (0 until n).map {
            arrayOf<Any?>(11L, "person" + it + "@example.invalid")
        }
        fails { repository(mapOf("contacts" to base, "phones" to phones)).load(CancellationSignal()) }
        fails { repository(mapOf("contacts" to base, "emails" to emails)).load(CancellationSignal()) }
        val groups = (1..ContactsRepository.MAX_GROUPS_PER_CONTACT + 1)
            .map { arrayOf<Any?>(11L, it.toLong()) }
        fails { repository(mapOf("contacts" to base, "memberships" to groups))
            .load(CancellationSignal()) }
    }

    /**
     * Captures a reproducible 5k/10k provider traversal baseline on a disposable
     * emulator. There is deliberately no brittle wall-clock pass/fail threshold:
     * this does not measure RecyclerView recycling, UI jank or OEM provider time.
     */
    @Test fun fiveAndTenThousandSyntheticRecordsRemainCompleteAndBounded() {
        for (count in listOf(5000, 10000)) {
            val contacts = ArrayList<Array<Any?>>(count)
            for (id in 1..count) {
                contacts.add(arrayOf<Any?>(
                    id.toLong(), "fixture-key-$id", "Synthetic fixture $id"
                ))
            }
            val started = SystemClock.elapsedRealtime()
            val result = repository(
                rows = mapOf("contacts" to contacts),
                maxContacts = 10000
            ).load(CancellationSignal())
            val elapsed = SystemClock.elapsedRealtime() - started
            assertEquals(count, result.contacts.size)
            assertEquals(count, result.contacts.map { it.id }.toSet().size)
            assertTrue(result.contacts.all { it.phones.isEmpty() && it.notes.isEmpty() })
            assertTrue(elapsed >= 0L)
            // Safe public CI signal: synthetic row count and monotonic duration only.
            Log.i("GoreeCloudSyntheticContactsQA", "rows=$count elapsedMs=$elapsed")
        }
    }

    @Test fun tenThousandAndOneSyntheticRecordsCannotSilentlyTruncate() {
        val contacts = (1..10001).map { id ->
            arrayOf<Any?>(id.toLong(), "fixture-key-$id", "Synthetic fixture $id")
        }
        fails {
            repository(rows = mapOf("contacts" to contacts), maxContacts = 10000)
                .load(CancellationSignal())
        }
    }

    @Test fun exactPerContactChannelLimitIsAccepted() {
        val n = ContactsRepository.MAX_CHANNELS_PER_CONTACT
        val snap = repository(mapOf(
            "contacts" to listOf(arrayOf<Any?>(11L, "lookup-a", "Alice")),
            "phones" to (0 until n).map {
                arrayOf<Any?>(11L, "555" + it.toString().padStart(4, '0'))
            }
        )).load(CancellationSignal())
        assertEquals(n, snap.contacts.single().phones.size)
    }

    @Test fun providerCancellationAtFinalCursorExhaustionRejectsSnapshot() {
        // A provider can cancel while moveToNext() returns false. No prior
        // per-row check can authenticate a supposedly complete directory.
        val signal = CancellationSignal()
        try {
            repository(
                rows = mapOf("contacts" to listOf(arrayOf(11L, "lookup-a", "Alice"))),
                cancelOnExhaustionAt = "notes"
            ).load(signal)
            fail("A canceled final cursor must never publish contact data")
        } catch (_: OperationCanceledException) {
            assertTrue(signal.isCanceled)
        }
    }

    @Test fun providerCancellationDuringFinalCursorCloseRejectsSnapshot() {
        val signal = CancellationSignal()
        try {
            repository(
                rows = mapOf("contacts" to listOf(arrayOf(11L, "lookup-a", "Alice"))),
                cancelOnCloseAt = "notes"
            ).load(signal)
            fail("A canceled final cursor close must never publish contact data")
        } catch (_: OperationCanceledException) {
            assertTrue(signal.isCanceled)
        }
    }

    @Test fun duplicateOrNonPositiveProviderContactIdsRejectPartialDirectory() {
        val rows = listOf(
            listOf(arrayOf<Any?>(11L, "first", "Alice"),
                arrayOf<Any?>(11L, "second", "Bob")),
            listOf(arrayOf<Any?>(0L, "zero", "Invalid")),
            listOf(arrayOf<Any?>(-1L, "negative", "Invalid"))
        )
        for (contacts in rows) {
            fails {
                repository(mapOf("contacts" to contacts)).load(CancellationSignal())
            }
        }
    }
}
