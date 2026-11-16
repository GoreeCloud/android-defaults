package com.goreecloud.clock.alarms

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerStartTransactionTest {
    @Test fun failedSaveNeverArmsOrRestores() {
        val calls = mutableListOf<String>()
        val result = TimerStartTransaction.start(
            save = { calls += "save"; false },
            schedule = { calls += "schedule"; true },
            restore = { calls += "restore"; true },
        )
        assertEquals(TimerStartTransaction.Result.SAVE_FAILED, result)
        assertEquals(listOf("save"), calls)
    }

    @Test fun successfulStartPersistsBeforeArming() {
        val calls = mutableListOf<String>()
        val result = TimerStartTransaction.start(
            save = { calls += "save"; true },
            schedule = { calls += "schedule"; true },
            restore = { calls += "restore"; true },
        )
        assertEquals(TimerStartTransaction.Result.STARTED, result)
        assertEquals(listOf("save", "schedule"), calls)
    }

    @Test fun scheduleFailureRestoresPriorTimerState() {
        val calls = mutableListOf<String>()
        val result = TimerStartTransaction.start(
            save = { calls += "save"; true },
            schedule = { calls += "schedule"; false },
            restore = { calls += "restore"; true },
        )
        assertEquals(TimerStartTransaction.Result.SCHEDULE_FAILED, result)
        assertEquals(listOf("save", "schedule", "restore"), calls)
    }

    @Test fun failedRestorationIsExplicit() {
        val calls = mutableListOf<String>()
        val result = TimerStartTransaction.start(
            save = { calls += "save"; true },
            schedule = { calls += "schedule"; false },
            restore = { calls += "restore"; false },
        )
        assertEquals(TimerStartTransaction.Result.RESTORE_FAILED, result)
        assertEquals(listOf("save", "schedule", "restore"), calls)
    }
}
