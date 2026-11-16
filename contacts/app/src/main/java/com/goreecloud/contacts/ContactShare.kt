package com.goreecloud.contacts

/** GoreeShield: share exactly the fields affirmatively selected by the user. */
enum class ShareFieldKind(val label: String) {
    NAME("Name"), PHONE("Phone"), EMAIL("Email")
}

data class ShareChoice(val kind: ShareFieldKind, val value: String)

/**
 * Pure, offline, short-lived text payload. Extended contact details, notes,
 * addresses and account metadata are never included.
 */
object ContactShare {
    private const val MAX_CHOICES = 128
    private const val MAX_FIELD_LENGTH = 4096
    private const val MAX_PAYLOAD_LENGTH = 16 * 1024

    fun choices(contact: ContactEntry): List<ShareChoice> {
        val fields = listOf(ShareChoice(ShareFieldKind.NAME, contact.name)) +
            contact.phones.map { ShareChoice(ShareFieldKind.PHONE, it) } +
            contact.emails.map { ShareChoice(ShareFieldKind.EMAIL, it) }
        require(fields.size <= MAX_CHOICES) { "Too many details to share" }
        return fields
    }

    private fun onOneLine(value: String): String {
        require(value.length <= MAX_FIELD_LENGTH) { "Shared detail exceeds length limit" }
        val sanitized = buildString {
            value.forEachIndexed { i, char ->
                require(!Character.isSurrogate(char) ||
                    (Character.isHighSurrogate(char) && i + 1 < value.length &&
                        Character.isLowSurrogate(value[i + 1])) ||
                    (Character.isLowSurrogate(char) && i > 0 &&
                        Character.isHighSurrogate(value[i - 1]))) {
                    "Invalid Unicode contact detail"
                }
                // Refuse bidi overrides and isolate marks instead of letting
                // an external share sheet mislabel or reverse selected values.
                // These are invisible formatting controls, not user-visible
                // international letters, numbers, or valid emoji.
                val point = char.code
                require(point != 0x061C && point != 0x200E && point != 0x200F &&
                    point !in 0x202A..0x202E && point !in 0x2066..0x2069) {
                    "Invalid Unicode share formatting"
                }
                append(if (Character.isISOControl(char) ||
                    char == '\u2028' || char == '\u2029') ' ' else char)
            }
        }.trim()
        require(sanitized.isNotEmpty()) { "Cannot share a blank detail" }
        return sanitized
    }

    fun format(fields: List<ShareChoice>, selected: Set<Int>): String {
        require(fields.size <= MAX_CHOICES && selected.isNotEmpty()) {
            "Choose at least one contact detail"
        }
        require(selected.all { it in fields.indices }) { "Invalid detail selection" }
        val text = selected.sorted().joinToString("\n") { index ->
            val field = fields[index]
            field.kind.label + ": " + onOneLine(field.value)
        }
        require(text.length <= MAX_PAYLOAD_LENGTH) { "Selected details are too large" }
        return text
    }
}
