package com.goreecloud.dialer

import java.text.Normalizer
import java.util.Locale

/**
 * Local-only dial-string validation and contact matching.
 * Never silently extract digits from arbitrary text: doing so can transform
 * a pasted label such as "CALL911" into a completely different dial target.
 */
object PhoneMatcher {
    private val alphabet = listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")
    private const val MAX_DIAL_LENGTH = 40

    fun cleanNumber(raw: String): String? {
        if (raw.isEmpty() || raw.length > 120) return null
        val value = StringBuilder()
        for (char in raw) {
            when {
                char == '+' -> {
                    if (value.isNotEmpty()) return null
                    value.append(char)
                }
                char == '*' || char == '#' -> value.append(char)
                char.isDigit() -> {
                    val digit = Character.digit(char, 10)
                    if (digit !in 0..9) return null
                    value.append(('0'.code + digit).toChar())
                }
                char.isWhitespace() || char in "-()." -> Unit
                else -> return null
            }
            if (value.length > MAX_DIAL_LENGTH) return null
        }
        val formatted = value.toString()
        return formatted.takeIf { it.any(Char::isDigit) }
    }

    fun t9(name: String): String = buildString {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT)
        for (char in normalized) {
            val index = alphabet.indexOfFirst { char in it }
            if (index >= 0) append(index + 2)
            else if (char.isDigit()) append(char)
        }
    }

    fun matches(query: String, name: String, number: String): Boolean {
        val digits = query.mapNotNull { char ->
            if (char.isDigit()) Character.digit(char, 10).takeIf { it in 0..9 }?.toString()
            else null
        }.joinToString("")
        if (digits.isEmpty()) return false
        val phoneDigits = number.mapNotNull { char ->
            if (char.isDigit()) Character.digit(char, 10).takeIf { it in 0..9 }?.toString()
            else null
        }.joinToString("")
        return phoneDigits.contains(digits) || t9(name).contains(digits)
    }
}
