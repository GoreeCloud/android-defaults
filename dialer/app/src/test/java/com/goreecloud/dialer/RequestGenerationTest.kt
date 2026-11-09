package com.goreecloud.dialer

import org.junit.Assert.*
import org.junit.Test

class RequestGenerationTest {
    @Test fun invalidatedRequestsCannotUpdateNewUi() {
        val tracker = RequestGeneration()
        val first = tracker.next()
        assertTrue(tracker.isCurrent(first))
        tracker.invalidate()
        assertFalse(tracker.isCurrent(first))
        val second = tracker.next()
        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))
    }

    @Test fun newerRequestDiscardsOlderRequest() {
        val tracker = RequestGeneration()
        val first = tracker.next()
        val second = tracker.next()
        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))
    }
}
