package com.goreecloud.contacts

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpcomingDatesTest {
    private fun contact(id: Long, label: String, value: String) =
        ContactEntry(id, "test$id", "Person $id",
            importantDates = listOf(ImportantDate(label, value)))

    @Test fun recurringBirthdayRespectsYearBoundary() {
        val entries = listOf(contact(1, "Birthday", "--01-01"))
        val result = UpcomingDates.list(entries, LocalDate.of(2026, 12, 20), 30)
        assertEquals(LocalDate.of(2027, 1, 1), result.single().nextDate)
        assertEquals(12L, result.single().daysUntil)
    }

    @Test fun leapDayWaitsForActualLeapYear() {
        val entries = listOf(contact(2, "Birthday", "2000-02-29"))
        val result = UpcomingDates.list(entries, LocalDate.of(2027, 3, 1), 90)
        assertTrue(result.isEmpty())
        val next = UpcomingDates.list(entries, LocalDate.of(2028, 2, 28))
        assertEquals(LocalDate.of(2028, 2, 29), next.single().nextDate)
        assertEquals(1L, next.single().daysUntil)
    }

    @Test fun invalidOrNonRecurringEventsDoNotGenerateInventedDates() {
        val items = listOf(
            contact(1, "Birthday", "2025-02-29"),
            contact(2, "Birthday", "--13-01"),
            contact(3, "Date", "--11-09"),
            contact(4, "Birthday", "2026-1-2"),
            contact(5, "Anniversary", "not-a-date")
        )
        assertTrue(UpcomingDates.list(items, LocalDate.of(2026, 11, 9)).isEmpty())
    }

    @Test fun todayAndSameDayAnniversaryAreShown() {
        val items = listOf(contact(3, "Anniversary", "--11-09"))
        assertEquals(0L,
            UpcomingDates.list(items, LocalDate.of(2026, 11, 9)).single().daysUntil)
    }

    @Test fun sortedByNextOccurrenceAndBoundedToTopResults() {
        val items = listOf(
            contact(1, "Birthday", "--11-20"),
            contact(2, "Birthday", "--11-10"),
            contact(3, "Anniversary", "--11-11")
        )
        assertEquals(listOf(2L, 3L),
            UpcomingDates.list(items, LocalDate.of(2026, 11, 9),
                horizonDays = 90, maxResults = 2).map { it.contactId })
    }

    @Test fun refusesUnboundedInputs() {
        for (days in listOf(0, 367)) {
            try {
                UpcomingDates.list(emptyList(), LocalDate.of(2026, 11, 9), horizonDays = days)
                fail("Invalid horizon accepted")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            UpcomingDates.list(emptyList(), LocalDate.of(2026, 11, 9), maxResults = 51)
            fail("Unbounded results accepted")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun boundedHeapReturnsSameTopCandidatesFromLargeSyntheticDirectory() {
        val today = LocalDate.of(2026, 11, 9)
        val contacts = (1L..5000L).map { id ->
            val day = (id % 30L + 1L).toInt()
            contact(id, "Birthday", "--11-" + day.toString().padStart(2, '0'))
        }
        val selected = UpcomingDates.list(contacts, today, horizonDays = 90, maxResults = 12)
        assertEquals(12, selected.size)
        assertTrue(selected.zipWithNext().all { (a, b) ->
            a.nextDate.isBefore(b.nextDate) || a.nextDate == b.nextDate
        })
        assertTrue(selected.all { it.daysUntil in 0..90L })
    }

    @Test fun topOneSelectionKeepsEarliestDateRegardlessOfInputOrder() {
        val items = listOf(
            contact(1, "Birthday", "--12-01"),
            contact(2, "Birthday", "--11-09"),
            contact(3, "Anniversary", "--11-20")
        )
        val result = UpcomingDates.list(items, LocalDate.of(2026, 11, 9),
            maxResults = 1)
        assertEquals(2L, result.single().contactId)
    }

    @Test fun tiedEventsRetainOriginalArrivalOrderAfterHeapReplacement() {
        val sameContact = ContactEntry(8, "test8", "Sam",
            importantDates = listOf(
                ImportantDate("Birthday", "--11-12"),
                ImportantDate("Anniversary", "--11-12")
            ))
        val items = listOf(contact(2, "Birthday", "--12-30"), sameContact)
        val result = UpcomingDates.list(items, LocalDate.of(2026, 11, 9),
            horizonDays = 90, maxResults = 2)
        assertEquals(listOf("Birthday", "Anniversary"), result.map { it.kind })
        assertEquals(listOf(8L, 8L), result.map { it.contactId })
    }

    @Test fun boundedHeapMatchesStableGlobalSortAcrossSixHundredSyntheticContacts() {
        val today = LocalDate.of(2026, 11, 9)
        val dates = listOf("--11-09", "--11-10", "--11-11", "--11-12",
            "--12-01", "--12-30", "--02-29", "--04-01", "--01-03")
        val entries = (1L..600L).map { id ->
            contact(id, if (id % 3L == 0L) "Anniversary" else "Birthday",
                dates[(id % dates.size).toInt()])
        }.reversed()
        val full = entries.flatMap {
            UpcomingDates.list(listOf(it), today, horizonDays = 366, maxResults = 50)
        }.sortedWith(compareBy<UpcomingDate> { it.nextDate }
            .thenBy { it.contactName.lowercase(java.util.Locale.ROOT) }
            .thenBy { it.contactId })
        for (limit in listOf(1, 2, 12, 50)) {
            assertEquals(full.take(limit),
                UpcomingDates.list(entries, today, horizonDays = 366, maxResults = limit))
        }
    }

}
