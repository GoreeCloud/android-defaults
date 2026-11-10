package com.goreecloud.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactExportAuthorizationTest {
    @Test fun onlyCurrentPermissionAndExplicitPickerChoiceAllowExport() {
        for (permission in listOf(false, true)) {
            for (picker in listOf(false, true)) {
                for (snapshot in listOf(false, true)) {
                    val actual = ContactExportAuthorization.mayStart(permission, picker, snapshot)
                    if (permission && picker && snapshot) assertTrue(actual)
                    else assertFalse(actual)
                }
            }
        }
    }

    @Test fun aRevokedPermissionCannotReusePreviousConsentOrSnapshot() {
        assertTrue(ContactExportAuthorization.mayStart(true, true, true))
        assertFalse(ContactExportAuthorization.mayStart(false, true, true))
        assertFalse(ContactExportAuthorization.mayStart(true, false, true))
        assertFalse(ContactExportAuthorization.mayStart(true, true, false))
    }
}
