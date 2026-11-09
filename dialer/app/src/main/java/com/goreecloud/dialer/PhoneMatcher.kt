package com.goreecloud.dialer

/** No network, analytics, or account data involved in matching. */
object PhoneMatcher {
    private val alphabet = listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")
    fun cleanNumber(raw: String): String? {
        val filtered = raw.filter { it.isDigit() || it in "+*#" }
        if (filtered.isEmpty() || filtered.length > 40) return null
        if (filtered.count { it == '+' } > 1 || (filtered.contains('+') && !filtered.startsWith('+'))) return null
        return filtered
    }
    fun t9(name: String): String = buildString {
        for (char in name.lowercase()) {
            val index = alphabet.indexOfFirst { char in it }
            if (index >= 0) append(index + 2)
            else if (char.isDigit()) append(char)
        }
    }
    fun matches(query: String, name: String, number: String): Boolean {
        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        return number.filter { it.isDigit() }.contains(digits) || t9(name).contains(digits)
    }
}
