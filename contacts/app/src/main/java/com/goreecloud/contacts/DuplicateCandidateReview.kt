package com.goreecloud.contacts

/** Select which potential duplicate to inspect; never merge or modify anything. */
data class DuplicateCandidateChoice(val contactId: Long, val title: String)

object DuplicateCandidateReview {
    const val MAX_CHOICES = 20

    private fun safeName(name: String): String {
        val text = name.map {
            if (Character.isISOControl(it) || it == '\u2028' || it == '\u2029')
                ' ' else it
        }.joinToString("").trim()
        return text.take(96).ifBlank { "Unnamed contact" }
    }

    fun choices(cluster: List<ContactEntry>, limit: Int = MAX_CHOICES):
        List<DuplicateCandidateChoice> {
        require(limit in 1..MAX_CHOICES) { "Invalid review limit" }
        return cluster.take(limit).mapIndexed { index, contact ->
            DuplicateCandidateChoice(
                contact.id,
                (index + 1).toString() + ". " + safeName(contact.name)
            )
        }
    }

    fun heading(cluster: List<ContactEntry>): String {
        require(cluster.size >= 2) { "Not a duplicate cluster" }
        val first = cluster.take(2).joinToString(" / ") { safeName(it.name).take(36) }
        return first + " · " + cluster.size + " possible matches"
    }
}
