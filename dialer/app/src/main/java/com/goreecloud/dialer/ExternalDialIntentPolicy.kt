package com.goreecloud.dialer

/**
 * Untrusted ACTION_DIAL data can be supplied by any installed application.
 * Admit only a bounded, valid dial string before placing it in our keypad,
 * saved Activity draft or contact-search suggestions. This never places calls.
 */
internal object ExternalDialIntentPolicy {
    private const val DIAL_ACTION = "android.intent.action.DIAL"
    private const val TELEPHONE_SCHEME = "tel"

    fun normalizedNumber(action: String?, scheme: String?, address: String?): String? {
        if (action != DIAL_ACTION || scheme != TELEPHONE_SCHEME) return null
        return PhoneMatcher.cleanNumber(address ?: "")
    }
}
