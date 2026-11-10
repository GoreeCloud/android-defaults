package com.goreecloud.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AddressNavigationTest {
    @Test fun encodesUnicodeSpacesAndQueryPunctuation() {
        val uri = AddressNavigation.geoUri("123 Rue de l'École, Montréal & Suite #5")
        assertTrue(uri.startsWith("geo:0,0?q="))
        assertTrue(uri.contains("%20"))
        assertTrue(uri.contains("%26"))
        assertTrue(uri.contains("%23"))
        assertFalse(uri.contains(" Suite #5"))
        assertFalse(uri.contains("Montréal"))
    }

    @Test fun percentEncodesInjectedQueryDelimiters() {
        val uri = AddressNavigation.geoUri("100 Test Street?zoom=4&fake=1")
        assertEquals(1, uri.count { it == '?' })
        assertFalse(uri.contains("&fake=1"))
        assertTrue(uri.contains("%26"))
    }

    @Test fun sanitizesLineBreaksToSingleQuery() {
        val uri = AddressNavigation.geoUri("1 Test Ave\nFloor 2\tSuite B")
        assertTrue(uri.contains("Test%20Ave%20Floor%202%20Suite%20B"))
        assertFalse(uri.contains("\n"))
    }

    @Test fun refusesBlankInvalidAndOversizedInput() {
        for (candidate in listOf("", "  ", "\t\n",
            "bad\u0000address", "a".repeat(AddressNavigation.MAX_ADDRESS_CHARS + 1))) {
            try {
                AddressNavigation.geoUri(candidate)
                fail("Expected address rejection")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
