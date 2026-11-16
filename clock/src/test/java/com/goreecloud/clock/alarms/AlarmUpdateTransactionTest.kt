package com.goreecloud.clock.alarms

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmUpdateTransactionTest {
    private fun test(
        enabled: Boolean,
        saved: Boolean = true,
        scheduled: Boolean = true,
        disabledSaved: Boolean = true,
    ): Pair<AlarmUpdateTransaction.Result, List<String>> {
        val calls = mutableListOf<String>()
        val result = AlarmUpdateTransaction.update(
            enabled = enabled,
            save = { calls += "save"; saved },
            schedule = { calls += "schedule"; scheduled },
            saveDisabled = { calls += "save-disabled"; disabledSaved },
            cancel = { calls += "cancel" },
        )
        return result to calls
    }

    @Test fun failedEditNeverArmsOrCancelsPreviouslyScheduledAlarm() {
        assertEquals(
            AlarmUpdateTransaction.Result.SAVE_FAILED to listOf("save"),
            test(enabled = true, saved = false),
        )
    }

    @Test fun failedDisableDoesNotCancelPreviouslyScheduledAlarm() {
        assertEquals(
            AlarmUpdateTransaction.Result.SAVE_FAILED to listOf("save"),
            test(enabled = false, saved = false),
        )
    }

    @Test fun successfulEnableSavesBeforeScheduling() {
        assertEquals(
            AlarmUpdateTransaction.Result.SAVED_AND_ARMED to listOf("save", "schedule"),
            test(enabled = true),
        )
    }

    @Test fun successfulDisableSavesBeforeCanceling() {
        assertEquals(
            AlarmUpdateTransaction.Result.SAVED_DISABLED to listOf("save", "cancel"),
            test(enabled = false),
        )
    }

    @Test fun scheduleFailureDurablyDisablesThenCancels() {
        assertEquals(
            AlarmUpdateTransaction.Result.SCHEDULE_FAILED to
                listOf("save", "schedule", "save-disabled", "cancel"),
            test(enabled = true, scheduled = false),
        )
    }

    @Test fun failedFallbackSaveIsExplicitAndCanceled() {
        assertEquals(
            AlarmUpdateTransaction.Result.DISABLE_SAVE_FAILED to
                listOf("save", "schedule", "save-disabled", "cancel"),
            test(enabled = true, scheduled = false, disabledSaved = false),
        )
    }
}
