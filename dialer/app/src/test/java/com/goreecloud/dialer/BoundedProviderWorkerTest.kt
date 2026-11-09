package com.goreecloud.dialer

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BoundedProviderWorkerTest {
    @Test fun oneActiveAndOnlyTwoQueuedOperationsAreAllowed() {
        val worker = BoundedProviderWorker()
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            assertTrue(worker.post {
                running.countDown()
                release.await(4, TimeUnit.SECONDS)
            })
            assertTrue(running.await(2, TimeUnit.SECONDS))
            assertTrue(worker.post {})
            assertTrue(worker.post {})
            assertFalse("Must refuse rather than grow without bound", worker.post {})
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test fun acceptedProviderWorkNeverRunsOnCallerThread() {
        val worker = BoundedProviderWorker()
        val caller = Thread.currentThread()
        val actual = AtomicReference<Thread?>()
        val completed = CountDownLatch(1)
        try {
            assertTrue(worker.post {
                actual.set(Thread.currentThread())
                completed.countDown()
            })
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertNotSame(caller, actual.get())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test fun shutdownRejectsNewProviderReadsWithoutExecutingThem() {
        val worker = BoundedProviderWorker()
        worker.shutdownNow()
        var executed = false
        assertFalse(worker.post { executed = true })
        assertFalse(executed)
    }
}
