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

    @Test fun retryAfterProviderFailureRejectsOldCompletion() {
        val generation = RequestGeneration()
        val failedRead = generation.next()
        // A user taps Retry while a canceled provider is still winding down.
        generation.invalidate()
        val retriedRead = generation.next()
        assertFalse(generation.isCurrent(failedRead))
        assertTrue(generation.isCurrent(retriedRead))
        // A late result from the first provider must not replace the retry.
        generation.invalidate()
        assertFalse(generation.isCurrent(retriedRead))
    }

    @Test fun newerRequestDiscardsOlderRequest() {
        val tracker = RequestGeneration()
        val first = tracker.next()
        val second = tracker.next()
        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))
    }
}
