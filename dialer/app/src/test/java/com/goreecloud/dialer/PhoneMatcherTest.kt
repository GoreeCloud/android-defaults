package com.goreecloud.dialer

import org.junit.Assert.*
import org.junit.Test

class PhoneMatcherTest {
    @Test fun normalizesInternationalAndDtmf() {
        assertEquals("+12025550199", PhoneMatcher.cleanNumber("+1 (202) 555-0199"))
        assertEquals("*#06#", PhoneMatcher.cleanNumber("*#06#"))
        assertEquals("2025550100", PhoneMatcher.cleanNumber("٢٠٢-٥٥٥-٠١٠٠"))
    }

    @Test fun refusesNonNumbersRatherThanSilentlyCallingTheirDigits() {
        assertNull(PhoneMatcher.cleanNumber("CALL911"))
        assertNull(PhoneMatcher.cleanNumber("tel:911"))
        assertNull(PhoneMatcher.cleanNumber("202x555"))
        assertNull(PhoneMatcher.cleanNumber("202;555"))
    }

    @Test fun rejectsInvalidEmptyAndOversized() {
        assertNull(PhoneMatcher.cleanNumber(" - "))
        assertNull(PhoneMatcher.cleanNumber("12+3"))
        assertNull(PhoneMatcher.cleanNumber("+++"))
        assertNull(PhoneMatcher.cleanNumber("+"))
        assertNull(PhoneMatcher.cleanNumber("#"))
        assertNull(PhoneMatcher.cleanNumber("+".repeat(41)))
        assertNull(PhoneMatcher.cleanNumber("1".repeat(41)))
    }

    @Test fun matchesT9NumbersAndAccentedNames() {
        assertEquals("25423", PhoneMatcher.t9("Alice"))
        assertEquals("3742", PhoneMatcher.t9("Éric"))
        assertTrue(PhoneMatcher.matches("254", "Alice", "555-0100"))
        assertTrue(PhoneMatcher.matches("0100", "Alice", "555-0100"))
        assertTrue(PhoneMatcher.matches("374", "Éric", "555-0100"))
        assertFalse(PhoneMatcher.matches("999", "Alice", "555-0100"))
    }

    @Test fun unicodeDigitsInContactNamesMatchNormalizedT9KeypadQueries() {
        assertEquals("6632", PhoneMatcher.t9("Moe ٢"))
        assertEquals("6633", PhoneMatcher.t9("Moe ３"))
        assertTrue(PhoneMatcher.matches("6632", "Moe ٢", "555-0100"))
        assertTrue(PhoneMatcher.matches("٦٦٣٢", "Moe ٢", "555-0100"))
        assertFalse(PhoneMatcher.matches("6634", "Moe ٢", "555-0100"))
    }

    @Test fun emptyQueryDoesNotMatchAllContacts() {
        assertFalse(PhoneMatcher.matches("", "Alice", "555-0100"))
        assertFalse(PhoneMatcher.matches("#", "Alice", "555-0100"))
    }
}
