package com.goreecloud.dialer

import org.junit.Assert.*
import org.junit.Test

class PhoneMatcherTest {
    @Test fun normalizesInternationalAndDtmf() {
        assertEquals("+12025550199", PhoneMatcher.cleanNumber("+1 (202) 555-0199"))
        assertEquals("*#06#", PhoneMatcher.cleanNumber("*#06#"))
    }
    @Test fun rejectsInvalidEmptyAndOversized() {
        assertNull(PhoneMatcher.cleanNumber(" - "))
        assertNull(PhoneMatcher.cleanNumber("12+3"))
        assertNull(PhoneMatcher.cleanNumber("+".repeat(41)))
    }
    @Test fun matchesT9AndNumberSubstrings() {
        assertEquals("25423", PhoneMatcher.t9("Alice"))
        assertTrue(PhoneMatcher.matches("254", "Alice", "555-0100"))
        assertTrue(PhoneMatcher.matches("0100", "Alice", "555-0100"))
        assertFalse(PhoneMatcher.matches("999", "Alice", "555-0100"))
    }
    @Test fun emptyQueryDoesNotMatchAllContacts() {
        assertFalse(PhoneMatcher.matches("", "Alice", "555-0100"))
    }
}
