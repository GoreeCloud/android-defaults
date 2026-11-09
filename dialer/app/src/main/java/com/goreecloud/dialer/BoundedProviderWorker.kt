package com.goreecloud.dialer

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Strictly bounded, single-thread Android provider reads. A stalled OEM
 * Contacts/CallLog provider cannot accumulate an unbounded queue of canceled
 * requests during repeated resumes and permission changes.
 *
 * Rejected tasks are never executed on the calling (usually UI) thread.
 * The Activity shows a retryable local-only error state instead.
 */
internal class BoundedProviderWorker {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(2)
    )

    fun post(operation: () -> Unit): Boolean =
        try {
            executor.execute { operation() }
            true
        } catch (_: RejectedExecutionException) {
            false
        }

    fun shutdownNow() {
        executor.shutdownNow()
    }
}
