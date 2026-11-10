package com.goreecloud.contacts

import java.time.DateTimeException
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.PriorityQueue

/** Pure local-only recurrence list. This does not schedule notifications or read calendars. */
data class UpcomingDate(
    val contactId: Long,
    val contactName: String,
    val kind: String,
    val nextDate: LocalDate,
    val daysUntil: Long
)

object UpcomingDates {
    const val DEFAULT_HORIZON_DAYS = 90
    const val DEFAULT_MAX_RESULTS = 12
    private val yearless = Regex("^--(\\d{2})-(\\d{2})$")
    private val fullDate = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    private fun monthDay(value: String): MonthDay? {
        val match = yearless.matchEntire(value)
        val year = fullDate.matchEntire(value)
        if (match == null && year == null) return null
        return try {
            if (year != null) {
                val parsed = LocalDate.of(
                    year.groupValues[1].toInt(),
                    year.groupValues[2].toInt(),
                    year.groupValues[3].toInt()
                )
                if (parsed.year < 1) return null
                MonthDay.from(parsed)
            } else {
                MonthDay.of(match!!.groupValues[1].toInt(),
                    match.groupValues[2].toInt())
            }
        } catch (_: DateTimeException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun nextOccurrence(date: MonthDay, today: LocalDate): LocalDate? {
        // Exact leap-day recurrence: do not silently rewrite Feb 29 to Feb 28.
        for (years in 0..8) {
            val dateInYear = try {
                LocalDate.of(today.year + years, date.monthValue, date.dayOfMonth)
            } catch (_: DateTimeException) {
                null
            }
            if (dateInYear != null && !dateInYear.isBefore(today)) return dateInYear
        }
        return null
    }

    fun list(
        contacts: List<ContactEntry>,
        today: LocalDate,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): List<UpcomingDate> {
        require(horizonDays in 1..366 && maxResults in 1..50) {
            "Invalid upcoming-date view bounds"
        }
        val ordering = compareBy<UpcomingDate> { it.nextDate }
            .thenBy { it.contactName.lowercase(Locale.ROOT) }
            .thenBy { it.contactId }
        // A worst-first heap retains at most maxResults candidates rather than
        // allocating and sorting every event in a 10,000-contact snapshot.
        // The old full-list sort was stable on equal date/name/contact ID.
        // Preserve source arrival order in those rare ties, without allocating
        // the full event list or changing the public result shape.
        data class Ranked(val date: UpcomingDate, val arrival: Long)
        val stable = Comparator<Ranked> { left, right ->
            val rank = ordering.compare(left.date, right.date)
            if (rank != 0) rank else left.arrival.compareTo(right.arrival)
        }
        val top = PriorityQueue<Ranked>(maxResults + 1, stable.reversed())
        var arrival = 0L
        contacts.forEach { contact ->
            contact.importantDates.forEach eventLoop@{ event ->
                // Arbitrary custom dates may be one-time events: do not invent recurrence.
                if (event.label != "Birthday" && event.label != "Anniversary")
                    return@eventLoop
                val monthDay = monthDay(event.value) ?: return@eventLoop
                val next = nextOccurrence(monthDay, today) ?: return@eventLoop
                val remaining = ChronoUnit.DAYS.between(today, next)
                if (remaining in 0..horizonDays.toLong()) {
                    val candidate = UpcomingDate(
                        contactId = contact.id, contactName = contact.name,
                        kind = event.label, nextDate = next, daysUntil = remaining
                    )
                    val ranked = Ranked(candidate, arrival++)
                    if (top.size < maxResults) top.add(ranked)
                    else if (stable.compare(ranked, top.peek()) < 0) {
                        top.poll()
                        top.add(ranked)
                    }
                }
            }
        }
        return top.toList().sortedWith(stable).map { it.date }
    }
}
