package com.goreecloud.dialer

import android.database.Cursor
import android.os.CancellationSignal

/**
 * Returns only an all-valid in-memory snapshot. A null Cursor, malformed row,
 * canceled read, or misbehaving provider must never publish partial data.
 * The caller converts exceptions into its existing local-only Retry UI.
 *
 * This does not impose a wall-clock deadline on an uncooperative provider;
 * the Android CancellationSignal is advisory to provider implementations.
 */
internal object SnapshotCursorReader {
    fun <T : Any> read(
        cursor: Cursor?, signal: CancellationSignal, maxRecords: Int,
        map: (Cursor) -> T?
    ): List<T> {
        val actual = cursor ?: throw IllegalStateException("Provider returned a null cursor")
        val snapshot = actual.use { source ->
            // Even cancellation or a bad limit must release an acquired cursor.
            require(maxRecords in 1..10000)
            signal.throwIfCanceled()
            val output = ArrayList<T>()
            var scanned = 0
            while (output.size < maxRecords && source.moveToNext()) {
                signal.throwIfCanceled()
                if (++scanned > maxRecords * 10) {
                    throw IllegalStateException("Provider exceeded the cursor scan budget")
                }
                map(source)?.let(output::add)
            }
            signal.throwIfCanceled()
            output
        }
        // Cursor.close() itself can signal cancellation after the last row
        // check. Do not publish an apparently complete contacts/call-log result.
        signal.throwIfCanceled()
        return snapshot
    }
}
