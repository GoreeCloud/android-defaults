package com.goreecloud.contacts

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/** A strictly limited, display-only preview. No Android Contacts records are written here. */
data class ImportedContactPreview(
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
    val organization: String,
    val omittedProperties: Int
)

/**
 * Local single-card vCard 3/4 reader. Refuses ambiguous/malformed input rather than
 * trying to partially import unknown encodings, binary content, or multiple cards.
 * The Android contact editor owns the eventual user-confirmed write.
 */
object VCardImport {
    const val MAX_FILE_BYTES = 128 * 1024
    private const val MAX_LOGICAL_LINES = 256
    private const val MAX_LINE_LENGTH = 8192
    private const val MAX_VALUES = 8

    fun readBounded(source: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var stalledReads = 0
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) {
                require(++stalledReads <= 3) { "Document stream did not advance" }
                continue
            }
            stalledReads = 0
            require(output.size() + count <= MAX_FILE_BYTES) { "Contact file exceeds size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun parse(bytes: ByteArray): ImportedContactPreview {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) { "Invalid file length" }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val data = decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        return parseText(data)
    }

    fun parseText(data: String): ImportedContactPreview {
        require(data.isNotEmpty() && data.length <= MAX_FILE_BYTES) { "Invalid card length" }
        val text = data.replace("\r\n", "\n")
        require('\r' !in text) { "Invalid line endings" }
        val lines = mutableListOf<String>()
        val rawLines = text.split('\n')
        require(rawLines.size <= MAX_LOGICAL_LINES * 8) { "Too many lines" }
        rawLines.forEachIndexed { index, value ->
            if (value.isEmpty() && index == rawLines.lastIndex) return@forEachIndexed
            require(value.isNotEmpty()) { "Unexpected blank line" }
            if (value.startsWith(' ') || value.startsWith('\t')) {
                require(lines.isNotEmpty()) { "Orphan folded line" }
                val updated = lines.last() + value.drop(1)
                require(updated.length <= MAX_LINE_LENGTH) { "Folded line too long" }
                lines[lines.lastIndex] = updated
            } else {
                require(value.length <= MAX_LINE_LENGTH) { "Contact property too long" }
                lines.add(value)
                require(lines.size <= MAX_LOGICAL_LINES) { "Too many properties" }
            }
        }
        require(lines.size >= 4 &&
            lines.first().equals("BEGIN:VCARD", ignoreCase = true) &&
            lines.last().equals("END:VCARD", ignoreCase = true)) {
            "A single vCard is required"
        }

        var version = ""
        var name: String? = null
        var organization = ""
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var omitted = 0

        lines.subList(1, lines.lastIndex).forEach { line ->
            val colon = line.indexOf(':')
            require(colon in 1 until line.lastIndex) { "Malformed contact property" }
            val descriptor = line.substring(0, colon)
            val key = descriptor.substringBefore(';').uppercase(Locale.ROOT)
            require(key.matches(Regex("[A-Z0-9-]{1,40}"))) { "Invalid contact field" }
            require(key != "BEGIN" && key != "END") { "Nested or extra card boundary" }
            val rawValue = line.substring(colon + 1)
            val supported = key in setOf("VERSION", "FN", "TEL", "EMAIL", "ORG")
            if (supported) {
                val options = descriptor.substringAfter(';', "")
                require(!Regex("(?i)(^|;)ENCODING=").containsMatchIn(options)) {
                    "Unsupported encoded contact field"
                }
                require(!Regex("(?i)(^|;)CHARSET=(?!UTF-8(?:;|$))").containsMatchIn(options)) {
                    "Unsupported text encoding"
                }
            }
            when (key) {
                "VERSION" -> {
                    require(version.isEmpty() && rawValue in setOf("3.0", "4.0")) {
                        "Unsupported vCard version"
                    }
                    version = rawValue
                }
                "FN" -> {
                    require(name == null) { "Ambiguous contact name" }
                    name = unescape(rawValue, 256)
                    require(name!!.isNotBlank()) { "Missing contact name" }
                }
                "TEL" -> {
                    val number = unescape(rawValue.removePrefix("tel:").removePrefix("TEL:"), 256)
                    require(number.isNotBlank()) { "Empty telephone field" }
                    require(phones.size < MAX_VALUES) { "Too many telephone fields" }
                    phones.add(number)
                }
                "EMAIL" -> {
                    val email = unescape(rawValue, 320)
                    require(email.isNotBlank()) { "Empty email field" }
                    require(emails.size < MAX_VALUES) { "Too many email fields" }
                    emails.add(email)
                }
                "ORG" -> {
                    require(organization.isEmpty()) { "Multiple organization fields" }
                    organization = unescape(rawValue, 512)
                }
                else -> omitted++
            }
        }
        require(version.isNotEmpty() && name != null) { "Missing required vCard fields" }
        return ImportedContactPreview(name!!, phones.toList(), emails.toList(),
            organization, omitted)
    }

    private fun unescape(raw: String, maxLength: Int): String {
        val value = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\') {
                require(i + 1 < raw.length) { "Incomplete escape sequence" }
                value.append(when (raw[++i]) {
                    'n', 'N' -> '\n'
                    '\\' -> '\\'
                    ';' -> ';'
                    ',' -> ','
                    else -> throw IllegalArgumentException("Unsupported escape sequence")
                })
            } else {
                require(!Character.isISOControl(c)) { "Invalid control character" }
                value.append(c)
            }
            require(value.length <= maxLength) { "Imported field too long" }
            i++
        }
        require(value.none { Character.isISOControl(it) && it != '\n' }) {
            "Invalid imported field"
        }
        return value.toString()
    }
}
