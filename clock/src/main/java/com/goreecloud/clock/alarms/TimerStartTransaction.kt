package com.goreecloud.clock.alarms

/**
 * Order-sensitive, Android-independent timer start transaction.
 *
 * Never arm an alarm if its receiver cannot recover a saved deadline.
 * If Android refuses scheduling after the save, attempt to restore the
 * previous timer state; surface both possible failures to the caller.
 */
internal object TimerStartTransaction {
    enum class Result { STARTED, SAVE_FAILED, SCHEDULE_FAILED, RESTORE_FAILED }

    fun start(
        save: () -> Boolean,
        schedule: () -> Boolean,
        restore: () -> Boolean,
    ): Result {
        if (!save()) return Result.SAVE_FAILED
        if (schedule()) return Result.STARTED
        return if (restore()) Result.SCHEDULE_FAILED else Result.RESTORE_FAILED
    }
}
