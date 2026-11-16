package com.goreecloud.contacts

/**
 * The document picker grants access to a destination, not ongoing permission to
 * reuse a Contacts snapshot. Recheck READ_CONTACTS at handoff and before writes.
 * This decision is not cached between lifecycle events or worker execution.
 */
internal object ContactExportAuthorization {
    fun mayStart(
        readContactsGranted: Boolean,
        pickerConfirmed: Boolean,
        selectedSnapshotPresent: Boolean
    ): Boolean = readContactsGranted && pickerConfirmed && selectedSnapshotPresent
}
