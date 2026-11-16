package com.goreecloud.dialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalDialIntentPolicyTest {
    @Test fun allowsOnlyRealTelephoneDialIntents() {
        assertEquals("911", ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "911"))
        assertEquals("+12025550199", ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "+1 (202) 555-0199"))
        assertEquals("*#06#", ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "*#06#"))
        assertEquals("2025550100", ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "٢٠٢-٥٥٥-٠١٠٠"))
    }

    @Test fun rejectsImplicitNonDialActionsAndSchemes() {
        assertNull(ExternalDialIntentPolicy.normalizedNumber(null, "tel", "911"))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.CALL", "tel", "911"))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "https", "911"))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "TEL", "911"))
    }

    @Test fun rejectsOversizedControlAndUriInjection() {
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "9".repeat(121)))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "911;foo"))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "911\nCall security"))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", "911\u202EABC"))
        assertNull(ExternalDialIntentPolicy.normalizedNumber(
            "android.intent.action.DIAL", "tel", null))
    }
}
