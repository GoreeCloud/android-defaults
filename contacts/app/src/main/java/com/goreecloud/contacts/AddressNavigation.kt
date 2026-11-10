package com.goreecloud.contacts

import java.net.URLEncoder

/**
 * Builds only a query for a user-chosen external maps application.
 * This app never resolves coordinates, requests location, or sends network data.
 */
object AddressNavigation {
    const val MAX_ADDRESS_CHARS = 2048

    fun geoUri(formattedAddress: String): String {
        require(formattedAddress.length in 1..MAX_ADDRESS_CHARS) {
            "Invalid contact address length"
        }
        val safe = buildString {
            formattedAddress.forEach { char ->
                require(!Character.isISOControl(char) ||
                    char in listOf('\n', '\r', '\t')) {
                    "Unexpected control character in contact address"
                }
                append(if (char == '\n' || char == '\r' || char == '\t' ||
                    char == '\u2028' || char == '\u2029') ' ' else char)
            }
        }.trim()
        require(safe.isNotEmpty()) { "No address to open" }
        // Encode punctuation, query delimiters and Unicode, not just spaces.
        // Explicit geo: intent is created only after user confirmation.
        val query = URLEncoder.encode(safe, "UTF-8").replace("+", "%20")
        return "geo:0,0?q=" + query
    }
}
