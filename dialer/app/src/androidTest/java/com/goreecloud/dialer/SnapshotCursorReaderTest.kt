package com.goreecloud.dialer

import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** All rows are synthetic; never read a real device's call or contact data. */
@RunWith(AndroidJUnit4::class)
class SnapshotCursorReaderTest {
    private fun cursor(vararg values: String): MatrixCursor =
        MatrixCursor(arrayOf("synthetic_number")).apply {
            values.forEach { addRow(arrayOf<Any?>(it)) }
        }

    private fun fails(block: () -> Unit) {
        try {
            block()
            fail("Must not accept an incomplete provider snapshot")
        } catch (_: RuntimeException) {
            // No partial return is possible.
        }
    }

    @Test fun emptyCursorIsAValidEmptyResultAndAlwaysCloses() {
        val source = cursor()
        val result = SnapshotCursorReader.read(source, CancellationSignal(), 10) {
            it.getString(0)
        }
        assertTrue(result.isEmpty())
        assertTrue(source.isClosed)
    }

    @Test fun nullCursorIsNotAnEmptyResult() {
        fails { SnapshotCursorReader.read<String>(null, CancellationSignal(), 10) { "row" } }
    }

    @Test fun readsSyntheticNumbersAndAppliesVisibleRecordCap() {
        val source = cursor("555-0001", "555-0002", "555-0003")
        val found = SnapshotCursorReader.read(source, CancellationSignal(), 2) {
            it.getString(0)
        }
        assertEquals(listOf("555-0001", "555-0002"), found)
        assertTrue(source.isClosed)
    }

    @Test fun ignoresInvalidRowsWithoutExposingThem() {
        val source = cursor("", "555-0101")
        val found = SnapshotCursorReader.read(source, CancellationSignal(), 2) {
            it.getString(0)?.takeIf { number -> number.isNotBlank() }
        }
        assertEquals(listOf("555-0101"), found)
    }

    @Test fun partialReadExceptionDiscardsReturnAndClosesCursor() {
        val source = cursor("555-0101", "MALFORMED")
        fails {
            SnapshotCursorReader.read(source, CancellationSignal(), 10) {
                it.getString(0).also { value ->
                    if (value == "MALFORMED") throw IllegalStateException("synthetic failure")
                }
            }
        }
        assertTrue(source.isClosed)
    }

    @Test fun cancellationBeforeReadDoesNotPublishOrTouchRows() {
        val source = cursor("555-0101")
        val signal = CancellationSignal().apply { cancel() }
        try {
            SnapshotCursorReader.read(source, signal, 10) { it.getString(0) }
            fail("Canceled read must not complete")
        } catch (_: OperationCanceledException) {
            assertTrue("An already-acquired cursor is always released", source.isClosed)
        } finally {
            source.close()
        }
    }

    @Test fun canceledDuringIterationClosesAndRefusesPartialResults() {
        val source = cursor("555-0101", "555-0102")
        val signal = CancellationSignal()
        try {
            SnapshotCursorReader.read(source, signal, 10) {
                signal.cancel()
                it.getString(0)
            }
            fail("Canceled read must not complete")
        } catch (_: OperationCanceledException) {
            assertTrue(source.isClosed)
        }
    }

    @Test fun repeatedInvalidRowsHitScanBudgetAndFailClosed() {
        val source = cursor(*Array(11) { "" })
        fails {
            SnapshotCursorReader.read(source, CancellationSignal(), 1) { null as String? }
        }
        assertTrue(source.isClosed)
    }

    @Test fun invalidRecordLimitIsRejected() {
        fails { SnapshotCursorReader.read(cursor("555-0100"), CancellationSignal(), 0) { it.getString(0) } }
    }
}
