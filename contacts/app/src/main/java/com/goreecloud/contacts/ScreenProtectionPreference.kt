package com.goreecloud.contacts

import android.content.SharedPreferences

/**
 * GoreeShield: stores only the user's opt-in screen-capture preference.
 * No contact details, screenshot contents or external device state are stored.
 */
class ScreenProtectionPreference(private val preferences: SharedPreferences) {
    companion object { private const val KEY = "secure_screen_enabled" }

    fun isEnabled(): Boolean = preferences.getBoolean(KEY, false)

    fun setEnabled(enabled: Boolean) {
        if (enabled) preferences.edit().putBoolean(KEY, true).apply()
        else preferences.edit().remove(KEY).apply()
    }
}
