package com.goreecloud.clock.alarms

/**
 * Order-sensitive alarm edit/delete transaction, independent of Android.
 *
 * The receiver must never be scheduled against an unwritten SharedPreferences
 * alarm record. Failed storage leaves existing Android scheduling untouched.
 * If a later schedule fails, persist a disabled state before canceling.
 */
internal object AlarmUpdateTransaction {
    enum class Result {
        SAVED_AND_ARMED,
        SAVED_DISABLED,
        SAVE_FAILED,
        SCHEDULE_FAILED,
        DISABLE_SAVE_FAILED,
    }

    fun update(
        enabled: Boolean,
        save: () -> Boolean,
        schedule: () -> Boolean,
        saveDisabled: () -> Boolean,
        cancel: () -> Unit,
    ): Result {
        if (!save()) return Result.SAVE_FAILED
        if (!enabled) {
            cancel()
            return Result.SAVED_DISABLED
        }
        if (schedule()) return Result.SAVED_AND_ARMED

        // The new record was saved, but Android refused to arm it. Persist
        // an honest disabled state before canceling any prior PendingIntent.
        val disabled = saveDisabled()
        cancel()
        return if (disabled) Result.SCHEDULE_FAILED else Result.DISABLE_SAVE_FAILED
    }
}
