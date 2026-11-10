package com.goreecloud.contacts

/** A portable but deliberately limited CSV snapshot. Not a full contact round-trip format. */
object CsvContactsExport {
    const val MAX_ROWS = 10000
    const val MAX_EXPORT_BYTES = 10 * 1024 * 1024
    private const val MAX_CELL_CHARS = 4096
    private const val MAX_ROW_BYTES = 128 * 1024

    val header = "Display name,Phone numbers,Email addresses,Organization\r\n"

    /** Prevent spreadsheet formula execution even after whitespace or invisible prefix marks. */
    private fun cell(original: String): String {
        require(original.length <= MAX_CELL_CHARS) { "CSV contact field limit exceeded" }
        require(original.none {
            Character.isISOControl(it) && it != '\r' && it != '\n' && it != '\t'
        }) { "Invalid CSV control character" }
        val candidate = original.dropWhile {
            // Unicode format marks (Cf) include zero-width joiners, direction
            // marks and BOM. Some spreadsheet engines hide these before a
            // formula prefix; treat them as ignorable for injection checks.
            it.isWhitespace() || Character.isISOControl(it) ||
                Character.getType(it) == Character.FORMAT.toInt()
        }
        val risky = candidate.firstOrNull() in listOf('=', '+', '-', '@')
        val value = if (risky) "'" + original else original
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    /** A single row groups multiple phone/email values using an explicit separator. */
    fun row(contact: ContactEntry): String {
        require(contact.phones.size <= 100 && contact.emails.size <= 100) {
            "Too many contact fields"
        }
        val fields = listOf(
            contact.name,
            contact.phones.joinToString(" | "),
            contact.emails.joinToString(" | "),
            contact.organization
        )
        val row = fields.joinToString(",") { cell(it) } + "\r\n"
        require(row.toByteArray(Charsets.UTF_8).size <= MAX_ROW_BYTES) {
            "Contact CSV row too large"
        }
        return row
    }

    /** Check every row before attempting to open the user's selected document. */
    fun validate(contacts: List<ContactEntry>): Int {
        require(contacts.isNotEmpty() && contacts.size <= MAX_ROWS) {
            "CSV contact count limit exceeded"
        }
        var size = header.toByteArray(Charsets.UTF_8).size.toLong()
        contacts.forEach {
            size += row(it).toByteArray(Charsets.UTF_8).size
            require(size <= MAX_EXPORT_BYTES) { "CSV exceeds maximum size" }
        }
        return size.toInt()
    }
}
